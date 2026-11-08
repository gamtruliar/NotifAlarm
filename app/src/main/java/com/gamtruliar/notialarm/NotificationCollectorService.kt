package com.gamtruliar.notialarm

import android.content.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.notification.NotificationListenerService.RankingMap
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import android.preference.PreferenceManager

import android.net.Uri
import android.os.*
import android.widget.Toast
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gamtruliar.notialarm.CData.RingingData
import com.gamtruliar.notialarm.Data.BanTimePeriod
import com.gamtruliar.notialarm.Data.FilterData
import com.gamtruliar.notialarm.Data.resolveResponse
import com.gamtruliar.notialarm.Enums.RingDurationType
import com.gamtruliar.notialarm.Enums.VibrationPattern
import java.lang.Exception
import java.net.URI
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet


class NotificationCollectorService : NotificationListenerService() {
    var prefs:SharedPreferences?=null
    var alarming=HashSet<Int>()
    var uid2RingingData=HashMap<Int, RingingData>()
    var m_Recv:ActionBReceiver?=null
    var m_Recv_lang:ActionBReceiver?=null
    var m_Recv_test:BroadcastReceiver?=null
    private val alarmHandler = Handler(Looper.getMainLooper())
    private data class AlarmVibration(val vibrator: Vibrator, val timings: LongArray, val repeat: Int)
    private val vibrationRequests = linkedMapOf<Int, AlarmVibration>()
    private var vibrationOwner: Int? = null
    private var vibrationCompletion: Runnable? = null

