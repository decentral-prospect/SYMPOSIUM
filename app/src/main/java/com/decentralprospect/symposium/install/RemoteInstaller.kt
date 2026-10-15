package com.decentralprospect.symposium

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hierynomus.sshj.key.KeyAlgorithm
import com.hierynomus.sshj.key.KeyAlgorithms
import com.hierynomus.sshj.transport.cipher.BlockCiphers
import com.hierynomus.sshj.transport.cipher.ChachaPolyCiphers
import com.hierynomus.sshj.transport.cipher.GcmCiphers
import com.hierynomus.sshj.transport.kex.DHGroups
import com.hierynomus.sshj.transport.kex.ExtInfoClientFactory
import com.hierynomus.sshj.transport.mac.Macs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.cipher.Cipher as SshCipher
import net.schmizz.sshj.transport.kex.Curve25519SHA256
import net.schmizz.sshj.transport.kex.DHGexSHA256
import net.schmizz.sshj.transport.kex.DHGroupData
import net.schmizz.sshj.transport.kex.ECDHNistP
import net.schmizz.sshj.transport.kex.KeyExchange
import net.schmizz.sshj.transport.mac.MAC
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.AlgorithmParameters
import java.security.AlgorithmParametersSpi
import java.security.Key
import java.security.KeyFactory
import java.security.KeyFactorySpi
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.cert.CertificateException
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.interfaces.RSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.InvalidParameterSpecException
import java.security.spec.KeySpec
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.KeyAgreement
import javax.crypto.spec.DHParameterSpec
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

data class OpenRoomInfo(
    val name: String,
    val roomInstance: String = "",
    val moderatorKey: String = "",
    val moderatorKeyAvailable: Boolean = moderatorKey.isNotBlank(),
    val requiredVersion: String = "",
    val muteAllByDefault: Boolean = false,
    val admitWithoutApproval: Boolean = false
)

enum class RelayCapacityPreset(val speakers: Int, val listeners: Int) {
    STANDARD(5, 50),
    SPEAKERS_10(10, 100),
    SPEAKERS_15(15, 250),
    SPEAKERS_25(25, 700),
    CUSTOM(0, 0)
}

internal fun relayCapacityPresetFromStoredName(name: String): RelayCapacityPreset = when (name) {
    "RAM_4_GB" -> RelayCapacityPreset.SPEAKERS_10
    "CPU_2_RAM_8_GB" -> RelayCapacityPreset.SPEAKERS_15
    "CPU_4_RAM_16_GB" -> RelayCapacityPreset.SPEAKERS_25
    else -> RelayCapacityPreset.entries.firstOrNull { it.name == name } ?: RelayCapacityPreset.CUSTOM
}

data class RelayCapacityConfig(
    val preset: RelayCapacityPreset,
    val maxActivePeers: Int,
    val maxPendingLobbyPeers: Int,
    val pendingIceCandidatesPerPeer: Int,
    val pendingIceBytesPerPeer: Long,
    val pendingIceBytesTotal: Long,
    val joinRatePerMinute: Int,
    val maxConcurrentJoins: Int,
    val maxPreJoinConnections: Int = 512,
    val maxPreJoinConnectionsPerIp: Int = 32,
    val preJoinTimeoutSeconds: Int = 15
) {
    fun validationError(): TextId? {
        if (maxActivePeers !in 1..100_000) return TextId.ACTIVE_PEERS_RANGE
        if (maxPendingLobbyPeers !in 1..100_000) return TextId.LOBBY_PEERS_RANGE
        if (maxPendingLobbyPeers == maxActivePeers) {
            return TextId.LOBBY_LIMIT_MUST_DIFFER
        }
        if (pendingIceCandidatesPerPeer !in 1..4_096) return TextId.ICE_CANDIDATE_RANGE
        if (pendingIceBytesPerPeer !in 4_096L..64L * 1024 * 1024) {
            return TextId.ICE_MEMORY_PER_PEER_RANGE
        }
        if (pendingIceBytesTotal !in 4_096L..64L * 1024 * 1024 * 1024) {
            return TextId.ICE_TOTAL_MEMORY_RANGE
        }
        if (pendingIceBytesTotal < pendingIceBytesPerPeer) {
            return TextId.ICE_TOTAL_BELOW_PER_PEER
        }
        if (joinRatePerMinute !in 1..10_000) return TextId.JOIN_RATE_RANGE
        if (maxConcurrentJoins !in 1..10_000) return TextId.CONCURRENT_JOINS_RANGE
        if (maxPreJoinConnections !in 1..100_000) return TextId.INVALID_RELAY_CAPACITY
        if (maxPreJoinConnectionsPerIp !in 1..maxPreJoinConnections) return TextId.INVALID_RELAY_CAPACITY
        if (preJoinTimeoutSeconds !in 1..300) return TextId.INVALID_RELAY_CAPACITY
        return null
    }

    companion object {
        fun forPreset(preset: RelayCapacityPreset): RelayCapacityConfig = when (preset) {
            RelayCapacityPreset.STANDARD -> RelayCapacityConfig(
                preset = preset,
                maxActivePeers = 100,
                maxPendingLobbyPeers = 200,
                pendingIceCandidatesPerPeer = 64,
                pendingIceBytesPerPeer = 256L * 1024,
                pendingIceBytesTotal = 16L * 1024 * 1024,
                joinRatePerMinute = 12,
                maxConcurrentJoins = 16,
                maxPreJoinConnections = 256
            )
            RelayCapacityPreset.SPEAKERS_10 -> RelayCapacityConfig(
                preset = preset,
                maxActivePeers = 200,
                maxPendingLobbyPeers = 400,
                pendingIceCandidatesPerPeer = 64,
                pendingIceBytesPerPeer = 256L * 1024,
                pendingIceBytesTotal = 32L * 1024 * 1024,
                joinRatePerMinute = 12,
                maxConcurrentJoins = 32,
                maxPreJoinConnections = 512
            )
            RelayCapacityPreset.SPEAKERS_15 -> RelayCapacityConfig(
                preset = preset,
                maxActivePeers = 400,
                maxPendingLobbyPeers = 800,
                pendingIceCandidatesPerPeer = 64,
                pendingIceBytesPerPeer = 256L * 1024,
                pendingIceBytesTotal = 64L * 1024 * 1024,
                joinRatePerMinute = 12,
                maxConcurrentJoins = 64,
                maxPreJoinConnections = 1_024
            )
            RelayCapacityPreset.SPEAKERS_25 -> RelayCapacityConfig(
                preset = preset,
                maxActivePeers = 1_000,
                maxPendingLobbyPeers = 2_000,
                pendingIceCandidatesPerPeer = 64,
                pendingIceBytesPerPeer = 256L * 1024,
                pendingIceBytesTotal = 128L * 1024 * 1024,
                joinRatePerMinute = 12,
                maxConcurrentJoins = 128,
                maxPreJoinConnections = 2_048
            )
            RelayCapacityPreset.CUSTOM -> forPreset(RelayCapacityPreset.STANDARD).copy(preset = preset)
        }
    }
}

internal fun relayDurationSeconds(seconds: Int): String = "${seconds}s"

internal fun relayIceUdpPortRange(currentRange: IntRange, capacity: RelayCapacityConfig): IntRange {
    require(currentRange.first in 1..65_535 && currentRange.last in currentRange.first..65_535)
    val requiredPorts = ((capacity.maxActivePeers + capacity.maxConcurrentJoins) * 4).coerceIn(512, 32_768)
    if (currentRange.last - currentRange.first + 1 >= requiredPorts) return currentRange
    val first = minOf(currentRange.first, 65_536 - requiredPorts)
    return first until first + requiredPorts
}

data class DeploymentProfile(
    val appDir: String,
    val serviceName: String,
    val instanceName: String,
    val serviceUser: String,
    val serviceGroup: String,
    val binaryDirName: String,
    val stateDirName: String,
    val identityDirName: String,
    val binaryName: String,
    val databaseName: String,
    val tokenName: String,
    val certificateName: String,
    val privateKeyName: String,
    val dbTable: String,
    val dbRoomColumn: String,
    val dbKeyColumn: String,
    val publicPort: Int,
    val adminPort: Int,
    val iceUdpPortMin: Int,
    val iceUdpPortMax: Int,
    val firewallTag: String,
    val stagingDir: String,
    val adminToken: String,
    val relayCapacity: RelayCapacityConfig = RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD)
)

private fun relayCapacityToJson(config: RelayCapacityConfig): JSONObject = JSONObject()
    .put("preset", config.preset.name)
    .put("maxActivePeers", config.maxActivePeers)
    .put("maxPendingLobbyPeers", config.maxPendingLobbyPeers)
    .put("pendingIceCandidatesPerPeer", config.pendingIceCandidatesPerPeer)
    .put("pendingIceBytesPerPeer", config.pendingIceBytesPerPeer)
    .put("pendingIceBytesTotal", config.pendingIceBytesTotal)
    .put("joinRatePerMinute", config.joinRatePerMinute)
    .put("maxConcurrentJoins", config.maxConcurrentJoins)
    .put("maxPreJoinConnections", config.maxPreJoinConnections)
    .put("maxPreJoinConnectionsPerIp", config.maxPreJoinConnectionsPerIp)
    .put("preJoinTimeoutSeconds", config.preJoinTimeoutSeconds)

private fun relayCapacityFromJson(value: JSONObject?): RelayCapacityConfig {
    val defaults = RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD)
    if (value == null) return defaults
    val preset = relayCapacityPresetFromStoredName(value.optString("preset"))
    val parsed = RelayCapacityConfig(
        preset = preset,
        maxActivePeers = value.optInt("maxActivePeers", defaults.maxActivePeers),
        maxPendingLobbyPeers = value.optInt("maxPendingLobbyPeers", defaults.maxPendingLobbyPeers),
        pendingIceCandidatesPerPeer = value.optInt(
            "pendingIceCandidatesPerPeer",
            defaults.pendingIceCandidatesPerPeer
        ),
        pendingIceBytesPerPeer = value.optLong("pendingIceBytesPerPeer", defaults.pendingIceBytesPerPeer),
        pendingIceBytesTotal = value.optLong("pendingIceBytesTotal", defaults.pendingIceBytesTotal),
        joinRatePerMinute = value.optInt("joinRatePerMinute", defaults.joinRatePerMinute),
        maxConcurrentJoins = value.optInt("maxConcurrentJoins", defaults.maxConcurrentJoins),
        maxPreJoinConnections = value.optInt("maxPreJoinConnections", defaults.maxPreJoinConnections),
        maxPreJoinConnectionsPerIp = value.optInt(
            "maxPreJoinConnectionsPerIp",
            defaults.maxPreJoinConnectionsPerIp
        ),
        preJoinTimeoutSeconds = value.optInt("preJoinTimeoutSeconds", defaults.preJoinTimeoutSeconds)
    ).takeIf { it.validationError() == null } ?: defaults
    return normalizeRelayCapacityConfig(parsed)
}

internal fun normalizeRelayCapacityConfig(config: RelayCapacityConfig): RelayCapacityConfig {
    val presets = listOf(
        RelayCapacityPreset.STANDARD,
        RelayCapacityPreset.SPEAKERS_10,
        RelayCapacityPreset.SPEAKERS_15,
        RelayCapacityPreset.SPEAKERS_25
    )
    val currentMatch = presets.firstOrNull { preset ->
        config.copy(preset = preset) == RelayCapacityConfig.forPreset(preset)
    }
    if (currentMatch != null) return config.copy(preset = currentMatch)

    val legacyMatch = presets.firstOrNull { preset ->
        config.copy(preset = preset) in legacyRelayCapacityConfigs(preset)
    }
    if (legacyMatch != null) return RelayCapacityConfig.forPreset(legacyMatch)

    return if (config.preset == RelayCapacityPreset.CUSTOM) {
        config
    } else {
        RelayCapacityConfig.forPreset(config.preset)
    }
}

private fun legacyRelayCapacityConfigs(preset: RelayCapacityPreset): List<RelayCapacityConfig> {
    val index = when (preset) {
        RelayCapacityPreset.STANDARD -> 0
        RelayCapacityPreset.SPEAKERS_10 -> 1
        RelayCapacityPreset.SPEAKERS_15 -> 2
        RelayCapacityPreset.SPEAKERS_25 -> 3
        RelayCapacityPreset.CUSTOM -> return emptyList()
    }
    val previous = RelayCapacityConfig(
        preset = preset,
        maxActivePeers = listOf(250, 500, 1_000, 2_000)[index],
        maxPendingLobbyPeers = listOf(500, 1_000, 2_000, 4_000)[index],
        pendingIceCandidatesPerPeer = 64,
        pendingIceBytesPerPeer = 256L * 1024,
        pendingIceBytesTotal = (16L shl index) * 1024 * 1024,
        joinRatePerMinute = 12,
        maxConcurrentJoins = listOf(16, 16, 32, 64)[index]
    )
    return listOf(previous, previous.copy(
        maxActivePeers = listOf(100, 250, 500, 1_000)[index],
        maxPendingLobbyPeers = listOf(250, 500, 1_000, 2_000)[index]
    ))
}

internal data class RelayReleaseMetadata(
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val bundledSha256: String = sha256
)

internal fun configuredRelayRelease(): RelayReleaseMetadata = RelayReleaseMetadata(
    version = BuildConfig.RELAY_RELEASE_VERSION,
    downloadUrl = BuildConfig.RELAY_BINARY_URL,
    sha256 = BuildConfig.RELAY_BINARY_SHA256,
    bundledSha256 = BuildConfig.RELAY_BUNDLED_SHA256
)

private fun deploymentProfileToJson(
    profile: DeploymentProfile,
    includeAdminToken: Boolean = true
): JSONObject = JSONObject()
    .put("appDir", profile.appDir)
    .put("serviceName", profile.serviceName)
    .put("instanceName", profile.instanceName)
    .put("serviceUser", profile.serviceUser)
    .put("serviceGroup", profile.serviceGroup)
    .put("binaryDirName", profile.binaryDirName)
    .put("stateDirName", profile.stateDirName)
    .put("identityDirName", profile.identityDirName)
    .put("binaryName", profile.binaryName)
    .put("databaseName", profile.databaseName)
    .put("tokenName", profile.tokenName)
    .put("certificateName", profile.certificateName)
    .put("privateKeyName", profile.privateKeyName)
    .put("dbTable", profile.dbTable)
    .put("dbRoomColumn", profile.dbRoomColumn)
    .put("dbKeyColumn", profile.dbKeyColumn)
    .put("publicPort", profile.publicPort)
    .put("adminPort", profile.adminPort)
    .put("iceUdpPortMin", profile.iceUdpPortMin)
    .put("iceUdpPortMax", profile.iceUdpPortMax)
    .put("firewallTag", profile.firewallTag)
    .put("stagingDir", profile.stagingDir)
    .apply { if (includeAdminToken) put("adminToken", profile.adminToken) }
    .put("relayCapacity", relayCapacityToJson(profile.relayCapacity))

private fun deploymentProfileFromJson(value: JSONObject?): DeploymentProfile? {
    if (value == null) return null
    return runCatching {
        DeploymentProfile(
            appDir = value.getString("appDir"),
            serviceName = value.getString("serviceName"),
            instanceName = value.optString("instanceName").ifBlank { value.getString("serviceName") },
            serviceUser = value.getString("serviceUser"),
            serviceGroup = value.getString("serviceGroup"),
            binaryDirName = value.getString("binaryDirName"),
            stateDirName = value.getString("stateDirName"),
            identityDirName = value.getString("identityDirName"),
            binaryName = value.getString("binaryName"),
            databaseName = value.getString("databaseName"),
            tokenName = value.getString("tokenName"),
            certificateName = value.getString("certificateName"),
            privateKeyName = value.getString("privateKeyName"),
            dbTable = value.getString("dbTable"),
            dbRoomColumn = value.getString("dbRoomColumn"),
            dbKeyColumn = value.getString("dbKeyColumn"),
            publicPort = value.getInt("publicPort"),
            adminPort = value.getInt("adminPort"),
            iceUdpPortMin = value.getInt("iceUdpPortMin"),
            iceUdpPortMax = value.getInt("iceUdpPortMax"),
            firewallTag = value.getString("firewallTag"),
            stagingDir = value.getString("stagingDir"),
            adminToken = value.getString("adminToken"),
            relayCapacity = relayCapacityFromJson(value.optJSONObject("relayCapacity"))
        )
    }.getOrNull()
}

internal fun parseOpenRoomsJson(raw: String): List<OpenRoomInfo> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return emptyList()

    return runCatching {
        val root = JSONObject(trimmed)
        parseOpenRoomsArray(root.optJSONArray("openRooms") ?: JSONArray())
    }.getOrElse {
        emptyList()
    }
}

private fun parseOpenRoomsArray(
    arr: JSONArray,
    requireRoomInstance: Boolean = true
): List<OpenRoomInfo> {
    val out = ArrayList<OpenRoomInfo>(arr.length())

    for (i in 0 until arr.length()) {
        when (val item = arr.opt(i)) {
            is JSONObject -> {
                val name = item.optString("room")
                    .ifBlank { item.optString("name") }
                    .trim()

                val roomInstance = item.optString("roomInstance").trim()

                val moderatorKey = item.optString("moderator_key")
                    .ifBlank { item.optString("moderatorKey") }
                    .trim()

                val requiredVersion = item.optString("requiredVersion")
                    .trim()
                    .takeIf(::isCanonicalVersion)
                    .orEmpty()

                require(isValidRelayRoomName(name)) { "invalid room name" }
                require(
                    isValidRoomInstance(roomInstance) || (!requireRoomInstance && roomInstance.isBlank())
                ) { "invalid room instance" }
                if (name.isNotBlank()) {
                    out += OpenRoomInfo(
                        name = name,
                        roomInstance = roomInstance,
                        moderatorKey = moderatorKey,
                        moderatorKeyAvailable = item.optBoolean(
                            "moderatorKeyAvailable",
                            moderatorKey.isNotBlank()
                        ),
                        requiredVersion = requiredVersion,
                        muteAllByDefault = item.optBoolean("muteAllByDefault", false),
                        admitWithoutApproval = item.optBoolean("admitWithoutApproval", false)
                    )
                }
            }

            is String -> {
                val name = item.trim()
                require(isValidRelayRoomName(name)) { "invalid room name" }
                if (name.isNotBlank()) {
                    out += OpenRoomInfo(name = name)
                }
            }

            else -> {
                val name = arr.optString(i).trim()
                require(name == "null" || isValidRelayRoomName(name)) { "invalid room name" }
                if (name.isNotBlank() && name != "null") {
                    out += OpenRoomInfo(name = name)
                }
            }
        }
    }

    return out
}

private fun isValidRelayRoomName(value: String): Boolean =
    value.isNotBlank() && value.length <= 128 && value.none(Char::isISOControl)

internal fun isValidRoomInstance(value: String): Boolean =
    value.isNotBlank() && value.length <= 64 && value.all {
        it.isLetterOrDigit() || it == '-' || it == '_'
    }

private fun openRoomsToJsonArray(
    rooms: List<OpenRoomInfo>,
    includeModeratorKeys: Boolean = true
): JSONArray {
    val arr = JSONArray()
    rooms.forEach { room ->
        arr.put(
            JSONObject()
                .put("room", room.name)
                .put("roomInstance", room.roomInstance)
                .apply {
                    if (includeModeratorKeys) put("moderator_key", room.moderatorKey)
                }
                .put("moderatorKeyAvailable", room.moderatorKeyAvailable || room.moderatorKey.isNotBlank())
                .put("requiredVersion", room.requiredVersion)
                .put("muteAllByDefault", room.muteAllByDefault)
                .put("admitWithoutApproval", room.admitWithoutApproval)
        )
    }
    return arr
}

