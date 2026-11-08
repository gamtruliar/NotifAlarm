package com.gamtruliar.notialarm

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.gamtruliar.notialarm.Enums.VibrationPattern

/** Keep the filter and rule editors' choices in the same order. */
internal object VibrationPatternUi {
    fun name(context: Context, id: String): String = context.getString(when (VibrationPattern.fromId(id)) {
        VibrationPattern.CONTINUOUS -> R.string.vibration_pattern_continuous
        VibrationPattern.SHORT_PULSE -> R.string.vibration_pattern_short
        VibrationPattern.LONG_PULSE -> R.string.vibration_pattern_long
        VibrationPattern.DOUBLE_PULSE -> R.string.vibration_pattern_double
        VibrationPattern.TRIPLE_PULSE -> R.string.vibration_pattern_triple
        VibrationPattern.SOS -> R.string.vibration_pattern_sos
    })

    fun populate(spinner: Spinner, selectedId: String) {
        spinner.adapter = ArrayAdapter(spinner.context, android.R.layout.simple_spinner_item,
            VibrationPattern.entries.map { name(spinner.context, it.id) }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(VibrationPattern.fromId(selectedId).ordinal)
    }

    fun selectedId(spinner: Spinner): String =
        VibrationPattern.entries.getOrNull(spinner.selectedItemPosition)?.id ?: VibrationPattern.CONTINUOUS.id
}
