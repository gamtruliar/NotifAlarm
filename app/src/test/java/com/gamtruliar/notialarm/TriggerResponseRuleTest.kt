package com.gamtruliar.notialarm

import com.gamtruliar.notialarm.Data.FilterData
import com.gamtruliar.notialarm.Data.TriggerContext
import com.gamtruliar.notialarm.Data.TriggerResponse
import com.gamtruliar.notialarm.Data.TriggerResponseRule
import com.gamtruliar.notialarm.Data.resolveResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class TriggerResponseRuleTest {
    private val monday = TriggerContext(12 * 60, Calendar.MONDAY, "Office", true)

    @Test
    fun daytimeRangeIncludesStartAndExcludesEnd() {
        val rule = TriggerResponseRule(useTimeRange = true, startMinute = 540, endMinute = 1080)
        assertFalse(rule.matches(monday.copy(minuteOfDay = 539)))
        assertTrue(rule.matches(monday.copy(minuteOfDay = 540)))
        assertTrue(rule.matches(monday.copy(minuteOfDay = 1079)))
        assertFalse(rule.matches(monday.copy(minuteOfDay = 1080)))
    }

    @Test
    fun overnightRangeUsesCurrentWeekdayOnEitherSideOfMidnight() {
        val rule = TriggerResponseRule(
            useTimeRange = true, startMinute = 22 * 60, endMinute = 6 * 60,
            weekdays = arrayListOf(Calendar.MONDAY)
        )
        assertTrue(rule.matches(monday.copy(minuteOfDay = 22 * 60)))
        assertTrue(rule.matches(monday.copy(minuteOfDay = 0)))
        assertTrue(rule.matches(monday.copy(minuteOfDay = 6 * 60 - 1)))
        assertFalse(rule.matches(monday.copy(minuteOfDay = 6 * 60)))
        assertFalse(rule.matches(monday.copy(minuteOfDay = 21 * 60 + 59)))
        assertFalse(rule.matches(monday.copy(minuteOfDay = 1, weekday = Calendar.TUESDAY)))
    }

    @Test
    fun equalTimeEndpointsMeanAllDayAndDisabledRangeIgnoresEndpoints() {
        val rule = TriggerResponseRule(useTimeRange = true, startMinute = 540, endMinute = 540)
        assertTrue(rule.matches(monday.copy(minuteOfDay = 0)))
        assertTrue(rule.matches(monday.copy(minuteOfDay = 1439)))
        rule.useTimeRange = false
        rule.startMinute = -1
        rule.endMinute = 2000
        assertTrue(rule.matches(monday))
    }

    @Test
    fun invalidTimeRangesCannotAccidentallyMatch() {
        val rule = TriggerResponseRule(useTimeRange = true, startMinute = -1, endMinute = 1440)
        assertFalse(rule.matches(monday))
        rule.startMinute = 0
        rule.endMinute = 0
        assertFalse(rule.matches(monday.copy(minuteOfDay = -1)))
        assertFalse(rule.matches(monday.copy(minuteOfDay = 1440)))
    }

    @Test
    fun allSpecifiedConditionsMustMatchAndAnyListedSsidIsAccepted() {
        val rule = TriggerResponseRule(
            useTimeRange = true, startMinute = 540, endMinute = 1080,
            weekdays = arrayListOf(Calendar.MONDAY, Calendar.TUESDAY),
            wifiSsids = arrayListOf("Office", "Home"), charging = 1
        )
        assertTrue(rule.matches(monday))
        assertTrue(rule.matches(monday.copy(wifiSsid = "Home")))
        assertFalse(rule.matches(monday.copy(minuteOfDay = 500)))
        assertFalse(rule.matches(monday.copy(weekday = Calendar.SUNDAY)))
        assertFalse(rule.matches(monday.copy(wifiSsid = "Other")))
        assertFalse(rule.matches(monday.copy(wifiSsid = "office")))
        assertFalse(rule.matches(monday.copy(isCharging = false)))
    }

    @Test
    fun unknownWifiOrChargingCannotMatchConstrainedRule() {
        assertFalse(TriggerResponseRule(wifiSsids = arrayListOf("Office"))
            .matches(monday.copy(wifiSsid = null)))
        assertFalse(TriggerResponseRule(charging = 1).matches(monday.copy(isCharging = null)))
        assertFalse(TriggerResponseRule(charging = 0).matches(monday.copy(isCharging = null)))
        assertTrue(TriggerResponseRule(charging = 0).matches(monday.copy(isCharging = false)))
        assertFalse(TriggerResponseRule(charging = 2).matches(monday))
        assertTrue(TriggerResponseRule().matches(monday.copy(wifiSsid = null, isCharging = null)))
    }

    @Test
    fun firstEnabledMatchingRuleWinsAndCanSelectSilentResponse() {
        val filter = FilterData(responseRules = arrayListOf(
            TriggerResponseRule(enabled = false, needSound = true),
            TriggerResponseRule(wifiSsids = arrayListOf("Other"), needSound = true),
            TriggerResponseRule(needSound = false, needVibrate = false, soundVolume = 35),
            TriggerResponseRule(needSound = true, needVibrate = true, soundVolume = 90)
        ))
        assertEquals(TriggerResponse(false, false, 35), filter.resolveResponse(monday))
    }

    @Test
    fun nonMatchingRulesFallBackToExistingFilterResponse() {
        val filter = FilterData(needSound = false, needVibrate = true, soundVolume = 60,
            responseRules = arrayListOf(TriggerResponseRule(charging = 0)))
        assertEquals(TriggerResponse(false, true, 60), filter.resolveResponse(monday))
        filter.responseRules.clear()
        assertEquals(TriggerResponse(false, true, 60), filter.resolveResponse(monday))
    }

    @Test
    fun resolvedVolumesAreClampedForBothRulesAndFallback() {
        val filter = FilterData(soundVolume = 200)
        assertEquals(100, filter.resolveResponse(monday).soundVolume)
        filter.soundVolume = -20
        assertEquals(0, filter.resolveResponse(monday).soundVolume)
        filter.responseRules.add(TriggerResponseRule(soundVolume = 300))
        assertEquals(100, filter.resolveResponse(monday).soundVolume)
        filter.responseRules[0].soundVolume = -10
        assertEquals(0, filter.resolveResponse(monday).soundVolume)
    }

    @Test
    fun legacyFilterJsonRetainsResponseAndNewDefaults() {
        val filter = Json.decodeFromString<FilterData>(
            """{"filerUID":7,"needSound":false,"needVibrate":true,"ringDuration":3}"""
        )
        assertEquals(100, filter.soundVolume)
        assertTrue(filter.responseRules.isEmpty())
        assertEquals(TriggerResponse(false, true, 100), filter.resolveResponse(monday))
        assertEquals(7, filter.filerUID)
        assertEquals(3, filter.ringDuration)
    }

    @Test
    fun ruleOrderAndAllConditionsSurviveJsonRoundTrip() {
        val filter = FilterData(soundVolume = 80, responseRules = arrayListOf(
            TriggerResponseRule(
                name = "Office hours", useTimeRange = true, startMinute = 540, endMinute = 1080,
                weekdays = arrayListOf(2, 3, 4, 5, 6), wifiSsids = arrayListOf("Office", "Office 5G"),
                charging = 1, needSound = false, needVibrate = true, soundVolume = 25
            ),
            TriggerResponseRule(name = "Fallback rule", needVibrate = false)
        ))
        val restored = Json.decodeFromString<FilterData>(Json.encodeToString(filter))
        assertEquals(filter, restored)
        assertEquals(TriggerResponse(false, true, 25), restored.resolveResponse(monday))
        assertEquals(TriggerResponse(true, false, 100),
            restored.resolveResponse(monday.copy(wifiSsid = null)))
    }
}