/**
 * SSHJ asks JCA for "ECDSA", while some Android crypto providers expose the same key factory
 * under "EC" only. Some providers also omit AlgorithmParameters("EC") even though their EC key
 * generator and ECDH implementation work. Repair only the missing aliases/primitives and leave
 * every working platform implementation untouched.
 */
internal fun enableAndroidEcdsaCompatibility(): Boolean {
    val parametersMissing = runCatching { AlgorithmParameters.getInstance("EC") }.isFailure
    val ecdsaFactoryMissing = runCatching { KeyFactory.getInstance("ECDSA") }.isFailure
    if (parametersMissing || ecdsaFactoryMissing) {
        val providerInstalled = runCatching {
            synchronized(Security::class.java) {
                if (Security.getProvider(AndroidEcProvider.NAME) == null) {
                    Security.addProvider(AndroidEcProvider())
                }
            }
        }.isSuccess
        if (!providerInstalled) return false
    }
    return runCatching {
        AlgorithmParameters.getInstance("EC")
        KeyFactory.getInstance("ECDSA")
    }.isSuccess
}

@Suppress("DEPRECATION")
private class AndroidEcProvider : Provider(
    NAME,
    1.0,
    "EC AlgorithmParameters compatibility bridge for Android"
) {
    init {
        put("AlgorithmParameters.EC", AndroidEcAlgorithmParameters::class.java.name)
        put("KeyFactory.ECDSA", AndroidEcdsaKeyFactory::class.java.name)
    }

    companion object {
        const val NAME = "SymposiumAndroidEC"
    }
}

class AndroidEcdsaKeyFactory : KeyFactorySpi() {
    private val delegate: KeyFactory by lazy { KeyFactory.getInstance("EC") }

    override fun engineGeneratePublic(keySpec: KeySpec): PublicKey =
        delegate.generatePublic(keySpec)

    override fun engineGeneratePrivate(keySpec: KeySpec): PrivateKey =
        delegate.generatePrivate(keySpec)

    override fun <T : KeySpec> engineGetKeySpec(key: Key, keySpec: Class<T>): T =
        delegate.getKeySpec(key, keySpec)

    override fun engineTranslateKey(key: Key): Key = delegate.translateKey(key)
}

class AndroidEcAlgorithmParameters : AlgorithmParametersSpi() {
    private var namedCurve: ECGenParameterSpec? = null
    private var parameters: ECParameterSpec? = null

    override fun engineInit(paramSpec: AlgorithmParameterSpec) {
        val curve = paramSpec as? ECGenParameterSpec
            ?: throw InvalidParameterSpecException("ECGenParameterSpec is required")
        try {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(curve)
            val publicKey = generator.generateKeyPair().public as ECPublicKey
            namedCurve = curve
            parameters = publicKey.params
        } catch (error: Exception) {
            throw InvalidParameterSpecException(error.message).apply { initCause(error) }
        }
    }

    override fun engineInit(params: ByteArray) {
        throw IOException("encoded EC parameters are not supported")
    }

    override fun engineInit(params: ByteArray, format: String) {
        throw IOException("encoded EC parameters are not supported")
    }

    override fun <T : AlgorithmParameterSpec> engineGetParameterSpec(
        paramSpec: Class<T>
    ): T {
        val value: AlgorithmParameterSpec = when {
            paramSpec.isAssignableFrom(ECParameterSpec::class.java) -> parameters
            paramSpec.isAssignableFrom(ECGenParameterSpec::class.java) -> namedCurve
            else -> null
        } ?: throw InvalidParameterSpecException("EC parameters are not initialized")
        return paramSpec.cast(value)
    }

    override fun engineGetEncoded(): ByteArray {
        throw IOException("encoded EC parameters are not supported")
    }

    override fun engineGetEncoded(format: String): ByteArray {
        throw IOException("encoded EC parameters are not supported")
    }

    override fun engineToString(): String = namedCurve?.name ?: "uninitialized EC parameters"
}

/**
 * A capability-based SSH configuration. Android API levels are not a reliable proxy for crypto
 * support because vendors can replace providers and hardware-backed implementations. Every
 * advertised primitive below has therefore been initialized successfully in this process.
 */
internal class AndroidCompatibleSshConfig : DefaultSecurityProviderConfig() {
    override fun initKeyExchangeFactories() {
        val capabilities = androidSshCapabilities
        val factories = mutableListOf<Factory.Named<KeyExchange>>()
        if (capabilities.x25519) {
            factories += Curve25519SHA256.Factory()
            factories += Curve25519SHA256.FactoryLibSsh()
        }
        if ("secp256r1" in capabilities.ecdsaCurves) factories += ECDHNistP.Factory256()
        if ("secp384r1" in capabilities.ecdsaCurves) factories += ECDHNistP.Factory384()
        if ("secp521r1" in capabilities.ecdsaCurves) factories += ECDHNistP.Factory521()
        if (capabilities.dhGroup14) {
            factories += DHGroups.Group14SHA256()
            factories += DHGexSHA256.Factory()
        }
        check(factories.isNotEmpty()) { "No compatible SSH key exchange is available" }
        factories += ExtInfoClientFactory()
        setKeyExchangeFactories(factories)
    }

    override fun initKeyAlgorithms() {
        val capabilities = androidSshCapabilities
        val algorithms = mutableListOf<Factory.Named<KeyAlgorithm>>()
        if (capabilities.ed25519) {
            algorithms += KeyAlgorithms.EdDSA25519CertV01()
            algorithms += KeyAlgorithms.EdDSA25519()
        }
        if ("secp521r1" in capabilities.ecdsaCurves) {
            algorithms += KeyAlgorithms.ECDSASHANistp521CertV01()
            algorithms += KeyAlgorithms.ECDSASHANistp521()
        }
        if ("secp384r1" in capabilities.ecdsaCurves) {
            algorithms += KeyAlgorithms.ECDSASHANistp384CertV01()
            algorithms += KeyAlgorithms.ECDSASHANistp384()
        }
        if ("secp256r1" in capabilities.ecdsaCurves) {
            algorithms += KeyAlgorithms.ECDSASHANistp256CertV01()
            algorithms += KeyAlgorithms.ECDSASHANistp256()
        }
        // Android Keystore implementations can advertise SHA-512 RSA support but fail when a
        // hardware-backed private key performs the actual SSH signature. Prefer SHA-256, which
        // is supported by modern OpenSSH and is verified against the same Keystore key type.
        if (capabilities.rsaSha256) algorithms += KeyAlgorithms.RSASHA256()
        if (capabilities.rsaSha512) algorithms += KeyAlgorithms.RSASHA512()
        if (capabilities.rsaSha1) {
            algorithms += KeyAlgorithms.SSHRSACertV01()
            algorithms += KeyAlgorithms.SSHRSA()
        }
        check(algorithms.isNotEmpty()) { "No compatible SSH host key algorithm is available" }
        setKeyAlgorithms(algorithms)
    }

    override fun initCipherFactories() {
        setCipherFactories(androidSshCapabilities.cipherFactories)
    }

    override fun initMACFactories() {
        setMACFactories(androidSshCapabilities.macFactories)
    }
}

private data class AndroidSshCapabilities(
    val x25519: Boolean,
    val ecdsaCurves: Set<String>,
    val dhGroup14: Boolean,
    val ed25519: Boolean,
    val rsaSha512: Boolean,
    val rsaSha256: Boolean,
    val rsaSha1: Boolean,
    val cipherFactories: List<Factory.Named<SshCipher>>,
    val macFactories: List<Factory.Named<MAC>>
)

private val androidSshCapabilities: AndroidSshCapabilities by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED
) {
    detectAndroidSshCapabilities()
}

private fun detectAndroidSshCapabilities(): AndroidSshCapabilities {
    val ecdsaBridgeAvailable = enableAndroidEcdsaCompatibility()
    val ecdsaCurves = if (ecdsaBridgeAvailable) {
        listOf(
            "secp256r1" to "SHA256withECDSA",
            "secp384r1" to "SHA384withECDSA",
            "secp521r1" to "SHA512withECDSA"
        ).filter { (curve, signature) -> supportsEcSshPrimitive(curve, signature) }
            .mapTo(linkedSetOf()) { it.first }
    } else {
        emptySet()
    }

    val cipherFactories = usableCipherFactories(
        listOf(
            ChachaPolyCiphers.CHACHA_POLY_OPENSSH(),
            GcmCiphers.AES256GCM(),
            GcmCiphers.AES128GCM(),
            BlockCiphers.AES256CTR(),
            BlockCiphers.AES128CTR(),
            BlockCiphers.AES256CBC(),
            BlockCiphers.AES128CBC()
        )
    )
    check(cipherFactories.isNotEmpty()) { "No compatible SSH cipher is available" }

    val macFactories = usableMacFactories(
        listOf(
            Macs.HMACSHA2512Etm(),
            Macs.HMACSHA2256Etm(),
            Macs.HMACSHA2512(),
            Macs.HMACSHA2256(),
            Macs.HMACSHA1Etm(),
            Macs.HMACSHA1()
        )
    )
    check(macFactories.isNotEmpty()) { "No compatible SSH MAC is available" }

    return AndroidSshCapabilities(
        x25519 = supportsX25519(),
        ecdsaCurves = ecdsaCurves,
        dhGroup14 = supportsDhGroup14(),
        ed25519 = supportsEd25519(),
        rsaSha512 = supportsRsaSignature("SHA512withRSA"),
        rsaSha256 = supportsRsaSignature("SHA256withRSA"),
        rsaSha1 = supportsRsaSignature("SHA1withRSA"),
        cipherFactories = cipherFactories,
        macFactories = macFactories
    )
}

private fun supportsEcSshPrimitive(curve: String, signatureAlgorithm: String): Boolean =
    runCatching {
        val parameters = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec(curve))
        }
        parameters.getParameterSpec(ECParameterSpec::class.java)
        KeyFactory.getInstance("ECDSA")

        val pair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec(curve))
            generateKeyPair()
        }
        KeyAgreement.getInstance("ECDH").run {
            init(pair.private)
            doPhase(pair.public, true)
            check(generateSecret().isNotEmpty())
        }
        Signature.getInstance(signatureAlgorithm).run {
            initSign(pair.private)
            update(SSH_CAPABILITY_TEST_MESSAGE)
            check(sign().isNotEmpty())
        }
    }.isSuccess

private fun supportsX25519(): Boolean = runCatching {
    KeyFactory.getInstance("X25519")
    val first = KeyPairGenerator.getInstance("X25519").generateKeyPair()
    val second = KeyPairGenerator.getInstance("X25519").generateKeyPair()
    val firstSecret = KeyAgreement.getInstance("X25519").run {
        init(first.private)
        doPhase(second.public, true)
        generateSecret()
    }
    val secondSecret = KeyAgreement.getInstance("X25519").run {
        init(second.private)
        doPhase(first.public, true)
        generateSecret()
    }
    check(firstSecret.contentEquals(secondSecret))
}.isSuccess

private fun supportsDhGroup14(): Boolean = runCatching {
    val parameters = DHParameterSpec(DHGroupData.P14, DHGroupData.G)
    val first = KeyPairGenerator.getInstance("DH").run {
        initialize(parameters)
        generateKeyPair()
    }
    val second = KeyPairGenerator.getInstance("DH").run {
        initialize(parameters)
        generateKeyPair()
    }
    val firstSecret = KeyAgreement.getInstance("DH").run {
        init(first.private)
        doPhase(second.public, true)
        generateSecret()
    }
    val secondSecret = KeyAgreement.getInstance("DH").run {
        init(second.private)
        doPhase(first.public, true)
        generateSecret()
    }
    check(firstSecret.contentEquals(secondSecret))
}.isSuccess

private fun supportsEd25519(): Boolean = runCatching {
    KeyFactory.getInstance("Ed25519")
    val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    Signature.getInstance("Ed25519").run {
        initSign(pair.private)
        update(SSH_CAPABILITY_TEST_MESSAGE)
        check(sign().isNotEmpty())
    }
}.isSuccess

private fun supportsRsaSignature(algorithm: String): Boolean = runCatching {
    KeyFactory.getInstance("RSA")
    Signature.getInstance(algorithm)
}.isSuccess

private fun usableCipherFactories(
    candidates: List<Factory.Named<SshCipher>>
): List<Factory.Named<SshCipher>> = candidates.filter { factory ->
    runCatching {
        val cipher = factory.create()
        cipher.init(
            SshCipher.Mode.Encrypt,
            ByteArray(cipher.blockSize),
            ByteArray(cipher.ivSize)
        )
    }.isSuccess
}

private fun usableMacFactories(
    candidates: List<Factory.Named<MAC>>
): List<Factory.Named<MAC>> = candidates.filter { factory ->
    runCatching {
        val mac = factory.create()
        mac.init(ByteArray(mac.blockSize))
        check(mac.doFinal(SSH_CAPABILITY_TEST_MESSAGE).isNotEmpty())
    }.isSuccess
}

private val SSH_CAPABILITY_TEST_MESSAGE = "symposium-ssh-capability".toByteArray()

class RemoteInstaller(private val appContext: Context) {
    private val sshCredentialStore = SshCredentialStore(appContext)

    internal fun hasStoredSshCredentials(): Boolean = sshCredentialStore.hasAny()

    data class RelayRemovalResult(
        val success: Boolean,
        val observedSshHostKeyPin: String? = null
    )

    data class ProbeResult(
        val installed: Boolean,
        val openRooms: List<OpenRoomInfo>,
        val relayInfo: RelayInfo? = null,
        val deploymentProfile: DeploymentProfile? = null,
        val observedSshHostKeyPin: String? = null,
        val installationInProgress: Boolean = false
    )

    data class RoomAdminResult(
        val openRooms: List<OpenRoomInfo>,
        val relayInfo: RelayInfo? = null,
        val observedSshHostKeyPin: String? = null
    )

    data class RelayVersionResult(
        val name: String,
        val version: String
    )

    data class RelayInfo(
        val host: String,
        val httpsPort: Int,
        val pin: String,
        val adminToken: String = ""
    ) {
        val httpsBaseUrl: String get() = if (httpsPort == 443) "https://$host" else "https://$host:$httpsPort"
        val wssBaseUrl: String get() = if (httpsPort == 443) "wss://$host" else "wss://$host:$httpsPort"
        val wssWsUrl: String get() = "${wssBaseUrl}/ws"
    }

    data class InstallResult(
        val success: Boolean,
        val relayInfo: RelayInfo? = null,
        val deploymentProfile: DeploymentProfile? = null,
        val openRooms: List<OpenRoomInfo> = emptyList(),
        val exitStatus: Int = -1,
        val observedSshHostKeyPin: String? = null
    )

