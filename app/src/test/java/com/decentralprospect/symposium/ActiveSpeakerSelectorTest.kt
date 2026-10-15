package com.decentralprospect.symposium

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSpeakerSelectorTest {
    @Test
    fun `short spike does not replace current speaker`() {
        val selector = StableActiveSpeakerSelector()

        assertEquals("alice", selector.select(samples(alice = .08f, bob = .01f), "alice", 0L))
        assertEquals("alice", selector.select(samples(alice = .01f, bob = .08f), "alice", 1_000L))
        assertEquals("alice", selector.select(samples(alice = .07f, bob = .01f), "alice", 2_000L))
    }

    @Test
    fun `sustained challenger becomes active after confirmation`() {
        val selector = StableActiveSpeakerSelector()

        assertEquals("alice", selector.select(samples(alice = .08f, bob = 0f), "alice", 0L))
        assertEquals("alice", selector.select(samples(alice = .01f, bob = .09f), "alice", 1_700L))
        assertEquals("bob", selector.select(samples(alice = .01f, bob = .08f), "alice", 2_700L))
    }

    @Test
    fun `muted active speaker is replaced immediately`() {
        val selector = StableActiveSpeakerSelector()
        assertEquals("alice", selector.select(samples(alice = .08f, bob = 0f), "alice", 0L))

        val next = listOf(
            ActiveSpeakerSample("alice", .08f, eligible = false),
            ActiveSpeakerSample("bob", .07f)
        )
        assertEquals("bob", selector.select(next, "bob", 500L))
    }

    @Test
    fun `background noise does not switch the stage`() {
        val selector = StableActiveSpeakerSelector()

        assertEquals("alice", selector.select(samples(alice = .06f, bob = 0f), "alice", 0L))
        assertEquals("alice", selector.select(samples(alice = .02f, bob = .03f), "alice", 2_000L))
        assertEquals("alice", selector.select(samples(alice = .01f, bob = .03f), "alice", 3_000L))
    }

    private fun samples(alice: Float, bob: Float) = listOf(
        ActiveSpeakerSample("alice", alice),
        ActiveSpeakerSample("bob", bob)
    )
}
