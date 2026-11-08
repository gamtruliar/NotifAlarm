package com.gamtruliar.notialarm.Enums

/** Stable persisted IDs; timings alternate between off and on, in milliseconds. */
enum class VibrationPattern(val id: String, private val waveform: LongArray) {
    CONTINUOUS("continuous", longArrayOf(0, 1000)),
    SHORT_PULSE("short_pulse", longArrayOf(0, 200, 800)),
    LONG_PULSE("long_pulse", longArrayOf(0, 800, 400)),
    DOUBLE_PULSE("double_pulse", longArrayOf(0, 150, 150, 150, 1000)),
    TRIPLE_PULSE("triple_pulse", longArrayOf(0, 150, 150, 150, 150, 150, 1000)),
    SOS("sos", longArrayOf(
        0, 150, 150, 150, 150, 150, 450,
        450, 150, 450, 150, 450, 450,
        150, 150, 150, 150, 150, 1050
    ));

    // Callers cannot accidentally change the shared preset for subsequent alarms.
    val timings: LongArray get() = waveform.copyOf()

    companion object {
        fun fromId(id: String): VibrationPattern = entries.firstOrNull { it.id == id } ?: CONTINUOUS
    }
}
