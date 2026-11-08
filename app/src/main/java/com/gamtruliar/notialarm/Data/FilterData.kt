package com.gamtruliar.notialarm.Data

import com.gamtruliar.notialarm.Enums.RingDurationType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
data class FilterData (
    var filerUID:Int=0,
    var filterName:String="",
    var packageNames:ArrayList<String> = ArrayList<String>(),
    var enable:Boolean=true,
    var subFilterData:HashMap<Int,SubFilterData> = HashMap<Int,SubFilterData>(),
    var subFilterDataUUID:Int=0,
    var overrideBanTime:Boolean=false,
    var banTimes: HashMap<Int,BanTimePeriod> = HashMap<Int,BanTimePeriod>(),
    var banTimesUUID:Int=0,
    var specSound:String="",
    var needVibrate:Boolean=true,
    var needSound:Boolean=true,
    var ringDuration:Int= RingDurationType._5_Minus.ordinal,
    var soundVolume:Int=100,
    var responseRules:ArrayList<TriggerResponseRule> = arrayListOf()
)
