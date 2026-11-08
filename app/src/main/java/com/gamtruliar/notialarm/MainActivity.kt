package com.gamtruliar.notialarm

import android.Manifest
import android.app.AlertDialog
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.gamtruliar.notialarm.databinding.ActivityMainBinding
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.net.Uri
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

import android.os.*
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.gamtruliar.notialarm.Enums.LangType
import com.google.android.gms.ads.initialization.InitializationStatus

import com.google.android.gms.ads.initialization.OnInitializationCompleteListener

import com.google.android.gms.ads.MobileAds
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var accessDialog: AlertDialog? = null
    private var accessGuidePage = 0
    private val appInfoSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Returning from App info does not grant notification-listener access.
        gotoSettingPage()
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, R.string.notification_permission_help, Toast.LENGTH_LONG).show()
        gotoSettingPage()
    }

    private fun hasNotificationAccess(): Boolean {
        val listeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val component = ComponentName(this, NotificationCollectorService::class.java)
        return listeners.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun gotoSettingPage() {
        if (!hasNotificationAccess()) showNotificationAccessGuide()
    }

    private fun showNotificationAccessGuide() {
        accessDialog?.dismiss()
        accessGuidePage = 1
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            R.string.notification_access_help
        } else {
            R.string.permissionText1
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.notification_access_title)
            .setMessage(message)
            .setPositiveButton(R.string.notification_access_open) { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.notification_settings_unavailable, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            dialog.setNeutralButton(R.string.notification_access_blocked) { _, _ ->
                showRestrictedSettingsGuide()
            }
        }
        accessDialog = dialog.show()
    }

    private fun showRestrictedSettingsGuide() {
        accessDialog?.dismiss()
        accessGuidePage = 2
        accessDialog = AlertDialog.Builder(this)
            .setTitle(R.string.restricted_settings_title)
            .setMessage(R.string.restricted_settings_help)
            .setPositiveButton(R.string.restricted_settings_open) { _, _ ->
                try {
                    appInfoSettings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.notification_settings_unavailable, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("notificationAccessGuide", if (accessDialog?.isShowing == true) accessGuidePage else 0)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        accessDialog?.dismiss()
        accessDialog = null
        super.onDestroy()
    }
    private fun requestStartupPermissions() {
        val prefs = AppCommon.getSharedPref(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("notificationPermissionRequested", false)) {
            prefs.edit().putBoolean("notificationPermissionRequested", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            gotoSettingPage()
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        Log.i("wawa", "onCreate");

//        Log.i("wawa", "requestPermissions");
        MobileAds.initialize(
            this
        ) { }
//        AppCommon.playAlermSnd(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
//        Log.i("wawa", "play alarm");
        binding = ActivityMainBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safeArea = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.root)

        setSupportActionBar(binding.toolbar)
        AppCommon.checkStartLang(this)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        AppCommon.createNotificationChannel(this)
        // Locale changes and rotation recreate the activity; preserve the current guide.
        when (savedInstanceState?.getInt("notificationAccessGuide", 0)) {
            1 -> if (!hasNotificationAccess()) showNotificationAccessGuide()
            2 -> if (!hasNotificationAccess()) showRestrictedSettingsGuide()
            else -> if (savedInstanceState == null) requestStartupPermissions()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
//        menuInflater.inflate(R.menu.action_about, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return when (item.itemId) {
            R.id.action_notification_access -> {
                showNotificationAccessGuide()
                true
            }
            R.id.action_notification_settings -> {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:$packageName"))
                }
                startActivity(intent)
                true
            }
            R.id.action_settings -> {

                navController.navigate(R.id.action_FirstFragment_to_Setting)
                true
            }
            R.id.action_about -> {

                navController.navigate(R.id.action_FirstFragment_to_About)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
