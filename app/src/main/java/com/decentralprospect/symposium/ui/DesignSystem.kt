package com.decentralprospect.symposium

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Patterns
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.hsl
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import android.graphics.Color as AndroidColor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class AccessibilityFontScale(
    val labelId: TextId,
    val talkBackLabelId: TextId,
    val multiplier: Float
) {
    SYSTEM(TextId.SYSTEM, TextId.ANDROID_SYSTEM_FONT_SIZE, 1f),
    LARGE(TextId.LARGE, TextId.LARGE_FONT, 1.18f),
    EXTRA_LARGE(TextId.EXTRA_LARGE, TextId.EXTRA_LARGE_FONT, 1.34f)
}

enum class AppThemeMode(
    val labelId: TextId,
    val talkBackLabelId: TextId
) {
    SYSTEM(TextId.SYSTEM_2, TextId.USE_THE_DEVICE_THEME),
    DARK(TextId.DARK, TextId.ALWAYS_USE_THE_DARK_THEME),
    LIGHT(TextId.LIGHT, TextId.ALWAYS_USE_THE_LIGHT_THEME)
}


internal val LocalAccessibilityFontScale = staticCompositionLocalOf { AccessibilityFontScale.SYSTEM }

@Composable
internal fun accessibleAccentColor(): Color = AppAccent

@Composable
internal fun accessiblePositiveColor(): Color = AppSuccess

@Composable
internal fun accessibleDangerColor(): Color = AppError

internal val CardShape = RoundedCornerShape(14.dp)
internal val InnerCardShape = RoundedCornerShape(10.dp)
internal val AppButtonShape = RoundedCornerShape(10.dp)
internal const val DEFAULT_RELAY_HTTP_PORT = 443
internal const val MAX_INVITATION_TOKEN_LENGTH = 8_192
private const val MAX_CONNECT_ENDPOINT_LENGTH = 300
private const val MAX_CONNECT_ROOM_LENGTH = 128
private const val MAX_CONNECT_USERNAME_LENGTH = 128
private const val MAX_CONNECT_MODERATOR_KEY_LENGTH = 256
private const val INVITATION_TOKEN_PREFIX = "SPT1."
private const val INVITATION_BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
private const val INVITATION_TOKEN_VERSION = 1
private const val INVITATION_TOKEN_NONCE_BYTES = 12
private const val INVITATION_TOKEN_TAG_BITS = 128
private val INVITATION_TOKEN_AAD = "SymposiumInvitationToken/v1".toByteArray(Charsets.UTF_8)
internal fun sshPasswordStorageNotice(): String =
    tr(TextId.WE_DO_NOT_STORE_THE_SERVER_PASSWORD_INSTEAD_SYMPOSIUM_CREATES_AN_SSH_KEY)

internal enum class RootScreen { HOME, MENU, SERVERS, SETTINGS, ABOUT }

enum class MeetLayout(
    private val fixedLabel: String? = null,
    private val labelId: TextId? = null
) {
    AUTO(labelId = TextId.AUTO_LAYOUT),
    GRID_2(fixedLabel = "2×2"),
    GRID_3(fixedLabel = "3×3"),
    GRID_4(fixedLabel = "4×4"),
    SPOTLIGHT(labelId = TextId.SPOTLIGHT_LAYOUT);

    val label: String
        get() = labelId?.let(::tr) ?: fixedLabel.orEmpty()
}

internal fun columnsFor(layout: MeetLayout, count: Int): Int {
    return when (layout) {
        MeetLayout.GRID_2 -> 2
        MeetLayout.GRID_3 -> 3
        MeetLayout.GRID_4 -> 4
        MeetLayout.SPOTLIGHT -> 2
        MeetLayout.AUTO -> when {
            count <= 1 -> 1
            count <= 4 -> 2
            count <= 9 -> 3
            else -> 4
        }
    }
}

enum class CallViewMode { FOCUS, GRID, FULLSCREEN }

