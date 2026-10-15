package com.decentralprospect.symposium

import android.os.Build
import java.io.File

private val KNOWN_ROOT_PATHS = listOf(
    "/system/app/Superuser.apk",
    "/system/app/Kinguser.apk",
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/su/bin/su",
    "/data/local/su",
    "/data/local/bin/su",
    "/data/local/xbin/su",
    "/data/adb/magisk",
    "/sbin/.magisk",
    "/debug_ramdisk/.magisk"
)

internal data class RootWarningEvidence(
    val testKeys: Boolean,
    val suspiciousPaths: List<String>
) {
    val shouldWarn: Boolean
        get() = testKeys || suspiciousPaths.isNotEmpty()
}

internal fun collectRootWarningEvidence(
    buildTags: String? = Build.TAGS,
    pathExists: (String) -> Boolean = { path -> File(path).exists() }
): RootWarningEvidence {
    val paths = linkedSetOf<String>()
    paths += KNOWN_ROOT_PATHS
    System.getenv("PATH")
        .orEmpty()
        .split(File.pathSeparatorChar)
        .filter(String::isNotBlank)
        .forEach { directory -> paths += File(directory, "su").absolutePath }

    return RootWarningEvidence(
        testKeys = buildTags.orEmpty().contains("test-keys", ignoreCase = true),
        suspiciousPaths = paths.filter { path -> runCatching { pathExists(path) }.getOrDefault(false) }
    )
}
