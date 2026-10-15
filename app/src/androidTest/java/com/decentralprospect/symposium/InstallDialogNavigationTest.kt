package com.decentralprospect.symposium

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.decentralprospect.symposium.ui.theme.MyApplicationTheme
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InstallDialogNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val preferences = mutableListOf<SharedPreferences>()

    @After
    fun clearFixture() {
        compose.activityRule.scenario.close()
        preferences.forEach { it.edit().clear().commit() }
    }

    @Test
    fun installationKeepsTheServerPickerWindowOpenAndReturnsToIt() {
        val prefix = "install-navigation-${UUID.randomUUID()}-"
        val context = object : ContextWrapper(compose.activity) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(prefix + name, mode).also { preferences += it }
        }
        val server = InstallServer(
            id = "server", name = "Relay", ip = "192.0.2.20", username = "admin", password = "",
            installed = false, sshKeyInstalled = true, relayVersionState = RelayVersionState.NOT_INSTALLED
        )
        InstallServersStore(context).save(listOf(server))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MyApplicationTheme {
                    AppScreen(
                        appVersion = "0.0.0", reconnectMode = false, secretProtectionEnabled = false,
                        checkForAppUpdate = { null },
                        onAdminSessionAccess = { true }, onRequestBind = {},
                        onConnect = { _, _, _, _, _, _, _ -> error("Unexpected connection") },
                        onCancelReconnect = {}, onDisconnect = {}, onToggleSpeaker = { false },
                        onToggleMic = { false }, onToggleVideo = { false }, onToggleOutput = { true },
                        onSwitchCamera = {},
                        onInstall = { _, _, _, _, _, _, _ -> error("Unexpected installation") },
                        onRemoveRelay = { _, _, _, _, _, _ -> error("Unexpected removal") },
                        onObserveSshHostKeyPin = { error("Unexpected SSH request") },
                        onEnrollSshKey = { _, _, _, _ -> error("Unexpected SSH enrollment") },
                        onDeleteSshKey = { _, _ -> error("Unexpected SSH deletion") },
                        onProbeServer = { _, _, _, _ -> error("Unexpected server probe") },
                        onSetRoomOpenState = { _, _, _, _, _, _ -> error("Unexpected room change") },
                        onCreateRoom = { _, _, _, _, _, _, _ -> error("Unexpected room creation") },
                        onSetRoomSettings = { _, _, _, _, _, _, _ -> error("Unexpected room settings") },
                        onRotateModeratorKey = { _, _, _, _, _ -> error("Unexpected key rotation") },
                        onFetchOpenRooms = { _, _, _, _ -> error("Unexpected rooms request") },
                        onFetchRelayVersion = { _, _, _, _ -> error("Unexpected version request") }
                    )
                }
            }
        }
        compose.onNodeWithText(tr(TextId.CREATE_MEETING)).performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        val pickerId = compose.onAllNodes(isDialog()).fetchSemanticsNodes().single().id

        repeat(2) {
            compose.onNodeWithText(server.ip).performClick()
            compose.onAllNodes(isDialog()).assertCountEquals(2)
            assertTrue(compose.onAllNodes(isDialog()).fetchSemanticsNodes().any { it.id == pickerId })
            compose.onNodeWithText(tr(TextId.STANDARD_CAPACITY_SUMMARY)).assertIsDisplayed()
            compose.onNode(hasText(tr(TextId.INSTALL_SYMPOSIUMRELAY)) and hasClickAction())
                .assertIsDisplayed().assertIsEnabled()
            compose.onNode(
                hasContentDescription(tr(TextId.CLOSE_DIALOG)) and hasAnyAncestor(
                    isDialog() and hasAnyDescendant(hasText(tr(TextId.INSTALL_SYMPOSIUMRELAY)))
                )
            ).performClick()
            compose.onAllNodes(isDialog()).assertCountEquals(1)
            assertEquals(pickerId, compose.onAllNodes(isDialog()).fetchSemanticsNodes().single().id)
            compose.onNodeWithText(server.ip).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription(tr(TextId.CLOSE_DIALOG)).performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onAllNodesWithText(tr(TextId.CREATE_MEETING)).assertCountEquals(1)
    }
}