enum class AudioOutputRoute {
    EARPIECE,
    SPEAKER,
    HEADSET
}

internal fun audioOutputRouteFromRaw(raw: String): AudioOutputRoute {
    return when (raw.trim().lowercase()) {
        "speaker" -> AudioOutputRoute.SPEAKER
        "bluetooth", "wired_headset", "headset" -> AudioOutputRoute.HEADSET
        "earpiece" -> AudioOutputRoute.EARPIECE
        else -> AudioOutputRoute.EARPIECE
    }
}

internal fun AudioOutputRoute.toWireValue(): String {
    return when (this) {
        AudioOutputRoute.EARPIECE -> "earpiece"
        AudioOutputRoute.SPEAKER -> "speaker"
        AudioOutputRoute.HEADSET -> "headset"
    }
}

internal data class InvitationTokenPayload(
    val ip: String,
    val room: String,
    val roomInstance: String,
    val tlsPin: String,
    val e2eeSecret: String,
    val requiredVersion: String = "",
    val username: String = "",
    val moderatorKey: String = ""
)

data class LobbyPeerStatus(
    val peerId: String,
    val username: String,
    val joinedAt: Long = 0L
)

internal fun parseInvitationToken(raw: String): InvitationTokenPayload? {
    if (raw.isBlank() || raw.length > MAX_INVITATION_TOKEN_LENGTH) return null
    return runCatching { parseInvitationTokenChecked(raw, enforceClientVersion = true) }.getOrNull()
}

internal fun invitationTokenRequiredVersionMismatch(raw: String): String? {
    if (raw.isBlank() || raw.length > MAX_INVITATION_TOKEN_LENGTH) return null
    val parsed = runCatching {
        parseInvitationTokenChecked(raw, enforceClientVersion = false)
    }.getOrNull() ?: return null
    return parsed.requiredVersion.takeIf {
        it.isNotBlank() && it != APP_VERSION
    }
}

private fun parseInvitationTokenChecked(
    raw: String,
    enforceClientVersion: Boolean
): InvitationTokenPayload? {
    val input = raw.trim()
    if (!input.startsWith(INVITATION_TOKEN_PREFIX) || input.any(Char::isWhitespace)) return null
    val encodedEnvelope = input.removePrefix(INVITATION_TOKEN_PREFIX)
    if (encodedEnvelope.isBlank()) return null
    val envelope = Base64.decode(encodedEnvelope, INVITATION_BASE64_FLAGS)
    if (Base64.encodeToString(envelope, INVITATION_BASE64_FLAGS) != encodedEnvelope) return null
    val minimumEnvelopeSize = 1 + CONFERENCE_E2EE_SECRET_BYTES + INVITATION_TOKEN_NONCE_BYTES + 16
    if (envelope.size < minimumEnvelopeSize) return null
    if (envelope[0].toInt() != INVITATION_TOKEN_VERSION) return null

    val secretBytes = envelope.copyOfRange(1, 1 + CONFERENCE_E2EE_SECRET_BYTES)
    val nonceOffset = 1 + CONFERENCE_E2EE_SECRET_BYTES
    val nonce = envelope.copyOfRange(nonceOffset, nonceOffset + INVITATION_TOKEN_NONCE_BYTES)
    val ciphertext = envelope.copyOfRange(nonceOffset + INVITATION_TOKEN_NONCE_BYTES, envelope.size)
    val plaintext = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(secretBytes, "AES"),
            GCMParameterSpec(INVITATION_TOKEN_TAG_BITS, nonce)
        )
        updateAAD(INVITATION_TOKEN_AAD)
        doFinal(ciphertext)
    }

    val fields = DataInputStream(ByteArrayInputStream(plaintext)).use { inputStream ->
        val values = listOf(
            inputStream.readTokenString(MAX_CONNECT_ENDPOINT_LENGTH),
            inputStream.readTokenString(MAX_CONNECT_ROOM_LENGTH),
            inputStream.readTokenString(64),
            inputStream.readTokenString(128),
            inputStream.readTokenString(64),
            inputStream.readTokenString(MAX_CONNECT_USERNAME_LENGTH),
            inputStream.readTokenString(MAX_CONNECT_MODERATOR_KEY_LENGTH)
        )
        if (inputStream.available() != 0) return null
        values
    }
    val ip = fields[0].trim()
    val room = fields[1].trim()
    val roomInstance = fields[2].trim()
    val tlsPin = normalizeRelayTlsPin(fields[3])
    val requiredVersion = fields[4].trim()
    val username = fields[5].trim()
    val moderatorKey = fields[6].trim()
    if (!isSafeConnectValue(room, MAX_CONNECT_ROOM_LENGTH, required = true)) return null
    if (!isValidRoomInstance(roomInstance)) return null
    if (!isSafeConnectValue(username, MAX_CONNECT_USERNAME_LENGTH, required = false)) return null
    if (!isSafeConnectValue(moderatorKey, MAX_CONNECT_MODERATOR_KEY_LENGTH, required = false)) return null
    if (requiredVersion.isNotBlank() && !isCanonicalVersion(requiredVersion)) return null
    if (
        enforceClientVersion &&
        requiredVersion.isNotBlank() &&
        requiredVersion != APP_VERSION
    ) return null

    if (!isSafeConnectEndpoint(ip) || parseRelayEndpoint(ip) == null) return null
    val e2eeSecret = encodeConferenceE2eeSecret(secretBytes)
    return InvitationTokenPayload(
        ip = ip,
        room = room,
        roomInstance = roomInstance,
        tlsPin = tlsPin,
        e2eeSecret = e2eeSecret,
        requiredVersion = requiredVersion,
        username = username,
        moderatorKey = moderatorKey
    )
}

