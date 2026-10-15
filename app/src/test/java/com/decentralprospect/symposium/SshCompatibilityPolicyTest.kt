package com.decentralprospect.symposium

import org.junit.Assert.assertEquals
import org.junit.Test

class SshCompatibilityPolicyTest {
    @Test
    fun rsaKeyGenerationFallsBackFromPreferredToCompatibleSize() {
        assertEquals(listOf(3072, 2048), sshRsaKeySizeCandidates())
    }
}
