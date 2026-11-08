package com.gamtruliar.notialarm

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.gamtruliar.notialarm.Data.FilterData
import com.gamtruliar.notialarm.Data.TriggerResponseRule

/** Edits a copy; cancelling a dialog never changes a saved rule. */
class ResponseRulesEditor(
    private val context: Context,
    private val container: LinearLayout,
    private val onWifiRuleSaved: () -> Unit,
    private val onCurrentWifiRequested: ((String) -> Unit) -> Unit
) {
    private var dialog: AlertDialog? = null
    private var childDialog: AlertDialog? = null
    private val dayNames = intArrayOf(R.string.Sunday, R.string.Monday, R.string.Tuesday,
        R.string.Wednesday, R.string.Thursday, R.string.Friday, R.string.Saturday)

    fun dismiss() {
        childDialog?.dismiss()
        dialog?.dismiss()
    }

    fun add(filter: FilterData) = edit(filter, -1)

    fun render(filter: FilterData) {
        container.removeAllViews()
        if (filter.responseRules.isEmpty()) {
            container.addView(label(context.getString(R.string.response_no_rules)))
            return
        }
        filter.responseRules.forEachIndexed { index, rule ->
            val card = column().apply { setPadding(dp(4), dp(12), dp(4), dp(8)) }
            val name = rule.name.ifEmpty { context.getString(R.string.response_rule_number, index + 1) }
            card.addView(Switch(context).apply {
                text = "${index + 1}. $name"
                isChecked = rule.enabled
                setTypeface(typeface, Typeface.BOLD)
                setOnCheckedChangeListener { _, checked -> rule.enabled = checked }
            })
            card.addView(label(summary(rule)))
            card.addView(row().apply {
                addView(button(R.string.response_edit) { edit(filter, index) }, weighted())
                addView(button(R.string.response_delete) {
                    dialog = AlertDialog.Builder(context)
                        .setMessage(context.getString(R.string.response_delete_confirm, name))
                        .setPositiveButton(R.string.response_delete) { _, _ ->
                            filter.responseRules.removeAt(index)
                            render(filter)
                        }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }, weighted())
            })
            card.addView(row().apply {
                addView(button(R.string.response_move_up) {
                    val previous = filter.responseRules.removeAt(index)
                    filter.responseRules.add(index - 1, previous)
                    render(filter)
                }.apply { isEnabled = index > 0 }, weighted())
                addView(button(R.string.response_move_down) {
                    val next = filter.responseRules.removeAt(index)
                    filter.responseRules.add(index + 1, next)
                    render(filter)
                }.apply { isEnabled = index < filter.responseRules.lastIndex }, weighted())
            })
            container.addView(card)
        }
    }

    private fun summary(rule: TriggerResponseRule): String {
        val conditions = arrayListOf<String>()
        if (rule.useTimeRange) conditions.add(context.getString(R.string.response_time_summary,
            AppCommon.minInt2Str(rule.startMinute), AppCommon.minInt2Str(rule.endMinute)))
        if (rule.weekdays.isNotEmpty()) conditions.add(rule.weekdays.filter { it in 1..7 }
            .joinToString(", ") { context.getString(dayNames[it - 1]) })
        if (rule.wifiSsids.isNotEmpty()) conditions.add(context.getString(R.string.response_wifi_summary, rule.wifiSsids.joinToString(", ")))
        if (rule.charging != -1) conditions.add(context.getString(if (rule.charging == 1) R.string.response_charging_yes else R.string.response_charging_no))
        if (conditions.isEmpty()) conditions.add(context.getString(R.string.response_any_condition))
        val actions = arrayListOf<String>()
        if (rule.needSound) actions.add(context.getString(R.string.response_sound_summary, rule.soundVolume.coerceIn(0, 100)))
        if (rule.needVibrate) actions.add(context.getString(R.string.Vibrate))
        if (actions.isEmpty()) actions.add(context.getString(R.string.response_silent))
        return conditions.joinToString(" · ") + "\n→ " + actions.joinToString(" + ")
    }

    private fun edit(filter: FilterData, index: Int) {
        val original = filter.responseRules.getOrNull(index) ?: TriggerResponseRule(
            needSound = filter.needSound,
            needVibrate = filter.needVibrate,
            soundVolume = filter.soundVolume
        )
        val content = column().apply { setPadding(dp(20), dp(8), dp(20), dp(16)) }
        val name = EditText(context).apply {
            hint = context.getString(R.string.response_rule_name)
            setText(original.name)
            isSingleLine = true
        }
        content.addView(name)
        val enabled = CheckBox(context).apply {
            setText(R.string.response_enabled)
            isChecked = original.enabled
        }
        content.addView(enabled)
        val useTime = CheckBox(context).apply {
            setText(R.string.response_use_time)
            isChecked = original.useTimeRange
        }
        content.addView(useTime)
        var startMinute = original.startMinute.coerceIn(0, 1439)
        var endMinute = original.endMinute.coerceIn(0, 1439)
        val start = button(R.string.StartTime) {}
        val end = button(R.string.EndTime) {}
        fun updateTime() {
            start.text = context.getString(R.string.response_time_button, context.getString(R.string.StartTime), AppCommon.minInt2Str(startMinute))
            end.text = context.getString(R.string.response_time_button, context.getString(R.string.EndTime), AppCommon.minInt2Str(endMinute))
            start.isEnabled = useTime.isChecked
            end.isEnabled = useTime.isChecked
        }
        start.setOnClickListener {
            childDialog = TimePickerDialog(context, { _, hour, minute ->
                startMinute = hour * 60 + minute
                updateTime()
            }, startMinute / 60, startMinute % 60, true).apply { show() }
        }
        end.setOnClickListener {
            childDialog = TimePickerDialog(context, { _, hour, minute ->
                endMinute = hour * 60 + minute
                updateTime()
            }, endMinute / 60, endMinute % 60, true).apply { show() }
        }
        useTime.setOnCheckedChangeListener { _, _ -> updateTime() }
        updateTime()
        content.addView(row().apply {
            addView(start, weighted())
            addView(end, weighted())
        })
        content.addView(label(context.getString(R.string.response_time_help)))

        val selectedDays = BooleanArray(7) { original.weekdays.contains(it + 1) }
        val weekdays = button(R.string.response_weekdays) {}
        fun updateWeekdays() {
            val selected = selectedDays.indices.filter { selectedDays[it] }
            weekdays.text = context.getString(R.string.response_weekdays_value,
                if (selected.isEmpty()) context.getString(R.string.response_any_day)
                else selected.joinToString(", ") { context.getString(dayNames[it]) })
        }
        weekdays.setOnClickListener {
            val draft = selectedDays.copyOf()
            childDialog = AlertDialog.Builder(context)
                .setTitle(R.string.response_weekdays)
                .setMultiChoiceItems(dayNames.map { context.getString(it) }.toTypedArray(), draft) { _, day, checked -> draft[day] = checked }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    draft.copyInto(selectedDays)
                    updateWeekdays()
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        updateWeekdays()
        content.addView(weekdays)
        content.addView(label(context.getString(R.string.response_wifi)))
        val ssids = EditText(context).apply {
            hint = context.getString(R.string.response_wifi_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 2
            setText(original.wifiSsids.joinToString("\n"))
        }
        content.addView(ssids)
        content.addView(button(R.string.response_wifi_use_current) {
            onCurrentWifiRequested { ssid ->
                // Permission results can arrive after this editor has closed.
                if (dialog?.isShowing == true && ssids.isAttachedToWindow) {
                    val entered = ssids.text.toString()
                    if (entered.lineSequence().none { it == ssid }) {
                        val separator = if (entered.isEmpty() || entered.endsWith('\n')) "" else "\n"
                        ssids.setText(entered + separator + ssid)
                        ssids.setSelection(ssids.length())
                    }
                }
            }
        })
        content.addView(label(context.getString(R.string.response_wifi_permission_help)))
        content.addView(label(context.getString(R.string.response_charging)))
        val charging = Spinner(context).apply {
            adapter = ArrayAdapter.createFromResource(context, R.array.response_charging_options, android.R.layout.simple_spinner_item).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(when (original.charging) { 0 -> 1; 1 -> 2; else -> 0 })
        }
        content.addView(charging)
        content.addView(label(context.getString(R.string.response_action)).apply { setTypeface(typeface, Typeface.BOLD) })
        val sound = CheckBox(context).apply { setText(R.string.Sound); isChecked = original.needSound }
        val vibrate = CheckBox(context).apply { setText(R.string.Vibrate); isChecked = original.needVibrate }
        content.addView(sound)
        content.addView(vibrate)
        val volumeLabel = label("")
        val volume = SeekBar(context).apply { max = 100; progress = original.soundVolume.coerceIn(0, 100) }
        fun updateVolume() {
            volumeLabel.text = context.getString(R.string.response_volume_value, volume.progress)
            volume.isEnabled = sound.isChecked
        }
        volume.contentDescription = context.getString(R.string.response_volume)
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateVolume()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        sound.setOnCheckedChangeListener { _, _ -> updateVolume() }
        updateVolume()
        content.addView(volumeLabel)
        content.addView(volume, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(label(context.getString(R.string.response_volume_help)))

        dialog = AlertDialog.Builder(context)
            .setTitle(if (index < 0) R.string.response_add_rule else R.string.response_edit_rule)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.response_save) { _, _ ->
                val saved = TriggerResponseRule(
                    name = name.text.toString().trim(),
                    enabled = enabled.isChecked,
                    useTimeRange = useTime.isChecked,
                    startMinute = startMinute,
                    endMinute = endMinute,
                    weekdays = ArrayList(selectedDays.indices.filter { selectedDays[it] }.map { it + 1 }),
                    // SSIDs are case-sensitive and may start or end with spaces.
                    wifiSsids = ArrayList(ssids.text.toString().lineSequence().filter { it.isNotEmpty() }.distinct().toList()),
                    charging = when (charging.selectedItemPosition) { 1 -> 0; 2 -> 1; else -> -1 },
                    needSound = sound.isChecked,
                    needVibrate = vibrate.isChecked,
                    soundVolume = volume.progress
                )
                if (index < 0) filter.responseRules.add(saved) else filter.responseRules[index] = saved
                render(filter)
                if (saved.wifiSsids.isNotEmpty()) onWifiRuleSaved()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun label(value: String) = TextView(context).apply { text = value }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private fun weighted() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    private fun button(text: Int, action: () -> Unit) = Button(context).apply {
        setText(text)
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