private fun DataInputStream.readTokenString(maxLength: Int): String {
    val byteCount = readUnsignedShort()
    require(byteCount <= maxLength * 4) { "invitation field is too large" }
    val bytes = ByteArray(byteCount)
    readFully(bytes)
    return bytes.toString(Charsets.UTF_8).also {
        require(it.toByteArray(Charsets.UTF_8).contentEquals(bytes)) { "invalid UTF-8" }
        require(it.length <= maxLength) { "invitation field is too large" }
    }
}

private fun DataOutputStream.writeTokenString(value: String, maxLength: Int) {
    require(value.length <= maxLength) { "invitation field is too large" }
    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= 65_535) { "invitation field is too large" }
    writeShort(bytes.size)
    write(bytes)
}

private fun isSafeConnectEndpoint(value: String): Boolean =
    isSafeConnectValue(value, MAX_CONNECT_ENDPOINT_LENGTH, required = true) &&
        value.none { it.isWhitespace() || it in "/\\?#@%" } &&
        "://" !in value

private fun isSafeConnectValue(value: String, maxLength: Int, required: Boolean): Boolean {
    if (required && value.isBlank()) return false
    return value.length <= maxLength && value.none(Char::isISOControl)
}

internal fun verifiedRoomRequiredVersion(room: OpenRoomInfo): String? =
    room.requiredVersion.trim().takeIf(::isCanonicalVersion)

