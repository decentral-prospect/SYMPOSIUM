package com.decentralprospect.symposium

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryConsentPolicyTest {
    @Test
    fun telemetryCannotTransmitWithoutExplicitConsent() {
        assertFalse(telemetryTransmissionAllowed(configured = true, consentGranted = false))
    }

    @Test
    fun consentDoesNotEnableAnUnconfiguredEndpoint() {
        assertFalse(telemetryTransmissionAllowed(configured = false, consentGranted = true))
    }

    @Test
    fun configuredEndpointAndConsentAreBothRequired() {
        assertTrue(telemetryTransmissionAllowed(configured = true, consentGranted = true))
    }
}
