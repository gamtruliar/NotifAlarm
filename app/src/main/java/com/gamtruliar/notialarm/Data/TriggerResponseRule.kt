package com.gamtruliar.notialarm.Data

import com.gamtruliar.notialarm.Enums.VibrationPattern
import kotlinx.serialization.Serializable

/** Conditions within a rule are combined with AND; entries in a Wi-Fi list use OR. */
@Serializable
data class TriggerResponseRule(
    var name: String = "",
    var enabled: Boolean = true,
    var useTimeRange: Boolean = false,
    var startMinute: Int = 0,
    var endMinute: Int = 0,
    // Calendar.DAY_OF_WEEK: Sunday = 1 through Saturday = 7. Empty means every day.
    var weekdays: ArrayList<Int> = arrayListOf(),
    // Exact, case-sensitive SSIDs. Empty means any Wi-Fi state, including disconnected.
    var wifiSsids: ArrayList<String> = arrayListOf(),
    // -1 = any, 0 = not charging, 1 = charging.
    var charging: Int = -1,
    var needSound: Boolean = true,
    var needVibrate: Boolean = true,
    var soundVolume: Int = 100,
    var vibrationPattern: String = VibrationPattern.CONTINUOUS.id
) {
    fun matches(context: TriggerContext): Boolean {
        if (!enabled) return false
        if (weekdays.isNotEmpty() && context.weekday !in weekdays) return false
        if (wifiSsids.isNotEmpty() &&
            (context.wifiSsid == null || context.wifiSsid !in wifiSsids)) return false
        when (charging) {
            -1 -> Unit
            0 -> if (context.isCharging != false) return false
            1 -> if (context.isCharging != true) return false
            else -> return false
        }
        if (!useTimeRange) return true
        if (startMinute !in 0..1439 || endMinute !in 0..1439 ||
            context.minuteOfDay !in 0..1439) return false
        return when {
            startMinute == endMinute -> true // A full day.
            startMinute < endMinute -> context.minuteOfDay >= startMinute &&
                context.minuteOfDay < endMinute
            else -> context.minuteOfDay >= startMinute || context.minuteOfDay < endMinute
        }
    }
}

/** A snapshot taken at notification time, using the device's local day and clock. */
data class TriggerContext(
    val minuteOfDay: Int,
    val weekday: Int,
    val wifiSsid: String?,
    val isCharging: Boolean?
)

data class TriggerResponse(
    val needSound: Boolean,
    val needVibrate: Boolean,
    val soundVolume: Int,
    val vibrationPattern: String = VibrationPattern.CONTINUOUS.id
)

/** The first matching rule wins; otherwise retain this filter's normal response. */
fun FilterData.resolveResponse(context: TriggerContext): TriggerResponse {
    val rule = responseRules.firstOrNull { it.matches(context) }
    return if (rule != null) {
        TriggerResponse(rule.needSound, rule.needVibrate, rule.soundVolume.coerceIn(0, 100),
            VibrationPattern.fromId(rule.vibrationPattern).id)
    } else {
        TriggerResponse(needSound, needVibrate, soundVolume.coerceIn(0, 100),
            VibrationPattern.fromId(vibrationPattern).id)
    }
}
