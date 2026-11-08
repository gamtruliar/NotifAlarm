package com.gamtruliar.notialarm

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gamtruliar.notialarm.Data.FilterData
import com.gamtruliar.notialarm.Data.GlobalSettingData
import com.gamtruliar.notialarm.Data.TriggerResponseRule
import com.gamtruliar.notialarm.Enums.RingDurationType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the same private broadcasts used by Test and the alarm stop notification. */
@RunWith(AndroidJUnit4::class)
class AlarmVibrationTest {
    @Before
    fun enableGlobalVibration() {
        assertTrue(prefs.edit().putBoolean(AppCommon.SETTING_KEY_ALLVIBRATE, true).commit())
    }

    @After
    fun stopTestAlarms() {
        filterUids.forEach { sendRequest(AppCommon.ACTION_NARM_NAction, it) }
    }

    @Test
    fun vibrationTestUsesAlarmUsageAndCanRestartAndStop() {
        assumeTrue("Vibration history requires Android 12+", Build.VERSION.SDK_INT >= 31)
        assumeTrue("Device must have a vibrator", vibrator().hasVibrator())
        val filter = newFilter()
        var previousRecords = appVibrationRecords()

        repeat(2) {
            assertEquals(R.string.test_started, sendRequest(AppCommon.ACTION_NARM_NAction_Test, filter.filerUID))
            awaitNotification(filter.filerUID, present = true)
            val records = awaitNewVibrationRecords(previousRecords)
            assertTrue("The new vibration from ${context.packageName} must use ALARM: $records",
                records.all { ALARM_USAGE.containsMatchIn(it) })
            previousRecords = appVibrationRecords()
        }

        sendRequest(AppCommon.ACTION_NARM_NAction, filter.filerUID)
        awaitNotification(filter.filerUID, present = false)
    }

    @Test
    fun globalVibrationSwitchExplainsWhyTestIsPaused() {
        val filter = newFilter()
        assertTrue(prefs.edit().putBoolean(AppCommon.SETTING_KEY_ALLVIBRATE, false).commit())

        assertEquals(R.string.test_vibration_paused,
            sendRequest(AppCommon.ACTION_NARM_NAction_Test, filter.filerUID))
        assertFalse(hasNotification(filter.filerUID))

        // A suppressed response must not latch this filter and prevent a later test.
        assertTrue(prefs.edit().putBoolean(AppCommon.SETTING_KEY_ALLVIBRATE, true).commit())
        assertEquals(if (vibrator().hasVibrator()) R.string.test_started else R.string.test_no_vibrator,
            sendRequest(AppCommon.ACTION_NARM_NAction_Test, filter.filerUID))
    }

    @Test
    fun matchingResponseRuleExplainsWhyVibrationIsDisabled() {
        val filter = newFilter().apply {
            responseRules.add(TriggerResponseRule(needSound = false, needVibrate = false))
        }
        saveFilter(filter)

        assertEquals(R.string.test_vibration_disabled,
            sendRequest(AppCommon.ACTION_NARM_NAction_Test, filter.filerUID))
        assertFalse(hasNotification(filter.filerUID))

        filter.responseRules.clear()
        saveFilter(filter)
        assertEquals(if (vibrator().hasVibrator()) R.string.test_started else R.string.test_no_vibrator,
            sendRequest(AppCommon.ACTION_NARM_NAction_Test, filter.filerUID))
    }

