package com.decentralprospect.symposium

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import kotlin.math.abs

object VideoTracksStore {
    private const val SELF_FALLBACK_KEY = "__self__"
    private val tracks = mutableStateMapOf<String, VideoTrack>()
    private val frameAspectRatios = mutableStateMapOf<String, Float>()
    private var selfPreviewMirrored by mutableStateOf(true)
    private var focusedPeerId: String? = null

    fun snapshot(): Map<String, VideoTrack> = tracks

    fun isSelfPreviewMirrored(): Boolean = selfPreviewMirrored

    fun frameAspectRatio(peerId: String?): Float? {
        val key = peerId?.takeIf { it.isNotBlank() } ?: SELF_FALLBACK_KEY
        return frameAspectRatios[key]
    }

    fun setFrameAspectRatio(peerId: String?, aspectRatio: Float) {
        if (!aspectRatio.isFinite() || aspectRatio <= 0f) return
        val key = peerId?.takeIf { it.isNotBlank() } ?: SELF_FALLBACK_KEY
        // Keep the real frame ratio for in-call layout. Android PiP applies its
        // own narrower platform limit when it builds PictureInPictureParams.
        val normalized = aspectRatio.coerceIn(0.01f, 100f)
        val previous = frameAspectRatios[key]
        if (previous == null || abs(previous - normalized) >= 0.02f) {
            frameAspectRatios[key] = normalized
        }
    }

    fun setFocusedPeer(peerId: String?) {
        focusedPeerId = peerId?.takeIf { it.isNotBlank() }
    }

    fun focusedFrameAspectRatio(): Float? = frameAspectRatio(focusedPeerId)

    fun setSelfCameraFacing(facing: String) {
        selfPreviewMirrored = facing.equals("front", ignoreCase = true)
    }

    fun setTrack(peerId: String?, track: VideoTrack?) {
        val key = peerId?.takeIf { it.isNotBlank() } ?: SELF_FALLBACK_KEY
        if (track == null) {
            tracks.remove(key)
            frameAspectRatios.remove(key)
        } else {
            if (tracks[key] !== track) frameAspectRatios.remove(key)
            tracks[key] = track
        }
    }

    fun moveSelfTrack(selfPeerId: String?) {
        val id = selfPeerId?.takeIf { it.isNotBlank() } ?: return
        val selfTrack = tracks.remove(SELF_FALLBACK_KEY) ?: return
        tracks[id] = selfTrack
        frameAspectRatios.remove(SELF_FALLBACK_KEY)?.let { ratio ->
            frameAspectRatios[id] = ratio
        }
    }

    fun retainOnlySelf(selfPeerId: String?) {
        val keep = mutableSetOf(SELF_FALLBACK_KEY)
        selfPeerId?.takeIf { it.isNotBlank() }?.let { keep += it }

        tracks.keys.toList()
            .filter { it !in keep }
            .forEach {
                tracks.remove(it)
                frameAspectRatios.remove(it)
            }
    }

    fun removePeer(peerId: String?) {
        val key = peerId?.takeIf { it.isNotBlank() } ?: SELF_FALLBACK_KEY
        tracks.remove(key)
        frameAspectRatios.remove(key)
        if (focusedPeerId == key) focusedPeerId = null
    }

    fun clear() {
        tracks.clear()
        frameAspectRatios.clear()
        focusedPeerId = null
        selfPreviewMirrored = true
    }
}

object VideoRenderContext {
    val eglBase: EglBase by lazy { EglBase.create() }
}
