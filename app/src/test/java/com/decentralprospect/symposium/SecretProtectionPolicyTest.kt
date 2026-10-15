package com.decentralprospect.symposium

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretProtectionPolicyTest {
    @Test
    fun requestedProtectionIsEnabledOnSecureDevice() {
        assertTrue(effectiveSecretProtectionEnabled(requested = true, deviceIsSecure = true))
    }

    @Test
    fun applicationRemainsAccessibleWithoutDeviceLock() {
        assertFalse(effectiveSecretProtectionEnabled(requested = true, deviceIsSecure = false))
    }

    @Test
    fun userCanDisableProtectionOnSecureDevice() {
        assertFalse(effectiveSecretProtectionEnabled(requested = false, deviceIsSecure = true))
    }
}
