package com.xzq.appstore.core.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSignalValueParserTest {

    @Test
    fun `booleanExtra accepts common parked values`() {
        listOf(true, 1, "1", "true", "yes", "on", "park", "parked", "parking", "P").forEach { value ->
            assertTrue(VehicleSignalValueParser.booleanExtra(value, fallback = false))
        }
    }

    @Test
    fun `booleanExtra accepts common driving values`() {
        listOf(false, 0, "0", "false", "no", "off", "drive", "driving", "D", "moving").forEach { value ->
            assertFalse(VehicleSignalValueParser.booleanExtra(value, fallback = true))
        }
    }

    @Test
    fun `booleanExtra keeps fallback for unknown values`() {
        assertTrue(VehicleSignalValueParser.booleanExtra("unknown", fallback = true))
        assertFalse(VehicleSignalValueParser.booleanExtra(null, fallback = false))
    }
}
