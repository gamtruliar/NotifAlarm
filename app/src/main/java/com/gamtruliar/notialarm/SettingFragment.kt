package com.gamtruliar.notialarm

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.gamtruliar.notialarm.Base.NoMenuFragment
import com.gamtruliar.notialarm.Data.BanTimePeriod
import com.gamtruliar.notialarm.Data.GlobalSettingData
import com.gamtruliar.notialarm.Data.SubFilterData
import com.gamtruliar.notialarm.databinding.FragmentSecondBinding
import com.gamtruliar.notialarm.databinding.FragmentSettingBinding
import com.gamtruliar.notialarm.databinding.FragmentSubFilterBinding









// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "UID"

/**
 * A simple [Fragment] subclass.
 * Use the [SubFilterFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SettingFragment : NoMenuFragment() , AdapterView.OnItemSelectedListener{


    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!
    var prefs: SharedPreferences?=null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        // Inflate the layout for this fragment

        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs =AppCommon.getSharedPref(view.context)
        val discord = DiscordSettings.read(requireContext())
        binding.discordEnabled.isChecked = discord.enabled
        binding.discordWebhookUrl.setText(discord.webhookUrl)
        binding.discordSave.setOnClickListener {
            val url = binding.discordWebhookUrl.text.toString().trim()
            if ((binding.discordEnabled.isChecked || url.isNotEmpty()) && !DiscordProtocol.validWebhookUrl(url)) {
                binding.discordStatus.setText(R.string.discord_invalid_url)
            } else {
                DiscordSettings.save(requireContext(), binding.discordEnabled.isChecked, url)
                binding.discordStatus.setText(R.string.discord_saved)
            }
        }
        binding.discordTest.setOnClickListener {
            val url = binding.discordWebhookUrl.text.toString().trim()
            if (!DiscordProtocol.validWebhookUrl(url)) {
                binding.discordStatus.setText(R.string.discord_invalid_url)
                return@setOnClickListener
            }
            binding.discordTest.isEnabled = false
            binding.discordStatus.setText(R.string.discord_sending)
            // The test explicitly sends the entered URL, even with forwarding disabled.
            val testView = binding
            DiscordForwarder.sendTest(requireContext(), url) { result ->
                if (_binding !== testView) return@sendTest
                binding.discordTest.isEnabled = true
                binding.discordStatus.text = when (result.kind) {
                    DiscordForwarder.Kind.SENT -> getString(R.string.discord_sent)
                    DiscordForwarder.Kind.INVALID_URL -> getString(R.string.discord_invalid_url)
                    DiscordForwarder.Kind.NETWORK_ERROR -> getString(R.string.discord_network_error)
                    DiscordForwarder.Kind.HTTP_ERROR -> getString(R.string.discord_http_error, result.httpCode)
                    DiscordForwarder.Kind.QUEUE_FULL -> getString(R.string.discord_queue_full)
                }
            }
        }
        var layoutManager = LinearLayoutManager(context)
        binding.infscrAllBan.layoutManager = layoutManager
        layoutManager = LinearLayoutManager(context)
        binding.infscrNoSound.layoutManager = layoutManager
        layoutManager = LinearLayoutManager(context)
        binding.infscrNoVibrate.layoutManager = layoutManager


        val navController =  findNavController()
        binding.fabAllBan.setOnClickListener { view ->
            var nBanTimePeriod=BanTimePeriod()
            nBanTimePeriod.uid=AppCommon.gsData!!.all_banTimesUUID++
            AppCommon.gsData!!.allFun_banTimes[nBanTimePeriod.uid]=nBanTimePeriod
            AppCommon.curBanPeriod=nBanTimePeriod
            navController.navigate(R.id.action_Setting_to_SelectOneTimePeriod)
        }
        binding.fabNoSound.setOnClickListener { view ->
            var nBanTimePeriod=BanTimePeriod()
            nBanTimePeriod.uid=AppCommon.gsData!!.all_banTimesUUID++
            AppCommon.gsData!!.allSound_banTimes[nBanTimePeriod.uid]=nBanTimePeriod
            AppCommon.curBanPeriod=nBanTimePeriod
            navController.navigate(R.id.action_Setting_to_SelectOneTimePeriod)
        }
        binding.fabNoVibrate.setOnClickListener { view ->
            var nBanTimePeriod=BanTimePeriod()
            nBanTimePeriod.uid=AppCommon.gsData!!.all_banTimesUUID++
            AppCommon.gsData!!.allVibrate_banTimes[nBanTimePeriod.uid]=nBanTimePeriod
            AppCommon.curBanPeriod=nBanTimePeriod
            navController.navigate(R.id.action_Setting_to_SelectOneTimePeriod)
        }
        binding.spinnerLang.onItemSelectedListener=this
    }

    fun loadAsData(){
        var ngsData=AppCommon.getGSData(prefs!!)
        var data=ArrayList<BanTimePeriod>()
        for(appInfo in ngsData.allFun_banTimes){
            data.add(appInfo.value)
        }
        var adapter = BanTimePeriodAdapter(data)
        binding.infscrAllBan.adapter = adapter
        adapter.onRowClick.plusAssign{x->onBanTimeEdit(x)}
        adapter.onRowDeleteClick.plusAssign{x->onAllFuncDel(x)}

        data=ArrayList<BanTimePeriod>()
        for(appInfo in ngsData.allSound_banTimes){
            data.add(appInfo.value)
        }
        adapter = BanTimePeriodAdapter(data)
        binding.infscrNoSound.adapter = adapter
        adapter.onRowClick.plusAssign{x->onBanTimeEdit(x)}
        adapter.onRowDeleteClick.plusAssign{x->onAllSndDel(x)}

        data=ArrayList<BanTimePeriod>()
        for(appInfo in ngsData.allVibrate_banTimes){
            data.add(appInfo.value)
        }
        adapter = BanTimePeriodAdapter(data)
        binding.infscrNoVibrate.adapter = adapter
        adapter.onRowClick.plusAssign{x->onBanTimeEdit(x)}
        adapter.onRowDeleteClick.plusAssign{x->onAllVibrateDel(x)}
        binding.spinnerLang.onItemSelectedListener=null
        binding.spinnerLang.setSelection(prefs!!.getInt("Lang",0))
        binding.spinnerLang.onItemSelectedListener=this
    }
    override fun onItemSelected(parent: AdapterView<*>, views: View?, pos: Int, id: Long) {
        // An item was selected. You can retrieve the selected item using
        //var item= parent.getItemAtPosition(pos)
        //Toast.makeText(view?.context,"selected",Toast.LENGTH_SHORT).show()
        var curLang=prefs!!.getInt("Lang",0)
        if(pos!=curLang) {
            prefs!!.edit().putInt("Lang", pos).apply()

            if(AppCommon.setLocale(view?.context, AppCommon.LangType2Str[pos])){
                AppCommon.shouldRecreateWhenBack=true
            }
            // Reload current fragment
            // Reload current fragment

            val ft = this.parentFragmentManager.beginTransaction()
            ft.detach(this)
            ft.attach(this)
            ft.commit()
        }
//        val navController =  findNavController()
//        navController.navigateUp()
//        navController.navigate(R.id.action_FirstFragment_to_Setting)
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }
    fun onBanTimeEdit(data:BanTimePeriod){
        val navController =  findNavController()

            AppCommon.curBanPeriod=data
            navController.navigate(R.id.action_Setting_to_SelectOneTimePeriod)


    }
    fun onAllFuncDel(data:BanTimePeriod){
        AppCommon.gsData?.allFun_banTimes?.remove(data.uid)
        AppCommon.saveGSDataAndAddVersion(prefs!!)
        loadAsData()
    }
    fun onAllSndDel(data:BanTimePeriod){
        AppCommon.gsData?.allSound_banTimes?.remove(data.uid)
        AppCommon.saveGSDataAndAddVersion(prefs!!)
        loadAsData()
    }
    fun onAllVibrateDel(data:BanTimePeriod){
        AppCommon.gsData?.allVibrate_banTimes?.remove(data.uid)
        AppCommon.saveGSDataAndAddVersion(prefs!!)
        loadAsData()
    }
    override fun onResume() {
        super.onResume()
        if(AppCommon.curBanPeriod!=null){
            if(AppCommon.curBanPeriod!!.fromTime== AppCommon.curBanPeriod!!.toTime && AppCommon.curBanPeriod!!.fromTime==0){
                AppCommon.gsData?.allVibrate_banTimes?.remove(AppCommon.curBanPeriod!!.uid)
                AppCommon.gsData?.allSound_banTimes?.remove(AppCommon.curBanPeriod!!.uid)
                AppCommon.gsData?.allFun_banTimes?.remove(AppCommon.curBanPeriod!!.uid)
            }else{
                AppCommon.saveGSDataAndAddVersion(prefs!!)
            }
            AppCommon.curBanPeriod=null
        }

        loadAsData()

    }

    override fun onPause() {
        super.onPause()
        AppCommon.saveGSDataAndAddVersion(prefs!!)
        if(AppCommon.shouldRecreateWhenBack){
            AppCommon.shouldRecreateWhenBack=false
            AppCommon.restartAll(context)

        }
        //setHasOptionsMenu(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