internal fun buildInvitationToken(
    ip: String,
    httpsPort: Int?,
    room: String,
    roomInstance: String,
    relayTlsPin: String?,
    e2eeSecret: String,
    requiredVersion: String,
    moderatorKey: String? = null,
    random: SecureRandom = SecureRandom()
): String {
    val pin = normalizeRelayTlsPin(relayTlsPin?.trim().orEmpty())
    val port = httpsPort ?: DEFAULT_RELAY_HTTP_PORT
    val endpoint = if (port == DEFAULT_RELAY_HTTP_PORT) ip.trim() else "${ip.trim()}:$port"
    require(isSafeConnectEndpoint(endpoint) && parseRelayEndpoint(endpoint) != null) {
        "invalid relay endpoint"
    }
    val version = requiredVersion.trim().also {
        require(isCanonicalVersion(it)) { "invalid required version" }
    }
    val normalizedRoom = room.trim().also {
        require(isSafeConnectValue(it, MAX_CONNECT_ROOM_LENGTH, required = true)) { "invalid room" }
    }
    require(isValidRoomInstance(roomInstance)) { "invalid room instance" }
    val modKey = moderatorKey?.trim().orEmpty()
    require(isSafeConnectValue(modKey, MAX_CONNECT_MODERATOR_KEY_LENGTH, required = false)) {
        "invalid moderator key"
    }
    val secretBytes = decodeConferenceE2eeSecret(e2eeSecret)
    val plaintext = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeTokenString(endpoint, MAX_CONNECT_ENDPOINT_LENGTH)
            output.writeTokenString(normalizedRoom, MAX_CONNECT_ROOM_LENGTH)
            output.writeTokenString(roomInstance.trim(), 64)
            output.writeTokenString(pin, 128)
            output.writeTokenString(version, 64)
            output.writeTokenString("", MAX_CONNECT_USERNAME_LENGTH)
            output.writeTokenString(modKey, MAX_CONNECT_MODERATOR_KEY_LENGTH)
        }
        buffer.toByteArray()
    }
    val nonce = ByteArray(INVITATION_TOKEN_NONCE_BYTES).also(random::nextBytes)
    val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(secretBytes, "AES"),
            GCMParameterSpec(INVITATION_TOKEN_TAG_BITS, nonce)
        )
        updateAAD(INVITATION_TOKEN_AAD)
        doFinal(plaintext)
    }
    val envelope = ByteArray(1 + secretBytes.size + nonce.size + ciphertext.size)
    envelope[0] = INVITATION_TOKEN_VERSION.toByte()
    secretBytes.copyInto(envelope, destinationOffset = 1)
    nonce.copyInto(envelope, destinationOffset = 1 + secretBytes.size)
    ciphertext.copyInto(envelope, destinationOffset = 1 + secretBytes.size + nonce.size)
    return INVITATION_TOKEN_PREFIX + Base64.encodeToString(envelope, INVITATION_BASE64_FLAGS)
}

internal fun generateQrBitmap(content: String, sizePx: Int = 900): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
    )

    val matrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        hints
    )

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)

    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(
                x,
                y,
                if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            )
        }
    }

    return bitmap
}

internal fun generateQrBitmapOrNull(content: String, sizePx: Int = 900): Bitmap? =
    runCatching { generateQrBitmap(content, sizePx) }.getOrNull()

internal fun normalizeIpInput(raw: String): String {
    val input = raw.trim()
    if (input.isBlank()) return ""

    if ("://" in input) {
        val uri = runCatching { Uri.parse(input) }.getOrNull()
        val host = uri?.host?.trim().orEmpty()
        if (host.isNotBlank()) return host
    }

    return input
        .substringBefore("/")
        .substringBefore(":")
        .trim()
}

internal fun isValidIpAddress(value: String): Boolean {
    return value.isNotBlank() && Patterns.IP_ADDRESS.matcher(value).matches()
}

internal fun openRoomsLabel(count: Int): String {
    return count.toString()
}

enum class RelayVersionState {
    UNKNOWN,
    NOT_INSTALLED,
    CURRENT,
    OUTDATED
}

data class InstallServer(
    val id: String,
    val name: String,
    val ip: String,
    val username: String,
    val password: String,
    val sshKeyInstalled: Boolean = false,
    val installed: Boolean,
    val sshHostKeyPin: String? = null,
    val httpsPort: Int? = null,
    val relayTlsPin: String? = null,
    val adminToken: String? = null,
    val adminTokenAvailable: Boolean = !adminToken.isNullOrBlank(),
    val deploymentProfile: DeploymentProfile? = null,
    val relayCapacity: RelayCapacityConfig = RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD),
    val openRooms: List<OpenRoomInfo> = emptyList(),
    val relayVersion: String? = null,
    val relayVersionState: RelayVersionState = RelayVersionState.UNKNOWN
)