    suspend fun performInstallationDetailed(
        serverIp: String,
        login: String,
        password: String,
        expectedSshHostKeyPin: String?,
        existingProfile: DeploymentProfile? = null,
        relayCapacity: RelayCapacityConfig = existingProfile?.relayCapacity
            ?: RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD),
        logger: suspend (String) -> Unit
    ): InstallResult = withContext(Dispatchers.IO) {
        requireKnownSshHostKeyPin(expectedSshHostKeyPin)
        require(relayCapacity.validationError() == null) {
            relayCapacity.validationError()?.let(::tr) ?: tr(TextId.INVALID_RELAY_CAPACITY)
        }

        var observedHostKeyPin: String? = null
        var selectedProfile: DeploymentProfile? = null
        var installMarkerCreated = false

        try {
            logger(tr(TextId.CONNECTING_TO_SERVER))

            buildSshClient().use { ssh ->
                val verifier = Sha256HostKeyPinVerifier(expectedSshHostKeyPin) { pin ->
                    observedHostKeyPin = pin
                }
                ssh.addHostKeyVerifier(verifier)
                ssh.connect(serverIp)
                authenticateSsh(ssh, serverIp, login, password)

                logger(tr(TextId.SSH_READY))

                val existingDeployments = discoverRandomizedDeployments(
                    ssh = ssh,
                    login = login,
                    password = password,
                    serverIp = serverIp
                )
                val legacyDeploymentPresent = hasLegacyDeployment(
                    ssh = ssh,
                    login = login,
                    password = password
                )

                if (existingDeployments.isNotEmpty() || legacyDeploymentPresent) {
                    logger(tr(TextId.AN_EXISTING_INSTALLATION_WAS_FOUND_REMOVING_IT_COMPLETELY_BEFORE_THE_NEW))
                    existingDeployments.forEach { deployment ->
                        val removal = runRootOrSudo(
                            ssh = ssh,
                            login = login,
                            password = password,
                            command = buildRemovalScript(deployment.profile)
                        )
                        check(removal.exitStatus == 0) {
                            tr(TextId.FAILED_TO_REMOVE_OLD_SERVICE, deployment.profile.serviceName)
                        }
                    }
                    if (legacyDeploymentPresent) {
                        val removal = runRootOrSudo(
                            ssh = ssh,
                            login = login,
                            password = password,
                            command = LEGACY_REMOVAL_SCRIPT
                        )
                        check(removal.exitStatus == 0) {
                            tr(TextId.COULD_NOT_REMOVE_THE_OLD_INSTALLATION)
                        }
                    }
                    logger(tr(TextId.THE_EXISTING_INSTALLATION_WAS_COMPLETELY_REMOVED))
                }

                val removedExistingInstallation =
                    existingDeployments.isNotEmpty() || legacyDeploymentPresent
                val baseProfile = if (removedExistingInstallation) {
                    generateDeploymentProfile()
                } else {
                    existingProfile ?: generateDeploymentProfile()
                }
                val mediaPorts = relayIceUdpPortRange(
                    baseProfile.iceUdpPortMin..baseProfile.iceUdpPortMax,
                    relayCapacity
                )
                val deploymentProfile = baseProfile.copy(
                    relayCapacity = relayCapacity,
                    iceUdpPortMin = mediaPorts.first,
                    iceUdpPortMax = mediaPorts.last,
                    // A reinstall/update must invalidate any token that may have appeared in
                    // legacy shell history or process arguments.
                    adminToken = randomSecret()
                )
                selectedProfile = deploymentProfile
                val markerResult = runRootOrSudo(
                    ssh = ssh,
                    login = login,
                    password = password,
                    command = "printf '%s\n' \"${'$'}(date +%s)\" > ${shQuote(INSTALL_IN_PROGRESS_MARKER_PATH)} && chmod 600 ${shQuote(INSTALL_IN_PROGRESS_MARKER_PATH)}"
                )
                check(markerResult.exitStatus == 0) { tr(TextId.COULD_NOT_CREATE_THE_INSTALLATION_MARKER) }
                installMarkerCreated = true

                val installRemoteDir = deploymentProfile.stagingDir
                logger(tr(TextId.CLEANING_UP))
                ssh.startSession().use { session ->
                    val cmd = session.exec(
                        "rm -rf ${shQuote(installRemoteDir)} && mkdir -p ${shQuote(installRemoteDir)} && chmod 700 ${shQuote(installRemoteDir)}"
                    )
                    cmd.join()
                    check((cmd.exitStatus ?: 1) == 0) { tr(TextId.COULD_NOT_PREPARE_THE_INSTALLER_DIRECTORY) }
                }
            }

            val deploymentProfile = checkNotNull(selectedProfile)
            val installRemoteDir = deploymentProfile.stagingDir
            val remoteScript = "$installRemoteDir/${deploymentProfile.binaryName}.sh"

            logger(tr(TextId.PREPARING_FILES))
            val scriptBytes = renderInstallScript(deploymentProfile, serverIp).toByteArray(Charsets.UTF_8)

            uploadResumableFile(
                serverIp = serverIp,
                login = login,
                password = password,
                expectedSshHostKeyPin = expectedSshHostKeyPin,
                remotePath = remoteScript,
                payload = scriptBytes,
                label = tr(TextId.SCRIPT),
                logger = logger,
                onObservedPin = { pin -> observedHostKeyPin = pin }
            )

            logger(tr(TextId.DOWNLOADING_SYMPOSIUMRELAY_FROM_THE_GITHUB_RELEASE))
            var installRun = runRemoteInstallScript(
                serverIp = serverIp,
                login = login,
                password = password,
                expectedSshHostKeyPin = expectedSshHostKeyPin,
                remoteScript = remoteScript,
                forceLocalBinary = false,
                logger = logger,
                onObservedPin = { pin -> observedHostKeyPin = pin }
            )

            if (
                installRun.exitStatus != 0 &&
                installRun.combinedOutput.contains(RELAY_DOWNLOAD_FAILED_MARKER)
            ) {
                logger(tr(TextId.THE_GITHUB_RELEASE_IS_UNAVAILABLE_USING_THE_FALLBACK_FILE_BUNDLED_WITH_T))
                val binBytes = appContext.assets.open(SERVER_BIN_ASSET_PATH).use { it.readBytes() }
                uploadResumableFile(
                    serverIp = serverIp,
                    login = login,
                    password = password,
                    expectedSshHostKeyPin = expectedSshHostKeyPin,
                    remotePath = "$installRemoteDir/${deploymentProfile.binaryName}.src",
                    payload = binBytes,
                    label = tr(TextId.FALLBACK_SERVER_BINARY),
                    logger = logger,
                    onObservedPin = { pin -> observedHostKeyPin = pin }
                )

                logger(tr(TextId.RETRYING_WITH_THE_FALLBACK_FILE))
                installRun = runRemoteInstallScript(
                    serverIp = serverIp,
                    login = login,
                    password = password,
                    expectedSshHostKeyPin = expectedSshHostKeyPin,
                    remoteScript = remoteScript,
                    forceLocalBinary = true,
                    logger = logger,
                    onObservedPin = { pin -> observedHostKeyPin = pin }
                )
            }

            if (
                installRun.exitStatus != 0 &&
                installRun.combinedOutput.contains(EXISTING_INSTALL_FOUND_MARKER)
            ) {
                logger(tr(TextId.AN_EXISTING_SERVICE_WAS_FOUND_DURING_INSTALLATION_REMOVING_IT_COMPLETELY))
                var removedExistingDeployment = false
                buildSshClient().use { ssh ->
                    val verifier = Sha256HostKeyPinVerifier(expectedSshHostKeyPin) { pin ->
                        observedHostKeyPin = pin
                    }
                    ssh.addHostKeyVerifier(verifier)
                    ssh.connect(serverIp)
                    authenticateSsh(ssh, serverIp, login, password)
                    val deployments = discoverRandomizedDeployments(
                        ssh = ssh,
                        login = login,
                        password = password,
                        serverIp = serverIp
                    )
                    val legacyDeploymentPresent = hasLegacyDeployment(ssh, login, password)
                    deployments.forEach { deployment ->
                        val removal = runRootOrSudo(
                            ssh = ssh,
                            login = login,
                            password = password,
                            command = buildRemovalScript(deployment.profile)
                        )
                        check(removal.exitStatus == 0) {
                            tr(TextId.FAILED_TO_REMOVE_OLD_SERVICE, deployment.profile.serviceName)
                        }
                        removedExistingDeployment = true
                    }
                    if (legacyDeploymentPresent) {
                        val removal = runRootOrSudo(
                            ssh = ssh,
                            login = login,
                            password = password,
                            command = LEGACY_REMOVAL_SCRIPT
                        )
                        check(removal.exitStatus == 0) {
                            tr(TextId.COULD_NOT_REMOVE_THE_OLD_INSTALLATION)
                        }
                        removedExistingDeployment = true
                    }

                    // The install script deliberately recognizes a wider range of old
                    // managed units than the structured parser. Remove any such unit too,
                    // otherwise an older or partially written unit would keep returning 42.
                    val forcedRemoval = runRootOrSudo(
                        ssh = ssh,
                        login = login,
                        password = password,
                        command = FORCED_EXISTING_INSTALL_REMOVAL_SCRIPT
                    )
                    check(forcedRemoval.exitStatus == 0) {
                        tr(TextId.SERVICE_REMOVAL_INCOMPLETE)
                    }
                    if (forcedRemoval.stdout.contains(EXISTING_INSTALL_REMOVED_MARKER)) {
                        removedExistingDeployment = true
                    }
                }

                if (removedExistingDeployment) {
                    logger(tr(TextId.THE_EXISTING_INSTALLATION_WAS_REMOVED_STARTING_A_NEW_INSTALLATION))
                    return@withContext performInstallationDetailed(
                        serverIp = serverIp,
                        login = login,
                        password = password,
                        expectedSshHostKeyPin = expectedSshHostKeyPin,
                        existingProfile = null,
                        relayCapacity = relayCapacity,
                        logger = logger
                    )
                }

                return@withContext InstallResult(
                    success = false,
                    deploymentProfile = deploymentProfile,
                    exitStatus = EXISTING_DEPLOYMENT_UNHEALTHY_EXIT_CODE,
                    observedSshHostKeyPin = observedHostKeyPin
                )
            }

            val exitStatus = installRun.exitStatus
            if (exitStatus != 0) {
                logger(tr(TextId.INSTALLATION_FAILED_CODE, exitStatus))
                return@withContext InstallResult(
                    success = false,
                    deploymentProfile = deploymentProfile,
                    exitStatus = exitStatus,
                    observedSshHostKeyPin = observedHostKeyPin
                )
            }

            val tlsPin = Regex("(?m)^RELAY_TLS_PIN=(sha256/[A-Za-z0-9+/=]+)$")
                .find(installRun.combinedOutput)
                ?.groupValues
                ?.getOrNull(1)
                ?: throw IllegalStateException(tr(TextId.THE_INSTALLER_DID_NOT_RETURN_A_TLS_PIN))
            logger(tr(TextId.DONE))
            InstallResult(
                success = true,
                relayInfo = RelayInfo(
                    host = serverIp,
                    httpsPort = deploymentProfile.publicPort,
                    pin = tlsPin,
                    adminToken = deploymentProfile.adminToken
                ),
                deploymentProfile = deploymentProfile,
                openRooms = emptyList(),
                exitStatus = exitStatus,
                observedSshHostKeyPin = observedHostKeyPin
            )
        } catch (e: Exception) {
            val sshError = explainSshConnectionIssue(e)
            val msg = e.message ?: e.javaClass.simpleName
            if (sshError != null) {
                logger(sshError)
            } else if (msg.contains("X25519", ignoreCase = true)) {
                logger(tr(TextId.SSH_ERROR_X25519_IS_NOT_SUPPORTED))
            } else {
                logger(tr(TextId.INSTALLATION_ERROR_MESSAGE, msg))
            }
            throw IllegalStateException(sshError ?: msg, e)
        } finally {
            if (installMarkerCreated) {
                runCatching {
                    clearInstallInProgressMarker(
                        serverIp = serverIp,
                        login = login,
                        password = password,
                        expectedSshHostKeyPin = expectedSshHostKeyPin
                    )
                }
            }
        }
    }

    suspend fun removeRelayFromServer(
        serverIp: String,
        login: String,
        password: String,
        expectedSshHostKeyPin: String?,
        deploymentProfile: DeploymentProfile? = null,
        logger: suspend (String) -> Unit
    ): RelayRemovalResult = withContext(Dispatchers.IO) {
        requireKnownSshHostKeyPin(expectedSshHostKeyPin)

        var observedHostKeyPin: String? = null
        try {
            logger(tr(TextId.CONNECTING_TO_SERVER))
            buildSshClient().use { ssh ->
                val verifier = Sha256HostKeyPinVerifier(expectedSshHostKeyPin) { pin ->
                    observedHostKeyPin = pin
                }
                ssh.addHostKeyVerifier(verifier)
                ssh.connect(serverIp)
                authenticateSsh(ssh, serverIp, login, password)

                logger(tr(TextId.REMOVING_RELAY))
                val effectiveProfile = deploymentProfile ?: discoverRandomizedDeployments(
                    ssh = ssh,
                    login = login,
                    password = password,
                    serverIp = serverIp
                ).firstOrNull()?.profile
                val cmdText = effectiveProfile?.let { profile ->
                    buildRemovalScript(profile)
                } ?: LEGACY_REMOVAL_SCRIPT
                val exitStatus = runRootOrSudo(ssh, login, password, cmdText).exitStatus
                check(exitStatus == 0) { tr(TextId.REMOVAL_FAILED_CODE, exitStatus) }

                ssh.startSession().use { cleanupSession ->
                    val stagingDir = effectiveProfile?.stagingDir ?: LEGACY_INSTALL_REMOTE_DIR
                    val cleanupCmd = cleanupSession.exec("rm -rf ${shQuote(stagingDir)}")
                    cleanupCmd.join()
                }

                logger(tr(TextId.RELAY_REMOVED))
                RelayRemovalResult(success = true, observedSshHostKeyPin = observedHostKeyPin)
            }
        } catch (e: Exception) {
            val sshError = explainSshConnectionIssue(e)
            if (sshError != null) {
                logger(sshError)
            } else {
                logger(tr(TextId.REMOVAL_ERROR_MESSAGE, e.message ?: e.javaClass.simpleName))
            }
            throw IllegalStateException(sshError ?: (e.message ?: tr(TextId.UNKNOWN_ERROR)), e)
        }
    }

    private suspend fun uploadResumableFile(
        serverIp: String,
        login: String,
        password: String,
        expectedSshHostKeyPin: String?,
        remotePath: String,
        payload: ByteArray,
        label: String,
        logger: suspend (String) -> Unit,
        onObservedPin: (String) -> Unit
    ) {
        requireKnownSshHostKeyPin(expectedSshHostKeyPin)

        val tempPath = "$remotePath.part"
        val totalBytes = payload.size.toLong()
        var attempt = 0

        while (true) {
            attempt++

            try {
                buildSshClient().use { ssh ->
                    val verifier = Sha256HostKeyPinVerifier(expectedSshHostKeyPin) { pin ->
                        onObservedPin(pin)
                    }
                    ssh.addHostKeyVerifier(verifier)
                    ssh.connect(serverIp)
                    authenticateSsh(ssh, serverIp, login, password)

                    ssh.newSFTPClient().use { sftp ->
                        var remoteBytes = runCatching { sftp.stat(tempPath).size }.getOrElse { 0L }
                        if (remoteBytes > totalBytes) {
                            runCatching { sftp.rm(tempPath) }
                            remoteBytes = 0L
                        }

                        if (remoteBytes > 0L && remoteBytes < totalBytes) {
                            val percent = ((remoteBytes * 100) / totalBytes).toInt()
                            logger(tr(TextId.INSTALLATION_PROGRESS, trExternal(label), percent))
                        } else if (remoteBytes == 0L) {
                            logger(tr(TextId.INSTALLATION_PROGRESS, trExternal(label), 0))
                        }

                        sftp.open(tempPath, EnumSet.of(OpenMode.CREAT, OpenMode.WRITE)).use { remoteFile ->
                            var offset = remoteBytes
                            var lastReportedBucket =
                                (((remoteBytes * 100) / totalBytes).toInt()) / UPLOAD_PROGRESS_STEP_PERCENT

                            while (offset < totalBytes) {
                                val len = minOf(UPLOAD_CHUNK_SIZE.toLong(), totalBytes - offset).toInt()
                                remoteFile.write(offset, payload, offset.toInt(), len)
                                offset += len

                                val percent = ((offset * 100) / totalBytes).toInt()
                                val bucket = percent / UPLOAD_PROGRESS_STEP_PERCENT
                                if (bucket > lastReportedBucket || offset == totalBytes) {
                                    lastReportedBucket = bucket
                                    logger(
                                        tr(
                                            TextId.INSTALLATION_PROGRESS,
                                            trExternal(label),
                                            percent.coerceAtMost(100)
                                        )
                                    )
                                }
                            }
                        }

                        runCatching { sftp.rm(remotePath) }
                        sftp.rename(tempPath, remotePath)

                        val uploadedBytes = sftp.stat(remotePath).size
                        check(uploadedBytes == totalBytes) {
                            "$label: uploaded size mismatch ($uploadedBytes/$totalBytes)"
                        }
                    }
                }

                return
            } catch (e: Exception) {
                if (attempt >= MAX_UPLOAD_RETRIES) throw e
                logger(
                    tr(
                        TextId.OPERATION_RETRY,
                        trExternal(label),
                        attempt + 1,
                        MAX_UPLOAD_RETRIES
                    )
                )
                delay(1500L * attempt)
            }
        }
    }

    private data class RemoteInstallRun(
        val exitStatus: Int,
        val combinedOutput: String
    )

    private suspend fun runRemoteInstallScript(
        serverIp: String,
        login: String,
        password: String,
        expectedSshHostKeyPin: String?,
        remoteScript: String,
        forceLocalBinary: Boolean,
        logger: suspend (String) -> Unit,
        onObservedPin: (String) -> Unit
    ): RemoteInstallRun {
        var attempt = 0

        while (true) {
            attempt++
            try {
                return buildSshClient().use { ssh ->
                    val verifier = Sha256HostKeyPinVerifier(expectedSshHostKeyPin) { pin ->
                        onObservedPin(pin)
                    }
                    ssh.addHostKeyVerifier(verifier)

                    ssh.connect(serverIp)
                    authenticateSsh(ssh, serverIp, login, password)

                    ssh.startSession().use { session ->
                        val isRootLogin = login.trim() == "root"
                        val env = buildString {
                            append("env ")
                            if (forceLocalBinary) {
                                append("RELAY_FORCE_LOCAL_BINARY=1 ")
                            }
                        }
                        val runCmd = "${env}bash ${shQuote(remoteScript)} ${shQuote(serverIp)}"
                        val launchCmd = if (isRootLogin) {
                            "chmod +x ${shQuote(remoteScript)} && $runCmd"
                        } else if (password.isBlank()) {
                            "chmod +x ${shQuote(remoteScript)} && sudo -n /bin/bash -lc ${shQuote(runCmd)}"
                        } else {
                            "chmod +x ${shQuote(remoteScript)} && sudo -S -p '' $runCmd"
                        }

                        val cmd = session.exec(launchCmd)
                        if (!isRootLogin && password.isNotBlank()) {
                            cmd.outputStream.bufferedWriter(Charsets.UTF_8).use { stdin ->
                                stdin.write(password)
                                stdin.newLine()
                                stdin.flush()
                            }
                        }

                        val uiLog = InstallUiLogFilter(logger)
                        val combinedOutput = StringBuilder()
                        fun remember(line: String) {
                            synchronized(combinedOutput) {
                                combinedOutput.append(line).append('\n')
                            }
                        }

                        coroutineScope {
                            val outJob = launch {
                                cmd.inputStream.bufferedReader().useLines { lines ->
                                    lines.forEach { line ->
                                        remember(line)
                                        uiLog.onStdoutLine(line)
                                    }
                                }
                            }
                            val errJob = launch {
                                cmd.errorStream.bufferedReader().useLines { lines ->
                                    lines.forEach { line ->
                                        remember(line)
                                        uiLog.onStderrLine(line)
                                    }
                                }
                            }

                            cmd.join()
                            outJob.join()
                            errJob.join()
                        }

                        RemoteInstallRun(
                            exitStatus = cmd.exitStatus ?: -1,
                            combinedOutput = combinedOutput.toString()
                        )
                    }
                }
            } catch (e: Exception) {
                if (attempt >= MAX_SSH_REQUEST_RETRIES || !shouldRetryNetworkRequest(e)) throw e
                logger(tr(TextId.SSH_RETRY, attempt + 1, MAX_SSH_REQUEST_RETRIES))
                delay(retryDelayMs(attempt))
            }
        }
    }

    private class InstallUiLogFilter(
        private val logger: suspend (String) -> Unit
    ) {
        private val shown = linkedSetOf<String>()
        private val errorShown = AtomicBoolean(false)

        suspend fun onStdoutLine(lineRaw: String) {
            val line = lineRaw.trim()
            if (line.isBlank()) return

            stageMessage(line)?.let {
                emitOnce(it)
                return
            }

            finalMessage(line)?.let {
                emitOnce(it)
                return
            }
        }

        suspend fun onStderrLine(lineRaw: String) {
            val line = lineRaw.trim()
            if (line.isBlank()) return

            stageMessage(line)?.let {
                emitOnce(it)
                return
            }

            logger(errorMessage(line))
        }

        private suspend fun emitOnce(message: String) {
            val shouldEmit = synchronized(shown) { shown.add(message) }
            if (shouldEmit) logger(message)
        }

        private fun stageMessage(line: String): String? {
            return when {
                "[1/8]" in line -> tr(TextId.STEP_1_8_CHECK)
                "[2/8]" in line -> tr(TextId.STEP_2_8_DEPENDENCIES)
                "[3/8]" in line -> tr(TextId.STEP_3_8_FIREWALL)
                "[4/8]" in line -> tr(TextId.STEP_4_8_TOKEN)
                "[5/8]" in line -> tr(TextId.STEP_5_8_SERVICE)
                "[6/8]" in line -> tr(TextId.STEP_6_8_START)
                "[7/8]" in line -> "7/8 HTTPS/WSS"
                "[8/8]" in line -> tr(TextId.STEP_8_8_VERIFICATION)
                else -> null
            }
        }

        private fun finalMessage(line: String): String? {
            return when {
                line.startsWith("Installation finished.", ignoreCase = true) -> tr(TextId.SERVER_READY)
                line.startsWith("SYMPOSIUM_PIN=", ignoreCase = true) -> tr(TextId.PIN_RECEIVED)
                line.startsWith("SYMPOSIUM_HTTPS=", ignoreCase = true) -> tr(TextId.HTTPS_READY)
                line.startsWith("SYMPOSIUM_WSS=", ignoreCase = true) -> tr(TextId.WSS_READY)
                else -> null
            }
        }

        private fun errorMessage(line: String): String {
            val message = translateServerError(line)
            return if (errorShown.compareAndSet(false, true)) {
                tr(TextId.INFO_MESSAGE, message)
            } else {
                tr(TextId.INFO_MESSAGE, message)
            }
        }

        private fun translateServerError(line: String): String {
            val text = line.removePrefix("ERROR:").trim()
            return when {
                text.contains(RELAY_DOWNLOAD_FAILED_MARKER, ignoreCase = true) ->
                    tr(TextId.COULD_NOT_DOWNLOAD_THE_SERVER_BINARY)
                text.contains("installer must be run as root", ignoreCase = true) ->
                    tr(TextId.ROOT_ACCESS_IS_REQUIRED)
                text.contains("APT is locked", ignoreCase = true) || text.contains("APT still locked", ignoreCase = true) ->
                    tr(TextId.APT_IS_BUSY_WITH_ANOTHER_PROCESS)
                text.contains("Permission denied", ignoreCase = true) ->
                    tr(TextId.PERMISSION_DENIED)
                text.contains("No such file or directory", ignoreCase = true) ->
                    tr(TextId.FILE_NOT_FOUND)
                text.contains("command not found", ignoreCase = true) ->
                    tr(TextId.COMMAND_NOT_FOUND)
                text.contains("nginx: [emerg]", ignoreCase = true) ->
                    tr(TextId.NGINX_ERROR)
                text.isBlank() ->
                    tr(TextId.UNKNOWN_ERROR_2)
                else ->
                    text.take(240)
            }
        }
    }

    suspend fun probeInstallationState(
        serverIp: String,
        login: String,
        password: String,
        expectedSshHostKeyPin: String?
    ): ProbeResult = withContext(Dispatchers.IO) {
        requireKnownSshHostKeyPin(expectedSshHostKeyPin)

        var observedHostKeyPin: String? = null

        try {
            buildSshClient().use { ssh ->
                val verifier = Sha256HostKeyPinVerifier(expectedSshHostKeyPin) { pin ->
                    observedHostKeyPin = pin
                }
                ssh.addHostKeyVerifier(verifier)
                ssh.connect(serverIp)
                authenticateSsh(ssh, serverIp, login, password)

                if (isInstallInProgress(ssh, login, password)) {
                    return@use ProbeResult(
                        installed = false,
                        openRooms = emptyList(),
                        observedSshHostKeyPin = observedHostKeyPin,
                        installationInProgress = true
                    )
                }

                val discovered = discoverRandomizedDeployments(
                    ssh = ssh,
                    login = login,
                    password = password,
                    serverIp = serverIp
                )
                (discovered.firstOrNull { it.installed && it.relayInfo != null }
                    ?: discovered.firstOrNull())?.let { deployment ->
                    return@use ProbeResult(
                        installed = deployment.installed,
                        openRooms = deployment.openRooms,
                        relayInfo = deployment.relayInfo,
                        deploymentProfile = deployment.profile,
                        observedSshHostKeyPin = observedHostKeyPin
                    )
                }

                // Compatibility path for installations created by older application builds.
                val adminToken = runCatching { readAdminToken(ssh, login, password) }.getOrNull()
                val relayInfo = runCatching {
                    readRemoteFilePrivileged(ssh, login, password, RELAY_INFO_PATH)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { raw ->
                            val parsed = parseRelayInfo(raw, fallbackIp = serverIp)
                            if (parsed.adminToken.isBlank() && !adminToken.isNullOrBlank()) {
                                parsed.copy(adminToken = adminToken)
                            } else {
                                parsed
                            }
                        }
                }.getOrNull()

                val httpsPort = relayInfo?.httpsPort ?: 443
                val relayTlsPin = relayInfo?.pin
                val token = relayInfo?.adminToken?.takeIf { it.isNotBlank() } ?: adminToken

                val versionResult = if (!relayTlsPin.isNullOrBlank() && !token.isNullOrBlank()) {
                    runCatching {
                        fetchRelayVersionOverHttps(
                            serverIp = serverIp,
                            httpsPort = httpsPort,
                            relayTlsPin = relayTlsPin,
                            adminToken = token
                        )
                    }
                } else {
                    Result.failure(IllegalStateException("Missing HTTPS admin credentials"))
                }

                val installed = versionResult.isSuccess
                val rooms = if (installed && !relayTlsPin.isNullOrBlank() && !token.isNullOrBlank()) {
                    runCatching {
                        fetchOpenRoomsOverHttps(
                            serverIp = serverIp,
                            httpsPort = httpsPort,
                            relayTlsPin = relayTlsPin,
                            adminToken = token
                        ).openRooms
                    }.getOrElse { emptyList() }
                } else {
                    emptyList()
                }

                ProbeResult(
                    installed = installed,
                    openRooms = rooms,
                    relayInfo = relayInfo?.let { info ->
                        if (!token.isNullOrBlank() && info.adminToken.isBlank()) info.copy(adminToken = token) else info
                    },
                    deploymentProfile = null,
                    observedSshHostKeyPin = observedHostKeyPin
                )
            }
        } catch (e: Exception) {
            throw IllegalStateException(explainSshConnectionIssue(e) ?: (e.message ?: tr(TextId.COULD_NOT_CONNECT_TO_THE_SERVER)), e)
        }
    }

    suspend fun setRoomOpenState(
        serverIp: String,
        login: String,
        password: String,
        expectedSshHostKeyPin: String?,
        roomName: String,
        open: Boolean
    ): RoomAdminResult = withContext(Dispatchers.IO) {
        requireKnownSshHostKeyPin(expectedSshHostKeyPin)

        val room = roomName.trim()
        require(room.isNotBlank()) { "Room name is empty" }

        var observedHostKeyPin: String? = null
        try {
            buildSshClient().use { ssh ->
                val verifier = Sha256HostKeyPinVerifier(expectedSshHostKeyPin) { pin ->
                    observedHostKeyPin = pin
                }
                ssh.addHostKeyVerifier(verifier)
                ssh.connect(serverIp)
                authenticateSsh(ssh, serverIp, login, password)

                val deployment = discoverRandomizedDeployments(
                    ssh = ssh,
                    login = login,
                    password = password,
                    serverIp = serverIp
                ).firstOrNull()
                if (deployment != null) {
                    val actionPath = if (open) "/admin/open-room" else "/admin/close-room"
                    val encodedRoom = URLEncoder.encode(room, Charsets.UTF_8.name())
                    val tokenPath = "${deployment.profile.appDir}/${deployment.profile.identityDirName}/${deployment.profile.tokenName}"
                    val result = runRootOrSudo(
                        ssh = ssh,
                        login = login,
                        password = password,
                        command = """
                            set -e
                            { printf 'X-Relay-Key: '; tr -d '\r\n' < ${shQuote(tokenPath)}; printf '\n'; } |
                              curl -fsS -X POST -H @- ${shQuote("http://127.0.0.1:${deployment.profile.adminPort}${actionPath}?name=$encodedRoom")} >/dev/null
                        """.trimIndent()
                    )
                    check(result.exitStatus == 0) {
                        "Failed to ${if (open) "open" else "close"} room"
                    }
                    val refreshed = discoverRandomizedDeployments(
                        ssh = ssh,
                        login = login,
                        password = password,
                        serverIp = serverIp
                    ).firstOrNull() ?: deployment
                    return@use RoomAdminResult(
                        openRooms = refreshed.openRooms,
                        relayInfo = refreshed.relayInfo,
                        observedSshHostKeyPin = observedHostKeyPin
                    )
                }

                val actionPath = if (open) "/admin/open-room" else "/admin/close-room"
                val encodedRoom = URLEncoder.encode(room, Charsets.UTF_8.name())
                val legacyResult = runRootOrSudo(
                    ssh = ssh,
                    login = login,
                    password = password,
                    command = """
                        set -e
                        { printf 'X-Relay-Key: '; tr -d '\r\n' < ${shQuote(ADMIN_TOKEN_PATH)}; printf '\n'; } |
                          curl -fsS -X POST -H @- ${shQuote("http://127.0.0.1:3002${actionPath}?name=$encodedRoom")} >/dev/null
                    """.trimIndent()
                )
                check(legacyResult.exitStatus == 0) {
                    "Failed to ${if (open) "open" else "close"} room"
                }

                fetchOpenRooms(ssh, login, password, observedHostKeyPin, serverIp)
            }
        } catch (e: Exception) {
            throw IllegalStateException(explainSshConnectionIssue(e) ?: (e.message ?: tr(TextId.COULD_NOT_CONNECT_TO_THE_SERVER)), e)
        }
    }

    suspend fun fetchOpenRoomsOverSsh(
        serverIp: String,
        login: String,
        password: String,
        expectedSshHostKeyPin: String?
    ): RoomAdminResult = withContext(Dispatchers.IO) {
        requireKnownSshHostKeyPin(expectedSshHostKeyPin)

        var observedHostKeyPin: String? = null
        try {
            buildSshClient().use { ssh ->
                val verifier = Sha256HostKeyPinVerifier(expectedSshHostKeyPin) { pin ->
                    observedHostKeyPin = pin
                }
                ssh.addHostKeyVerifier(verifier)
                ssh.connect(serverIp)
                authenticateSsh(ssh, serverIp, login, password)
                discoverRandomizedDeployments(
                    ssh = ssh,
                    login = login,
                    password = password,
                    serverIp = serverIp
                ).firstOrNull()?.let { deployment ->
                    return@use RoomAdminResult(
                        openRooms = deployment.openRooms,
                        relayInfo = deployment.relayInfo,
                        observedSshHostKeyPin = observedHostKeyPin
                    )
                }
                fetchOpenRooms(ssh, login, password, observedHostKeyPin, serverIp)
            }
        } catch (e: Exception) {
            throw IllegalStateException(explainSshConnectionIssue(e) ?: (e.message ?: tr(TextId.COULD_NOT_CONNECT_TO_THE_SERVER)), e)
        }
    }

    suspend fun fetchRelayVersionOverHttps(
        serverIp: String,
        httpsPort: Int?,
        relayTlsPin: String?,
        adminToken: String?
    ): RelayVersionResult = withContext(Dispatchers.IO) {
        val token = adminToken?.trim().orEmpty()
        val pin = relayTlsPin?.trim().orEmpty()
        val port = httpsPort ?: 443

        require(token.isNotBlank()) { "Admin token is missing" }
        require(pin.isNotBlank()) { tr(TextId.TLS_PIN_IS_MISSING) }

        val base = if (port == 443) "https://$serverIp" else "https://$serverIp:$port"
        val client = pinnedHttpsClient(pin, callTimeoutSeconds = RELAY_VERSION_TIMEOUT_SECONDS)
        val request = Request.Builder()
            .url("$base/admin/version")
            .header("X-Relay-Key", token)
            .get()
            .build()

        try {
            withTimeout(RELAY_VERSION_TIMEOUT_MS) {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    check(response.isSuccessful) { "Version request failed: HTTP ${response.code}" }

                    val root = JSONObject(body)
                    val version = root.optString("version").trim()
                    require(version.isNotBlank()) { "Server version is missing" }

                    RelayVersionResult(
                        name = root.optString("name").ifBlank { "SymposiumRelay" },
                        version = version
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(tr(TextId.SYMPOSIUMRELAY_IS_NOT_INSTALLED), e)
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException(tr(TextId.SYMPOSIUMRELAY_IS_NOT_INSTALLED), e)
        }
    }

    suspend fun fetchOpenRoomsOverHttps(
        serverIp: String,
        httpsPort: Int?,
        relayTlsPin: String?,
        adminToken: String?
    ): RoomAdminResult = withContext(Dispatchers.IO) {
        val token = adminToken?.trim().orEmpty()
        val pin = relayTlsPin?.trim().orEmpty()
        val port = httpsPort ?: 443

        require(token.isNotBlank()) { "Admin token is missing" }
        require(pin.isNotBlank()) { tr(TextId.TLS_PIN_IS_MISSING) }

        val base = if (port == 443) "https://$serverIp" else "https://$serverIp:$port"
        val client = pinnedHttpsClient(pin)
        val request = Request.Builder()
            .url("$base/admin/open-rooms")
            .header("X-Relay-Key", token)
            .get()
            .build()

        retryNetworkRequest {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { "Admin request failed: HTTP ${response.code}" }

                RoomAdminResult(
                    openRooms = parseOpenRoomsJson(body),
                    relayInfo = RelayInfo(
                        host = serverIp,
                        httpsPort = port,
                        pin = pin,
                        adminToken = token
                    )
                )
            }
        }
    }

    suspend fun setRoomOpenStateOverHttps(
        serverIp: String,
        httpsPort: Int?,
        relayTlsPin: String?,
        adminToken: String?,
        roomName: String,
        open: Boolean,
        muteAllByDefault: Boolean = false,
        admitWithoutApproval: Boolean = false
    ): RoomAdminResult = withContext(Dispatchers.IO) {
        val token = adminToken?.trim().orEmpty()
        val pin = relayTlsPin?.trim().orEmpty()
        val room = roomName.trim()
        val port = httpsPort ?: 443

        require(token.isNotBlank()) { "Admin token is missing" }
        require(pin.isNotBlank()) { tr(TextId.TLS_PIN_IS_MISSING) }
        require(room.isNotBlank()) { "Room name is empty" }

        val base = if (port == 443) "https://$serverIp" else "https://$serverIp:$port"
        val actionPath = if (open) "/admin/open-room" else "/admin/close-room"
        val encodedRoom = URLEncoder.encode(room, Charsets.UTF_8.name())

        val client = pinnedHttpsClient(pin)
        val settingsQuery = if (open) {
            "&muteAllByDefault=$muteAllByDefault&admitWithoutApproval=$admitWithoutApproval"
        } else {
            ""
        }
        val request = Request.Builder()
            .url("$base$actionPath?name=$encodedRoom$settingsQuery")
            .header("X-Relay-Key", token)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        retryNetworkRequest {
            client.newCall(request).execute().use { response ->
                response.body?.string()
                check(response.isSuccessful) { "Admin request failed: HTTP ${response.code}" }
            }
        }

        fetchOpenRoomsOverHttps(serverIp, port, pin, token)
    }

    suspend fun setRoomSettingsOverHttps(
        serverIp: String,
        httpsPort: Int?,
        relayTlsPin: String?,
        adminToken: String?,
        roomName: String,
        muteAllByDefault: Boolean,
        admitWithoutApproval: Boolean
    ): RoomAdminResult = withContext(Dispatchers.IO) {
        val token = adminToken?.trim().orEmpty()
        val pin = relayTlsPin?.trim().orEmpty()
        val room = roomName.trim()
        val port = httpsPort ?: 443

        require(token.isNotBlank()) { "Admin token is missing" }
        require(pin.isNotBlank()) { tr(TextId.TLS_PIN_IS_MISSING) }
        require(room.isNotBlank()) { "Room name is empty" }

        val base = if (port == 443) "https://$serverIp" else "https://$serverIp:$port"
        val encodedRoom = URLEncoder.encode(room, Charsets.UTF_8.name())
        val client = pinnedHttpsClient(pin)
        val request = Request.Builder()
            .url(
                "$base/admin/room-settings?name=$encodedRoom" +
                    "&muteAllByDefault=$muteAllByDefault" +
                    "&admitWithoutApproval=$admitWithoutApproval"
            )
            .header("X-Relay-Key", token)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        retryNetworkRequest {
            client.newCall(request).execute().use { response ->
                response.body?.string()
                check(response.isSuccessful) { "Admin request failed: HTTP ${response.code}" }
            }
        }

        fetchOpenRoomsOverHttps(serverIp, port, pin, token)
    }

    suspend fun rotateModeratorKeyOverHttps(
        serverIp: String,
        httpsPort: Int?,
        relayTlsPin: String?,
        adminToken: String?,
        roomName: String
    ): RoomAdminResult = withContext(Dispatchers.IO) {
        val token = adminToken?.trim().orEmpty()
        val pin = relayTlsPin?.trim().orEmpty()
        val room = roomName.trim()
        val port = httpsPort ?: 443

        require(token.isNotBlank()) { "Admin token is missing" }
        require(pin.isNotBlank()) { tr(TextId.TLS_PIN_IS_MISSING) }
        require(room.isNotBlank()) { "Room name is empty" }

        val base = if (port == 443) "https://$serverIp" else "https://$serverIp:$port"
        val encodedRoom = URLEncoder.encode(room, Charsets.UTF_8.name())
        val client = pinnedHttpsClient(pin)
        val request = Request.Builder()
            .url("$base/admin/rotate-moderator-key?name=$encodedRoom")
            .header("X-Relay-Key", token)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        retryNetworkRequest {
            client.newCall(request).execute().use { response ->
                response.body?.string()
                check(response.isSuccessful) { "Admin request failed: HTTP ${response.code}" }
            }
        }

        fetchOpenRoomsOverHttps(serverIp, port, pin, token)
    }

    private suspend fun <T> retryNetworkRequest(block: () -> T): T {
        var attempt = 0

        while (true) {
            attempt++
            try {
                return block()
            } catch (e: Exception) {
                if (attempt >= MAX_HTTP_REQUEST_RETRIES || !shouldRetryNetworkRequest(e)) throw e
                delay(retryDelayMs(attempt))
            }
        }
    }

    private fun shouldRetryNetworkRequest(error: Throwable): Boolean {
        val chain = generateSequence(error) { it.cause }.toList()
        val root = chain.lastOrNull() ?: error
        val message = chain.joinToString("\n") { it.message.orEmpty() }

        if (root is UserAuthException) return false
        if (root is CertificateException) return false
        if (message.contains("pin mismatch", ignoreCase = true)) return false
        if (message.contains("TLS pin", ignoreCase = true)) return false
        if (
            message.contains("SSH key", ignoreCase = true) ||
            message.contains("SSH-ключ", ignoreCase = true)
        ) return false
        if (message.contains("Admin token is missing", ignoreCase = true)) return false
        if (message.contains("Room name is empty", ignoreCase = true)) return false
        if (message.contains("Server version is missing", ignoreCase = true)) return false

        val httpCode = Regex("HTTP\\s+(\\d{3})").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (httpCode != null) {
            return httpCode == 408 || httpCode == 425 || httpCode == 429 || httpCode in 500..599
        }

        if (root is SocketTimeoutException) return true
        if (root is SocketException) return true
        if (root is TransportException) return true
        if (root is IOException) return true

        return message.contains("timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ||
            message.contains("connection reset", ignoreCase = true) ||
            message.contains("broken pipe", ignoreCase = true) ||
            message.contains("disconnect", ignoreCase = true) ||
            message.contains("unexpected eof", ignoreCase = true)
    }

    private fun retryDelayMs(attempt: Int): Long {
        val multiplier = 1L shl (attempt - 1).coerceAtMost(4)
        return minOf(10_000L, NETWORK_RETRY_BASE_DELAY_MS * multiplier)
    }

    private fun explainSshConnectionIssue(error: Throwable): String? {
        val root = generateSequence(error) { it.cause }.last()
        val message = (root.message ?: error.message ?: "").trim()
        if (root is UserAuthException || message.contains("auth fail", ignoreCase = true)) {
            return tr(TextId.SSH_AUTHENTICATION_FAILED_INCORRECT_LOGIN_OR_PASSWORD_2)
        }
        if (message.contains("host key", ignoreCase = true) ||
            message.contains("key verification", ignoreCase = true) ||
            message.contains("certificate", ignoreCase = true)
        ) {
            return tr(TextId.SERVER_SSH_KEY_VERIFICATION_FAILED)
        }
        if (root is TransportException && message.contains("disconnect", ignoreCase = true)) {
            return tr(TextId.THE_SERVER_CLOSED_THE_SSH_CONNECTION_DURING_AUTHENTICATION)
        }
        return null
    }

    private fun fetchOpenRooms(
        ssh: SSHClient,
        login: String,
        password: String,
        observedSshHostKeyPin: String?,
        fallbackIp: String
    ): RoomAdminResult {
        val adminToken = readAdminToken(ssh, login, password)
        require(!adminToken.isNullOrBlank()) { "Admin token is missing on server" }

        val roomsResult = runRootOrSudo(
            ssh = ssh,
            login = login,
            password = password,
            command = """
                set -e
                { printf 'X-Relay-Key: '; tr -d '\r\n' < ${shQuote(ADMIN_TOKEN_PATH)}; printf '\n'; } |
                  curl -fsS -H @- http://127.0.0.1:3002/admin/open-rooms
            """.trimIndent()
        )
        check(roomsResult.exitStatus == 0) { "Failed to get open rooms" }
        val out = roomsResult.stdout

        val rooms = parseOpenRoomsJson(out)

        val relayInfo = readRemoteFilePrivileged(ssh, login, password, RELAY_INFO_PATH)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                runCatching {
                    val parsed = parseRelayInfo(it, fallbackIp = fallbackIp)
                    if (parsed.adminToken.isBlank()) parsed.copy(adminToken = adminToken) else parsed
                }.getOrNull()
            }

        return RoomAdminResult(
            openRooms = rooms,
            relayInfo = relayInfo,
            observedSshHostKeyPin = observedSshHostKeyPin
        )
    }

    private data class ParsedDeploymentUnit(
        val profile: DeploymentProfile,
        val tokenPath: String,
        val certificatePath: String
    )

    private data class DiscoveredDeployment(
        val profile: DeploymentProfile,
        val relayInfo: RelayInfo?,
        val openRooms: List<OpenRoomInfo>,
        val installed: Boolean
    )

    private fun isInstallInProgress(
        ssh: SSHClient,
        login: String,
        password: String
    ): Boolean {
        val result = runRootOrSudo(
            ssh = ssh,
            login = login,
            password = password,
            command = """
                marker=${shQuote(INSTALL_IN_PROGRESS_MARKER_PATH)}
                [ -f "${'$'}marker" ] || exit 1
                timestamp="${'$'}(tr -dc '0-9' < "${'$'}marker")"
                [ -n "${'$'}timestamp" ] || { rm -f "${'$'}marker"; exit 1; }
                now="${'$'}(date +%s)"
                age="${'$'}((now - timestamp))"
                if [ "${'$'}age" -ge 0 ] && [ "${'$'}age" -le ${INSTALL_MARKER_MAX_AGE_SECONDS} ]; then
                  exit 0
                fi
                rm -f "${'$'}marker"
                exit 1
            """.trimIndent()
        )
        return result.exitStatus == 0
    }

    private fun clearInstallInProgressMarker(
        serverIp: String,
        login: String,
        password: String,
        expectedSshHostKeyPin: String?
    ) {
        buildSshClient().use { ssh ->
            ssh.addHostKeyVerifier(Sha256HostKeyPinVerifier(expectedSshHostKeyPin) { })
            ssh.connect(serverIp)
            authenticateSsh(ssh, serverIp, login, password)
            runRootOrSudo(
                ssh = ssh,
                login = login,
                password = password,
                command = "rm -f ${shQuote(INSTALL_IN_PROGRESS_MARKER_PATH)}"
            )
        }
    }

    private fun discoverRandomizedDeployments(
        ssh: SSHClient,
        login: String,
        password: String,
        serverIp: String
    ): List<DiscoveredDeployment> {
        val listing = runRootOrSudo(
            ssh = ssh,
            login = login,
            password = password,
            command = RANDOMIZED_UNIT_DISCOVERY_SCRIPT
        )
        if (listing.exitStatus != 0) return emptyList()

        val unitPaths = listing.stdout
            .lineSequence()
            .map(String::trim)
            .filter { DISCOVERABLE_UNIT_PATH.matches(it) }
            .distinct()
            .sorted()
            .toList()

        val discovered = unitPaths.mapNotNull { unitPath ->
            val unitText = readRemoteFilePrivileged(ssh, login, password, unitPath)
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val parsed = parseRandomizedDeploymentUnit(unitPath, unitText)
                ?: return@mapNotNull null

            val adminToken = readRemoteFilePrivileged(ssh, login, password, parsed.tokenPath)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
            val tlsPin = readCertificatePin(
                ssh = ssh,
                login = login,
                password = password,
                certificatePath = parsed.certificatePath
            )

            val profile = parsed.profile.copy(adminToken = adminToken)
            val relayInfo = tlsPin?.takeIf { adminToken.isNotBlank() }?.let {
                RelayInfo(
                    host = serverIp,
                    httpsPort = profile.publicPort,
                    pin = it,
                    adminToken = adminToken
                )
            }
            val rooms = if (adminToken.isNotBlank()) {
                readDeploymentRoomsOverLoopback(
                    ssh = ssh,
                    login = login,
                    password = password,
                    profile = profile,
                    tokenPath = parsed.tokenPath
                )
            } else {
                null
            }

            DiscoveredDeployment(
                profile = profile,
                relayInfo = relayInfo,
                openRooms = rooms ?: emptyList(),
                installed = relayInfo != null && rooms != null
            )
        }

        return discovered.sortedWith(
            compareByDescending<DiscoveredDeployment> { it.installed }
                .thenBy { it.profile.serviceName }
        )
    }

    private fun parseRandomizedDeploymentUnit(
        unitPath: String,
        unitText: String
    ): ParsedDeploymentUnit? = runCatching {
        fun property(name: String): String {
            return unitText.lineSequence()
                .map(String::trim)
                .firstOrNull { it.startsWith("$name=") }
                ?.substringAfter('=')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: error("Missing $name")
        }

        val serviceName = unitPath
            .substringAfterLast('/')
            .removeSuffix(".service")
            .also { require(SAFE_SYSTEMD_NAME.matches(it)) }
        val workingDirectory = property("WorkingDirectory").trimEnd('/')
            .also(::requireSafeAbsolutePath)
        val serviceUser = property("User").also { require(SAFE_ACCOUNT_NAME.matches(it)) }
        val serviceGroup = property("Group").also { require(SAFE_ACCOUNT_NAME.matches(it)) }
        val execStart = property("ExecStart")
        val tokens = execStart.split(Regex("\\s+")).filter(String::isNotBlank)
        require(tokens.size >= 2)

        fun optionalFlagValue(name: String): String? {
            tokens.firstOrNull { it.startsWith("$name=") }?.let {
                return it.substringAfter('=').takeIf(String::isNotBlank)
            }
            val index = tokens.indexOf(name)
            return if (index >= 0 && index + 1 < tokens.size) tokens[index + 1] else null
        }

        fun flagValue(name: String): String {
            return optionalFlagValue(name) ?: error("Missing or empty $name")
        }

        require(flagValue("--admin-on-public").equals("true", ignoreCase = true))
        val executablePath = tokens.first().also(::requireSafeAbsolutePath)
        val binaryDirectory = executablePath.substringBeforeLast('/', missingDelimiterValue = "")
            .also(::requireSafeAbsolutePath)
        val appDir = binaryDirectory.substringBeforeLast('/', missingDelimiterValue = "")
            .also(::requireSafeAbsolutePath)
        val databasePath = flagValue("--rooms-db").also(::requireSafeAbsolutePath)
        val tokenPath = flagValue("--admin-token-file").also(::requireSafeAbsolutePath)
        val certificatePath = flagValue("--tls-cert").also(::requireSafeAbsolutePath)
        val privateKeyPath = flagValue("--tls-key").also(::requireSafeAbsolutePath)

        val (binaryDirName, binaryName) = splitManagedFilePath(appDir, executablePath)
        val (stateDirName, databaseName) = splitManagedFilePath(appDir, databasePath)
        val (identityDirName, tokenName) = splitManagedFilePath(appDir, tokenPath)
        val (certificateDirName, certificateName) = splitManagedFilePath(appDir, certificatePath)
        val (privateKeyDirName, privateKeyName) = splitManagedFilePath(appDir, privateKeyPath)
        require(identityDirName == certificateDirName && identityDirName == privateKeyDirName)
        require(
            workingDirectory == appDir ||
                workingDirectory == "$appDir/$stateDirName"
        )

        val publicPort = parseListenPort(flagValue("--addr"))
        val adminAddress = flagValue("--admin-addr")
        require(
            adminAddress.startsWith("127.0.0.1:") ||
                adminAddress.startsWith("localhost:")
        )
        val adminPort = parseListenPort(adminAddress)
        val iceMin = flagValue("--ice-udp-port-min").toInt()
        val iceMax = flagValue("--ice-udp-port-max").toInt()
        require(publicPort != adminPort)
        require(iceMin in 1..65535 && iceMax in iceMin..65535)

        val dbTable = flagValue("--db-table").also { require(SAFE_DB_IDENTIFIER.matches(it)) }
        val dbRoomColumn = flagValue("--db-room-column").also { require(SAFE_DB_IDENTIFIER.matches(it)) }
        val dbKeyColumn = flagValue("--db-key-column").also { require(SAFE_DB_IDENTIFIER.matches(it)) }
        require(dbRoomColumn != dbKeyColumn)

        val capacityDefaults = RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD)
        val relayCapacity = normalizeRelayCapacityConfig(
            RelayCapacityConfig(
                preset = RelayCapacityPreset.CUSTOM,
                maxActivePeers = optionalFlagValue("--max-active-peers")?.toInt()
                    ?: capacityDefaults.maxActivePeers,
                maxPendingLobbyPeers = optionalFlagValue("--max-pending-lobby-peers")?.toInt()
                    ?: capacityDefaults.maxPendingLobbyPeers,
                pendingIceCandidatesPerPeer = optionalFlagValue("--pending-ice-candidates-per-peer")?.toInt()
                    ?: capacityDefaults.pendingIceCandidatesPerPeer,
                pendingIceBytesPerPeer = optionalFlagValue("--pending-ice-bytes-per-peer")?.toLong()
                    ?: capacityDefaults.pendingIceBytesPerPeer,
                pendingIceBytesTotal = optionalFlagValue("--pending-ice-bytes-total")?.toLong()
                    ?: capacityDefaults.pendingIceBytesTotal,
                joinRatePerMinute = optionalFlagValue("--join-rate-per-minute")?.toInt()
                    ?: capacityDefaults.joinRatePerMinute,
                maxConcurrentJoins = optionalFlagValue("--max-concurrent-joins")?.toInt()
                    ?: capacityDefaults.maxConcurrentJoins,
                maxPreJoinConnections = optionalFlagValue("--max-pre-join-connections")?.toInt()
                    ?: capacityDefaults.maxPreJoinConnections,
                maxPreJoinConnectionsPerIp = optionalFlagValue("--max-pre-join-connections-per-ip")?.toInt()
                    ?: capacityDefaults.maxPreJoinConnectionsPerIp,
                preJoinTimeoutSeconds = optionalFlagValue("--pre-join-timeout")
                    ?.removeSuffix("s")
                    ?.toInt()
                    ?: capacityDefaults.preJoinTimeoutSeconds
            ).also { require(it.validationError() == null) }
        )

        ParsedDeploymentUnit(
            profile = DeploymentProfile(
                appDir = appDir,
                serviceName = serviceName,
                instanceName = optionalFlagValue("--instance-name")
                    ?.also { require(SAFE_SYSTEMD_NAME.matches(it)) }
                    ?: serviceName,
                serviceUser = serviceUser,
                serviceGroup = serviceGroup,
                binaryDirName = binaryDirName,
                stateDirName = stateDirName,
                identityDirName = identityDirName,
                binaryName = binaryName,
                databaseName = databaseName,
                tokenName = tokenName,
                certificateName = certificateName,
                privateKeyName = privateKeyName,
                dbTable = dbTable,
                dbRoomColumn = dbRoomColumn,
                dbKeyColumn = dbKeyColumn,
                publicPort = publicPort,
                adminPort = adminPort,
                iceUdpPortMin = iceMin,
                iceUdpPortMax = iceMax,
                firewallTag = serviceName,
                stagingDir = ".cache/.${randomHex(8)}",
                adminToken = "",
                relayCapacity = relayCapacity
            ),
            tokenPath = tokenPath,
            certificatePath = certificatePath
        )
    }.getOrNull()

    private fun requireSafeAbsolutePath(path: String) {
        require(SAFE_ABSOLUTE_PATH.matches(path))
        require(path != "/")
        require(path.split('/').none { it == "." || it == ".." })
    }

    private fun splitManagedFilePath(appDir: String, path: String): Pair<String, String> {
        val prefix = "$appDir/"
        require(path.startsWith(prefix))
        val parts = path.removePrefix(prefix).split('/')
        require(parts.size == 2)
        parts.forEach { require(SAFE_PATH_COMPONENT.matches(it)) }
        return parts[0] to parts[1]
    }

    private fun parseListenPort(address: String): Int {
        val port = address.substringAfterLast(':').toInt()
        require(port in 1..65535)
        return port
    }

    private fun readCertificatePin(
        ssh: SSHClient,
        login: String,
        password: String,
        certificatePath: String
    ): String? {
        val result = runRootOrSudo(
            ssh = ssh,
            login = login,
            password = password,
            command = """
                set -e
                openssl x509 -in ${shQuote(certificatePath)} -pubkey -noout |
                  openssl pkey -pubin -outform DER |
                  openssl dgst -sha256 -binary |
                  openssl base64 -A
            """.trimIndent()
        )
        if (result.exitStatus != 0) return null
        val value = result.stdout.trim()
        if (!BASE64_VALUE.matches(value)) return null
        return "sha256/$value"
    }

    private fun readDeploymentRoomsOverLoopback(
        ssh: SSHClient,
        login: String,
        password: String,
        profile: DeploymentProfile,
        tokenPath: String
    ): List<OpenRoomInfo>? {
        val result = runRootOrSudo(
            ssh = ssh,
            login = login,
            password = password,
            command = """
                set -e
                auth_header() { printf 'X-Relay-Key: '; tr -d '\r\n' < ${shQuote(tokenPath)}; printf '\n'; }
                version="${'$'}(auth_header | curl -fsS --connect-timeout 5 --max-time 10 -H @- http://127.0.0.1:${profile.adminPort}/admin/version)"
                rooms="${'$'}(auth_header | curl -fsS --connect-timeout 5 --max-time 10 -H @- http://127.0.0.1:${profile.adminPort}/admin/open-rooms)"
                printf 'VERSION_B64=%s\n' "${'$'}(printf '%s' "${'$'}version" | base64 | tr -d '\n')"
                printf 'ROOMS_B64=%s\n' "${'$'}(printf '%s' "${'$'}rooms" | base64 | tr -d '\n')"
            """.trimIndent()
        )
        if (result.exitStatus != 0) return null

        val values = result.stdout.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
        val versionJson = decodeBase64Text(values["VERSION_B64"]) ?: return null
        val roomsJson = decodeBase64Text(values["ROOMS_B64"]) ?: return null
        val version = runCatching { JSONObject(versionJson) }.getOrNull() ?: return null
        if (version.optString("version").isBlank()) return null
        if (version.optString("name").isBlank()) return null
        return parseOpenRoomsJson(roomsJson)
    }

    private fun decodeBase64Text(value: String?): String? {
        val encoded = value?.trim()?.takeIf { BASE64_VALUE.matches(it) } ?: return null
        return runCatching {
            Base64.decode(encoded, Base64.DEFAULT).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private data class RemoteCommandResult(
        val exitStatus: Int,
        val stdout: String,
        val stderr: String
    )

    private fun runRootOrSudo(
        ssh: SSHClient,
        login: String,
        password: String,
        command: String
    ): RemoteCommandResult {
        val isRootLogin = login.trim() == "root"
        val shellCommand = if (isRootLogin) {
            "bash -lc ${shQuote(command)}"
        } else if (password.isBlank()) {
            "sudo -n /bin/bash -lc ${shQuote(command)}"
        } else {
            "sudo -S -p '' bash -lc ${shQuote(command)}"
        }

        return ssh.startSession().use { session ->
            val cmd = session.exec(shellCommand)
            if (!isRootLogin && password.isNotBlank()) {
                cmd.outputStream.bufferedWriter(Charsets.UTF_8).use { stdin ->
                    stdin.write(password)
                    stdin.newLine()
                    stdin.flush()
                }
            }
            cmd.join()
            RemoteCommandResult(
                exitStatus = cmd.exitStatus ?: -1,
                stdout = cmd.inputStream.bufferedReader().readText(),
                stderr = cmd.errorStream.bufferedReader().readText()
            )
        }
    }

    private fun hasLegacyDeployment(
        ssh: SSHClient,
        login: String,
        password: String
    ): Boolean {
        val result = runRootOrSudo(
            ssh = ssh,
            login = login,
            password = password,
            command = """
                test -f /etc/systemd/system/symposium-server.service ||
                test -d /opt/symposium-server
            """.trimIndent()
        )
        return result.exitStatus == 0
    }

    private fun readRemoteFile(ssh: SSHClient, path: String): String? {
        return ssh.startSession().use { session ->
            val cmd = session.exec("cat ${shQuote(path)}")
            cmd.join()
            val out = cmd.inputStream.bufferedReader().readText()
            if ((cmd.exitStatus ?: 1) == 0) out else null
        }
    }

    private fun readRemoteFilePrivileged(
        ssh: SSHClient,
        login: String,
        password: String,
        path: String
    ): String? {
        val direct = readRemoteFile(ssh, path)
        if (direct != null) return direct

        val result = runRootOrSudo(
            ssh = ssh,
            login = login,
            password = password,
            command = "cat ${shQuote(path)}"
        )
        return if (result.exitStatus == 0) result.stdout else null
    }

    private fun readAdminToken(ssh: SSHClient, login: String, password: String): String? {
        return readRemoteFilePrivileged(ssh, login, password, ADMIN_TOKEN_PATH)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseRelayInfo(json: String, fallbackIp: String): RelayInfo {
        val o = JSONObject(json)
        val host = o.optString("host").takeIf { it.isNotBlank() } ?: fallbackIp
        val httpsPort = o.optInt("httpsPort", 443)
        val pin = o.getString("pin")
        val adminToken = o.optString("adminToken").trim()
        return RelayInfo(host = host, httpsPort = httpsPort, pin = pin, adminToken = adminToken)
    }

    suspend fun observeSshHostKeyPin(
        serverIp: String,
        port: Int = DEFAULT_SSH_PORT
    ): String = withContext(Dispatchers.IO) {
        var observedHostKeyPin: String? = null
        var connectionFailure: Exception? = null

        try {
            buildSshClient().use { ssh ->
                val verifier = Sha256HostKeyPinVerifier(expectedPin = null) { pin ->
                    observedHostKeyPin = pin
                }
                ssh.addHostKeyVerifier(verifier)
                ssh.connect(serverIp, port)
            }
        } catch (error: Exception) {
            connectionFailure = error
        }

        observedHostKeyPin?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                tr(TextId.COULD_NOT_RETRIEVE_THE_SERVER_SSH_KEY),
                connectionFailure
            )
    }

    suspend fun enrollSshKey(
        serverIp: String,
        login: String,
        password: String,
        expectedSshHostKeyPin: String?
    ) = withContext(Dispatchers.IO) {
        requireKnownSshHostKeyPin(expectedSshHostKeyPin)
        require(password.isNotBlank()) { "Password is required for the first connection" }

        val keyPair = sshCredentialStore.getSession(serverIp, login)
            ?: sshCredentialStore.generate(serverIp, login)

        buildSshClient().use { ssh ->
            try {
                ssh.addHostKeyVerifier(Sha256HostKeyPinVerifier(expectedSshHostKeyPin) {})
                ssh.connect(serverIp)
                ssh.authPassword(login, password)
            } catch (error: Exception) {
                diagnosticWarning(TAG, "SSH key enrollment password stage failed: ${sshErrorSummary(error)}")
                val message = explainSshConnectionIssue(error)
                    ?: tr(TextId.COULD_NOT_CONFIGURE_SSH_KEY_ACCESS)
                throw IllegalStateException(message, error)
            }

            if (login.trim() != "root") {
                require(SAFE_SSH_USERNAME.matches(login.trim())) { "Unsupported SSH username" }
                val noPasswordSudo = runRootOrSudo(
                    ssh = ssh,
                    login = login,
                    password = password,
                    command = """
                        set -e
                        file=/etc/sudoers.d/symposium-${login.trim()}
                        printf '%s\n' ${shQuote("${login.trim()} ALL=(root) NOPASSWD: /bin/bash")} > "${'$'}file"
                        chmod 0440 "${'$'}file"
                        visudo -cf "${'$'}file" >/dev/null
                    """.trimIndent()
                )
                if (noPasswordSudo.exitStatus != 0) {
                    diagnosticWarning(
                        TAG,
                        "SSH key enrollment sudo stage failed: ${safeRemoteError(noPasswordSudo.stderr)}"
                    )
                    throw IllegalStateException(
                        tr(TextId.COULD_NOT_CONFIGURE_PASSWORDLESS_ADMINISTRATION)
                    )
                }
            }

            try {
                installSshPublicKey(ssh, keyPair, login, password)
            } catch (error: Exception) {
                diagnosticWarning(TAG, "SSH public key installation failed: ${sshErrorSummary(error)}")
                throw IllegalStateException(tr(TextId.COULD_NOT_INSTALL_SSH_PUBLIC_KEY), error)
            }
        }

        try {
            buildSshClient().use { ssh ->
                ssh.addHostKeyVerifier(Sha256HostKeyPinVerifier(expectedSshHostKeyPin) {})
                ssh.connect(serverIp)
                ssh.authPublickey(login, ssh.loadKeys(keyPair))
            }
        } catch (error: Exception) {
            diagnosticWarning(TAG, "SSH installed key verification failed: ${sshErrorSummary(error)}")
            throw IllegalStateException(tr(TextId.SERVER_REJECTED_INSTALLED_SSH_KEY), error)
        }
        sshCredentialStore.deleteLegacy(serverIp, login)
    }

    internal suspend fun migrateLegacySshCredential(
        serverIp: String,
        login: String,
        expectedSshHostKeyPin: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sshCredentialStore.hasLegacy(serverIp, login)) return@withContext true
        requireKnownSshHostKeyPin(expectedSshHostKeyPin)

        buildSshClient().use { ssh ->
            ssh.addHostKeyVerifier(Sha256HostKeyPinVerifier(expectedSshHostKeyPin) {})
            ssh.connect(serverIp)
            authenticateSsh(ssh, serverIp, login, password = "")
        }
        !sshCredentialStore.hasLegacy(serverIp, login)
    }

    fun hasSshKey(serverIp: String, login: String): Boolean {
        return sshCredentialStore.contains(serverIp, login)
    }

    fun deleteSshKey(serverIp: String, login: String) {
        sshCredentialStore.delete(serverIp, login)
    }

    private fun requireKnownSshHostKeyPin(expectedPin: String?) {
        require(!expectedPin.isNullOrBlank()) {
            tr(TextId.THE_SERVER_SSH_KEY_HAS_NOT_BEEN_TRUSTED_YET_ADD_THE_SERVER_AND_CONFIRM_I)
        }
    }

    private fun buildSshClient(): SSHClient {
        return SSHClient(AndroidCompatibleSshConfig()).apply {
            setConnectTimeout(20_000)
            setTimeout(30_000)
        }
    }

    private fun authenticateSsh(
        ssh: SSHClient,
        serverIp: String,
        login: String,
        password: String
    ) {
        // An explicitly supplied password belongs to the current add-server operation. Prefer it
        // over cached keys, which may be stale after a failed enrollment or server reinstall.
        if (password.isNotBlank()) {
            ssh.authPassword(login, password)
            return
        }

        val legacyKeyPair = sshCredentialStore.getLegacy(serverIp, login)
        if (legacyKeyPair != null) {
            ssh.authPublickey(login, ssh.loadKeys(legacyKeyPair))
            runCatching {
                val sessionKeyPair = sshCredentialStore.getSession(serverIp, login)
                    ?: sshCredentialStore.generate(serverIp, login)
                installSshPublicKey(ssh, sessionKeyPair, login, password = "")
                sshCredentialStore.deleteLegacy(serverIp, login)
            }
            return
        }

        val sessionKeyPair = sshCredentialStore.getSession(serverIp, login)
        if (sessionKeyPair != null) {
            ssh.authPublickey(login, ssh.loadKeys(sessionKeyPair))
            return
        }
        error("SSH key is unavailable; add the server again")
    }

    private fun installSshPublicKey(
        ssh: SSHClient,
        keyPair: KeyPair,
        login: String,
        password: String
    ) {
        val normalizedLogin = login.trim()
        require(SAFE_SSH_USERNAME.matches(normalizedLogin)) { "Unsupported SSH username" }
        val publicLine = opensshRsaPublicKey(keyPair.public)
        val command = """
            set -e
            user=${shQuote(normalizedLogin)}
            home="${'$'}(getent passwd "${'$'}user" | cut -d: -f6)"
            group="${'$'}(id -gn "${'$'}user")"
            test -n "${'$'}home"
            install -d -m 0700 -o "${'$'}user" -g "${'$'}group" "${'$'}home/.ssh"
            touch "${'$'}home/.ssh/authorized_keys"
            chown "${'$'}user:${'$'}group" "${'$'}home/.ssh/authorized_keys"
            chmod 0600 "${'$'}home/.ssh/authorized_keys"
            grep -qxF ${shQuote(publicLine)} "${'$'}home/.ssh/authorized_keys" || printf '%s\n' ${shQuote(publicLine)} >> "${'$'}home/.ssh/authorized_keys"
            chown "${'$'}user:${'$'}group" "${'$'}home/.ssh/authorized_keys"
            chmod 0600 "${'$'}home/.ssh/authorized_keys"
        """.trimIndent()
        val result = runRootOrSudo(
            ssh = ssh,
            login = normalizedLogin,
            password = password,
            command = command
        )
        check(result.exitStatus == 0) {
            safeRemoteError(result.stderr).ifBlank { "Unable to install SSH public key" }
        }
    }

    private fun sshErrorSummary(error: Throwable): String =
        generateSequence(error) { it.cause }
            .take(4)
            .joinToString(" <- ") { cause ->
                val message = cause.message.orEmpty()
                    .replace(Regex("[\\r\\n\\t]+"), " ")
                    .take(160)
                "${cause.javaClass.simpleName}:$message"
            }

    private fun safeRemoteError(value: String): String = value
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .take(200)

    private class Sha256HostKeyPinVerifier(
        private val expectedPin: String?,
        private val onObserved: (String) -> Unit
    ) : HostKeyVerifier {

        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val pin = sha256PinOf(key.encoded)
            onObserved(pin)

            val expected = expectedPin?.trim()?.takeIf { it.isNotBlank() }

            if (expected == null) return false

            return MessageDigest.isEqual(
                expected.toByteArray(Charsets.UTF_8),
                pin.toByteArray(Charsets.UTF_8)
            )
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): MutableList<String> {
            return mutableListOf()
        }

        private fun sha256PinOf(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val b64 = Base64.encodeToString(digest, Base64.NO_WRAP)
            return "sha256/$b64"
        }
    }

    private fun pinnedHttpsClient(
        tlsPin: String,
        callTimeoutSeconds: Long? = null
    ): OkHttpClient {
        val trustManager = SpkiPinTrustManager(tlsPin)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        val builder = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (callTimeoutSeconds != null) {
            builder.callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
        }

        return builder.build()
    }

    private fun shQuote(s: String): String = "'" + s.replace("'", "'\"'\"'") + "'"

    private fun randomHex(bytes: Int): String {
        val value = ByteArray(bytes)
        SecureRandom().nextBytes(value)
        return value.joinToString("") { "%02x".format(it) }
    }

    private fun randomSecret(bytes: Int = 32): String {
        val value = ByteArray(bytes)
        SecureRandom().nextBytes(value)
        return Base64.encodeToString(value, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun generateDeploymentProfile(): DeploymentProfile {
        val root = randomHex(8)
        val userSuffix = randomHex(5)
        val publicPort = 20_000 + SecureRandom().nextInt(35_000)
        var adminPort = 20_000 + SecureRandom().nextInt(35_000)
        while (adminPort == publicPort) adminPort = 20_000 + SecureRandom().nextInt(35_000)
        val iceMin = 30_000 + SecureRandom().nextInt(12_000)
        val dbRoomColumn = "c_${randomHex(7)}"
        var dbKeyColumn = "c_${randomHex(7)}"
        while (dbKeyColumn == dbRoomColumn) dbKeyColumn = "c_${randomHex(7)}"
        return DeploymentProfile(
            appDir = "/opt/.$root",
            serviceName = "w${randomHex(7)}",
            instanceName = "n${randomHex(9)}",
            serviceUser = "u$userSuffix",
            serviceGroup = "g$userSuffix",
            binaryDirName = ".${randomHex(6)}",
            stateDirName = ".${randomHex(6)}",
            identityDirName = ".${randomHex(6)}",
            binaryName = "x${randomHex(8)}",
            databaseName = ".${randomHex(7)}.dat",
            tokenName = ".${randomHex(7)}.key",
            certificateName = ".${randomHex(7)}.crt",
            privateKeyName = ".${randomHex(7)}.pem",
            dbTable = "t_${randomHex(7)}",
            dbRoomColumn = dbRoomColumn,
            dbKeyColumn = dbKeyColumn,
            publicPort = publicPort,
            adminPort = adminPort,
            iceUdpPortMin = iceMin,
            iceUdpPortMax = iceMin + 199,
            firewallTag = "r${randomHex(7)}",
            stagingDir = ".cache/.${randomHex(8)}",
            adminToken = randomSecret()
        )
    }

    private fun renderInstallScript(profile: DeploymentProfile, serverIp: String): String {
        val relayRelease = configuredRelayRelease()
        val replacements = mapOf(
            "@@SERVER_IP@@" to shQuote(serverIp),
            "@@APP_DIR@@" to shQuote(profile.appDir),
            "@@SERVICE_NAME@@" to shQuote(profile.serviceName),
            "@@INSTANCE_NAME@@" to shQuote(profile.instanceName),
            "@@SERVICE_USER@@" to shQuote(profile.serviceUser),
            "@@SERVICE_GROUP@@" to shQuote(profile.serviceGroup),
            "@@BINARY_DIR_NAME@@" to shQuote(profile.binaryDirName),
            "@@STATE_DIR_NAME@@" to shQuote(profile.stateDirName),
            "@@IDENTITY_DIR_NAME@@" to shQuote(profile.identityDirName),
            "@@BINARY_NAME@@" to shQuote(profile.binaryName),
            "@@DATABASE_NAME@@" to shQuote(profile.databaseName),
            "@@TOKEN_NAME@@" to shQuote(profile.tokenName),
            "@@CERTIFICATE_NAME@@" to shQuote(profile.certificateName),
            "@@PRIVATE_KEY_NAME@@" to shQuote(profile.privateKeyName),
            "@@DB_TABLE@@" to shQuote(profile.dbTable),
            "@@DB_ROOM_COLUMN@@" to shQuote(profile.dbRoomColumn),
            "@@DB_KEY_COLUMN@@" to shQuote(profile.dbKeyColumn),
            "@@PUBLIC_PORT@@" to profile.publicPort.toString(),
            "@@ADMIN_PORT@@" to profile.adminPort.toString(),
            "@@ICE_MIN@@" to profile.iceUdpPortMin.toString(),
            "@@ICE_MAX@@" to profile.iceUdpPortMax.toString(),
            "@@MAX_ACTIVE_PEERS@@" to profile.relayCapacity.maxActivePeers.toString(),
            "@@MAX_PENDING_LOBBY_PEERS@@" to profile.relayCapacity.maxPendingLobbyPeers.toString(),
            "@@PENDING_ICE_CANDIDATES_PER_PEER@@" to profile.relayCapacity.pendingIceCandidatesPerPeer.toString(),
            "@@PENDING_ICE_BYTES_PER_PEER@@" to profile.relayCapacity.pendingIceBytesPerPeer.toString(),
            "@@PENDING_ICE_BYTES_TOTAL@@" to profile.relayCapacity.pendingIceBytesTotal.toString(),
            "@@JOIN_RATE_PER_MINUTE@@" to profile.relayCapacity.joinRatePerMinute.toString(),
            "@@MAX_CONCURRENT_JOINS@@" to profile.relayCapacity.maxConcurrentJoins.toString(),
            "@@MAX_PRE_JOIN_CONNECTIONS@@" to profile.relayCapacity.maxPreJoinConnections.toString(),
            "@@MAX_PRE_JOIN_CONNECTIONS_PER_IP@@" to profile.relayCapacity.maxPreJoinConnectionsPerIp.toString(),
            "@@PRE_JOIN_TIMEOUT@@" to relayDurationSeconds(
                profile.relayCapacity.preJoinTimeoutSeconds
            ),
            "@@FIREWALL_TAG@@" to shQuote(profile.firewallTag),
            "@@ADMIN_TOKEN@@" to shQuote(profile.adminToken),
            "@@RELAY_RELEASE_VERSION@@" to relayRelease.version,
            "@@RELAY_DOWNLOAD_URL@@" to relayRelease.downloadUrl,
            "@@RELAY_SHA256@@" to relayRelease.sha256,
            "@@RELAY_BUNDLED_SHA256@@" to relayRelease.bundledSha256
        )
        return replacements.entries.fold(ISOLATED_INSTALL_SCRIPT) { script, entry ->
            script.replace(entry.key, entry.value)
        }
    }

    private fun buildRemovalScript(profile: DeploymentProfile): String = """
        set -Eeuo pipefail
        SERVICE=${shQuote(profile.serviceName)}
        APP_DIR=${shQuote(profile.appDir)}
        APP_USER=${shQuote(profile.serviceUser)}
        APP_GROUP=${shQuote(profile.serviceGroup)}
        PUBLIC_PORT=${profile.publicPort}
        ICE_MIN=${profile.iceUdpPortMin}
        ICE_MAX=${profile.iceUdpPortMax}
        FIREWALL_TAG=${shQuote(profile.firewallTag)}
        systemctl stop "${'$'}{SERVICE}.service" 2>/dev/null || true
        systemctl disable "${'$'}{SERVICE}.service" 2>/dev/null || true
        rm -f "/etc/systemd/system/${'$'}{SERVICE}.service"
        systemctl daemon-reload
        rm -rf "${'$'}APP_DIR"
        userdel "${'$'}APP_USER" 2>/dev/null || true
        groupdel "${'$'}APP_GROUP" 2>/dev/null || true
        if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
          ufw --force delete allow "${'$'}PUBLIC_PORT/tcp" >/dev/null 2>&1 || true
          ufw --force delete allow "${'$'}ICE_MIN:${'$'}ICE_MAX/udp" >/dev/null 2>&1 || true
        fi
        if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
          firewall-cmd --permanent --remove-port="${'$'}PUBLIC_PORT/tcp" >/dev/null 2>&1 || true
          firewall-cmd --permanent --remove-port="${'$'}ICE_MIN-${'$'}ICE_MAX/udp" >/dev/null 2>&1 || true
          firewall-cmd --reload >/dev/null 2>&1 || true
        fi
    """.trimIndent()

    companion object {
        private const val LEGACY_INSTALL_REMOTE_DIR = ".symposium-installer"
        private const val RELAY_INFO_PATH = "/opt/symposium-server/relay-info.json"
        private const val ADMIN_TOKEN_PATH = "/opt/symposium-server/admin-token"
        private const val OPEN_ROOMS_DB_PATH = "/opt/symposium-server/open_rooms.db"
        private const val SERVER_BIN_ASSET_PATH = "symposium/symposium-server-linux-amd64"
        private const val RELAY_DOWNLOAD_FAILED_MARKER = "RELAY_DOWNLOAD_FAILED"
        private const val EXISTING_INSTALL_FOUND_MARKER = "EXISTING_INSTALL_FOUND"
        private const val EXISTING_INSTALL_REMOVED_MARKER = "EXISTING_INSTALL_REMOVED"
        private const val EXISTING_DEPLOYMENT_UNHEALTHY_EXIT_CODE = 42
        private const val INSTALL_IN_PROGRESS_MARKER_PATH = "/run/.network-worker-bootstrap.pending"
        private const val INSTALL_MARKER_MAX_AGE_SECONDS = 30 * 60
        private const val UPLOAD_CHUNK_SIZE = 16 * 1024
        private const val MAX_UPLOAD_RETRIES = 10
        private const val MAX_SSH_REQUEST_RETRIES = 4
        private const val MAX_HTTP_REQUEST_RETRIES = 4
        private const val DEFAULT_SSH_PORT = 22
        private const val NETWORK_RETRY_BASE_DELAY_MS = 750L
        private const val RELAY_VERSION_TIMEOUT_SECONDS = 8L
        private const val RELAY_VERSION_TIMEOUT_MS = RELAY_VERSION_TIMEOUT_SECONDS * 1000L
        private const val UPLOAD_PROGRESS_STEP_PERCENT = 5
        private val DISCOVERABLE_UNIT_PATH =
            Regex("^/etc/systemd/system/[A-Za-z0-9_.@-]+\\.service$")
        private val SAFE_SYSTEMD_NAME = Regex("^[A-Za-z0-9_.@-]{1,128}$")
        private val SAFE_ACCOUNT_NAME = Regex("^[A-Za-z_][A-Za-z0-9_-]{0,30}$")
        private val SAFE_DB_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]{0,127}$")
        private val SAFE_ABSOLUTE_PATH = Regex("^/[A-Za-z0-9._/-]+$")
        private val SAFE_PATH_COMPONENT = Regex("^[A-Za-z0-9._-]+$")
        private val BASE64_VALUE = Regex("^[A-Za-z0-9+/]+={0,2}$")

        private val RANDOMIZED_UNIT_DISCOVERY_SCRIPT = """
            set -e
            for unit in /etc/systemd/system/*.service; do
              [ -f "${'$'}unit" ] || continue
              grep -Fq -- '--admin-on-public=true' "${'$'}unit" || continue
              grep -Fq -- '--rooms-db' "${'$'}unit" || continue
              grep -Fq -- '--db-table' "${'$'}unit" || continue
              grep -Fq -- '--db-room-column' "${'$'}unit" || continue
              grep -Fq -- '--db-key-column' "${'$'}unit" || continue
              grep -Fq -- '--admin-token-file' "${'$'}unit" || continue
              grep -Fq -- '--tls-cert' "${'$'}unit" || continue
              grep -Fq -- '--tls-key' "${'$'}unit" || continue
              grep -Fq -- '--ice-udp-port-min' "${'$'}unit" || continue
              grep -Fq -- '--ice-udp-port-max' "${'$'}unit" || continue
              printf '%s\n' "${'$'}unit"
            done
        """.trimIndent()

        private val LEGACY_REMOVAL_SCRIPT = """
            set -e
            systemctl stop symposium-server.service || true
            systemctl disable symposium-server.service || true
            rm -f /etc/systemd/system/symposium-server.service
            systemctl daemon-reload
            rm -rf /opt/symposium-server
            rm -rf /var/lib/symposium-server
            rm -f /etc/symposium/admin-token
            userdel symposium 2>/dev/null || true
            groupdel symposium 2>/dev/null || true
            rm -f /etc/nginx/sites-enabled/symposium-relay.conf
            rm -f /etc/nginx/sites-available/symposium-relay.conf
            rm -f /etc/nginx/sites-enabled/symposium.conf
            rm -f /etc/nginx/sites-available/symposium.conf
            rm -f /etc/nginx/conf.d/symposium_ws_map.conf
            nginx -t >/dev/null 2>&1 && systemctl reload nginx || true
        """.trimIndent()

        private val FORCED_EXISTING_INSTALL_REMOVAL_SCRIPT = """
            set -Eeuo pipefail
            removed=0

            safe_path() {
              local path="${'$'}{1:-}"
              [[ "${'$'}path" =~ ^/[A-Za-z0-9._/-]+${'$'} ]] || return 1
              [ "${'$'}path" != "/" ] || return 1
              [[ "/${'$'}path/" != *"/../"* ]] || return 1
              [[ "/${'$'}path/" != *"/./"* ]] || return 1
            }

            safe_account() {
              [[ "${'$'}{1:-}" =~ ^[A-Za-z_][A-Za-z0-9_-]{0,30}${'$'} ]]
            }

            unit_scalar() {
              local key="${'$'}1"
              local unit="${'$'}2"
              sed -nE "s/^[[:space:]]*${'$'}{key}=([^[:space:]]+).*/\\1/p" "${'$'}unit" | head -n 1
            }

            flag_value() {
              local name="${'$'}1"
              local command="${'$'}2"
              printf '%s\n' "${'$'}command" |
                sed -nE "s/.*${'$'}{name}(=|[[:space:]]+)([^[:space:]]+).*/\\2/p" |
                head -n 1
            }

            valid_port() {
              [[ "${'$'}{1:-}" =~ ^[0-9]+${'$'} ]] || return 1
              [ "${'$'}1" -ge 1 ] && [ "${'$'}1" -le 65535 ]
            }

            for unit in /etc/systemd/system/*.service; do
              [ -f "${'$'}unit" ] || continue
              grep -Fq -- '--admin-on-public=true' "${'$'}unit" || continue
              grep -Fq -- '--rooms-db' "${'$'}unit" || continue
              grep -Fq -- '--db-table' "${'$'}unit" || continue
              grep -Fq -- '--db-room-column' "${'$'}unit" || continue
              grep -Fq -- '--db-key-column' "${'$'}unit" || continue
              grep -Fq -- '--admin-token-file' "${'$'}unit" || continue
              grep -Fq -- '--tls-cert' "${'$'}unit" || continue
              grep -Fq -- '--tls-key' "${'$'}unit" || continue

              service="${'$'}{unit##*/}"
              service="${'$'}{service%.service}"
              [[ "${'$'}service" =~ ^[A-Za-z0-9_.@-]{1,128}${'$'} ]] || {
                echo "unsafe managed service name: ${'$'}service" >&2
                exit 1
              }

              exec_start="${'$'}(sed -nE 's/^[[:space:]]*ExecStart=//p' "${'$'}unit" | head -n 1)"
              executable="${'$'}{exec_start%%[[:space:]]*}"
              working_dir="${'$'}(unit_scalar WorkingDirectory "${'$'}unit")"
              app_user="${'$'}(unit_scalar User "${'$'}unit")"
              app_group="${'$'}(unit_scalar Group "${'$'}unit")"
              database_path="${'$'}(flag_value --rooms-db "${'$'}exec_start")"
              token_path="${'$'}(flag_value --admin-token-file "${'$'}exec_start")"
              certificate_path="${'$'}(flag_value --tls-cert "${'$'}exec_start")"
              private_key_path="${'$'}(flag_value --tls-key "${'$'}exec_start")"
              public_address="${'$'}(flag_value --addr "${'$'}exec_start")"
              public_port="${'$'}{public_address##*:}"
              ice_min="${'$'}(flag_value --ice-udp-port-min "${'$'}exec_start")"
              ice_max="${'$'}(flag_value --ice-udp-port-max "${'$'}exec_start")"

              safe_path "${'$'}executable" || { echo "unsafe managed executable path" >&2; exit 1; }
              binary_dir="${'$'}{executable%/*}"
              app_dir="${'$'}{binary_dir%/*}"
              safe_path "${'$'}app_dir" || { echo "unsafe managed application path" >&2; exit 1; }
              case "${'$'}app_dir" in
                /opt/*) ;;
                *) echo "managed application is outside /opt" >&2; exit 1 ;;
              esac

              for managed_path in \
                "${'$'}executable" \
                "${'$'}working_dir" \
                "${'$'}database_path" \
                "${'$'}token_path" \
                "${'$'}certificate_path" \
                "${'$'}private_key_path"; do
                safe_path "${'$'}managed_path" || { echo "unsafe managed data path" >&2; exit 1; }
                case "${'$'}managed_path" in
                  "${'$'}app_dir"|"${'$'}app_dir"/*) ;;
                  *) echo "managed data is outside the application directory" >&2; exit 1 ;;
                esac
              done

              systemctl stop "${'$'}{service}.service" 2>/dev/null || true
              systemctl disable "${'$'}{service}.service" 2>/dev/null || true
              rm -f -- "${'$'}unit"
              rm -rf -- "${'$'}app_dir"

              if safe_account "${'$'}app_user" && [ "${'$'}app_user" != root ]; then
                account_home="${'$'}(getent passwd "${'$'}app_user" 2>/dev/null | cut -d: -f6 || true)"
                if [ "${'$'}account_home" = "${'$'}app_dir" ]; then
                  userdel "${'$'}app_user" 2>/dev/null || true
                  if safe_account "${'$'}app_group" && [ "${'$'}app_group" != root ]; then
                    groupdel "${'$'}app_group" 2>/dev/null || true
                  fi
                fi
              fi

              if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
                valid_port "${'$'}public_port" && ufw --force delete allow "${'$'}public_port/tcp" >/dev/null 2>&1 || true
                if valid_port "${'$'}ice_min" && valid_port "${'$'}ice_max"; then
                  ufw --force delete allow "${'$'}ice_min:${'$'}ice_max/udp" >/dev/null 2>&1 || true
                fi
              fi
              if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
                valid_port "${'$'}public_port" && firewall-cmd --permanent --remove-port="${'$'}public_port/tcp" >/dev/null 2>&1 || true
                if valid_port "${'$'}ice_min" && valid_port "${'$'}ice_max"; then
                  firewall-cmd --permanent --remove-port="${'$'}ice_min-${'$'}ice_max/udp" >/dev/null 2>&1 || true
                fi
                firewall-cmd --reload >/dev/null 2>&1 || true
              fi

              removed=1
            done

            if [ "${'$'}removed" = "1" ]; then
              systemctl daemon-reload
              echo "EXISTING_INSTALL_REMOVED"
            fi
        """.trimIndent()

        private val ISOLATED_INSTALL_SCRIPT = """
            #!/usr/bin/env bash
            set -Eeuo pipefail
            log(){ echo "[${'$'}(date +'%F %T')] ${'$'}*"; }
            die(){ echo "ERROR: ${'$'}*" >&2; exit 1; }
            need_cmd(){ command -v "${'$'}1" >/dev/null 2>&1; }
            [ "${'$'}{EUID:-1}" -eq 0 ] || die "installer must be run as root"

            SERVER_IP=@@SERVER_IP@@
            APP_DIR=@@APP_DIR@@
            SERVICE_NAME=@@SERVICE_NAME@@
            INSTANCE_NAME=@@INSTANCE_NAME@@
            APP_USER=@@SERVICE_USER@@
            APP_GROUP=@@SERVICE_GROUP@@
            BINARY_DIR_NAME=@@BINARY_DIR_NAME@@
            STATE_DIR_NAME=@@STATE_DIR_NAME@@
            IDENTITY_DIR_NAME=@@IDENTITY_DIR_NAME@@
            BINARY_NAME=@@BINARY_NAME@@
            DATABASE_NAME=@@DATABASE_NAME@@
            TOKEN_NAME=@@TOKEN_NAME@@
            CERTIFICATE_NAME=@@CERTIFICATE_NAME@@
            PRIVATE_KEY_NAME=@@PRIVATE_KEY_NAME@@
            DB_TABLE=@@DB_TABLE@@
            DB_ROOM_COLUMN=@@DB_ROOM_COLUMN@@
            DB_KEY_COLUMN=@@DB_KEY_COLUMN@@
            PUBLIC_PORT=@@PUBLIC_PORT@@
            ADMIN_PORT=@@ADMIN_PORT@@
            ICE_MIN=@@ICE_MIN@@
            ICE_MAX=@@ICE_MAX@@
            MAX_ACTIVE_PEERS=@@MAX_ACTIVE_PEERS@@
            MAX_PENDING_LOBBY_PEERS=@@MAX_PENDING_LOBBY_PEERS@@
            PENDING_ICE_CANDIDATES_PER_PEER=@@PENDING_ICE_CANDIDATES_PER_PEER@@
            PENDING_ICE_BYTES_PER_PEER=@@PENDING_ICE_BYTES_PER_PEER@@
            PENDING_ICE_BYTES_TOTAL=@@PENDING_ICE_BYTES_TOTAL@@
            JOIN_RATE_PER_MINUTE=@@JOIN_RATE_PER_MINUTE@@
            MAX_CONCURRENT_JOINS=@@MAX_CONCURRENT_JOINS@@
            MAX_PRE_JOIN_CONNECTIONS=@@MAX_PRE_JOIN_CONNECTIONS@@
            MAX_PRE_JOIN_CONNECTIONS_PER_IP=@@MAX_PRE_JOIN_CONNECTIONS_PER_IP@@
            PRE_JOIN_TIMEOUT=@@PRE_JOIN_TIMEOUT@@
            FIREWALL_TAG=@@FIREWALL_TAG@@
            ADMIN_TOKEN=@@ADMIN_TOKEN@@
            SCRIPT_DIR="${'$'}(cd "${'$'}(dirname "${'$'}{BASH_SOURCE[0]}")" && pwd)"
            RELAY_RELEASE_VERSION="@@RELAY_RELEASE_VERSION@@"
            RELAY_DOWNLOAD_URL="${'$'}{SYMPOSIUM_RELAY_DOWNLOAD_URL:-@@RELAY_DOWNLOAD_URL@@}"
            RELAY_EXPECTED_SHA256="@@RELAY_SHA256@@"
            RELAY_BUNDLED_SHA256="@@RELAY_BUNDLED_SHA256@@"
            LOCAL_BINARY="${'$'}SCRIPT_DIR/${'$'}BINARY_NAME.src"
            DOWNLOADED_BINARY="${'$'}SCRIPT_DIR/${'$'}BINARY_NAME.download"
            SERVER_BINARY_SOURCE=""
            SERVER_BINARY_SHA256=""
            KEEP_STAGING=0
            BINARY_DIR="${'$'}APP_DIR/${'$'}BINARY_DIR_NAME"
            STATE_DIR="${'$'}APP_DIR/${'$'}STATE_DIR_NAME"
            IDENTITY_DIR="${'$'}APP_DIR/${'$'}IDENTITY_DIR_NAME"
            DATABASE_PATH="${'$'}STATE_DIR/${'$'}DATABASE_NAME"
            TOKEN_PATH="${'$'}IDENTITY_DIR/${'$'}TOKEN_NAME"
            CERTIFICATE_PATH="${'$'}IDENTITY_DIR/${'$'}CERTIFICATE_NAME"
            PRIVATE_KEY_PATH="${'$'}IDENTITY_DIR/${'$'}PRIVATE_KEY_NAME"

            cleanup(){
              if [ "${'$'}KEEP_STAGING" = "1" ]; then
                return 0
              fi
              rm -rf "${'$'}SCRIPT_DIR" 2>/dev/null || true
            }
            trap cleanup EXIT

            need_cmd flock || die "required lock utility is unavailable"
            need_cmd sha256sum || die "required checksum utility is unavailable"
            exec 9>/run/lock/.network-worker-bootstrap.lock
            flock -w 300 9 || die "another installer is still running"

            EXISTING_UNIT=""
            for unit in /etc/systemd/system/*.service; do
              [ -f "${'$'}unit" ] || continue
              grep -Fq -- '--admin-on-public=true' "${'$'}unit" || continue
              grep -Fq -- '--rooms-db' "${'$'}unit" || continue
              grep -Fq -- '--db-table' "${'$'}unit" || continue
              grep -Fq -- '--db-room-column' "${'$'}unit" || continue
              grep -Fq -- '--db-key-column' "${'$'}unit" || continue
              grep -Fq -- '--admin-token-file' "${'$'}unit" || continue
              grep -Fq -- '--tls-cert' "${'$'}unit" || continue
              grep -Fq -- '--tls-key' "${'$'}unit" || continue
              EXISTING_UNIT="${'$'}unit"
              break
            done
            if [ -n "${'$'}EXISTING_UNIT" ]; then
              echo "EXISTING_INSTALL_FOUND" >&2
              exit 42
            fi

            verify_server_binary() {
              local path="${'$'}1"
              local expected="${'$'}2"
              local actual
              [ -s "${'$'}path" ] || return 1
              actual="${'$'}(sha256sum "${'$'}path")"
              actual="${'$'}{actual%% *}"
              [ "${'$'}actual" = "${'$'}expected" ]
            }

            download_release_binary() {
              local tmp="${'$'}{DOWNLOADED_BINARY}.part"
              rm -f "${'$'}DOWNLOADED_BINARY" "${'$'}tmp"
              log "Downloading SymposiumRelay ${'$'}RELAY_RELEASE_VERSION from GitHub Release"

              set +e
              curl -fL --show-error --connect-timeout 15 --max-time 900 \
                -o "${'$'}tmp" "${'$'}RELAY_DOWNLOAD_URL"
              local rc=${'$'}?
              set -e

              if [ "${'$'}rc" -eq 0 ] && verify_server_binary "${'$'}tmp" "${'$'}RELAY_EXPECTED_SHA256"; then
                chmod 0750 "${'$'}tmp"
                mv -f "${'$'}tmp" "${'$'}DOWNLOADED_BINARY"
                SERVER_BINARY_SOURCE="${'$'}DOWNLOADED_BINARY"
                SERVER_BINARY_SHA256="${'$'}RELAY_EXPECTED_SHA256"
                log "GitHub Release download complete"
                return 0
              fi

              rm -f "${'$'}tmp" "${'$'}DOWNLOADED_BINARY"
              log "GitHub Release download or integrity verification failed"
              return 1
            }

            select_server_binary() {
              if [ "${'$'}{RELAY_FORCE_LOCAL_BINARY:-0}" = "1" ]; then
                [ -s "${'$'}LOCAL_BINARY" ] || die "RELAY_DOWNLOAD_FAILED: app fallback binary is not available"
                verify_server_binary "${'$'}LOCAL_BINARY" "${'$'}RELAY_BUNDLED_SHA256" || die "RELAY_DOWNLOAD_FAILED: app fallback binary failed integrity verification"
                SERVER_BINARY_SOURCE="${'$'}LOCAL_BINARY"
                SERVER_BINARY_SHA256="${'$'}RELAY_BUNDLED_SHA256"
                log "Using SymposiumRelay binary uploaded by the app"
                return 0
              fi

              if download_release_binary; then
                return 0
              fi

              KEEP_STAGING=1
              die "RELAY_DOWNLOAD_FAILED: GitHub Release download failed"
            }

            log "[1/8] Detect OS"
            [ -r /etc/os-release ] || die "unsupported operating system"
            . /etc/os-release
            case "${'$'}{ID:-}" in debian|ubuntu) ;; *) die "unsupported operating system" ;; esac

            log "[2/8] Install runtime dependencies"
            export DEBIAN_FRONTEND=noninteractive
            apt-get -o Dpkg::Use-Pty=0 -o Acquire::Retries=3 -o DPkg::Lock::Timeout=300 update
            apt-get -o Dpkg::Use-Pty=0 -o Acquire::Retries=3 -o DPkg::Lock::Timeout=300 install -y ca-certificates curl openssl

            select_server_binary

            systemctl stop "${'$'}SERVICE_NAME.service" 2>/dev/null || true
            port_busy(){ ss -ltnH 2>/dev/null | awk '{print ${'$'}4}' | grep -Eq "(^|:)${'$'}1${'$'}"; }
            if port_busy "${'$'}PUBLIC_PORT"; then die "selected public TCP port is already in use"; fi
            if port_busy "${'$'}ADMIN_PORT"; then die "selected admin TCP port is already in use"; fi

            log "[3/8] Configure isolated firewall ports"
            if need_cmd ufw && ufw status 2>/dev/null | grep -q '^Status: active'; then
              ufw allow "${'$'}PUBLIC_PORT/tcp" comment "${'$'}FIREWALL_TAG" >/dev/null
              ufw allow "${'$'}ICE_MIN:${'$'}ICE_MAX/udp" comment "${'$'}FIREWALL_TAG" >/dev/null
            elif need_cmd firewall-cmd && firewall-cmd --state >/dev/null 2>&1; then
              firewall-cmd --permanent --add-port="${'$'}PUBLIC_PORT/tcp" >/dev/null
              firewall-cmd --permanent --add-port="${'$'}ICE_MIN-${'$'}ICE_MAX/udp" >/dev/null
              firewall-cmd --reload >/dev/null
            else
              log "No managed firewall is active; no global firewall configuration changed"
            fi

            log "[4/8] Create isolated identity"
            getent group "${'$'}APP_GROUP" >/dev/null 2>&1 || groupadd --system "${'$'}APP_GROUP"
            id -u "${'$'}APP_USER" >/dev/null 2>&1 || useradd --system --gid "${'$'}APP_GROUP" --home-dir "${'$'}APP_DIR" --no-create-home --shell /usr/sbin/nologin "${'$'}APP_USER"
            install -d -o root -g "${'$'}APP_GROUP" -m 0750 "${'$'}APP_DIR"
            install -d -o root -g "${'$'}APP_GROUP" -m 0750 "${'$'}BINARY_DIR" "${'$'}IDENTITY_DIR"
            install -d -o "${'$'}APP_USER" -g "${'$'}APP_GROUP" -m 0750 "${'$'}STATE_DIR"
            umask 077
            printf '%s\n' "${'$'}ADMIN_TOKEN" > "${'$'}TOKEN_PATH"

            log "[5/8] Generate pinned TLS identity"
            openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 825 \
              -subj "/CN=${'$'}SERVER_IP" \
              -addext "subjectAltName=IP:${'$'}SERVER_IP" \
              -keyout "${'$'}PRIVATE_KEY_PATH" -out "${'$'}CERTIFICATE_PATH" >/dev/null 2>&1
            PIN="${'$'}(openssl x509 -in "${'$'}CERTIFICATE_PATH" -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | openssl base64 -A)"
            PIN="sha256/${'$'}PIN"

            log "[6/8] Install isolated worker"
            [ -s "${'$'}SERVER_BINARY_SOURCE" ] || die "selected server binary is missing"
            install -o root -g "${'$'}APP_GROUP" -m 0750 "${'$'}SERVER_BINARY_SOURCE" "${'$'}BINARY_DIR/${'$'}BINARY_NAME"
            verify_server_binary "${'$'}BINARY_DIR/${'$'}BINARY_NAME" "${'$'}SERVER_BINARY_SHA256" || die "installed server binary failed integrity verification"
            install -o "${'$'}APP_USER" -g "${'$'}APP_GROUP" -m 0600 /dev/null "${'$'}DATABASE_PATH"
            chown "root:${'$'}APP_GROUP" "${'$'}TOKEN_PATH" "${'$'}CERTIFICATE_PATH" "${'$'}PRIVATE_KEY_PATH"
            chmod 640 "${'$'}TOKEN_PATH" "${'$'}PRIVATE_KEY_PATH"
            chmod 644 "${'$'}CERTIFICATE_PATH"

            cat > "/etc/systemd/system/${'$'}SERVICE_NAME.service" <<UNIT
            [Unit]
            Description=Managed network worker
            After=network-online.target
            Wants=network-online.target

            [Service]
            Type=simple
            WorkingDirectory=${'$'}STATE_DIR
            ExecStart=${'$'}BINARY_DIR/${'$'}BINARY_NAME --instance-name ${'$'}INSTANCE_NAME --addr :${'$'}PUBLIC_PORT --admin-addr 127.0.0.1:${'$'}ADMIN_PORT --admin-on-public=true --public-ip ${'$'}SERVER_IP --nat1to1 ${'$'}SERVER_IP --rooms-db ${'$'}DATABASE_PATH --db-table ${'$'}DB_TABLE --db-room-column ${'$'}DB_ROOM_COLUMN --db-key-column ${'$'}DB_KEY_COLUMN --admin-token-file ${'$'}TOKEN_PATH --tls-cert ${'$'}CERTIFICATE_PATH --tls-key ${'$'}PRIVATE_KEY_PATH --ice-udp-port-min ${'$'}ICE_MIN --ice-udp-port-max ${'$'}ICE_MAX --max-active-peers ${'$'}MAX_ACTIVE_PEERS --max-pending-lobby-peers ${'$'}MAX_PENDING_LOBBY_PEERS --pending-ice-candidates-per-peer ${'$'}PENDING_ICE_CANDIDATES_PER_PEER --pending-ice-bytes-per-peer ${'$'}PENDING_ICE_BYTES_PER_PEER --pending-ice-bytes-total ${'$'}PENDING_ICE_BYTES_TOTAL --join-rate-per-minute ${'$'}JOIN_RATE_PER_MINUTE --max-concurrent-joins ${'$'}MAX_CONCURRENT_JOINS --max-pre-join-connections ${'$'}MAX_PRE_JOIN_CONNECTIONS --max-pre-join-connections-per-ip ${'$'}MAX_PRE_JOIN_CONNECTIONS_PER_IP --pre-join-timeout ${'$'}PRE_JOIN_TIMEOUT
            Restart=always
            RestartSec=2
            LimitNOFILE=65536
            User=${'$'}APP_USER
            Group=${'$'}APP_GROUP
            UMask=0077
            NoNewPrivileges=true
            PrivateTmp=true
            ProtectSystem=strict
            ProtectHome=true
            PrivateDevices=true
            ReadOnlyPaths=${'$'}APP_DIR
            ReadWritePaths=${'$'}STATE_DIR
            CapabilityBoundingSet=
            AmbientCapabilities=
            LockPersonality=true
            MemoryDenyWriteExecute=true
            SystemCallArchitectures=native

            [Install]
            WantedBy=multi-user.target
            UNIT

            log "[7/8] Start isolated service"
            systemctl daemon-reload
            systemctl enable "${'$'}SERVICE_NAME.service" >/dev/null
            service_diagnostics() {
              systemctl show "${'$'}SERVICE_NAME.service" --no-pager \
                -p LoadState -p ActiveState -p SubState -p Result \
                -p ExecMainCode -p ExecMainStatus >&2 || true
              journalctl -u "${'$'}SERVICE_NAME.service" -n 40 \
                --no-pager -o cat >&2 || true
            }
            if ! systemctl restart "${'$'}SERVICE_NAME.service"; then
              service_diagnostics
              systemctl stop "${'$'}SERVICE_NAME.service" 2>/dev/null || true
              die "service failed to start"
            fi
            sleep 2
            if ! systemctl is-active --quiet "${'$'}SERVICE_NAME.service"; then
              service_diagnostics
              systemctl stop "${'$'}SERVICE_NAME.service" 2>/dev/null || true
              die "service failed to start"
            fi

            log "[8/8] Verify pinned HTTPS endpoint"
            if ! { printf 'X-Relay-Key: '; tr -d '\r\n' < "${'$'}TOKEN_PATH"; printf '\n'; } |
              curl -kfsS --connect-timeout 10 -H @- "https://127.0.0.1:${'$'}PUBLIC_PORT/admin/version" >/dev/null; then
              service_diagnostics
              systemctl stop "${'$'}SERVICE_NAME.service" 2>/dev/null || true
              die "service HTTPS health check failed"
            fi
            echo "RELAY_TLS_PIN=${'$'}PIN"
            echo "Installation finished."
        """.trimIndent()

    }
}

