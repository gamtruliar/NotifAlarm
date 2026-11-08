package com.gamtruliar.notialarm

import com.gamtruliar.notialarm.Data.FilterData
import com.gamtruliar.notialarm.Data.GlobalSettingData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SettingsSerializationTest {
    @Test
    fun existingFilterJsonPreservesNestedSettingsAndDefaults() {
        // Persisted field names (including filerUID) must survive compiler/runtime upgrades.
        val stored = """{
            "filerUID":7,"filterName":"Live alerts","packageNames":["example.app"],
            "subFilterData":{"2":{"uid":2,"titleKeyWords":"live","textUseRegex":true}},
            "banTimes":{"3":{"uid":3,"weekDay":[2,3,4,5,6],"fromTime":540,"toTime":1080}},
            "specSound":"content://media/internal/audio/media/1"
        }"""

        val filter = Json.decodeFromString<FilterData>(stored)
        assertEquals(7, filter.filerUID)
        assertEquals(listOf("example.app"), filter.packageNames)
        assertEquals("live", filter.subFilterData[2]!!.titleKeyWords)
        assertTrue(filter.subFilterData[2]!!.textUseRegex)
        assertEquals(setOf(2, 3, 4, 5, 6), filter.banTimes[3]!!.weekDay)
        assertEquals(1080, filter.banTimes[3]!!.toTime)
        assertTrue(filter.enable)
        assertTrue(filter.needSound)
        assertEquals(filter, Json.decodeFromString<FilterData>(Json.encodeToString(filter)))
    }

    @Test
    fun existingGlobalSettingsAndFilterIdsRoundTrip() {
        val stored = """{"allSound_banTimes":{"1":{"uid":1,"fromTime":60,"toTime":120}},"all_banTimesUUID":2,"gsversion":5}"""
        val settings = Json.decodeFromString<GlobalSettingData>(stored)
        assertEquals(5, settings.gsversion)
        assertEquals(2, settings.all_banTimesUUID)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), settings.allSound_banTimes[1]!!.weekDay)
        assertTrue(settings.allFun_banTimes.isEmpty())
        assertEquals(settings, Json.decodeFromString<GlobalSettingData>(Json.encodeToString(settings)))
        val ids = Json.decodeFromString<HashSet<Int>>("[1,7,12]")
        assertEquals(setOf(1, 7, 12), ids)
        assertEquals(ids, Json.decodeFromString<HashSet<Int>>(Json.encodeToString(ids)))
    }
}
