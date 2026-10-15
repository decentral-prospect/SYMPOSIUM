package com.decentralprospect.symposium

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

/** Floating video must participate in Compose clipping and overlap the main video normally. */
@Composable
internal fun WebRtcVideoTexture(
    track: VideoTrack,
    isSelf: Boolean,
    modifier: Modifier = Modifier,
    onFrameAspectRatio: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val mirror = isSelf && VideoTracksStore.isSelfPreviewMirrored()
    val latestOnFrameAspectRatio by rememberUpdatedState(onFrameAspectRatio)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val renderer = remember {
        EglRenderer("FloatingVideo").apply {
            init(VideoRenderContext.eglBase.eglBaseContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
            setMirror(mirror)
        }
    }
    val view = remember {
        TextureView(context).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    renderer.setLayoutAspectRatio(width.toFloat() / height.coerceAtLeast(1))
                    renderer.createEglSurface(surface)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    renderer.setLayoutAspectRatio(width.toFloat() / height.coerceAtLeast(1))
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    // EGL must stop using the texture before it is released.
                    renderer.releaseEglSurface { surface.release() }
                    return false
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { view },
        update = { renderer.setMirror(mirror) }
    )

    DisposableEffect(track, renderer) {
        var lastWidth = 0
        var lastHeight = 0
        val sink = VideoSink { frame ->
            val width = frame.rotatedWidth
            val height = frame.rotatedHeight
            if (width > 0 && height > 0 && (width != lastWidth || height != lastHeight)) {
                lastWidth = width
                lastHeight = height
                val ratio = width.toFloat() / height
                mainHandler.post { latestOnFrameAspectRatio(ratio) }
            }
            renderer.onFrame(frame)
        }
        track.addSink(sink)
        onDispose {
            track.removeSink(sink)
            mainHandler.removeCallbacksAndMessages(null)
            renderer.clearImage()
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer.release()
            view.surfaceTextureListener = null
        }
    }
}
