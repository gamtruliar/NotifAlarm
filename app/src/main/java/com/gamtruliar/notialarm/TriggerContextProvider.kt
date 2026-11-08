package com.gamtruliar.notialarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gamtruliar.notialarm.Data.TriggerContext
import java.util.Calendar

/** Reads device state at trigger time; never reuses an SSID from an earlier connection. */
object TriggerContextProvider {
    fun read(context: Context, needsWifi: Boolean = true): TriggerContext {
        val calendar = Calendar.getInstance()
        return TriggerContext(
            minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE),
            weekday = calendar.get(Calendar.DAY_OF_WEEK),
            wifiSsid = if (needsWifi) readConnectedSsid(context) else null,
            isCharging = readCharging(context)
        )
    }

    private fun readCharging(context: Context): Boolean? {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        return when (battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    fun readConnectedSsid(context: Context): String? {
        // Android 10+ requires precise location; older devices also accept coarse location.
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || !coarse)) return null

        return try {
            val location = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                location.isLocationEnabled
            } else {
                location.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
            if (!locationEnabled) return null

            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            // Inspect all networks: a VPN or mobile connection may be the default while Wi-Fi is connected.
            val connectedToWifi = connectivity.allNetworks.any { network ->
                val capabilities = connectivity.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    connectivity.getNetworkInfo(network)?.isConnected == true
            }
            if (!connectedToWifi) return null

            val wifi = (context.applicationContext ?: context)
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            // This compatibility API still supplies the current SSID; a synchronous
            // NetworkCapabilities snapshot can redact it even with location permission.
            // The platform also enforces foreground/background location app-ops here.
            val info = wifi.connectionInfo ?: return null
            if (info.supplicantState != SupplicantState.COMPLETED || info.networkId == -1) return null
            val ssid = info.ssid ?: return null
            if (ssid == WifiManager.UNKNOWN_SSID) return null
            val decoded = if (ssid.length >= 2 && ssid.first() == '"' && ssid.last() == '"') {
                ssid.substring(1, ssid.length - 1)
            } else {
                ssid
            }
            decoded.takeIf { it.isNotEmpty() }
        } catch (_: SecurityException) {
            // Revoked permission, denied app-op, or unavailable background access: unknown, not a match.
            null
        } catch (_: IllegalArgumentException) {
            // A provider or network can disappear while the snapshot is being read.
            null
        }
    }
}
