package com.decentralprospect.symposium

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.core.os.LocaleListCompat
import java.util.Locale
import androidx.compose.material3.Text as MaterialText

private const val LANGUAGE_PREFS_NAME = "language_preferences"
private const val PREF_APP_LANGUAGE = "app_language"

enum class AppLanguage(
    val languageTag: String?,
    val labelId: TextId,
    val descriptionId: TextId
) {
    SYSTEM(null, TextId.SYSTEM, TextId.USE_THE_DEVICE_LANGUAGE),
    RUSSIAN("ru", TextId.RUSSIAN, TextId.ALWAYS_USE_RUSSIAN),
    ENGLISH("en", TextId.ENGLISH, TextId.ALWAYS_USE_ENGLISH)
}

internal object AppLocalization {
    private const val FALLBACK_LANGUAGE = "en"
    private val supportedLanguages = setOf("en", "ru")

    @Volatile
    private var catalog: LocalizationCatalog? = null

    fun initialize(context: Context) {
        if (catalog != null) return
        synchronized(this) {
            if (catalog == null) {
                catalog = LocalizationDatabase(context).loadCatalog()
            }
        }
    }

    fun text(id: TextId, vararg arguments: Any?): String {
        val loaded = checkNotNull(catalog) {
            "Localization database has not been initialized"
        }
        val language = currentLanguageCode()
        val template = loaded.translations[language]?.get(id)
            ?: loaded.translations[FALLBACK_LANGUAGE]?.get(id)
            ?: error("Missing localization text for ${id.name}")
        return formatTemplate(template, arguments)
    }

    /**
     * Compatibility path for messages received from the relay or installer as plain text.
     * New application copy must use [TextId] directly.
     */
    fun externalText(text: String): String {
        val loaded = catalog ?: return text
        val id = loaded.aliases[text] ?: return text
        return text(id)
    }

    private fun currentLanguageCode(): String {
        val appLocale = AppCompatDelegate.getApplicationLocales().get(0)
        val language = (appLocale ?: Locale.getDefault()).language.lowercase(Locale.ROOT)
        return language.takeIf(supportedLanguages::contains) ?: FALLBACK_LANGUAGE
    }
}

internal fun tr(id: TextId, vararg arguments: Any?): String =
    AppLocalization.text(id, *arguments)

internal fun trExternal(text: String): String = AppLocalization.externalText(text)

internal data class LocalizedText(
    val id: TextId,
    val arguments: List<Any?> = emptyList()
) {
    fun resolve(): String = tr(id, *arguments.toTypedArray())
}

private fun formatTemplate(template: String, arguments: Array<out Any?>): String {
    var result = template
    arguments.forEachIndexed { index, value ->
        result = result.replace("{$index}", value?.toString().orEmpty())
    }
    return result
}

internal fun MainActivity.loadLanguagePrefs() {
    val prefs = getSharedPreferences(LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(PREF_APP_LANGUAGE, AppLanguage.SYSTEM.name)
    val language = AppLanguage.entries.firstOrNull { it.name == raw } ?: AppLanguage.SYSTEM
    appLanguageState = language
    applyAppLanguage(language)
}

internal fun MainActivity.setAppLanguage(language: AppLanguage) {
    appLanguageState = language
    getSharedPreferences(LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_APP_LANGUAGE, language.name)
        .apply()
    applyAppLanguage(language)
}

private fun applyAppLanguage(language: AppLanguage) {
    val locales = language.languageTag
        ?.let(LocaleListCompat::forLanguageTags)
        ?: LocaleListCompat.getEmptyLocaleList()
    if (AppCompatDelegate.getApplicationLocales() != locales) {
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    MaterialText(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
internal fun Text(
    textId: TextId,
    vararg arguments: Any?,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    Text(
        text = tr(textId, *arguments),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
