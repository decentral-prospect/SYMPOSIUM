package com.decentralprospect.symposium

import android.app.KeyguardManager
import android.content.Context
import android.widget.Toast

private const val SECURITY_PREFS_NAME = "security_preferences"
private const val PREF_SECRET_PROTECTION_ENABLED = "secret_protection_enabled"
private const val PREF_SECRET_PROTECTION_CHOICE_MADE = "secret_protection_choice_made_v1"

internal fun isSecretProtectionEnabled(context: Context): Boolean {
    val appContext = context.applicationContext
    val preferences = appContext.getSharedPreferences(SECURITY_PREFS_NAME, Context.MODE_PRIVATE)
    if (!preferences.getBoolean(PREF_SECRET_PROTECTION_CHOICE_MADE, false)) return false
    val requested = preferences.getBoolean(PREF_SECRET_PROTECTION_ENABLED, false)
    val deviceIsSecure = appContext.getSystemService(KeyguardManager::class.java).isDeviceSecure
    return requested && deviceIsSecure
}

internal fun effectiveSecretProtectionEnabled(
    requested: Boolean,
    deviceIsSecure: Boolean
): Boolean = requested && deviceIsSecure

private fun MainActivity.persistSecretProtectionEnabled(enabled: Boolean): Boolean =
    getSharedPreferences(SECURITY_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PREF_SECRET_PROTECTION_ENABLED, enabled)
        .commit()

private fun MainActivity.hasExistingSensitiveData(): Boolean = runCatching {
    InstallServersStore(applicationContext).hasEncryptedSecrets() ||
        ConferenceE2eeSecretStore(applicationContext).hasStoredSecrets() ||
        RelayTlsPinTrustStore(applicationContext).hasEncryptedSecrets()
}.getOrDefault(false)

internal fun MainActivity.loadSecretProtectionPrefs() {
    val preferences = getSharedPreferences(SECURITY_PREFS_NAME, Context.MODE_PRIVATE)
    var choiceMade = preferences.getBoolean(PREF_SECRET_PROTECTION_CHOICE_MADE, false)

    // Older builds protected saved secrets by default and had no explicit-choice marker.
    // Preserve access to those values while fresh installations start unprotected.
    if (!choiceMade && hasExistingSensitiveData()) {
        choiceMade = preferences.edit()
            .putBoolean(PREF_SECRET_PROTECTION_CHOICE_MADE, true)
            .putBoolean(PREF_SECRET_PROTECTION_ENABLED, true)
            .commit()
    }

    if (!choiceMade) {
        preferences.edit().putBoolean(PREF_SECRET_PROTECTION_ENABLED, false).commit()
        secretProtectionEnabledState = false
        secretProtectionChoicePromptVisibleState = true
        return
    }

    val requested = preferences.getBoolean(PREF_SECRET_PROTECTION_ENABLED, false)
    val deviceIsSecure = getSystemService(KeyguardManager::class.java).isDeviceSecure
    val enabled = effectiveSecretProtectionEnabled(requested, deviceIsSecure)

    // A missing or removed device lock must never make the application impossible to open.
    // Remember the effective disabled state so protection is not unexpectedly re-enabled later.
    if (requested != enabled) {
        preferences.edit().putBoolean(PREF_SECRET_PROTECTION_ENABLED, enabled).commit()
    }
    secretProtectionEnabledState = enabled
    secretProtectionChoicePromptVisibleState = false
}

internal fun MainActivity.setSecretProtectionEnabled(
    enabled: Boolean,
    showToast: Boolean = false,
    onResult: (Boolean) -> Unit = {}
) {
    if (enabled == secretProtectionEnabledState) {
        onResult(true)
        return
    }
    if (enabled && !getSystemService(KeyguardManager::class.java).isDeviceSecure) {
        if (showToast) {
            Toast.makeText(
                this,
                tr(TextId.SET_UP_A_DEVICE_SCREEN_LOCK_BEFORE_ENABLING_STARTUP_PROTECTION),
                Toast.LENGTH_LONG
            ).show()
        }
        onResult(false)
        return
    }

    requestDeviceAuthentication(
        onSuccess = {
            val previous = secretProtectionEnabledState
            val updated = persistSecretProtectionEnabled(enabled)
            secretProtectionEnabledState = enabled && updated

            if (!updated || !migrateAuthenticatedSecrets()) {
                persistSecretProtectionEnabled(previous)
                secretProtectionEnabledState = previous
                // A migration may have completed only partially. Normalize every value back to
                // the previous mode while the authentication window is still valid.
                runCatching { migrateAuthenticatedSecrets() }
                if (showToast) {
                    Toast.makeText(
                        this,
                        tr(TextId.COULD_NOT_CHANGE_PROTECTION_FOR_SAVED_DATA),
                        Toast.LENGTH_LONG
                    ).show()
                }
                onResult(false)
                return@requestDeviceAuthentication
            }

            migrateLegacySshCredentialsForSession()
            if (showToast) {
                val message = if (enabled) {
                    tr(TextId.STARTUP_PROTECTION_ENABLED)
                } else {
                    tr(TextId.STARTUP_PROTECTION_DISABLED)
                }
                Toast.makeText(this, trExternal(message), Toast.LENGTH_SHORT).show()
            }
            onResult(true)
        },
        onError = { onResult(false) }
    )
}

internal fun MainActivity.resolveInitialSecretProtectionChoice(enabled: Boolean) {
    if (!secretProtectionChoicePromptVisibleState) return
    val preferences = getSharedPreferences(SECURITY_PREFS_NAME, Context.MODE_PRIVATE)

    if (!enabled) {
        val saved = preferences.edit()
            .putBoolean(PREF_SECRET_PROTECTION_ENABLED, false)
            .putBoolean(PREF_SECRET_PROTECTION_CHOICE_MADE, true)
            .commit()
        if (saved) {
            secretProtectionEnabledState = false
            secretProtectionChoicePromptVisibleState = false
            requestNotificationPermissionIfNeeded()
        }
        return
    }

    if (!preferences.edit().putBoolean(PREF_SECRET_PROTECTION_CHOICE_MADE, true).commit()) return
    setSecretProtectionEnabled(enabled = true, showToast = true) { success ->
        if (success) {
            secretProtectionChoicePromptVisibleState = false
            requestNotificationPermissionIfNeeded()
        } else {
            preferences.edit()
                .putBoolean(PREF_SECRET_PROTECTION_ENABLED, false)
                .remove(PREF_SECRET_PROTECTION_CHOICE_MADE)
                .commit()
            secretProtectionEnabledState = false
        }
    }
}
