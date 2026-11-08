package com.gamtruliar.notialarm

import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.gamtruliar.notialarm.Data.BanTimePeriod
import com.gamtruliar.notialarm.Data.FilterData
import com.gamtruliar.notialarm.Data.SubFilterData
import com.gamtruliar.notialarm.Enums.VibrationPattern
import com.gamtruliar.notialarm.databinding.FragmentSecondBinding
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import androidx.core.app.ActivityCompat.startActivityForResult

import android.media.RingtoneManager

import android.content.Intent
import android.net.Uri


import android.app.Activity.RESULT_OK
import android.graphics.drawable.Drawable
import android.widget.AdapterView
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gamtruliar.notialarm.Base.NoMenuFragment
import com.gamtruliar.notialarm.Data.AppData
import kotlinx.serialization.encodeToString


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : NoMenuFragment(), AdapterView.OnItemSelectedListener {

    private var _binding: FragmentSecondBinding? = null
    var prefs: SharedPreferences?=null
    private var responseRulesEditor: ResponseRulesEditor? = null
    private var wifiPermissionDialog: AlertDialog? = null
    private var currentWifiSsidResult: ((String) -> Unit)? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        if (AppCommon.curFilterData == null) {
            savedInstanceState?.getString("editing_filter")?.let { json ->
                AppCommon.curFilterData = Json.decodeFromString<FilterData>(json)
            }
        }
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root

    }

    fun loadFromData(filterData:FilterData){
        var data=ArrayList<AppData>()
        for(appInfo in filterData.packageNames){
            var nAppData=AppData()
            nAppData.packageName=appInfo
            var pm=context?.getPackageManager()!!
            try {
                val app_name= pm.getApplicationLabel(  pm.getApplicationInfo(appInfo,    PackageManager.GET_META_DATA ))
                val icon: Drawable = pm.getApplicationIcon(appInfo)
                nAppData.icon=icon
                nAppData.app_name=app_name.toString()
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("NotiAlarm","NameNotFoundException:"+appInfo)
            }
            data.add(nAppData)
//            Log.i("NARM",appInfo)
        }
        val adapter = AppAdapter(data,true)
        binding.infscr.adapter = adapter
        adapter.onRowDeleteClick.plusAssign{x->onAppDelete(x)}

        binding.filterName.setText(filterData.filterName)

        var data2=ArrayList<SubFilterData>()
        for(appInfo in filterData.subFilterData){
            data2.add(appInfo.value)
//            Log.i("NARM",appInfo)
        }
        val adapter2 = SubFilterDataAdapter(data2)
        binding.infscrCond.adapter = adapter2
        adapter2.onRowClick.plusAssign{x->onSubConEdit(x)}
        adapter2.onRowDeleteClick.plusAssign{x->onSubConDelete(x)}

        binding.switchVibrate.isChecked= AppCommon.curFilterData?.needVibrate!!
        binding.switchSound.isChecked= AppCommon.curFilterData?.needSound!!
        binding.vibrationPattern.setSelection(VibrationPattern.fromId(filterData.vibrationPattern).ordinal)
        binding.vibrationPattern.isEnabled = filterData.needVibrate

        binding.switchOSPBan.isChecked= AppCommon.curFilterData?.overrideBanTime!!
        if(binding.switchOSPBan.isChecked){
            binding.SPBPart1.visibility=View.VISIBLE
            binding.SPBPart2.visibility=View.VISIBLE
        }else{
            binding.SPBPart1.visibility=View.GONE
            binding.SPBPart2.visibility=View.GONE
        }

        var data3=ArrayList<BanTimePeriod>()
        for(appInfo in filterData.banTimes){
            data3.add(appInfo.value)
//            Log.i("NARM",appInfo)
        }
        val adapter3 = BanTimePeriodAdapter(data3)
        binding.infscrSPBan.adapter = adapter3
        adapter3.onRowClick.plusAssign{x->onSPBanEdit(x)}
        adapter3.onRowDeleteClick.plusAssign{x->onSPBanDelete(x)}
        if(AppCommon.curFilterData?.specSound==""){
            binding.SpSound.text = view?.context?.getString(R.string.Def)
        }else {
            var mtch=AppCommon.soundRegex.find( AppCommon.curFilterData?.specSound!!)
            if(mtch!=null){
                val (name) = mtch.destructured
                binding.SpSound.text = name
            }else {
                binding.SpSound.text = AppCommon.curFilterData?.specSound
            }
        }

        binding.spinner.setSelection(AppCommon.curFilterData!!.ringDuration)
        binding.volumeSeekBar.progress = filterData.soundVolume.coerceIn(0, 100)
        binding.volumeValue.text = getString(R.string.response_volume_value, filterData.soundVolume.coerceIn(0, 100))
        responseRulesEditor?.render(filterData)
    }
    fun onSubConEdit(data:SubFilterData){
        AppCommon.curSubFilter=data
        val navController =  findNavController()
        navController.navigate(R.id.action_SecondFragment_to_SubFilter)
    }
    fun onSubConDelete(data:SubFilterData){
        var filterData=AppCommon.curFilterData!!
        filterData.subFilterData.remove(data.uid)
        loadFromData(filterData)
    }
    fun onSPBanEdit(data:BanTimePeriod){
        AppCommon.curBanPeriod=data
        val navController =  findNavController()
        navController.navigate(R.id.action_SecondFragment_to_SelectOneTimePeriod)
    }
    fun onSPBanDelete(data:BanTimePeriod){
        var filterData=AppCommon.curFilterData!!
        filterData.banTimes.remove(data.uid)
        loadFromData(filterData)
    }
    fun onAppDelete(packageName:AppData){
        var filterData=AppCommon.curFilterData!!
        filterData.packageNames.remove(packageName.packageName)
        loadFromData(filterData)
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        // An item was selected. You can retrieve the selected item using
        //var item= parent.getItemAtPosition(pos)
        AppCommon.curFilterData!!.ringDuration=pos

    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs =AppCommon.getSharedPref(view.context)
        responseRulesEditor = ResponseRulesEditor(
            requireContext(), binding.responseRules,
            onWifiRuleSaved = { requestWifiRulePermission() },
            onCurrentWifiRequested = { requestCurrentWifiSsid(it) }
        )
        binding.addResponseRule.setOnClickListener {
            responseRulesEditor?.add(AppCommon.curFilterData!!)
        }
        VibrationPatternUi.populate(binding.vibrationPattern, AppCommon.curFilterData!!.vibrationPattern)
        binding.vibrationPattern.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                AppCommon.curFilterData?.vibrationPattern = VibrationPatternUi.selectedId(binding.vibrationPattern)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.volumeValue.text = getString(R.string.response_volume_value, progress)
                if (fromUser) AppCommon.curFilterData?.soundVolume = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val navController =  findNavController()
        binding.fabApp.setOnClickListener { view ->
            Toast.makeText(activity,getString(R.string.NeedTimeToSearchApp),Toast.LENGTH_SHORT).show()
            navController.navigate(R.id.action_SecondFragment_to_SelectAppFragment)
        }
        val layoutManager = LinearLayoutManager(context)
        binding.infscr.layoutManager = layoutManager
        val layoutManager2 = LinearLayoutManager(context)
        binding.infscrCond.layoutManager = layoutManager2
        binding.fabCond.setOnClickListener { view ->
            var nuid= AppCommon.curFilterData!!.subFilterDataUUID++
            var curEditData= SubFilterData()
            curEditData.uid=nuid
            AppCommon.curFilterData!!.subFilterData[nuid]= curEditData
            AppCommon.curSubFilter=curEditData
            navController.navigate(R.id.action_SecondFragment_to_SubFilter)
        }

        val layoutManager3 = LinearLayoutManager(context)
        binding.infscrSPBan.layoutManager = layoutManager3
        binding.fabSPBan.setOnClickListener { view ->
            var nBanTimePeriod=BanTimePeriod()
            nBanTimePeriod.uid=AppCommon.curFilterData!!.banTimesUUID++
            AppCommon.curFilterData!!.banTimes[nBanTimePeriod.uid]=nBanTimePeriod
            AppCommon.curBanPeriod=nBanTimePeriod
            navController.navigate(R.id.action_SecondFragment_to_SelectOneTimePeriod)
        }

        binding.filterName.addTextChangedListener { view->
            AppCommon.curFilterData?.filterName= binding.filterName.text.toString()
        }
        binding.switchVibrate.setOnCheckedChangeListener{view,checked->
            AppCommon.curFilterData?.needVibrate=checked
            binding.vibrationPattern.isEnabled = checked
        }
        binding.switchSound.setOnCheckedChangeListener{view,checked->
            AppCommon.curFilterData?.needSound=checked
        }
        binding.switchOSPBan.setOnCheckedChangeListener{view,checked->
            AppCommon.curFilterData?.overrideBanTime=checked
            loadFromData( AppCommon.curFilterData!!)
        }

        binding.EditSPSound.setOnClickListener { view ->
            var currentTone: Uri = RingtoneManager.getActualDefaultRingtoneUri(
                this.context,
                RingtoneManager.TYPE_ALARM
            )
            if(AppCommon.curFilterData?.specSound!=""){
                currentTone=Uri.parse(AppCommon.curFilterData?.specSound!!)
            }
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone")
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentTone)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            startActivityForResult(intent, 999)
        }
        binding.spinner.onItemSelectedListener=this
        binding.testBtn.setOnClickListener{view->
            if(AppCommon.curFilterData!!.packageNames.count()>0) {
                AppCommon.saveFilter(AppCommon.curFilterData!!,prefs!!)
                val context = requireContext().applicationContext
                var it=Intent(AppCommon.ACTION_NARM_NAction_Test).setPackage(context.packageName)
                it.putExtra("FilterUID",AppCommon.curFilterData!!.filerUID)
                context.sendOrderedBroadcast(it, null, object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context, intent: Intent?) {
                        val message = when {
                            resultCode > 0 -> resultCode
                            resultCode == Activity.RESULT_CANCELED -> {
                                NotificationListenerService.requestRebind(
                                    ComponentName(context, NotificationCollectorService::class.java))
                                R.string.test_service_unavailable
                            }
                            else -> return
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }, null, Activity.RESULT_CANCELED, null, null)
            }else{
                Toast.makeText(activity,getString(R.string.needAddAppToTest),Toast.LENGTH_LONG).show()
            }


        }

    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 999) {
            if (resultCode === RESULT_OK) {
                Log.i("NARM", "onActivityResult:" + requestCode + "|" + resultCode)
                if (data != null) {
                    if (data.extras != null) {
                        AppCommon.curFilterData?.specSound= data.extras!!["android.intent.extra.ringtone.PICKED_URI"].toString()
                        loadFromData(AppCommon.curFilterData!!)
//                        for (v in data.extras!!.keySet()) {
//                            Log.i("NARM", "DRES " + v + ":" + data.extras!![v])
//                        }
                    }
                }
            }
//                when (requestCode) {
//                    1 -> AppCommon.curFilterData?.specSound = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
//                    else -> {
//                    }
//                }
//            }
        }
    }

    private fun requestCurrentWifiSsid(onResult: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            currentWifiSsidResult = onResult
            fillCurrentWifiSsid()
        } else {
            wifiPermissionDialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.response_wifi_current_permission_title)
                .setMessage(R.string.response_wifi_current_permission_help)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    currentWifiSsidResult = onResult
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION), 2109)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun fillCurrentWifiSsid() {
        val onResult = currentWifiSsidResult ?: return
        currentWifiSsidResult = null
        if (_binding == null) return
        val ssid = TriggerContextProvider.readConnectedSsid(requireContext())
        if (ssid != null) {
            onResult(ssid)
        } else {
            wifiPermissionDialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.response_wifi_current_permission_title)
                .setMessage(R.string.response_wifi_current_unavailable)
                .setPositiveButton(R.string.response_open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + requireContext().packageName)))
                }
                .setNeutralButton(R.string.response_wifi_location_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun requestWifiRulePermission() {
        AppCommon.curFilterData?.takeIf { it.packageNames.isNotEmpty() }?.let {
            AppCommon.saveFilter(it, prefs!!)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2107)
        } else {
            requestBackgroundWifiPermission()
        }
    }

    private fun requestBackgroundWifiPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2108)
        } else {
            wifiPermissionDialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.response_wifi_background_title)
                .setMessage(R.string.response_wifi_background_help)
                .setPositiveButton(R.string.response_open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + requireContext().packageName)))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2109) {
            fillCurrentWifiSsid()
        } else if (requestCode == 2107) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                requestBackgroundWifiPermission()
            } else {
                Toast.makeText(context, R.string.response_wifi_permission_help, Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == 2108 && (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(context, R.string.response_wifi_permission_help, Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()

    }
    override fun onSaveInstanceState(outState: Bundle) {
        AppCommon.curFilterData?.let { outState.putString("editing_filter", Json.encodeToString(it)) }
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        Log.i("NARM","FUID:"+AppCommon.curFilterData!!.filerUID)
        if(AppCommon.curBanPeriod!=null){
            if(AppCommon.curBanPeriod!!.fromTime== AppCommon.curBanPeriod!!.toTime && AppCommon.curBanPeriod!!.fromTime==0){
                AppCommon.curFilterData?.banTimes?.remove(AppCommon.curBanPeriod!!.uid)
            }
            AppCommon.curBanPeriod=null
        }
        if(AppCommon.curSubFilter!=null){
            if(AppCommon.curSubFilter!!.titleKeyWords== "" && AppCommon.curSubFilter!!.titleKeyWordsNot== "" && AppCommon.curSubFilter!!.textKeyWords== "" && AppCommon.curSubFilter!!.textKeyWordsNot== "" ){
                AppCommon.curFilterData?.subFilterData?.remove(AppCommon.curSubFilter!!.uid)
            }
            AppCommon.curSubFilter=null
        }
        loadFromData(AppCommon.curFilterData!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        responseRulesEditor?.dismiss()
        responseRulesEditor = null
        currentWifiSsidResult = null
        wifiPermissionDialog?.dismiss()
        wifiPermissionDialog = null
        _binding = null
    }
}