    private fun playVibration(uid: Int) {
        val request = vibrationRequests[uid] ?: return
        vibrationCompletion?.let { alarmHandler.removeCallbacks(it) }
        vibrationCompletion = null
        // Android may ignore a one-time effect while a repeating alarm is still running.
        vibrationOwner?.let { vibrationRequests[it]?.vibrator?.cancel() }
        // A preempted one-time rhythm must not replay when another alarm finishes.
        vibrationOwner?.takeIf { it != uid && vibrationRequests[it]?.repeat == -1 }
            ?.let { vibrationRequests.remove(it) }
        vibrationOwner = uid
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // Android repeats the whole rhythm, including pauses, with alarm usage in the background.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            request.vibrator.vibrate(
                VibrationEffect.createWaveform(request.timings, request.repeat),
                VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            request.vibrator.vibrate(VibrationEffect.createWaveform(request.timings, request.repeat), audioAttributes)
        } else {
            @Suppress("DEPRECATION")
            request.vibrator.vibrate(request.timings, request.repeat, audioAttributes)
        }
        if (request.repeat == -1) {
            val complete = Runnable {
                if (vibrationOwner == uid) {
                    vibrationRequests.remove(uid)
                    vibrationOwner = null
                    vibrationCompletion = null
                    vibrationRequests.keys.lastOrNull()?.let { playVibration(it) }
                }
            }
            vibrationCompletion = complete
            alarmHandler.postDelayed(complete, request.timings.sum())
        }
    }

    private fun stopVibration(uid: Int) {
        val request = vibrationRequests.remove(uid) ?: return
        // Several filters share one motor; stopping a superseded alarm must not cancel its owner.
        if (vibrationOwner != uid) return
        vibrationCompletion?.let { alarmHandler.removeCallbacks(it) }
        vibrationCompletion = null
        vibrationOwner = null
        request.vibrator.cancel()
        vibrationRequests.keys.lastOrNull()?.let { playVibration(it) }
    }



    fun regRecv(){
        if (m_Recv != null) return

        var mRecv=ActionBReceiver { c, intv ->
//            Log.i("NARM","recv")
            onNotiRecv(c, intv)
        }
        m_Recv=mRecv
        var ifilter= IntentFilter()
        ifilter.addAction(AppCommon.ACTION_NARM_NAction)
        ContextCompat.registerReceiver(this, mRecv, ifilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        mRecv=ActionBReceiver { c, intv ->
            onNLangRecv(c, intv)
        }
        m_Recv_lang=mRecv
        ifilter= IntentFilter()
        ifilter.addAction(AppCommon.ACTION_NARM_NAction_LANG)
        ContextCompat.registerReceiver(this, mRecv, ifilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val testReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val message = onTestRecv(context, intent)
                if (isOrderedBroadcast) resultCode = message
            }
        }
        m_Recv_test=testReceiver
        ifilter= IntentFilter()
        ifilter.addAction(AppCommon.ACTION_NARM_NAction_Test)
        ContextCompat.registerReceiver(this, testReceiver, ifilter, ContextCompat.RECEIVER_NOT_EXPORTED)


    }
    fun relocal(){
        Log.i("NARM","relocal")
        AppCommon.checkStartLang(this)
    }
    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        relocal()
        regRecv()
    }

    override fun onBind(intent: Intent?): IBinder? {
        AppCommon.createNotificationChannel(this)
        relocal()
        regRecv()
        return super.onBind(intent)

    }

    override fun onUnbind(intent: Intent?): Boolean {
        uid2RingingData.keys.toList().forEach { stopSoundByUID(it) }
        if(m_Recv!=null)unregisterReceiver(m_Recv)
        m_Recv=null
        if(m_Recv_lang!=null)unregisterReceiver(m_Recv_lang)
        m_Recv_lang=null
        if(m_Recv_test!=null)unregisterReceiver(m_Recv_test)
        m_Recv_test=null
        return super.onUnbind(intent)
    }
    fun shotRingWithNoti(notiUID: Int, filterName: String, soundUri: Uri,
                         isSound: Boolean, isVibrate: Boolean, durationType: Int,
                         soundVolume: Int = 100,
                         vibrationPattern: String = VibrationPattern.CONTINUOUS.id): Int {
        if (alarming.contains(notiUID)) return R.string.test_started
        // Every alarm needs a visible stop control, including after permission revocation.
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return R.string.notification_permission_help
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            getSystemService(NotificationManager::class.java).getNotificationChannel("NARM")?.importance ==
            NotificationManager.IMPORTANCE_NONE) return R.string.test_alarm_channel_blocked
        val playSound = isSound && soundVolume > 0 && canSound()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(VIBRATOR_SERVICE) as Vibrator)
        }
        val vibrationMessage = when {
            !isVibrate -> R.string.test_vibration_disabled
            !canVibrate() -> R.string.test_vibration_paused
            !vibrator.hasVibrator() -> R.string.test_no_vibrator
            else -> R.string.test_started
        }
        val vibrate = vibrationMessage == R.string.test_started
        // A silent response must not latch the filter and block later notifications.
        if (!playSound && !vibrate) return vibrationMessage

        val ringData = RingingData()
        uid2RingingData[notiUID] = ringData
        alarming.add(notiUID)
        AppCommon.sendNoti(this, getString(R.string.alarmTitle).format(filterName),
            getString(R.string.alarmText), Bundle(), 123 + notiUID) {
            it.putExtra("FilterUID", notiUID)
        }
        if (playSound) {
            try {
                ringData.mediaPlayer = AppCommon.playAlermSnd(this, soundUri, durationType, soundVolume)
            } catch (ex: Exception) {
                Log.e("NARM", "Unable to play alarm", ex)
            }
        }
        if (vibrate) {
            val timings = VibrationPattern.fromId(vibrationPattern).timings
            val repeat = if (durationType == RingDurationType.Once.ordinal) -1 else 0
            vibrationRequests[notiUID] = AlarmVibration(vibrator, timings, repeat)
            ringData.vibStoper = { stopVibration(notiUID) }
            playVibration(notiUID)
        }
        val duration = AppCommon.RingDurationType2MS[durationType] ?: -1L
        if (duration > 0) {
            val stop = Runnable { stopSoundByUID(notiUID) }
            alarmHandler.postDelayed(stop, duration)
            ringData.durStoper = { alarmHandler.removeCallbacks(stop) }
        }
        return vibrationMessage
    }

    private fun triggerFilterResponse(filterData: FilterData): Int {
        val context = TriggerContextProvider.read(this,
            needsWifi = filterData.responseRules.any { it.enabled && it.wifiSsids.isNotEmpty() })
        val response = filterData.resolveResponse(context)
        val uri = if (filterData.specSound.isNotEmpty()) Uri.parse(filterData.specSound)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return shotRingWithNoti(filterData.filerUID, filterData.filterName, uri,
            response.needSound, response.needVibrate, filterData.ringDuration, response.soundVolume,
            response.vibrationPattern)
    }
    fun onNLangRecv(context: Context?,intent: Intent?) {
        relocal()
    }
    fun onNotiRecv(context: Context?,intent: Intent?){
        if(intent==null)return
        var fuid: Int=0;
        if(intent.hasExtra("FilterUID")) {
            fuid = intent.getIntExtra("FilterUID", 0);
        }
        Log.i("NARM","onNotiRecv:"+fuid)
//        Toast.makeText(context,"wawaw:"+intent.getIntExtra("FilterUID",0), Toast.LENGTH_LONG).show()
        if(intent.hasExtra("FilterUID")){

            var rUID=fuid
            alarming.remove(rUID)
            stopSoundByUID(rUID)
        }
    }
    fun onTestRecv(context: Context?,intent: Intent?): Int {
        if(intent == null || !intent.hasExtra("FilterUID")) return 0
        if (prefs == null) prefs = AppCommon.getSharedPref(this)
        val fuid = intent.getIntExtra("FilterUID", 0)
        Log.i("NARM", "onTestRecv:$fuid")
        val filterData = AppCommon.getFilterDataByUID(prefs!!, fuid)
        stopSoundByUID(fuid)
        return triggerFilterResponse(filterData)
    }

    fun stopSoundByUID(uid:Int){
        NotificationManagerCompat.from(this).cancel(123 + uid)
        alarming.remove(uid)
        val ringData = uid2RingingData.remove(uid) ?: return
        ringData.mediaPlayer?.let { player ->
            // release also cancels preparation, so a dismissed alarm cannot start late.
            runCatching {
                player.setOnPreparedListener(null)
                player.release()
            }
        }
        ringData.vibStoper?.invoke()
        ringData.durStoper?.invoke()
    }

    override fun onDestroy() {
        uid2RingingData.keys.toList().forEach { stopSoundByUID(it) }
        super.onDestroy()
    }
    fun checkWithInTime(periods:HashMap<Int, BanTimePeriod>):Boolean{
        val calendar: Calendar = Calendar.getInstance()
        val nDOW: Int = calendar.get(Calendar.DAY_OF_WEEK)
        var nHM=calendar.get(Calendar.HOUR_OF_DAY)*60+calendar.get(Calendar.MINUTE)
        var stTimes=ArrayList<Int>()
        var edTimes=ArrayList<Int>()
        var endOfDay=60*24
        for(v in periods){
            if(v.value.weekDay.contains(nDOW)){
                var sttime=v.value.fromTime
                var edtime=v.value.toTime
                if(edtime<sttime)edtime=endOfDay
                stTimes.add(sttime)
                edTimes.add(edtime)
            }
            if(v.value.weekDay.contains(((nDOW-2)%7)+1)){
                if(v.value.toTime<v.value.fromTime){
                    var sttime=0
                    var edtime=v.value.toTime
                    stTimes.add(sttime)
                    edTimes.add(edtime)
                }
            }
        }
        stTimes.sort()
        edTimes.sort()
        var lastEdTimeIdx=0
        var sti=0
        while (sti < stTimes.size){
            var stt=stTimes[sti]
            for(i in lastEdTimeIdx until edTimes.size){
                var edt=edTimes[i]
                if(edt<stt)continue
                while(sti+1<stTimes.size && edt>stTimes[sti+1])sti++ //skip to found next start time
                if((sti+1>=stTimes.size || edt>stTimes[sti+1]) && i<edTimes.size-1)continue // skip to found last end time before next start time
               // println("chk:"+stt+"->"+edt)
                if(stt<=nHM && nHM<edt){
                    //println("tttt:"+stt+"->"+edt)
                    return true
                }
                lastEdTimeIdx=i+1
                break
            }
            sti++
        }
        return false
    }
    fun canFun(filterData: FilterData):Boolean{
        if(filterData.overrideBanTime){
            return checkWithInTime(filterData.banTimes)==false
        }
        var gsSetting=AppCommon.getGSData(prefs!!)
        return checkWithInTime(gsSetting.allFun_banTimes)==false
    }
    fun canSound():Boolean{
        if(prefs!!.getBoolean(AppCommon.SETTING_KEY_ALLSOUND,true)==false)return false
        var gsSetting=AppCommon.getGSData(prefs!!)
        return checkWithInTime(gsSetting.allSound_banTimes)==false
    }
    fun canVibrate():Boolean{
        if(prefs!!.getBoolean(AppCommon.SETTING_KEY_ALLVIBRATE,true)==false)return false
        var gsSetting=AppCommon.getGSData(prefs!!)
        return checkWithInTime(gsSetting.allVibrate_banTimes)==false
    }
    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if(prefs==null){
            var pName=getPackageName()+"_preferences";
            //Log.i("wawa", "pName:" + pName)
            prefs = getSharedPreferences( pName, Context.MODE_PRIVATE);
        }
        if(!prefs!!.getBoolean(AppCommon.SETTING_KEY_ALLFUNCTION,true))return




        var includedPackageName= prefs?.getStringSet(AppCommon.SETTING_KEY_sbnIncPName,HashSet<String>());

