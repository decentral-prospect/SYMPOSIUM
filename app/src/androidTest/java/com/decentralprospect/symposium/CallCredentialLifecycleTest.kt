package com.decentralprospect.symposium

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CallCredentialLifecycleTest {
    private fun checkTermination(terminate: (CallRuntime) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val runtime = populatedRuntime()
            // Terminal server events must also cancel an in-flight reconnect.
            runtime.reconnectMode = true
            terminate(runtime)
            assertEquals("", runtime.lastModKey)
            assertEquals("", runtime.lastE2eeSecret)
            assertEquals("", runtime.lastRoomInstance)
            assertEquals("", runtime.reconnectToken)
            assertEquals("", runtime.lastWsUrl)
            assertFalse(runtime.reconnectMode)
        }
    }

    @Test fun explicitDisconnectClearsCredentials() = checkTermination { it.disconnect() }

    @Test fun serverRoomClosureClearsCredentials() = checkTermination {
        it.handleSignalingMessage("""{"type":"room-closed","reason":"test"}""")
    }

    @Test fun terminalVersionRejectionClearsCredentials() = checkTermination {
        it.handleSignalingMessage("""{"type":"error","code":"client-version-mismatch","requiredVersion":"9.9.9"}""")
    }

    @Test fun serviceDestructionClearsCredentials() = checkTermination {
        it.shutdownBecauseServiceDestroyed()
    }

    @Test fun reconnectKeepsCredentialsUntilCancelled() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val runtime = populatedRuntime()
            runtime.reconnectMode = true
            runtime.teardown()
            assertEquals("test-moderator", runtime.lastModKey)
            assertEquals("test-e2ee", runtime.lastE2eeSecret)
            assertEquals("test-instance", runtime.lastRoomInstance)
            assertEquals("test-reconnect", runtime.reconnectToken)
            runtime.disconnect()
            assertEquals("", runtime.lastModKey)
            assertEquals("", runtime.lastE2eeSecret)
        }
    }

    @Test fun teardownWithoutReconnectClearsCredentials() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val runtime = populatedRuntime()
            runtime.teardown()
            assertEquals("", runtime.lastModKey)
            assertEquals("", runtime.lastE2eeSecret)
        }
    }

    private fun populatedRuntime() = CallRuntime(
        InstrumentationRegistry.getInstrumentation().targetContext,
        object : CallForegroundController {
            override fun startForegroundForCall(microphone: Boolean, camera: Boolean) {}
            override fun stopForegroundForCall() {}
        }
    ).apply {
        lastWsUrl = "relay.example"
        lastModKey = "test-moderator"
        lastE2eeSecret = "test-e2ee"
        lastRoomInstance = "test-instance"
        reconnectToken = "test-reconnect"
    }
}
