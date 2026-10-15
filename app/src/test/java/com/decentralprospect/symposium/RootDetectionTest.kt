package com.decentralprospect.symposium

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootDetectionTest {

    @Test
    fun releaseBuildWithoutRootArtifactsDoesNotWarn() {
        val evidence = collectRootWarningEvidence(
            buildTags = "release-keys",
            pathExists = { false }
        )

        assertFalse(evidence.shouldWarn)
    }

    @Test
    fun testKeysOrRootArtifactProduceWarningOnlyEvidence() {
        val testKeys = collectRootWarningEvidence(
            buildTags = "test-keys",
            pathExists = { false }
        )
        val rootArtifact = collectRootWarningEvidence(
            buildTags = "release-keys",
            pathExists = { it == "/data/adb/magisk" }
        )

        assertTrue(testKeys.shouldWarn)
        assertTrue(rootArtifact.shouldWarn)
    }
}