private fun opensshRsaPublicKey(key: PublicKey): String {
    val rsa = key as? RSAPublicKey ?: throw IllegalArgumentException("RSA public key required")
    val blob = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeSshField("ssh-rsa".toByteArray(Charsets.US_ASCII))
            out.writeSshField(rsa.publicExponent.toSshMpInt())
            out.writeSshField(rsa.modulus.toSshMpInt())
        }
        bytes.toByteArray()
    }
    return "ssh-rsa ${Base64.encodeToString(blob, Base64.NO_WRAP)} symposium-mobile"
}

private fun DataOutputStream.writeSshField(value: ByteArray) {
    writeInt(value.size)
    write(value)
}

private fun BigInteger.toSshMpInt(): ByteArray = toByteArray()

internal fun sshRsaKeySizeCandidates(): List<Int> = listOf(3072, 2048)

internal class SshCredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    fun getSession(serverIp: String, login: String): KeyPair? =
        getEntry(alias(activeAliasPrefix(), serverIp, login))

    fun getLegacy(serverIp: String, login: String): KeyPair? {
        return inactiveAliasPrefixes().firstNotNullOfOrNull { prefix ->
            getEntry(alias(prefix, serverIp, login))
        }
    }

    private fun getEntry(alias: String): KeyPair? {
        val entry = keyStore.getEntry(alias, null)
            as? KeyStore.PrivateKeyEntry
            ?: return null
        return KeyPair(entry.certificate.publicKey, entry.privateKey)
    }

    fun generate(serverIp: String, login: String): KeyPair {
        val requireAuthentication = isSecretProtectionEnabled(appContext)
        val alias = alias(
            if (requireAuthentication) AUTHENTICATED_SSH_KEY_ALIAS_PREFIX
            else SESSION_SSH_KEY_ALIAS_PREFIX,
            serverIp,
            login
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateWithCompatibleSize(
                    alias = alias,
                    useStrongBox = true,
                    requireAuthentication = requireAuthentication
                )
            } catch (_: Exception) {
                runCatching { keyStore.deleteEntry(alias) }
            }
        }
        return generateWithCompatibleSize(
            alias = alias,
            useStrongBox = false,
            requireAuthentication = requireAuthentication
        )
    }

    fun contains(serverIp: String, login: String): Boolean {
        return keyStore.containsAlias(alias(AUTHENTICATED_SSH_KEY_ALIAS_PREFIX, serverIp, login)) ||
            keyStore.containsAlias(alias(SESSION_SSH_KEY_ALIAS_PREFIX, serverIp, login)) ||
            keyStore.containsAlias(alias(LEGACY_SSH_KEY_ALIAS_PREFIX, serverIp, login))
    }

    fun hasLegacy(serverIp: String, login: String): Boolean =
        inactiveAliasPrefixes().any { prefix ->
            keyStore.containsAlias(alias(prefix, serverIp, login))
        }

    fun delete(serverIp: String, login: String) {
        keyStore.deleteEntry(alias(AUTHENTICATED_SSH_KEY_ALIAS_PREFIX, serverIp, login))
        keyStore.deleteEntry(alias(SESSION_SSH_KEY_ALIAS_PREFIX, serverIp, login))
        keyStore.deleteEntry(alias(LEGACY_SSH_KEY_ALIAS_PREFIX, serverIp, login))
    }

    fun deleteLegacy(serverIp: String, login: String) {
        inactiveAliasPrefixes().forEach { prefix ->
            keyStore.deleteEntry(alias(prefix, serverIp, login))
        }
    }

    fun hasAny(): Boolean {
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (alias.startsWith(AUTHENTICATED_SSH_KEY_ALIAS_PREFIX) ||
                alias.startsWith(SESSION_SSH_KEY_ALIAS_PREFIX) ||
                alias.startsWith(LEGACY_SSH_KEY_ALIAS_PREFIX)
            ) {
                return true
            }
        }
        return false
    }

    private fun activeAliasPrefix(): String =
        if (isSecretProtectionEnabled(appContext)) AUTHENTICATED_SSH_KEY_ALIAS_PREFIX
        else SESSION_SSH_KEY_ALIAS_PREFIX

    private fun inactiveAliasPrefixes(): List<String> =
        if (isSecretProtectionEnabled(appContext)) {
            listOf(SESSION_SSH_KEY_ALIAS_PREFIX, LEGACY_SSH_KEY_ALIAS_PREFIX)
        } else {
            listOf(AUTHENTICATED_SSH_KEY_ALIAS_PREFIX, LEGACY_SSH_KEY_ALIAS_PREFIX)
        }

    private fun generateWithCompatibleSize(
        alias: String,
        useStrongBox: Boolean,
        requireAuthentication: Boolean
    ): KeyPair {
        var lastFailure: Exception? = null
        sshRsaKeySizeCandidates().forEach { keySizeBits ->
            runCatching { keyStore.deleteEntry(alias) }
            try {
                return generateAtSize(
                    alias = alias,
                    useStrongBox = useStrongBox,
                    requireAuthentication = requireAuthentication,
                    keySizeBits = keySizeBits
                )
            } catch (error: Exception) {
                lastFailure = error
            }
        }
        runCatching { keyStore.deleteEntry(alias) }
        throw lastFailure ?: IllegalStateException("No compatible Android Keystore RSA size")
    }

    private fun generateAtSize(
        alias: String,
        useStrongBox: Boolean,
        requireAuthentication: Boolean,
        keySizeBits: Int
    ): KeyPair {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(keySizeBits)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .apply {
                setUserAuthenticationRequired(requireAuthentication)
                if (requireAuthentication) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            AUTH_VALIDITY_SECONDS,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or
                                KeyProperties.AUTH_DEVICE_CREDENTIAL
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (requireAuthentication) setUnlockedDeviceRequired(true)
                    setIsStrongBoxBacked(useStrongBox)
                }
            }
            .build()
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    private fun alias(prefix: String, serverIp: String, login: String): String {
        val normalized = "${serverIp.trim().lowercase()}\u0000${login.trim()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$prefix$digest"
    }

    companion object {
        private const val AUTH_VALIDITY_SECONDS = 8 * 60 * 60
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val LEGACY_SSH_KEY_ALIAS_PREFIX = "symposium_ssh_admin_"
        private const val SESSION_SSH_KEY_ALIAS_PREFIX = "symposium_ssh_session_"
        private const val AUTHENTICATED_SSH_KEY_ALIAS_PREFIX = "symposium_ssh_authenticated_v2_"
    }
}

