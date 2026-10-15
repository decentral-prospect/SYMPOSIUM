package com.decentralprospect.symposium

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class OpenedProtectedSecret(
    val value: String,
    val requiresMigration: Boolean
)

/**
 * Adds an authentication-bound encryption layer over EncryptedSharedPreferences.
 *
 * EncryptedSharedPreferences protects data at rest, but its default master key can be used by the
 * app process without proving that the user has just unlocked the device. This key is generated in
 * Android Keystore with user authentication required. StrongBox is requested when the device can
 * provide it and transparently falls back to the regular hardware-backed Keystore otherwise.
 */
internal class AuthenticatedSecretCipher(
    context: Context,
    purpose: String,
    private val protectionRequired: () -> Boolean = {
        isSecretProtectionEnabled(context.applicationContext)
    }
) {
    private val appContext = context.applicationContext
    private val aad = "Symposium/$purpose/v2".toByteArray(Charsets.UTF_8)

    fun seal(cleartext: String): String {
        if (!protectionRequired()) return cleartext
        val cleartextBytes = cleartext.toByteArray(Charsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(cleartextBytes)
            listOf(
                FORMAT_PREFIX,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            ).joinToString(":")
        } finally {
            cleartextBytes.fill(0)
        }
    }

    fun open(storedValue: String): OpenedProtectedSecret {
        val protectionRequiredNow = protectionRequired()
        val storedValueIsProtected = storedValue.startsWith("$FORMAT_PREFIX:")
        if (!storedValueIsProtected) {
            return OpenedProtectedSecret(storedValue, protectionRequiredNow)
        }

        val parts = storedValue.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == FORMAT_PREFIX) {
            "invalid protected secret format"
        }
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
        require(iv.size == GCM_IV_BYTES) { "invalid protected secret IV" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        val cleartextBytes = cipher.doFinal(ciphertext)
        return try {
            OpenedProtectedSecret(cleartextBytes.toString(Charsets.UTF_8), !protectionRequiredNow)
        } finally {
            cleartextBytes.fill(0)
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    fun isProtected(storedValue: String): Boolean =
        storedValue.startsWith("$FORMAT_PREFIX:")

    private fun secretKey(): SecretKey {
        check(appContext.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            "a secure device lock is required to access protected secrets"
        }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateKey(useStrongBox = true)
            } catch (_: StrongBoxUnavailableException) {
                keyStore.deleteEntry(KEY_ALIAS)
            } catch (_: ProviderException) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }
        return generateKey(useStrongBox = false)
    }

    private fun generateKey(useStrongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .apply {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setUnlockedDeviceRequired(true)
                    setIsStrongBoxBacked(useStrongBox)
                }
            }
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        // The app authenticates at every launch. The bounded validity window keeps long-running
        // conference/admin sessions usable while the cryptographic key remains tied to that login.
        private const val AUTH_VALIDITY_SECONDS = 8 * 60 * 60
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "symposium_authenticated_secrets_v2"
        private const val FORMAT_PREFIX = "symposium-auth-v2"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
