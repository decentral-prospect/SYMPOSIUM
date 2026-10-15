package com.decentralprospect.symposium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationTrustTest {

    private val firstPin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val secondPin = "sha256/" + "B".repeat(42) + "A="
    private val endpoint = RelayEndpoint("relay.example", 443)

    @Test
    fun relayEndpointIsCanonicalizedWithAnExplicitPort() {
        assertEquals(endpoint, parseRelayEndpoint("HTTPS://relay.example"))
        assertEquals(
            RelayEndpoint("relay.example", 38443),
            parseRelayEndpoint("relay.example:38443/path")
        )
        assertEquals(
            RelayEndpoint("2001:db8::1", 443),
            parseRelayEndpoint("[2001:db8::1]")
        )
        assertNull(parseRelayEndpoint("relay.example:not-a-port"))
        assertNull(parseRelayEndpoint("relay.example:70000"))
    }

    @Test
    fun firstConnectionDoesNotRequireAStoredPin() {
        val trust = assessInvitationTlsTrust(endpoint, firstPin, null, changedPinAccepted = false)

        assertEquals(InvitationTlsTrustState.FIRST_CONNECTION, trust.state)
        assertTrue(trust.mayConnect)
    }

    @Test
    fun malformedTlsPinsAreRejected() {
        assertTrue(runCatching { normalizeRelayTlsPin(firstPin) }.isSuccess)
        assertTrue(runCatching { normalizeRelayTlsPin("sha256/short") }.isFailure)
        assertTrue(runCatching { normalizeRelayTlsPin("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA-") }.isFailure)
        val nonCanonicalPin = "sha256/" + "A".repeat(42) + "B="
        assertTrue(runCatching { normalizeRelayTlsPin(nonCanonicalPin) }.isFailure)
    }

    @Test
    fun canonicalSha256PinWithOnePaddingCharacterIsAccepted() {
        val canonicalPin = "sha256/" + "A".repeat(42) + "M="
        assertEquals(canonicalPin, normalizeRelayTlsPin(canonicalPin))
    }

    @Test
    fun matchingStoredPinIsAccepted() {
        val trust = assessInvitationTlsTrust(endpoint, firstPin, firstPin, changedPinAccepted = false)

        assertEquals(InvitationTlsTrustState.MATCHED, trust.state)
        assertTrue(trust.mayConnect)
    }

    @Test
    fun changedPinBlocksUntilExplicitlyAccepted() {
        val changed = assessInvitationTlsTrust(endpoint, secondPin, firstPin, changedPinAccepted = false)
        val accepted = assessInvitationTlsTrust(endpoint, secondPin, firstPin, changedPinAccepted = true)

        assertEquals(InvitationTlsTrustState.CHANGED, changed.state)
        assertFalse(changed.mayConnect)
        assertEquals(InvitationTlsTrustState.CHANGED_ACCEPTED, accepted.state)
        assertTrue(accepted.mayConnect)
    }
}
