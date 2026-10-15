package com.decentralprospect.symposium

/** A single audio-level sample used by [StableActiveSpeakerSelector]. */
internal data class ActiveSpeakerSample(
    val peerId: String,
    val audioLevel: Float,
    val eligible: Boolean = true
)

/**
 * Keeps the active-speaker view from bouncing between people on noise or short level spikes.
 * A challenger must be clearly audible, louder than the current speaker, and win for more than
 * one normal WebRTC stats interval before the stage changes.
 */
internal class StableActiveSpeakerSelector(
    private val activationLevel: Float = 0.035f,
    private val retainedLevel: Float = 0.018f,
    private val switchMargin: Float = 0.012f,
    private val confirmationMs: Long = 700L,
    private val minimumSpeakerTimeMs: Long = 1_600L
) {
    private var activePeerId: String? = null
    private var activeSinceMs: Long? = null
    private var challengerPeerId: String? = null
    private var challengerSinceMs: Long? = null

    fun select(
        samples: List<ActiveSpeakerSample>,
        fallbackPeerId: String?,
        nowMs: Long
    ): String? {
        val eligible = samples.filter { it.eligible }
        if (eligible.isEmpty()) {
            clear()
            return fallbackPeerId
        }

        val fallback = fallbackPeerId
            ?.takeIf { id -> eligible.any { it.peerId == id } }
            ?: eligible.first().peerId
        val current = activePeerId?.let { id -> eligible.firstOrNull { it.peerId == id } }
        val loudest = eligible.maxByOrNull { it.audioLevel }

        if (current == null) {
            val initial = loudest
                ?.takeIf { it.audioLevel >= activationLevel }
                ?.peerId
                ?: fallback
            setActive(initial, nowMs, countAsSwitch = loudest?.audioLevel?.let { it >= activationLevel } == true)
            return initial
        }

        if (loudest == null || loudest.peerId == current.peerId) {
            clearChallenger()
            return current.peerId
        }

        val currentIsQuiet = current.audioLevel < retainedLevel
        val challengerIsStrongEnough = loudest.audioLevel >= activationLevel &&
            (currentIsQuiet || loudest.audioLevel >= current.audioLevel + switchMargin)
        if (!challengerIsStrongEnough) {
            clearChallenger()
            return current.peerId
        }

        if (challengerPeerId != loudest.peerId) {
            challengerPeerId = loudest.peerId
            challengerSinceMs = nowMs
            return current.peerId
        }

        val confirmedFor = nowMs - (challengerSinceMs ?: nowMs)
        val activeFor = activeSinceMs?.let { nowMs - it } ?: Long.MAX_VALUE
        if (confirmedFor >= confirmationMs && activeFor >= minimumSpeakerTimeMs) {
            setActive(loudest.peerId, nowMs, countAsSwitch = true)
        }
        return activePeerId
    }

    private fun setActive(peerId: String, nowMs: Long, countAsSwitch: Boolean) {
        activePeerId = peerId
        activeSinceMs = if (countAsSwitch) nowMs else null
        clearChallenger()
    }

    private fun clearChallenger() {
        challengerPeerId = null
        challengerSinceMs = null
    }

    private fun clear() {
        activePeerId = null
        activeSinceMs = null
        clearChallenger()
    }
}
