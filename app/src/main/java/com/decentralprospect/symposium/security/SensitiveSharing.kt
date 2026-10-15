package com.decentralprospect.symposium

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal fun copySensitiveText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)

    Handler(Looper.getMainLooper()).postDelayed({
        val current = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        if (current == value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }, SENSITIVE_CLIPBOARD_TTL_MS)
}

internal fun shareTemporaryQr(
    context: Context,
    bitmap: Bitmap,
    containsModeratorKey: Boolean
): Boolean {
    return runCatching {
        val directory = File(context.cacheDir, "qr-share").apply { mkdirs() }
        directory.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > QR_CACHE_TTL_MS) {
                file.delete()
            }
        }
        val file = File(directory, "qr-${UUID.randomUUID()}.png")
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.temporary-content",
            file
        )
        val warning = if (containsModeratorKey) {
            tr(TextId.CONFIDENTIAL_THIS_QR_CODE_CONTAINS_AN_E2EE_KEY_AND_MODKEY_SHARE_IT_ONLY_)
        } else {
            tr(TextId.CONFIDENTIAL_THIS_QR_CODE_CONTAINS_AN_E2EE_KEY_SHARE_IT_ONLY_WITH_A_TRUS)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, warning)
            clipData = ClipData.newUri(context.contentResolver, tr(TextId.PROTECTED_QR_CODE), uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, tr(TextId.SHARE_QR_CODE)))
        Handler(Looper.getMainLooper()).postDelayed({ file.delete() }, QR_CACHE_TTL_MS)
        true
    }.getOrDefault(false)
}

private const val SENSITIVE_CLIPBOARD_TTL_MS = 60_000L
private const val QR_CACHE_TTL_MS = 10 * 60_000L
