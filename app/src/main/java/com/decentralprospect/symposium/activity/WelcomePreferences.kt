package com.decentralprospect.symposium

import android.content.Context

private const val WELCOME_PREFS_NAME = "welcome_preferences"
private const val PREF_NEWS_PROMPT_SHOWN = "news_prompt_shown_v1"

internal fun MainActivity.loadWelcomeNewsPrompt() {
    val preferences = getSharedPreferences(WELCOME_PREFS_NAME, Context.MODE_PRIVATE)
    welcomeNewsPromptVisibleState = !preferences.getBoolean(PREF_NEWS_PROMPT_SHOWN, false)
}

internal fun MainActivity.dismissWelcomeNewsPrompt() {
    welcomeNewsPromptVisibleState = false
    getSharedPreferences(WELCOME_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PREF_NEWS_PROMPT_SHOWN, true)
        .apply()
}
