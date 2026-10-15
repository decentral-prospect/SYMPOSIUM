package com.decentralprospect.symposium

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

private const val RELAY_TLS_PIN_PREFS_NAME = "relay_tls_pin_trust_v1"
private val RELAY_TLS_PIN_PATTERN = Regex("^sha256/[A-Za-z0-9+/]{43}=$")
private const val CANONICAL_SHA256_BASE64_LAST_CHARS = "AEIMQUYcgkosw048"

internal data class RelayEndpoint(
    val host: String,
    val port: Int
) {
    val authority: String
        get() = if (':' in host) "[$host]:$port" else "$host:$port"
}

internal fun parseRelayEndpoint(raw: String): RelayEndpoint? {
    val authority = raw.trim()
        .replaceFirst(Regex("^(?i:https?|wss?)://"), "")
        .substringBefore('/')
        .trim()
    if (
        authority.isBlank() ||
        authority.length > 300 ||
        authority.any { it.isWhitespace() || it.isISOControl() } ||
        authority.any { it == '?' || it == '#' || it == '@' || it == '%' }
    ) return null

    val host: String
    val port: Int
    if (authority.startsWith("[")) {
        val closingBracket = authority.indexOf(']')
        if (closingBracket <= 1) return null
        host = authority.substring(1, closingBracket)
        val remainder = authority.substring(closingBracket + 1)
        port = when {
            remainder.isBlank() -> DEFAULT_RELAY_HTTP_PORT
            remainder.startsWith(':') -> remainder.drop(1).toIntOrNull() ?: return null
            else -> return null
        }
    } else {
        val colonCount = authority.count { it == ':' }
        if (colonCount == 1) {
            val candidatePort = authority.substringAfterLast(':')
            if (candidatePort.all(Char::isDigit) && candidatePort.isNotBlank()) {
                host = authority.substringBeforeLast(':')
                port = candidatePort.toIntOrNull() ?: return null
            } else {
                return null
            }
        } else {
            host = authority
            port = DEFAULT_RELAY_HTTP_PORT
        }
    }

    val canonicalHost = host.trim().lowercase()
    if (!isSafeRelayHost(canonicalHost) || port !in 1..65535) return null
    return RelayEndpoint(canonicalHost, port)
}

private fun isSafeRelayHost(host: String): Boolean {
    if (host.isBlank() || host.length > 253) return false
    if (':' in host) {
        return host.count { it == ':' } >= 2 && host.all { it in "0123456789abcdef:." }
    }
    return host.split('.').all { label ->
        label.isNotBlank() &&
            label.length <= 63 &&
            label.first() != '-' &&
            label.last() != '-' &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}

internal fun normalizeRelayTlsPin(raw: String): String {
    val pin = raw.trim()
    require(RELAY_TLS_PIN_PATTERN.matches(pin)) { "invalid TLS pin format" }
    // A 32-byte SHA-256 digest leaves two input bytes in the final Base64 block.
    // Its last data character therefore has four payload bits and two zero pad bits.
    require(pin[pin.length - 2] in CANONICAL_SHA256_BASE64_LAST_CHARS) {
        "non-canonical TLS pin"
    }
    return pin
}

internal fun conferenceE2eeFingerprint(rawSecret: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(decodeConferenceE2eeSecret(rawSecret))
    return digest
        .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xff) }
}

internal enum class InvitationTlsTrustState {
    FIRST_CONNECTION,
    MATCHED,
    CHANGED,
    CHANGED_ACCEPTED
}

internal data class InvitationTlsTrust(
    val endpoint: RelayEndpoint,
    val presentedPin: String,
    val savedPin: String?,
    val state: InvitationTlsTrustState
) {
    val mayConnect: Boolean
        get() = state != InvitationTlsTrustState.CHANGED
}

internal fun assessInvitationTlsTrust(
    endpoint: RelayEndpoint,
    presentedPin: String,
    savedPin: String?,
    changedPinAccepted: Boolean
): InvitationTlsTrust {
    val normalizedPresented = normalizeRelayTlsPin(presentedPin)
    val normalizedSaved = savedPin?.takeIf { it.isNotBlank() }?.let(::normalizeRelayTlsPin)
    val state = when {
        normalizedSaved == null -> InvitationTlsTrustState.FIRST_CONNECTION
        MessageDigest.isEqual(
            normalizedPresented.toByteArray(Charsets.UTF_8),
            normalizedSaved.toByteArray(Charsets.UTF_8)
        ) -> InvitationTlsTrustState.MATCHED
        changedPinAccepted -> InvitationTlsTrustState.CHANGED_ACCEPTED
        else -> InvitationTlsTrustState.CHANGED
    }
    return InvitationTlsTrust(endpoint, normalizedPresented, normalizedSaved, state)
}

internal data class AcceptedInvitationTlsPin(
    val endpoint: RelayEndpoint,
    val pin: String
)

internal data class PendingInvitationTrust(
    val endpoint: RelayEndpoint,
    val tlsPin: String,
    val room: String,
    val roomInstance: String,
    val e2eeSecret: String
)

internal class RelayTlsPinTrustStore(
    context: Context,
    private val preferencesName: String = RELAY_TLS_PIN_PREFS_NAME
) {
    private val appContext = context.applicationContext
    private val secretCipher = AuthenticatedSecretCipher(
        context = appContext,
        purpose = "relay-tls-pins",
        protectionRequired = {
            preferencesName == RELAY_TLS_PIN_PREFS_NAME && isSecretProtectionEnabled(appContext)
        }
    )

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            preferencesName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasEncryptedSecrets(): Boolean = securePrefs.all.isNotEmpty()

    fun get(endpoint: RelayEndpoint): String? {
        val key = storageKey(endpoint)
        val stored = securePrefs.getString(key, null) ?: return null
        val opened = runCatching { secretCipher.open(stored) }.getOrNull() ?: return null
        val pin = runCatching { normalizeRelayTlsPin(opened.value) }.getOrNull() ?: return null
        if (opened.requiresMigration) {
            check(securePrefs.edit().putString(key, secretCipher.seal(pin)).commit()) {
                "failed to migrate the relay TLS pin"
            }
        }
        return pin
    }

    fun save(endpoint: RelayEndpoint, rawPin: String): String {
        val pin = normalizeRelayTlsPin(rawPin)
        check(
            securePrefs.edit()
                .putString(storageKey(endpoint), secretCipher.seal(pin))
                .commit()
        ) {
            "failed to store the relay TLS pin"
        }
        return pin
    }

    fun remove(endpoint: RelayEndpoint) {
        securePrefs.edit().remove(storageKey(endpoint)).apply()
    }

    fun migrateAll() {
        val editor = securePrefs.edit()
        var changed = false
        securePrefs.all.forEach { (key, value) ->
            val stored = value as? String ?: return@forEach
            val opened = secretCipher.open(stored)
            if (!opened.requiresMigration) return@forEach
            val pin = normalizeRelayTlsPin(opened.value)
            editor.putString(key, secretCipher.seal(pin))
            changed = true
        }
        if (changed) check(editor.commit()) { "failed to migrate relay TLS pins" }
    }

    private fun storageKey(endpoint: RelayEndpoint): String =
        MessageDigest.getInstance("SHA-256")
            .digest(endpoint.authority.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