private val SAFE_SSH_USERNAME = Regex("^[A-Za-z_][A-Za-z0-9_.-]{0,31}${'$'}")

class InstallServersStore(context: Context) {
    private val appContext = context.applicationContext
    private val secretCipher = AuthenticatedSecretCipher(
        context = appContext,
        purpose = "install-servers"
    )

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun migrateLegacyIfNeeded() {
        val current = securePrefs.getString(SECURE_KEY, null)
        if (!current.isNullOrBlank()) return

        val legacy = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = legacy.getString(LEGACY_KEY, null) ?: return
        if (securePrefs.edit().putString(SECURE_KEY, raw).commit()) {
            legacy.edit().remove(LEGACY_KEY).apply()
        }
    }

    fun load(): List<InstallServer> {
        migrateLegacyIfNeeded()
        val stored = securePrefs.getString(SECURE_KEY, null) ?: return emptyList()
        val opened = secretCipher.open(stored)
        val raw = opened.value
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: run {
            securePrefs.edit().clear().commit()
            return emptyList()
        }

        val out = ArrayList<InstallServer>(arr.length())
        var legacySecretsFound = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue

            val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
            val secrets = loadServerSecrets(id)
            val name = o.optString("name")
            val ip = o.optString("ip")
            val username = o.optString("username")
            val password = ""
            val sshKeyInstalled = o.optBoolean("sshKeyInstalled", false)
            val installed = o.optBoolean("installed", false)
            val sshHostKeyPin = o.optString("sshHostKeyPin").takeIf { it.isNotBlank() }
            val httpsPort = o.optInt("httpsPort", -1).takeIf { it > 0 }
            val relayTlsPin = o.optString("relayTlsPin").takeIf { it.isNotBlank() }
            val legacyAdminToken = o.optString("adminToken").takeIf { it.isNotBlank() }
            val adminToken = secrets?.optString("adminToken")?.takeIf { it.isNotBlank() }
                ?: legacyAdminToken
            val legacyProfile = deploymentProfileFromJson(o.optJSONObject("deploymentProfile"))
            val profileAdminToken = secrets?.optString("deploymentAdminToken")
                ?.takeIf { it.isNotBlank() }
                ?: legacyProfile?.adminToken
            val deploymentProfile = legacyProfile?.copy(adminToken = profileAdminToken.orEmpty())
            val relayCapacity = o.optJSONObject("relayCapacity")?.let(::relayCapacityFromJson)
                ?: deploymentProfile?.relayCapacity
                ?: RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD)
            val persistedRooms = parseOpenRoomsArray(
                o.optJSONArray("openRooms") ?: JSONArray(),
                requireRoomInstance = false
            )
            val moderatorKeys = secrets?.optJSONArray("moderatorKeys")
                ?.let(::moderatorKeysByRoomInstance)
                .orEmpty()
            val openRooms = persistedRooms.map { room ->
                val moderatorKey = moderatorKeys[roomSecretIdentity(room)]
                    ?: room.moderatorKey
                room.copy(
                    moderatorKey = moderatorKey,
                    moderatorKeyAvailable = room.moderatorKeyAvailable || moderatorKey.isNotBlank()
                )
            }
            val relayVersion = o.optString("relayVersion").takeIf { it.isNotBlank() }
            val relayVersionState = runCatching {
                RelayVersionState.valueOf(o.optString("relayVersionState"))
            }.getOrElse {
                if (installed) RelayVersionState.UNKNOWN else RelayVersionState.NOT_INSTALLED
            }