//        Log.i("wawa", "open" + "-----" + sbn.packageName)
//
//        Log.i("wawa", "open" + "------" + sbn.notification.tickerText)
//        Log.i("wawa", "open" + "-----" + sbn.notification.extras["android.title"])
//        Log.i("wawa", "open" + "-----" + sbn.notification.extras["android.text"])
////        Log.i("wawa", "open" + "-----" + Json.encodeToString(sbn.notification.extras.keySet()))
//        for (v in sbn.notification.extras.keySet()){
//            Log.i("wawa", "open" + v+":" + sbn.notification.extras[v])
//        }
//        Log.i("wawa", "open" + "-inc:" + Json.encodeToString(includedPackageName))
        if(includedPackageName?.contains( sbn.packageName) == true) {
            var uidMap=AppCommon.getPackName2UIDMap(prefs!!)
            if(uidMap.containsKey(sbn.packageName)){
                for(uid in uidMap[sbn.packageName]!!){
                    //--to do: cache
                    oneFilterHandle(AppCommon.getFilterDataByUID(prefs!!,uid),
                        sbn.notification.extras.getCharSequence("android.title")?.toString() ?: "",
                        sbn.notification.extras.getCharSequence("android.text")?.toString() ?: "",
                        DiscordNotificationOrigin(sbn.packageName, sbn.key, sbn.postTime))
                }
            }

        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
//        Log.i("wawa", "kill" + "-----" + sbn.packageName)
//
//        Log.i("wawa", "kill" + "------" + sbn.notification.tickerText)
//        Log.i("wawa", "kill" + "-----" + sbn.notification.extras["android.title"])
//        Log.i("wawa", "kill" + "-----" + sbn.notification.extras["android.text"])
////        Log.i("wawa", "open" + "-----" + Json.encodeToString(sbn.notification.extras.keySet()))
//        for (v in sbn.notification.extras.keySet()){
//            Log.i("wawa", "kill" + v+":" + sbn.notification.extras[v])
//        }

    }

    fun subkeywordCheck(KeyWords:String,useRegex:Boolean,targetwords:String):Boolean{
        if(KeyWords!=""){

            if(useRegex){
                if(Regex(KeyWords).matches(targetwords)){
                    return true
                }
            }else{
                var keywords=KeyWords.split(' ')
                for(v in keywords){
                    if(v.trim()=="")continue
                    if(targetwords.contains(v)==false)return false
                }
                return true

            }
        }else{
           return true
        }
        return false
    }
    fun keywordPassCheck(filterData: FilterData,title:String,text:String):Boolean{
        var anychk=false

        for (sdata in filterData.subFilterData){
            anychk=true
            var anyBanPassed=false
            var title_passed=false
            var text_passed=false
            title_passed=subkeywordCheck(sdata.value.titleKeyWords.trim(),sdata.value.titleUseRegex,title)
            text_passed=subkeywordCheck(sdata.value.textKeyWords.trim(),sdata.value.textUseRegex,text)
            if(sdata.value.titleKeyWordsNot.trim()!="") {
                anyBanPassed = subkeywordCheck(
                    sdata.value.titleKeyWordsNot,
                    sdata.value.titleUseRegexNot,
                    title
                )
            }
            if(sdata.value.textKeyWordsNot.trim()!="") {
                anyBanPassed = anyBanPassed || subkeywordCheck(
                    sdata.value.textKeyWordsNot,
                    sdata.value.textUseRegexNot,
                    text
                )
            }
//            Log.i("NARM","title_passed:"+title_passed)
//            Log.i("NARM","text_passed:"+text_passed)
//            Log.i("NARM","anyBanPassed:"+anyBanPassed)
            if((title_passed && text_passed) && !anyBanPassed)return true
        }
        return !anychk
    }

    fun oneFilterHandle(filterData: FilterData,title:String,text:String, origin: DiscordNotificationOrigin? = null){
        if(filterData.enable==false)return;
        if(canFun(filterData)==false)return;
        if(keywordPassCheck(filterData,title,text)){
            // Forward independently: a silent/already-active local alarm must not hide an event.
            origin?.let { DiscordForwarder.forwardMatched(this, filterData, it, title, text) }
            triggerFilterResponse(filterData)

        }

    }
}
