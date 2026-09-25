package com.example.ads

import com.example.ads.interstitial.AdFrequencyController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AdFrequencyController logic.
 */
class AdFrequencyControllerTest {

    private lateinit var controller: AdFrequencyController

    @Before
    fun setup() {
        controller = AdFrequencyController(frequency = 3, cooldownIntervalMillis = 0L)
    }

    @Test
    fun testFrequencyThreshold() {
        // Action 1
        assertFalse(controller.recordAction())
        // Action 2
        assertFalse(controller.recordAction())
        // Action 3: should trigger
        assertTrue(controller.recordAction())
        // Action 4 (restarted count): Action 1 of next cycle
        assertFalse(controller.recordAction())
    }

    @Test
    fun testFrequency1_alwaysTriggers() {
        val freq1Controller = AdFrequencyController(frequency = 1, cooldownIntervalMillis = 0L)
        assertTrue(freq1Controller.recordAction())
        assertTrue(freq1Controller.recordAction())
    }

    @Test
    fun testReset() {
        assertFalse(controller.recordAction())
        assertFalse(controller.recordAction())
        controller.reset()
        assertFalse(controller.recordAction())
        assertFalse(controller.recordAction())
        assertTrue(controller.recordAction())
    }
}