            out += InstallServer(
                id = id,
                name = name,
                ip = ip,
                username = username,
                password = password,
                sshKeyInstalled = sshKeyInstalled,
                installed = installed,
                sshHostKeyPin = sshHostKeyPin,
                httpsPort = httpsPort,
                relayTlsPin = relayTlsPin,
                adminToken = adminToken,
                adminTokenAvailable = o.optBoolean(
                    "adminTokenAvailable",
                    !adminToken.isNullOrBlank()
                ) || !adminToken.isNullOrBlank(),
                deploymentProfile = deploymentProfile,
                relayCapacity = relayCapacity,
                openRooms = openRooms,
                relayVersion = relayVersion,
                relayVersionState = relayVersionState
            )
            legacySecretsFound = legacySecretsFound ||
                o.optString("password").isNotBlank() ||
                legacyAdminToken != null ||
                legacyProfile?.adminToken?.isNotBlank() == true ||
                persistedRooms.any { it.moderatorKey.isNotBlank() }
        }
        if (opened.requiresMigration || legacySecretsFound) {
            save(out)
        }
        return out.map(::redactForUi)
    }

    private fun redactForUi(server: InstallServer): InstallServer = server.copy(
        password = "",
        adminToken = null,
        adminTokenAvailable = server.adminTokenAvailable || !server.adminToken.isNullOrBlank(),
        deploymentProfile = server.deploymentProfile?.copy(adminToken = ""),
        openRooms = server.openRooms.map { room ->
            room.copy(
                moderatorKey = "",
                moderatorKeyAvailable = room.moderatorKeyAvailable || room.moderatorKey.isNotBlank()
            )
        }
    )

    fun readAdminToken(serverId: String): String? = loadServerSecrets(serverId)
        ?.optString("adminToken")
        ?.takeIf(String::isNotBlank)

    fun readModeratorKey(serverId: String, room: OpenRoomInfo): String? {
        val values = loadServerSecrets(serverId)?.optJSONArray("moderatorKeys") ?: return null
        return moderatorKeysByRoomInstance(values)[roomSecretIdentity(room)]?.takeIf(String::isNotBlank)
    }

    fun hydrateDeploymentProfile(server: InstallServer): DeploymentProfile? {
        val profile = server.deploymentProfile ?: return null
        val token = loadServerSecrets(server.id)
            ?.optString("deploymentAdminToken")
            ?.takeIf(String::isNotBlank)
            .orEmpty()
        return profile.copy(adminToken = token)
    }

    private fun loadServerSecrets(id: String): JSONObject? {
        val stored = securePrefs.getString(serverSecretsKey(id), null) ?: return null
        val opened = secretCipher.open(stored)
        val parsed = runCatching { JSONObject(opened.value) }.getOrNull() ?: return null
        if (opened.requiresMigration) {
            check(
                securePrefs.edit()
                    .putString(serverSecretsKey(id), secretCipher.seal(parsed.toString()))
                    .commit()
            ) { "Unable to migrate server credentials" }
        }
        return parsed
    }

    private fun moderatorKeysByRoomInstance(values: JSONArray): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (index in 0 until values.length()) {
            val value = values.optJSONObject(index) ?: continue
            val room = OpenRoomInfo(
                name = value.optString("room"),
                roomInstance = value.optString("roomInstance")
            )
            val moderatorKey = value.optString("moderatorKey")
            if (room.name.isNotBlank() && moderatorKey.isNotBlank()) {
                result[roomSecretIdentity(room)] = moderatorKey
            }
        }
        return result
    }

    private fun roomSecretIdentity(room: OpenRoomInfo): String =
        "${room.name}\u0000${room.roomInstance}"

    private fun serverSecretsKey(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$SERVER_SECRETS_PREFIX$digest"
    }

    fun hasEncryptedSecrets(): Boolean {
        migrateLegacyIfNeeded()
        if (securePrefs.all.any { (key, value) ->
                key.startsWith(SERVER_SECRETS_PREFIX) &&
                    value is String &&
                    secretCipher.isProtected(value)
            }
        ) return true
        val raw = securePrefs.getString(SECURE_KEY, null) ?: return false
        if (secretCipher.isProtected(raw)) return true
        val servers = runCatching { JSONArray(raw) }.getOrNull() ?: return false

        for (index in 0 until servers.length()) {
            val server = servers.optJSONObject(index) ?: continue
            if (server.optString("password").isNotBlank()) return true
            if (server.optString("adminToken").isNotBlank()) return true
            if (server.optJSONObject("deploymentProfile")
                    ?.optString("adminToken")
                    ?.isNotBlank() == true
            ) {
                return true
            }

            val rooms = server.optJSONArray("openRooms") ?: continue
            for (roomIndex in 0 until rooms.length()) {
                val room = rooms.optJSONObject(roomIndex) ?: continue
                if (room.optString("moderator_key").isNotBlank() ||
                    room.optString("moderatorKey").isNotBlank()
                ) {
                    return true
                }
            }
        }
        return false
    }

    fun save(servers: List<InstallServer>) {
        val arr = JSONArray()
        val editor = securePrefs.edit()
        val activeSecretKeys = HashSet<String>()
        servers.forEach { s ->
            val existingSecrets = loadServerSecrets(s.id)
            val existingModeratorKeys = existingSecrets?.optJSONArray("moderatorKeys")
                ?.let(::moderatorKeysByRoomInstance)
                .orEmpty()
            val adminToken = s.adminToken?.takeIf(String::isNotBlank)
                ?: existingSecrets?.optString("adminToken")?.takeIf(String::isNotBlank)
            val deploymentAdminToken = s.deploymentProfile?.adminToken?.takeIf(String::isNotBlank)
                ?: existingSecrets?.optString("deploymentAdminToken")?.takeIf(String::isNotBlank)
            val o = JSONObject()
                .put("id", s.id)
                .put("name", s.name)
                .put("ip", s.ip)
                .put("username", s.username)
                .put("sshKeyInstalled", s.sshKeyInstalled)
                .put("installed", s.installed)
                .put("adminTokenAvailable", s.adminTokenAvailable || !adminToken.isNullOrBlank())

            s.sshHostKeyPin?.let { o.put("sshHostKeyPin", it) }
            s.httpsPort?.let { o.put("httpsPort", it) }
            s.relayTlsPin?.let { o.put("relayTlsPin", it) }
            s.deploymentProfile?.let {
                o.put("deploymentProfile", deploymentProfileToJson(it, includeAdminToken = false))
            }
            o.put("relayCapacity", relayCapacityToJson(s.relayCapacity))
            s.relayVersion?.let { o.put("relayVersion", it) }
            o.put("relayVersionState", s.relayVersionState.name)
            o.put("openRooms", openRoomsToJsonArray(s.openRooms, includeModeratorKeys = false))

            arr.put(o)

            val moderatorKeys = JSONArray()
            s.openRooms.forEach { room ->
                val moderatorKey = room.moderatorKey.takeIf(String::isNotBlank)
                    ?: existingModeratorKeys[roomSecretIdentity(room)]
                if (!moderatorKey.isNullOrBlank()) {
                    moderatorKeys.put(
                        JSONObject()
                            .put("room", room.name)
                            .put("roomInstance", room.roomInstance)
                            .put("moderatorKey", moderatorKey)
                    )
                }
            }
            val secrets = JSONObject()
                .put("adminToken", adminToken.orEmpty())
                .put("deploymentAdminToken", deploymentAdminToken.orEmpty())
                .put("moderatorKeys", moderatorKeys)
            val secretKey = serverSecretsKey(s.id)
            activeSecretKeys += secretKey
            editor.putString(secretKey, secretCipher.seal(secrets.toString()))
        }

        securePrefs.all.keys
            .filter { it.startsWith(SERVER_SECRETS_PREFIX) && it !in activeSecretKeys }
            .forEach(editor::remove)

        check(
            editor
                .putString(SECURE_KEY, secretCipher.seal(arr.toString()))
                .commit()
        ) {
            "Unable to store server settings"
        }
    }

    fun upsert(server: InstallServer): List<InstallServer> {
        val list = load().toMutableList()
        val idx = list.indexOfFirst { it.id == server.id }
        if (idx >= 0) list[idx] = server else list.add(server)
        save(list)
        return load()
    }

    fun delete(id: String): List<InstallServer> {
        val list = load().filterNot { it.id == id }
        save(list)
        return load()
    }

    companion object {
        private const val LEGACY_PREFS_NAME = "install_servers"
        private const val LEGACY_KEY = "servers"
        private const val SECURE_PREFS_NAME = "install_servers_secure"
        private const val SECURE_KEY = "servers_json"
        private const val SERVER_SECRETS_PREFIX = "server_secrets_"
    }
}
