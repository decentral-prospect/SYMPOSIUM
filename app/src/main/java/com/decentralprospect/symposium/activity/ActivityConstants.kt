package com.decentralprospect.symposium

internal const val TAG = "RTC_APP"
internal val APP_VERSION_NAME: String = canonicalVersion(BuildConfig.VERSION_NAME)
internal val APP_VERSION: String = APP_VERSION_NAME
internal val EXPECTED_RELAY_VERSION: String = APP_VERSION_NAME
private val CANONICAL_VERSION_PATTERN =
    Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?$")

internal fun canonicalVersion(version: String): String {
    val trimmed = version.trim()
    return if (
        trimmed.length > 1 &&
        (trimmed[0] == 'v' || trimmed[0] == 'V') &&
        trimmed[1].isDigit()
    ) {
        trimmed.substring(1)
    } else {
        trimmed
    }
}

internal fun isCanonicalVersion(version: String): Boolean =
    CANONICAL_VERSION_PATTERN.matches(version.trim())
internal const val TARGET_PUBLISH = "publish"
internal const val TARGET_SUBSCRIBE = "subscribe"
internal const val ROLE_GUEST = "guest"
internal const val ROLE_MODERATOR = "moderator"
internal const val RECONNECT_INITIAL_DELAY_MS = 350L
internal const val RECONNECT_BASE_DELAY_MS = 2_000L
internal const val RECONNECT_MAX_DELAY_MS = 30_000L
internal const val RECONNECT_MAX_JITTER_MS = 1_500L
internal const val RECONNECT_CONNECT_POLL_MS = 1_000L
internal const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L
internal const val WAKELOCK_REFRESH_MS = 5 * 60 * 1000L
internal const val PRIVACY_PREFS_NAME = "privacy_settings"
internal const val PREF_TELEMETRY_ENABLED = "telemetry_enabled"
internal const val TELEMETRY_STATS_INTERVAL_MS = 15000L
internal const val TELEMETRY_PING_EVENT_INTERVAL_MS = 60000L
internal const val TELEMETRY_SLOW_PING_MS = 1500L
internal const val TELEMETRY_CRITICAL_PING_MS = 3000L
internal const val TELEMETRY_BAD_JITTER_SEC = 0.08
internal const val TELEMETRY_BAD_CANDIDATE_RTT_SEC = 1.0