internal fun serverDisplayAddress(server: InstallServer): String {
    return server.ip.ifBlank { server.name }
}

internal fun displayName(peer: PeerStatus): String {
    val n = peer.username.trim()
    return if (n.isNotBlank()) n else peer.peerId
}

internal fun displayName(peer: LobbyPeerStatus): String {
    val n = peer.username.trim()
    return if (n.isNotBlank()) n else peer.peerId
}

internal fun inactiveVideoColor(): Color = Color(0xFF17324D)

internal fun serverStateLabel(server: InstallServer): String {
    if (server.relayVersionState == RelayVersionState.UNKNOWN) {
        return tr(TextId.CHECKING_VERSION)
    }
    if (!server.installed || server.relayVersionState == RelayVersionState.NOT_INSTALLED) {
        return tr(TextId.SYMPOSIUMRELAY_IS_NOT_INSTALLED)
    }

    return when (server.relayVersionState) {
        RelayVersionState.CURRENT -> tr(TextId.RELAY_UP_TO_DATE, server.relayVersion.orEmpty())
        RelayVersionState.OUTDATED -> tr(TextId.RELAY_OUTDATED, server.relayVersion ?: "—")
        RelayVersionState.UNKNOWN -> tr(TextId.CHECKING_VERSION)
        RelayVersionState.NOT_INSTALLED -> tr(TextId.SYMPOSIUMRELAY_IS_NOT_INSTALLED)
    }
}

@Composable
internal fun serverStateColor(server: InstallServer): Color {
    if (server.relayVersionState == RelayVersionState.UNKNOWN) {
        return AppIdle
    }
    if (!server.installed || server.relayVersionState == RelayVersionState.NOT_INSTALLED) {
        return accessibleDangerColor()
    }

    return when (server.relayVersionState) {
        RelayVersionState.CURRENT -> accessiblePositiveColor()
        RelayVersionState.OUTDATED -> AppWarning
        RelayVersionState.UNKNOWN -> AppIdle
        RelayVersionState.NOT_INSTALLED -> accessibleDangerColor()
    }
}

internal interface UiBinder {
    fun setStatus(s: String)
    fun setConnected(on: Boolean)
    fun setMic(on: Boolean)
    fun setVideo(on: Boolean)
    fun setOutput(on: Boolean)
    fun setSpeaker(on: Boolean)
    fun setPeerId(id: String)
    fun setIceState(s: String)
    fun setPcState(s: String)
    fun setCameraDebug(s: String)
    fun updatePeers(peers: List<PeerStatus>)
    fun setAudioRoute(route: String, headsetAvailable: Boolean)
    fun setRole(role: String)
    fun setLobbyWaiting(waiting: Boolean)
    fun onRelayConnectionAuthenticated()
    fun updateLobbyPending(peers: List<LobbyPeerStatus>)
    fun setMuteAll(on: Boolean)
    fun setForcedMute(on: Boolean)
    fun setPeerMuted(peerId: String, muted: Boolean)
    fun setSelfHandRaised(on: Boolean)
    fun setPeerHandRaised(peerId: String, raised: Boolean)
    fun setPeerAudioEnabled(peerId: String, enabled: Boolean)
    fun setPeerVideoEnabled(peerId: String, enabled: Boolean)
}

internal enum class ActionButtonKind { PRIMARY, SECONDARY, DANGER, GHOST }

@Composable
internal fun appTextButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Color.Transparent,
    contentColor = AppAccent,
    disabledContainerColor = Color.Transparent,
    disabledContentColor = DarkTextSecondary.copy(alpha = 0.55f)
)
