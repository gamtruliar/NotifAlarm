package com.gamtruliar.notialarm

import android.app.Notification.EXTRA_NOTIFICATION_ID
import android.app.NotificationChannel
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.gamtruliar.notialarm.Data.FilterData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.Exception
import java.util.concurrent.Executors

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build

import android.app.Notification
import android.os.Bundle
import com.gamtruliar.notialarm.Data.BanTimePeriod
import com.gamtruliar.notialarm.Data.GlobalSettingData
import com.gamtruliar.notialarm.Enums.RingDurationType
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import com.gamtruliar.notialarm.Enums.LangType
import androidx.core.content.ContextCompat.startActivity

import android.content.Intent.getIntent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gamtruliar.notialarm.Data.SubFilterData


class AppCommon {
    companion object {
        var SETTING_KEY_sbnIncPName="sbnIncPName";
        var SETTING_KEY_sbnIncPName2UID="sbnIncPName2UID";
        var SETTING_KEY_AllFilterUID="AllFilterUID";
        var SETTING_KEY_FILTERUUID="FILTERUUID";
        var SETTING_KEY_FILTERDataPrefix="FILTER_Data";
        var SETTING_KEY_ALLFUNCTION="ALLFUNCTION";
        var SETTING_KEY_ALLSOUND="ALLSOUND";
        var SETTING_KEY_ALLVIBRATE="ALLVIBRATE";
        var SETTING_KEY_GlobalSetting="GlobalSetting";
        var SETTING_KEY_GSVersion="GSVersion";

        var ACTION_NARM_NAction="NARM_ACTION"
        var ACTION_NARM_NAction_LANG="NARM_ACTION_LANG"
        var ACTION_NARM_NAction_Test="NARM_ACTION_Test"

        var soundRegex=Regex("title=([\\w\\d\\s_]+)")

        var WD2SStr=hashMapOf(
            Calendar.SUNDAY to R.string.WD0,
            Calendar.MONDAY to R.string.WD1,
            Calendar.TUESDAY to R.string.WD2,
            Calendar.WEDNESDAY to R.string.WD3,
            Calendar.THURSDAY to R.string.WD4,
            Calendar.FRIDAY to R.string.WD5,
            Calendar.SATURDAY to R.string.WD6,

        )

        var RingDurationType2MS= hashMapOf<Int,Long>(
            RingDurationType.Once.ordinal to -1,
            RingDurationType._1_Minus.ordinal to 1L*60*1000,
            RingDurationType._3_Minus.ordinal to 3L*60*1000,
            RingDurationType._5_Minus.ordinal to 5L*60*1000,
            RingDurationType._10_Minus.ordinal to 10L*60*1000,
            RingDurationType._15_Minus.ordinal to 15L*60*1000,
            RingDurationType._30_Minus.ordinal to 30L*60*1000,
            RingDurationType._1_Hours.ordinal to 60L*60*1000,
            RingDurationType._2_Hours.ordinal to 120L*60*1000,
            RingDurationType._6_Hours.ordinal to 6*60L*60*1000,
            RingDurationType.Forever.ordinal to -2

        )
        var LangType2Str= hashMapOf<Int,String>(
           LangType.EN.ordinal to "en",
            LangType.HK.ordinal to "zh",
            LangType.JP.ordinal to "ja",


        )
        var Str2LangType= hashMapOf<String,Int>(

            "zh" to LangType.HK.ordinal,
            "ja" to LangType.JP.ordinal ,
            )
        var gsData: GlobalSettingData?=null
        var curFilterData: FilterData?=null
        var curBanPeriod:BanTimePeriod?=null
        var curSubFilter:SubFilterData?=null
        var shouldRecreateWhenBack=false


        fun getCurrentDOW():Int{
            val calendar: Calendar = Calendar.getInstance()
            val day: Int = calendar.get(Calendar.DAY_OF_WEEK)
            return day
        }
        fun restartAll(activity: Context?){
//            var ser= activity?.getSystemService(NotificationCollectorService::class.java)
//            ser.re
            var tcontext=activity
            if(tcontext is Activity){
//                var ser= activity?.getSystemService(NotificationCollectorService::class.java)
//                checkStartLang(ser)
                tcontext.sendBroadcast(Intent(ACTION_NARM_NAction_LANG).setPackage(tcontext.packageName))
//                tcontext.stopService(Intent(tcontext, NotificationCollectorService::class.java))
//                tcontext.startService(Intent(tcontext, NotificationCollectorService::class.java))
                tcontext.recreate()
            }else if(tcontext is NotificationCollectorService){//service

            }
        }
        fun checkStartLang(activity: Context?){
            if(activity==null)return
            var prefs =AppCommon.getSharedPref(activity)
            var curLang=prefs!!.getInt("Lang",-1)

            if(curLang==-1){ //first
                curLang=0
                var syslang= Locale.getDefault().getLanguage()
//                Toast.makeText(activity,syslang, Toast.LENGTH_SHORT).show()
                if(AppCommon.Str2LangType.containsKey(syslang)){
                    curLang=AppCommon.Str2LangType[syslang]!!
                }
                prefs.edit().putInt("Lang",curLang).apply()
            }

            if( AppCommon.setLocale(activity, AppCommon.LangType2Str[curLang])){
                AppCommon.restartAll(activity)
            }
        }
        fun setLocale(activity: Context?, languageCode: String?):Boolean {
            val locale = Locale(languageCode)
            val resources: Resources = activity!!.resources
            val config: Configuration = resources.getConfiguration()


            var diffLocal=false

            diffLocal=(config.locales.get(0)!=locale)

            diffLocal=diffLocal || (Locale.getDefault()!=locale)
            Log.i("NARM","Set Lang:"+activity+"|"+diffLocal)
            if(diffLocal) {
                Locale.setDefault(locale)
                config.setLocale(locale)
                resources.updateConfiguration(config, resources.getDisplayMetrics())

                return true
            }
            return false
        }

        fun getGSData(prefs :SharedPreferences):GlobalSettingData{
            var lastGSVersion=prefs.getInt(SETTING_KEY_GSVersion,0)
            if(gsData==null || gsData!!.gsversion!=lastGSVersion){
                var gsJson=prefs.getString(SETTING_KEY_GlobalSetting,"")
                if(gsJson==""){
                    gsData=GlobalSettingData()
                    gsData!!.gsversion=lastGSVersion
                }else{
                    try{
                        gsData=Json.decodeFromString<GlobalSettingData>(gsJson!!)
                    }catch (e:Exception){
                        gsData=GlobalSettingData()
                        gsData!!.gsversion=lastGSVersion
                    }
                }
            }
            return gsData!!
        }
        fun saveGSDataAndAddVersion(prefs :SharedPreferences){
            if(gsData!=null){
                gsData!!.gsversion++
                var edit=prefs.edit()
                edit.putInt(SETTING_KEY_GSVersion,gsData!!.gsversion)
                edit.putString(SETTING_KEY_GlobalSetting,Json.encodeToString(gsData!!))
                edit.apply()
            }
        }
        fun getFilterDataByUID(prefs :SharedPreferences,uid:Int):FilterData{
            var filterDataJson=prefs?.getString(AppCommon.SETTING_KEY_FILTERDataPrefix+uid,"")!!
//            Log.i("NARM","filterDataJson:"+filterDataJson)
            var filterData=Json.decodeFromString<FilterData>(filterDataJson);
            return filterData
        }
        fun saveFilter(filterData:FilterData,prefs :SharedPreferences){
            var edit = prefs?.edit()
            var allFilterUID = AppCommon.getAllFilterUIDS(prefs!!)
            var nuid = filterData.filerUID
            if (allFilterUID.contains(nuid) == false) {
                allFilterUID.add(nuid)
                edit?.putString(                    AppCommon.SETTING_KEY_AllFilterUID,                    Json.encodeToString(allFilterUID)                )
            }


            edit?.putString(                AppCommon.SETTING_KEY_FILTERDataPrefix + nuid,                Json.encodeToString(filterData)            )
            edit?.apply()
            AppCommon.saveP2UID(prefs, allFilterUID)
        }
        fun killFilter(uid:Int,prefs :SharedPreferences){
            var allFilterUID = AppCommon.getAllFilterUIDS(prefs)
            allFilterUID.remove(uid)
            var edit=prefs.edit()
            edit?.putString(
                AppCommon.SETTING_KEY_AllFilterUID,
                Json.encodeToString(allFilterUID)
            )
            edit?.remove(                AppCommon.SETTING_KEY_FILTERDataPrefix + uid          )
            edit?.apply()
            saveP2UID(prefs,allFilterUID)
        }
        fun getPackName2UIDMap(prefs :SharedPreferences):HashMap<String,HashSet<Int>>{
            var mapJSon=prefs.getString(SETTING_KEY_sbnIncPName2UID,"")
            var retMap=HashMap<String,HashSet<Int>>()
            if(mapJSon!=""){
                retMap=Json.decodeFromString(mapJSon!!)
            }
            return retMap
        }
        fun saveP2UID(prefs :SharedPreferences,allUID:HashSet<Int>){
            var pNameHS=HashSet<String>()
            var pNameMap=HashMap<String,HashSet<Int>>()
            for (uid in allUID){
                var filterData=AppCommon.getFilterDataByUID(prefs!!,uid)
                for (pname in filterData.packageNames){
                    pNameHS.add(pname)
                    if(pNameMap.containsKey(pname)==false){
                        pNameMap[pname]= HashSet<Int>()
                    }
                    pNameMap[pname]?.add(uid)
                }
            }
            var edit=prefs.edit()
            edit.putStringSet(SETTING_KEY_sbnIncPName,pNameHS)
            edit.putString(SETTING_KEY_sbnIncPName2UID,Json.encodeToString(pNameMap))
            edit.apply()
        }

        fun getAllFilterPackageName(prefs :SharedPreferences):HashSet<String> {
            var allFilterUIDJson= prefs?.getString(AppCommon.SETTING_KEY_sbnIncPName,"")!!
            var allFilterUID=HashSet<String>()
            if(allFilterUIDJson!="") {
                allFilterUID = Json.decodeFromString<HashSet<String>>(allFilterUIDJson);
            }
            return allFilterUID
        }
        fun getAllFilterUIDS(prefs :SharedPreferences):HashSet<Int>{
            var allFilterUIDJson= prefs?.getString(AppCommon.SETTING_KEY_AllFilterUID,"")!!
            var allFilterUID=HashSet<Int>()
            if(allFilterUIDJson!="") {
                allFilterUID = Json.decodeFromString<HashSet<Int>>(allFilterUIDJson);
            }
            return allFilterUID
        }

        fun playAlermSnd(context: Context, path: Uri, durationType: Int, volume: Int = 100): MediaPlayer {
            val mediaPlayer = MediaPlayer()
            try {
                mediaPlayer.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                mediaPlayer.setDataSource(context, path)
                mediaPlayer.isLooping = durationType != RingDurationType.Once.ordinal
                // Per-player gain preserves the user's system alarm volume.
                val gain = volume.coerceIn(0, 100) / 100f
                mediaPlayer.setVolume(gain, gain)
                mediaPlayer.setOnPreparedListener { it.start() }
                mediaPlayer.setOnCompletionListener { it.release() }
                mediaPlayer.setOnErrorListener { player, what, extra ->
                    Log.e("NARM", "Alarm playback failed: $what/$extra")
                    player.release()
                    true
                }
                mediaPlayer.prepareAsync()
                return mediaPlayer
            } catch (ex: Exception) {
                mediaPlayer.release()
                throw ex
            }
        }
        fun charLimitStr(str:String,limit:Int):String{
            if(str.length>limit){
                return str.substring(limit)+"..."
            }
            return str
        }
        fun getAppName(context: Context,packageName:String):String{
            var pm=context.getPackageManager()
            try {
                val app_name= pm.getApplicationLabel(  pm.getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA ))
                return app_name.toString()

            } catch (e: PackageManager.NameNotFoundException) {
//            e.printStackTrace()
                Log.e("NotiAlarm","NameNotFoundException:"+packageName)
            }
            return packageName
        }
        fun setAppInfo(context: Context,packageName:String,imageView: ImageView,textView: TextView){
            var pm=context.getPackageManager()
            try {
                val app_name= pm.getApplicationLabel(  pm.getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA ))
                val icon: Drawable = pm.getApplicationIcon(packageName)
                imageView.setImageDrawable(icon)
                textView.text=app_name
            } catch (e: PackageManager.NameNotFoundException) {
//            e.printStackTrace()
                Log.e("NotiAlarm","NameNotFoundException:"+packageName)
            }
        }

        fun getSharedPref(context: Context):SharedPreferences{
            var pName=context.getPackageName()+"_preferences";
            var prefs =context.getSharedPreferences( pName, Context.MODE_PRIVATE);
            return prefs
        }
        fun newFilter(prefs :SharedPreferences,context:Context?):FilterData{
            var maxUID=prefs.getInt(AppCommon.SETTING_KEY_FILTERUUID,0)
            var filterData=FilterData()
            filterData.filerUID=maxUID
            filterData.filterName="new filter"+maxUID
            var currentTone: Uri = RingtoneManager.getActualDefaultRingtoneUri(                context,                RingtoneManager.TYPE_ALARM            )
            filterData.specSound=currentTone.toString()
            var edit=prefs.edit()
            edit.putInt(AppCommon.SETTING_KEY_FILTERUUID,maxUID+1)
            edit.apply()
            return filterData
        }

        fun minInt2Str(minInt:Int):String{
            return ((minInt/60).toString().padStart(2,'0')+":"+(minInt%60).toString().padStart(2,'0'))
        }
        fun createNotificationChannel(context: Context) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.notiChannel)
                val descriptionText =context.getString(R.string.notiChannel)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel("NARM", name, importance).apply {
                    description = descriptionText
                }
                // Register the channel with the system
                val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun sendNoti(context: Context,title:String,text:String,extraData:Bundle,uid:Int,intentMod:(it:Intent)->Unit){


            var intent= Intent(ACTION_NARM_NAction).setPackage(context.packageName)
            intentMod(intent)
            val snoozePendingIntent: PendingIntent = PendingIntent.getBroadcast(
                context, uid, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            var builder = NotificationCompat.Builder(context, "NARM")
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                // Sound and vibration are controlled by the resolved response rule.
                .setSilent(true)
                .setExtras(extraData)
                .setContentIntent(snoozePendingIntent)
                .setDeleteIntent(snoozePendingIntent)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            manager!!.notify(uid, builder.build())
        }


    }

}
