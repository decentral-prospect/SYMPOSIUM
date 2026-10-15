package com.decentralprospect.symposium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallVideoLayoutTest {
    @Test
    fun `very wide frame stays inside the working area`() {
        val fitted = fitFrameWithin(
            maximumWidth = 900f,
            maximumHeight = 500f,
            frameAspectRatio = 30f
        )

        assertEquals(900f, fitted.width, 0.001f)
        assertEquals(30f, fitted.height, 0.001f)
        assertTrue(fitted.width <= 900f)
        assertTrue(fitted.height <= 500f)
    }

    @Test
    fun `very tall frame stays inside the working area`() {
        val fitted = fitFrameWithin(
            maximumWidth = 900f,
            maximumHeight = 500f,
            frameAspectRatio = 1f / 30f
        )

        assertEquals(500f / 30f, fitted.width, 0.001f)
        assertEquals(500f, fitted.height, 0.001f)
        assertTrue(fitted.width <= 900f)
        assertTrue(fitted.height <= 500f)
    }

    @Test
    fun `ping thresholds map to five signal levels`() {
        assertEquals(0, networkSignalBars(null))
        assertEquals(5, networkSignalBars(80L))
        assertEquals(4, networkSignalBars(150L))
        assertEquals(3, networkSignalBars(250L))
        assertEquals(2, networkSignalBars(450L))
        assertEquals(1, networkSignalBars(451L))
    }

    @Test
    fun `portrait video stays inside square cell without changing ratio`() {
        val size = fitFrameWithin(
            maximumWidth = 180f,
            maximumHeight = 150f,
            frameAspectRatio = 9f / 16f
        )

        assertEquals(150f, size.height, 0.001f)
        assertEquals(9f / 16f, size.width / size.height, 0.001f)
        assertTrue(size.width <= 180f && size.height <= 150f)
    }

}
