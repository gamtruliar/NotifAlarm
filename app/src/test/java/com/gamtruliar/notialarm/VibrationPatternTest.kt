package com.gamtruliar.notialarm

import com.gamtruliar.notialarm.Data.FilterData
import com.gamtruliar.notialarm.Data.TriggerContext
import com.gamtruliar.notialarm.Data.TriggerResponseRule
import com.gamtruliar.notialarm.Data.resolveResponse
import com.gamtruliar.notialarm.Enums.VibrationPattern
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class VibrationPatternTest {
    private val context = TriggerContext(720, Calendar.MONDAY, "Office", true)

    @Test
    fun legacyFilterAndRulesKeepOriginalVibration() {
        val filter = Json.decodeFromString<FilterData>(
            """{"filerUID":7,"responseRules":[{"needSound":false,"charging":1}]}"""
        )
        assertEquals(VibrationPattern.CONTINUOUS.id, filter.vibrationPattern)
        assertEquals(VibrationPattern.CONTINUOUS.id, filter.responseRules.single().vibrationPattern)
        assertEquals(VibrationPattern.CONTINUOUS.id, filter.resolveResponse(context).vibrationPattern)
        assertEquals(VibrationPattern.CONTINUOUS.id,
            filter.resolveResponse(context.copy(isCharging = false)).vibrationPattern)
        assertArrayEquals(longArrayOf(0, 1000), VibrationPattern.CONTINUOUS.timings)
    }

    @Test
    fun filterAndRulePatternsSurviveSavingAndLoading() {
        val filter = FilterData(
            vibrationPattern = VibrationPattern.DOUBLE_PULSE.id,
            responseRules = arrayListOf(
                TriggerResponseRule(charging = 1, vibrationPattern = VibrationPattern.SOS.id),
                TriggerResponseRule(charging = 0, vibrationPattern = VibrationPattern.SHORT_PULSE.id)
            )
        )
        val restored = Json.decodeFromString<FilterData>(Json.encodeToString(filter))
        assertEquals(filter, restored)
        assertEquals(VibrationPattern.SOS.id, restored.resolveResponse(context).vibrationPattern)
        assertEquals(VibrationPattern.SHORT_PULSE.id,
            restored.resolveResponse(context.copy(isCharging = false)).vibrationPattern)
        assertEquals(VibrationPattern.DOUBLE_PULSE.id,
            restored.resolveResponse(context.copy(isCharging = null)).vibrationPattern)
    }

    @Test
    fun firstEnabledMatchingRuleControlsPatternEvenWhenVibrationIsDisabled() {
        val filter = FilterData(
            vibrationPattern = VibrationPattern.SHORT_PULSE.id,
            responseRules = arrayListOf(
                TriggerResponseRule(enabled = false, vibrationPattern = VibrationPattern.SOS.id),
                TriggerResponseRule(charging = 0, vibrationPattern = VibrationPattern.DOUBLE_PULSE.id),
                TriggerResponseRule(needVibrate = false, vibrationPattern = VibrationPattern.TRIPLE_PULSE.id),
                TriggerResponseRule(vibrationPattern = VibrationPattern.LONG_PULSE.id)
            )
        )
        val response = filter.resolveResponse(context)
        assertFalse(response.needVibrate)
        assertEquals(VibrationPattern.TRIPLE_PULSE.id, response.vibrationPattern)
    }

    @Test
    fun unknownPatternIdsSafelyUseOriginalVibration() {
        assertEquals(VibrationPattern.CONTINUOUS, VibrationPattern.fromId("unknown-preset"))
        assertEquals(VibrationPattern.CONTINUOUS, VibrationPattern.fromId(""))
        val filter = FilterData(vibrationPattern = "unknown-filter-preset")
        assertEquals(VibrationPattern.CONTINUOUS.id, filter.resolveResponse(context).vibrationPattern)
        filter.responseRules.add(TriggerResponseRule(vibrationPattern = "unknown-rule-preset"))
        assertEquals(VibrationPattern.CONTINUOUS.id, filter.resolveResponse(context).vibrationPattern)
    }

    @Test
    fun presetsHaveUniqueStableIdsAndPlayableAlternatingWaveforms() {
        assertEquals(VibrationPattern.entries.size, VibrationPattern.entries.map { it.id }.toSet().size)
        VibrationPattern.entries.forEach { pattern ->
            assertEquals(pattern, VibrationPattern.fromId(pattern.id))
            val timings = pattern.timings
            assertTrue(pattern.id, timings.size >= 2)
            assertEquals(pattern.id, 0L, timings[0])
            assertTrue(pattern.id, timings.drop(1).all { it > 0 })
            assertTrue(pattern.id, timings.filterIndexed { index, _ -> index % 2 == 1 }.sum() > 0)
            // All pulsed presets have a final off period before their next repetition.
            if (pattern != VibrationPattern.CONTINUOUS) assertEquals(pattern.id, 1, timings.size % 2)
        }
        assertEquals(2, VibrationPattern.DOUBLE_PULSE.timings.size / 2)
        assertEquals(3, VibrationPattern.TRIPLE_PULSE.timings.size / 2)
        assertArrayEquals(longArrayOf(150, 150, 150, 450, 450, 450, 150, 150, 150),
            VibrationPattern.SOS.timings.filterIndexed { index, _ -> index % 2 == 1 }.toLongArray())
    }

    @Test
    fun modifyingReturnedTimingsDoesNotChangePreset() {
        val timings = VibrationPattern.DOUBLE_PULSE.timings
        timings[1] = 9999
        assertEquals(150L, VibrationPattern.DOUBLE_PULSE.timings[1])
    }
}