    companion object {
        private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
        private val context get() = instrumentation.targetContext
        private val notifications get() = context.getSystemService(NotificationManager::class.java)
        private lateinit var prefs: SharedPreferences
        private val savedValues = linkedMapOf<String, Any?>()
        private val filterUids = linkedSetOf<Int>()
        private var bound = false
        private var previousSettings: GlobalSettingData? = null
        private lateinit var previousLocale: Locale
        private lateinit var previousConfiguration: Configuration
        private val connected = CountDownLatch(1)
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        private val ALARM_USAGE = Regex("\\busage\\s*[:=]\\s*(?:USAGE_)?ALARM\\b", RegexOption.IGNORE_CASE)

        @BeforeClass
        @JvmStatic
        fun bindNotificationService() {
            // Grant this in host setup (adb install -g or pm grant). Revoking it in teardown
            // would kill the instrumentation process, so these tests never change permission.
            assumeTrue("Grant notification permission before running these tests",
                Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED)
            prefs = AppCommon.getSharedPref(context)
            previousSettings = AppCommon.gsData
            previousLocale = Locale.getDefault()
            previousConfiguration = Configuration(context.resources.configuration)
            listOf("Lang", AppCommon.SETTING_KEY_ALLVIBRATE, AppCommon.SETTING_KEY_GlobalSetting,
                AppCommon.SETTING_KEY_GSVersion).forEach { savedValues[it] = prefs.all[it] }
            val version = prefs.getInt(AppCommon.SETTING_KEY_GSVersion, 0) + 1
            assertTrue(prefs.edit()
                .putInt(AppCommon.SETTING_KEY_GSVersion, version)
                .putString(AppCommon.SETTING_KEY_GlobalSetting, Json.encodeToString(GlobalSettingData(gsversion = version)))
                .commit())
            AppCommon.gsData = null
            bound = context.bindService(Intent(context, NotificationCollectorService::class.java),
                connection, Context.BIND_AUTO_CREATE)
            assertTrue("Bind target app notification service", bound)
            assertTrue("Notification service must connect", connected.await(5, TimeUnit.SECONDS))
            assumeTrue("App notifications must be enabled", notifications.areNotificationsEnabled())
            assumeTrue("Alarm notification channel must be enabled",
                Build.VERSION.SDK_INT < 26 || notifications.getNotificationChannel("NARM")?.importance !=
                    NotificationManager.IMPORTANCE_NONE)
        }

        @AfterClass
        @JvmStatic
        fun restoreAppState() {
            if (!::prefs.isInitialized) return
            try {
                if (bound) {
                    filterUids.forEach { sendRequest(AppCommon.ACTION_NARM_NAction, it) }
                    context.unbindService(connection)
                    bound = false
                    instrumentation.waitForIdleSync()
                }
            } finally {
                val edit = prefs.edit()
                savedValues.forEach { (key, value) ->
                    when (value) {
                        null -> edit.remove(key)
                        is Boolean -> edit.putBoolean(key, value)
                        is Int -> edit.putInt(key, value)
                        is String -> edit.putString(key, value)
                        else -> error("Unexpected saved preference type for $key")
                    }
                }
                assertTrue(edit.commit())
                AppCommon.gsData = previousSettings
                Locale.setDefault(previousLocale)
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(previousConfiguration, context.resources.displayMetrics)
            }
        }

        private fun newFilter(): FilterData {
            var uid = 1_000_000
            while (prefs.contains(AppCommon.SETTING_KEY_FILTERDataPrefix + uid)) uid++
            filterUids.add(uid)
            savedValues[AppCommon.SETTING_KEY_FILTERDataPrefix + uid] = null
            return FilterData(filerUID = uid, filterName = "Vibration integration test",
                needSound = false, needVibrate = true, ringDuration = RingDurationType.Once.ordinal)
                .also { saveFilter(it) }
        }

        private fun saveFilter(filter: FilterData) {
            // Avoid modifying the user's app/filter index; Test only needs this filter record.
            assertTrue(prefs.edit().putString(AppCommon.SETTING_KEY_FILTERDataPrefix + filter.filerUID,
                Json.encodeToString(filter)).commit())
        }

        private fun sendRequest(action: String, uid: Int): Int {
            val completed = CountDownLatch(1)
            val result = AtomicInteger(Activity.RESULT_CANCELED)
            context.sendOrderedBroadcast(Intent(action).setPackage(context.packageName).putExtra("FilterUID", uid),
                null, object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent?) {
                        result.set(resultCode)
                        completed.countDown()
                    }
                }, null, Activity.RESULT_CANCELED, null, null)
            assertTrue("Alarm broadcast must complete", completed.await(5, TimeUnit.SECONDS))
            return result.get()
        }

        @Suppress("DEPRECATION")
        private fun vibrator(): Vibrator = if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        else context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        private fun hasNotification(uid: Int) = notifications.activeNotifications.any { it.id == 123 + uid }

        private fun awaitNotification(uid: Int, present: Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 3_000
            while (hasNotification(uid) != present && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
            assertEquals("Alarm stop notification presence", present, hasNotification(uid))
        }

        private fun appVibrationRecords(): Set<String> {
            val dump = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys vibrator_manager"))
                .bufferedReader().use { it.readText() }
            val owner = Regex("(?<![\\w.])${Regex.escape(context.packageName)}(?![\\w.])")
            val uid = Regex("\\buid\\s*[:=]\\s*${context.applicationInfo.uid}\\b", RegexOption.IGNORE_CASE)
            return dump.lineSequence().filter { owner.containsMatchIn(it) && uid.containsMatchIn(it) }.toSet()
        }

        private fun awaitNewVibrationRecords(previous: Set<String>): Set<String> {
            val deadline = SystemClock.elapsedRealtime() + 3_000
            var records: Set<String>
            do {
                records = appVibrationRecords() - previous
                if (records.isNotEmpty()) return records
                SystemClock.sleep(20)
            } while (SystemClock.elapsedRealtime() < deadline)
            throw AssertionError("No new vibration record for ${context.packageName} (uid=${context.applicationInfo.uid})")
        }
    }
}
