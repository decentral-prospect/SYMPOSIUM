package com.decentralprospect.symposium

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.decentralprospect.symposium.ui.theme.MyApplicationTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RelayCapacityInterfaceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val originalLocales = AppCompatDelegate.getApplicationLocales()

    @Before
    fun prepareDisplay() {
        val args = InstrumentationRegistry.getArguments()
        val landscape = args.getString("orientation") == "landscape"
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(args.getString("language") ?: "ru"))
        }
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation ==
                if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(400)
    }

    @After
    fun restoreDisplay() {
        instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(originalLocales) }
    }

    @Test
    fun speakerPresetsShowRequirementsAndSaveTheirLimits() {
        var config by mutableStateOf(RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD))
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize()) {
                    RelayAdvancedSettingsButton(config = config, enabled = true, onSave = { config = it })
                }
            }
        }
        compose.onNodeWithText(tr(TextId.STANDARD_CAPACITY_SUMMARY)).assertIsDisplayed()
        for (preset in RelayCapacityPreset.entries.filter { it != RelayCapacityPreset.CUSTOM }) {
            compose.onNodeWithText(tr(TextId.CONFIGURATION)).performClick()
            compose.onNodeWithText(tr(relayCapacityPresetLabelId(config.preset))).assertIsSelected()
            compose.onNodeWithText(tr(TextId.SPEAKER_DEFINITION)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(tr(relayCapacityPresetLabelId(preset))).performScrollTo().performClick()
            compose.onNodeWithText(tr(relayCapacityPresetLabelId(preset))).assertIsSelected()
            compose.onNodeWithText(tr(TextId.SERVER_REQUIREMENTS, tr(relayCapacityPresetDescriptionId(preset))))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(tr(TextId.LISTENER_CAPACITY, preset.listeners)).performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText(tr(TextId.LISTENER_CAPACITY, preset.listeners)).assertCountEquals(1)
            capture("capacity-${preset.speakers}")
            compose.onNodeWithText(tr(TextId.SAVE)).performClick()
            compose.runOnIdle { assertEquals(RelayCapacityConfig.forPreset(preset), config) }
            compose.onNodeWithText(relayCapacitySummary(config).resolve()).assertIsDisplayed()
        }
    }

    @Test
    fun customLimitsRetainConnectionHeadroomFromTheSelectedPreset() {
        var config by mutableStateOf(RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD))
        compose.setContent {
            MyApplicationTheme {
                RelayAdvancedSettingsButton(config = config, enabled = true, onSave = { config = it })
            }
        }
        compose.onNodeWithText(tr(TextId.CONFIGURATION)).performClick()
        compose.onNodeWithText(tr(TextId.CAPACITY_UP_TO_25_SPEAKERS)).performScrollTo().performClick()
        compose.onNodeWithText(tr(TextId.CUSTOM_CONFIGURATION)).performScrollTo().performClick()
        compose.onNodeWithText(tr(TextId.MAX_ACTIVE_PARTICIPANTS)).performScrollTo().performTextReplacement("1200")
        SystemClock.sleep(600)
        compose.onNodeWithText(tr(TextId.MAX_ACTIVE_PARTICIPANTS)).assertIsDisplayed()
        capture("capacity-custom")
        compose.onNodeWithText(tr(TextId.SAVE)).assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(
                RelayCapacityConfig.forPreset(RelayCapacityPreset.SPEAKERS_25).copy(
                    preset = RelayCapacityPreset.CUSTOM, maxActivePeers = 1_200
                ), config
            )
        }
    }

    private fun capture(name: String) {
        val folder = InstrumentationRegistry.getArguments().getString("screenshots") ?: return
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), folder).apply { mkdirs() }
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(200)
        File(directory, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
