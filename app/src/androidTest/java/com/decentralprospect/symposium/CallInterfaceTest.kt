package com.decentralprospect.symposium

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.decentralprospect.symposium.ui.theme.MyApplicationTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CallInterfaceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val server = InstallServer(
        id = "server", name = "Server", ip = "relay.example", username = "", password = "",
        installed = true, relayVersionState = RelayVersionState.CURRENT
    )
    private val remote = PeerStatus("remote", "Alex", 42, true, false, 0f)
    private val self = PeerStatus("self", "Sam", 30, true, false, 0f)

    @Before
    fun prepareOrientation() {
        val landscape = InstrumentationRegistry.getArguments().getString("orientation") == "landscape"
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation ==
                if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(400)
    }

    @Test
    fun gridSelectionExpandsAndReturnsWithoutDuplicatingParticipants() {
        var mode by mutableStateOf(CallViewMode.GRID)
        var pinned by mutableStateOf<String?>(null)
        var peers by mutableStateOf(listOf(self, remote))
        showCall({ mode }, { pinned }, { peers }, { mode = it }, { pinned = it })

        compose.onAllNodes(hasText("Alex") and hasClickAction()).assertCountEquals(1)
        capture("call-grid")
        compose.onNode(hasText("Alex") and hasClickAction()).performClick()
        compose.runOnIdle {
            assertEquals(CallViewMode.FOCUS, mode)
            assertEquals("remote", pinned)
        }
        capture("call-focus")
        compose.onNodeWithContentDescription(tr(TextId.EXPAND_VIDEO)).performClick()
        compose.onNodeWithContentDescription(tr(TextId.SEARCH_PARTICIPANTS)).assertDoesNotExist()
        compose.onNodeWithContentDescription(tr(TextId.SHOW_GRID)).assertDoesNotExist()
        compose.onAllNodesWithText("Sam").assertCountEquals(0)
        compose.onNodeWithContentDescription(tr(TextId.END_CALL)).assertIsDisplayed()
        capture("call-fullscreen")
        compose.runOnIdle { peers = emptyList() }
        compose.onNodeWithContentDescription(tr(TextId.COLLAPSE_VIDEO)).performClick()
        compose.runOnIdle { assertEquals(CallViewMode.FOCUS, mode) }
        compose.onNodeWithContentDescription(tr(TextId.SHOW_GRID)).assertIsDisplayed()
    }

    @Test
    fun closingSearchRestoresTheWholeGrid() {
        var mode by mutableStateOf(CallViewMode.GRID)
        showCall({ mode }, { null }, { listOf(self, remote) }, { mode = it }, {})
        compose.onNodeWithContentDescription(tr(TextId.SEARCH_PARTICIPANTS)).performClick()
        compose.onNodeWithText(tr(TextId.SEARCH_PARTICIPANTS)).performTextInput("no match")
        compose.onNodeWithText(tr(TextId.NO_PARTICIPANTS_FOUND)).assertIsDisplayed()
        compose.onNodeWithContentDescription(tr(TextId.CLOSE_SEARCH)).performClick()
        compose.onNodeWithText(tr(TextId.NO_PARTICIPANTS_FOUND)).assertDoesNotExist()
        compose.onAllNodes(hasText("Alex") and hasClickAction()).assertCountEquals(1)
        compose.onAllNodes(hasText("Sam") and hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun cameraSwitchOnlyAppearsWhileSelfVideoIsEnabled() {
        var mode by mutableStateOf(CallViewMode.FOCUS)
        var peer by mutableStateOf(self)
        var videoOff by mutableStateOf(false)
        showCall({ mode }, { self.peerId }, { listOf(peer) }, { mode = it }, {},
            videoOffStates = { mapOf(self.peerId to videoOff) })

        for (viewMode in listOf(CallViewMode.FOCUS, CallViewMode.FULLSCREEN)) {
            compose.runOnIdle { mode = viewMode }
            compose.onNodeWithContentDescription(tr(TextId.SWITCH_CAMERA)).assertDoesNotExist()
            compose.runOnIdle { peer = self.copy(videoAttached = true) }
            compose.onNodeWithContentDescription(tr(TextId.SWITCH_CAMERA)).assertIsDisplayed()
            compose.runOnIdle { videoOff = true }
            compose.onNodeWithContentDescription(tr(TextId.SWITCH_CAMERA)).assertDoesNotExist()
            compose.runOnIdle { videoOff = false }
            compose.onNodeWithContentDescription(tr(TextId.SWITCH_CAMERA)).assertIsDisplayed()
            compose.runOnIdle { peer = self }
            compose.onNodeWithContentDescription(tr(TextId.SWITCH_CAMERA)).assertDoesNotExist()
        }
    }

    @Test
    fun creatingRoomOnlyRequiresItsName() {
        var name by mutableStateOf("")
        var submitted = ""
        compose.setContent {
            MyApplicationTheme {
                HomeCreateRoomDialog(
                    server = server, roomName = name, error = null, loading = false,
                    onRoomNameChange = { name = it }, onDismiss = {}, onCreate = { submitted = name }
                )
            }
        }
        compose.onNodeWithText(tr(TextId.MUTE_MICROPHONES_ON_ENTRY)).assertDoesNotExist()
        compose.onNodeWithText(tr(TextId.ADMIT_WITHOUT_APPROVAL)).assertDoesNotExist()
        compose.onNodeWithText(tr(TextId.CREATE)).assertIsNotEnabled()
        compose.onNodeWithText(tr(TextId.ROOM_NAME)).performTextInput("Weekly meeting")
        capture("room-create")
        compose.onNodeWithText(tr(TextId.CREATE)).performClick()
        compose.runOnIdle { assertEquals("Weekly meeting", submitted) }
    }

    @Test
    fun roomSettingsKeepEditsWhenCollapsedAndRevealKeyActions() {
        var muted by mutableStateOf(false)
        var admitted by mutableStateOf(false)
        compose.setContent {
            MyApplicationTheme {
                HomeMeetingDialog(
                    server = server, room = OpenRoomInfo("Weekly meeting"), loading = false,
                    muteAllByDefault = muted, admitWithoutApproval = admitted,
                    onMuteAllByDefaultChange = { muted = it },
                    onAdmitWithoutApprovalChange = { admitted = it },
                    onSaveSettings = {}, onCopyGuest = {}, onQrGuest = {},
                    onCopyModerator = {}, onQrModerator = {}, onRotateModeratorLink = {},
                    onRotateE2eeKey = {}, onCloseMeeting = {}, onDismiss = {}
                )
            }
        }
        compose.onNodeWithText(tr(TextId.REFRESH_E2EE_KEY)).assertDoesNotExist()
        compose.onNodeWithText(tr(TextId.MUTE_MICROPHONES_ON_ENTRY)).assertDoesNotExist()
        capture("room-collapsed")
        compose.onNodeWithText(tr(TextId.ROOM_SETTINGS)).performScrollTo().performClick()
        compose.onNodeWithText(tr(TextId.MUTE_MICROPHONES_ON_ENTRY)).performScrollTo().performClick()
        compose.onNodeWithText(tr(TextId.ROOM_SETTINGS)).performScrollTo().performClick()
        compose.onNodeWithText(tr(TextId.REFRESH_E2EE_KEY)).assertDoesNotExist()
        compose.onNodeWithText(tr(TextId.ROOM_SETTINGS)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, muted) }
        compose.onNodeWithText(tr(TextId.SAVE_SETTINGS)).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(tr(TextId.REFRESH_E2EE_KEY)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(tr(TextId.CHANGE_MODERATOR_LINK)).assertIsDisplayed()
        compose.onNodeWithText(tr(TextId.CLOSE_ROOM)).assertIsDisplayed()
        capture("room-expanded")
    }

    private fun showCall(
        mode: () -> CallViewMode,
        pinned: () -> String?,
        peers: () -> List<PeerStatus>,
        onMode: (CallViewMode) -> Unit,
        onPin: (String?) -> Unit,
        videoOffStates: () -> Map<String, Boolean> = { emptyMap() }
    ) {
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        CallInProgressView(
                            peers = peers(), selfPeerId = "self", mode = mode(), pinnedPeerId = pinned(),
                            peerHandStates = emptyMap(), peerMicOffStates = emptyMap(), peerVideoOffStates = videoOffStates(),
                            onPin = onPin, onModeChange = onMode, onSwitchCamera = {},
                            pipOffsetX = 0f, pipOffsetY = 0f, onMovePip = { _, _ -> },
                            pipExpanded = false, onTogglePipExpanded = {}
                        )
                    }
                    ControlBar(
                        speakerOn = true, audioRoute = AudioOutputRoute.SPEAKER, headsetAvailable = false,
                        micEnabled = true, outputOn = true, onSpeaker = {}, onAudioRouteSelected = {},
                        onMic = {}, onHand = {}, videoEnabled = false, onVideo = {}, onOutput = {},
                        onDisconnect = {}, showModeratorButton = true
                    )
                }
            }
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = InstrumentationRegistry.getArguments().getString("screenshots") ?: return
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), folder).apply { mkdirs() }
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(400)
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
