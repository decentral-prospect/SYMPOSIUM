package com.decentralprospect.symposium

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.decentralprospect.symposium.ui.theme.MyApplicationTheme
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.webrtc.JavaI420Buffer
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoFrame
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class VideoControlsLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val running = AtomicBoolean(true)
    private val sources = mutableListOf<VideoSource>()
    private val tracks = mutableListOf<VideoTrack>()
    private val producers = mutableListOf<Thread>()
    private lateinit var factory: PeerConnectionFactory

    @Before
    fun prepareVideo() {
        val landscape = InstrumentationRegistry.getArguments().getString("orientation") == "landscape"
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation ==
                if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(400)
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(instrumentation.targetContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    @After
    fun releaseVideo() {
        running.set(false)
        producers.forEach { it.join(2_000) }
        compose.activityRule.scenario.close()
        instrumentation.runOnMainSync { VideoTracksStore.clear() }
        sources.forEach { it.capturerObserver.onCapturerStopped() }
        tracks.forEach { it.dispose() }
        sources.forEach { it.dispose() }
        if (::factory.isInitialized) factory.dispose()
    }

    @Test
    fun filmstripUsesSideSpaceForPortraitAndOverlayForLandscape() {
        val portrait = videoPeer("portrait", 180, 320)
        val landscape = videoPeer("landscape", 320, 180)
        var peer by mutableStateOf(portrait)
        var videoOff by mutableStateOf(false)
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TopBarPeerVideoTile(
                        peer = peer, selfPeerId = "self", pinned = true, handRaised = true,
                        audioMuted = true, videoOff = videoOff, modifier = Modifier.width(108.dp), onClick = {}
                    )
                }
            }
        }
        val statuses = listOf(TextId.PINNED, TextId.HAND_RAISED, TextId.MIC_OFF)
        val portraitFrame = videoBounds(portrait)
        assertSeparate(statuses.map(::bounds))
        statuses.forEach { assertFalse("Portrait status covers the frame", bounds(it).overlaps(portraitFrame)) }
        compose.onNodeWithContentDescription(tr(TextId.VIDEO_ON), useUnmergedTree = true).assertDoesNotExist()
        capture("filmstrip-portrait")

        compose.runOnIdle { peer = landscape }
        val landscapeFrame = videoBounds(landscape)
        assertSeparate(statuses.map(::bounds))
        statuses.forEach { assertTrue("Landscape status falls outside the frame", contains(landscapeFrame, bounds(it))) }
        capture("filmstrip-landscape")

        compose.runOnIdle { videoOff = true }
        assertSeparate((statuses + TextId.VIDEO_OFF).map(::bounds))
        capture("filmstrip-camera-off")
    }

    @Test
    fun previewControlsStayOutsideTheFrameAndRemainClickable() {
        val portrait = videoPeer("portrait", 180, 320)
        val landscape = videoPeer("landscape", 320, 180)
        var peer by mutableStateOf(portrait)
        var expanded by mutableStateOf(false)
        var switches = 0
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SelfVideoPip(
                        peer = peer, selfPeerId = peer.peerId, handRaised = true, audioMuted = true,
                        expanded = expanded, isLandscape = false, onToggleExpanded = { expanded = !expanded },
                        onSwitchCamera = { switches++ }, maximumWidth = 280.dp, maximumHeight = 260.dp
                    )
                }
            }
        }
        for ((participant, suffix) in listOf(portrait to "portrait", landscape to "landscape")) {
            compose.runOnIdle { peer = participant; expanded = false }
            val frame = videoBounds(participant)
            val controls = listOf(TextId.SWITCH_CAMERA, TextId.ENLARGE_THUMBNAIL, TextId.HAND_RAISED, TextId.MIC_OFF)
            assertSeparate(controls.map(::bounds))
            controls.forEach { assertFalse("Preview control covers the frame", bounds(it).overlaps(frame)) }
            compose.onNodeWithContentDescription(tr(TextId.SWITCH_CAMERA)).performClick()
            capture("preview-$suffix")
            compose.onNodeWithContentDescription(tr(TextId.ENLARGE_THUMBNAIL)).performClick()
            compose.onNodeWithContentDescription(tr(TextId.SHRINK_THUMBNAIL)).assertIsDisplayed().performClick()
        }
        compose.runOnIdle { assertEquals(2, switches) }
    }

    @Test
    fun floatingVideoClipsCornersOverAnotherVideo() {
        val backdrop = videoPeer("backdrop", 320, 180, solidGray = true)
        val portrait = videoPeer("portrait", 180, 320)
        val landscape = videoPeer("landscape", 320, 180)
        var peer by mutableStateOf(portrait)
        var expanded by mutableStateOf(false)
        var minimized by mutableStateOf(false)
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WebRtcVideoSurface(
                        track = VideoTracksStore.snapshot().getValue(backdrop.peerId),
                        isSelf = false, modifier = Modifier.fillMaxSize()
                    )
                    if (minimized) {
                        MinimizedCallPreview(
                            peers = listOf(peer), selfPeerId = peer.peerId, pinnedPeerId = peer.peerId,
                            peerHandStates = emptyMap(), peerMicOffStates = emptyMap(),
                            peerVideoOffStates = emptyMap(), visible = true, onExpand = {}, onDismiss = {}
                        )
                    } else {
                        SelfVideoPip(
                            peer = peer, selfPeerId = peer.peerId, handRaised = false, audioMuted = false,
                            expanded = expanded, isLandscape = false, onToggleExpanded = { expanded = !expanded },
                            onSwitchCamera = {}, maximumWidth = 280.dp, maximumHeight = 260.dp
                        )
                    }
                }
            }
        }
        for (participant in listOf(portrait, landscape)) {
            for (enlarged in listOf(false, true)) {
                compose.runOnIdle { peer = participant; expanded = enlarged }
                assertRoundedVideoCorner(participant)
            }
        }
        compose.runOnIdle { minimized = true }
        assertRoundedVideoCorner(landscape)
        capture("floating-video-rounded-corners")
    }

    private fun assertRoundedVideoCorner(peer: PeerStatus) {
        compose.waitForIdle()
        val frame = compose.onNodeWithContentDescription(
            tr(TextId.PARTICIPANT_VIDEO, peer.username), useUnmergedTree = true
        ).fetchSemanticsNode().boundsInWindow
        val inset = (2 * compose.activity.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        // Window screenshots include the actual video surfaces, unlike a Compose-only capture.
        compose.waitUntil(5_000) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            try {
                val center = bitmap.getPixel(frame.center.x.toInt(), frame.center.y.toInt())
                val corner = bitmap.getPixel(frame.left.toInt() + inset, frame.top.toInt() + inset)
                val videoVisible = Color.red(center) > Color.green(center) + 10
                val backdropVisible = listOf(Color.red(corner), Color.green(corner), Color.blue(corner))
                    .all { it in 180..200 }
                videoVisible && backdropVisible
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun fullscreenRetainsSelfPreviewAndItsControls() {
        val self = videoPeer("self", 180, 320)
        val remote = videoPeer("remote", 320, 180)
        var mode by mutableStateOf(CallViewMode.FOCUS)
        var expanded by mutableStateOf(false)
        var offsetX by mutableStateOf(0f)
        var offsetY by mutableStateOf(0f)
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        CallInProgressView(
                            peers = listOf(self, remote), selfPeerId = self.peerId, mode = mode,
                            pinnedPeerId = remote.peerId, peerHandStates = mapOf(self.peerId to true),
                            peerMicOffStates = mapOf(self.peerId to true), peerVideoOffStates = emptyMap(),
                            onPin = {}, onModeChange = { mode = it }, onSwitchCamera = {},
                            pipOffsetX = offsetX, pipOffsetY = offsetY,
                            onMovePip = { x, y -> offsetX += x; offsetY += y },
                            pipExpanded = expanded, onTogglePipExpanded = { expanded = !expanded }
                        )
                    }
                    ControlBar(
                        speakerOn = true, audioRoute = AudioOutputRoute.SPEAKER, headsetAvailable = false,
                        micEnabled = false, outputOn = true, onSpeaker = {}, onAudioRouteSelected = {},
                        onMic = {}, onHand = {}, videoEnabled = true, onVideo = {}, onOutput = {},
                        onDisconnect = {}, showModeratorButton = true
                    )
                }
            }
        }
        compose.onNodeWithContentDescription(tr(TextId.EXPAND_VIDEO)).performClick()
        compose.onNodeWithContentDescription(tr(TextId.PARTICIPANT_VIDEO, self.username), useUnmergedTree = true)
            .assertIsDisplayed()
        val frame = videoBounds(self)
        listOf(TextId.SWITCH_CAMERA, TextId.ENLARGE_THUMBNAIL, TextId.COLLAPSE_VIDEO).forEach {
            assertFalse("Fullscreen action covers self video", bounds(it).overlaps(frame))
        }
        compose.onNodeWithContentDescription(tr(TextId.END_CALL)).assertIsDisplayed()
        capture("fullscreen-preview")
        compose.onNodeWithContentDescription(tr(TextId.ENLARGE_THUMBNAIL)).performClick()
        compose.onNodeWithContentDescription(tr(TextId.SHRINK_THUMBNAIL)).assertIsDisplayed()
        capture("fullscreen-preview-expanded")
        compose.onNodeWithContentDescription(tr(TextId.COLLAPSE_VIDEO)).performClick()
        compose.runOnIdle { assertEquals(CallViewMode.FOCUS, mode) }
    }

    @Test
    fun previewFollowsDraggingAcrossTheStageAndAfterResizing() {
        val self = videoPeer("self", 180, 320)
        val remote = videoPeer("remote", 320, 180)
        var expanded by mutableStateOf(false)
        var fullscreen by mutableStateOf(false)
        var offsetX by mutableStateOf(0f)
        var offsetY by mutableStateOf(0f)
        compose.setContent {
            MyApplicationTheme {
                CallFocusStage(
                    peers = listOf(self, remote), selfPeerId = self.peerId, pinnedPeerId = remote.peerId,
                    peerHandStates = emptyMap(), peerMicOffStates = emptyMap(), peerVideoOffStates = emptyMap(),
                    pipOffsetX = offsetX, pipOffsetY = offsetY,
                    onMovePip = { x, y -> offsetX += x; offsetY += y },
                    pipExpanded = expanded, onTogglePipExpanded = { expanded = !expanded },
                    onSwitchCamera = {}, fullscreen = fullscreen,
                    onToggleFullscreen = { fullscreen = !fullscreen }, onUnpin = {}
                )
            }
        }
        val stage = compose.onRoot().fetchSemanticsNode().boundsInRoot
        for (enlarged in listOf(false, true)) {
            compose.runOnIdle {
                expanded = enlarged
                fullscreen = enlarged
                offsetX = 0f
                offsetY = 0f
            }
            val before = videoBounds(self)
            val distance = Offset(-(before.left - stage.left) * 0.5f, -(before.top - stage.top) * 0.5f)
            dragPreview(self, distance)
            val moved = videoBounds(self)
            assertEquals("Preview does not follow horizontal dragging", distance.x, moved.left - before.left, 28f)
            assertEquals("Preview does not follow vertical dragging", distance.y, moved.top - before.top, 28f)
            dragPreview(self, -distance * 0.5f)
            val reversed = videoBounds(self)
            assertTrue("Preview cannot move back horizontally", reversed.left > moved.left + 20f)
            assertTrue("Preview cannot move back vertically", reversed.top > moved.top + 20f)
            assertTrue("Preview leaves the stage", contains(stage, reversed))
            dragPreview(self, stage.topLeft + Offset(1f, 1f) - reversed.center)
            val atStart = videoBounds(self)
            assertTrue("Preview leaves the stage at the upper edge", contains(stage, atStart))
            dragPreview(self, stage.bottomRight - Offset(1f, 1f) - atStart.center)
            val atEnd = videoBounds(self)
            assertEquals("Preview cannot return to the right edge", before.left, atEnd.left, 1f)
            assertEquals("Preview cannot return to the bottom edge", before.top, atEnd.top, 1f)
            assertTrue("Preview controls leave the stage", contains(stage, bounds(TextId.SWITCH_CAMERA)))
        }
    }

    private fun dragPreview(peer: PeerStatus, delta: Offset) {
        val start = videoBounds(peer).center
        compose.onRoot().performTouchInput { down(start) }
        for (step in 1..12) {
            compose.onRoot().performTouchInput { moveTo(start + delta * (step / 12f), delayMillis = 24) }
            compose.waitForIdle()
        }
        compose.onRoot().performTouchInput { up() }
    }

    private fun videoPeer(id: String, width: Int, height: Int, solidGray: Boolean = false): PeerStatus {
        val source = factory.createVideoSource(false)
        val track = factory.createVideoTrack(id, source)
        sources += source
        tracks += track
        source.capturerObserver.onCapturerStarted(true)
        instrumentation.runOnMainSync {
            VideoTracksStore.setTrack(id, track)
            VideoTracksStore.setFrameAspectRatio(id, width.toFloat() / height)
        }
        producers += Thread {
            while (running.get()) {
                val buffer = JavaI420Buffer.allocate(width, height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        buffer.dataY.put(y * buffer.strideY + x,
                            (if (solidGray) 180 else 60 + x * 100 / width + y * 70 / height).toByte())
                    }
                }
                for (y in 0 until height / 2) {
                    for (x in 0 until width / 2) {
                        buffer.dataU.put(y * buffer.strideU + x,
                            (if (solidGray) 128 else if (x < width / 4) 100 else 160).toByte())
                        buffer.dataV.put(y * buffer.strideV + x, (if (solidGray) 128 else 140).toByte())
                    }
                }
                val frame = VideoFrame(buffer, 0, System.nanoTime())
                source.capturerObserver.onFrameCaptured(frame)
                frame.release()
                SystemClock.sleep(80)
            }
        }.apply { start() }
        return PeerStatus(id, id, 40, true, true, 0f)
    }

    private fun videoBounds(peer: PeerStatus): Rect = compose.onNodeWithContentDescription(
        tr(TextId.PARTICIPANT_VIDEO, peer.username), useUnmergedTree = true
    ).fetchSemanticsNode().boundsInRoot

    private fun bounds(id: TextId): Rect = compose.onNodeWithContentDescription(
        tr(id), useUnmergedTree = true
    ).fetchSemanticsNode().boundsInRoot

    private fun assertSeparate(rectangles: List<Rect>) {
        rectangles.forEachIndexed { index, first ->
            rectangles.drop(index + 1).forEach { second ->
                assertFalse("Indicators overlap: $first and $second", first.overlaps(second))
            }
        }
    }

    private fun contains(outer: Rect, inner: Rect): Boolean =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun capture(name: String) {
        val folder = InstrumentationRegistry.getArguments().getString("screenshots") ?: return
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), folder).apply { mkdirs() }
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(400)
        File(directory, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
