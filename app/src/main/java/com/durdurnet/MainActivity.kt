package com.durdurnet

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.provider.Settings

class MainActivity : Activity() {
    private val requestVpn = 100
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val blocked = mutableSetOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 18, 32)
        window.navigationBarColor = Color.rgb(12, 18, 32)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 12)
            setBackgroundColor(Color.rgb(12, 18, 32))
        }
        outer.addView(TextView(this).apply {
            text = "DurdurNet"
            textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
        })
        outer.addView(TextView(this).apply {
            text = "Root gerektirmeyen internet kontrolü"
            textSize = 14f; setTextColor(Color.LTGRAY); setPadding(0, 4, 0, 18)
        })
        status = TextView(this).apply {
            text = "VPN kapalı"; textSize = 16f; setTextColor(Color.rgb(255, 190, 90))
            setPadding(0, 8, 0, 14)
        }
        outer.addView(status)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(Button(this).apply {
            text = "VPN'i başlat"
            setOnClickListener {
                val intent = VpnService.prepare(this@MainActivity)
                if (intent != null) startActivityForResult(intent, requestVpn)
                else startVpn()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply {
            text = "Uygulama ayarları"
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        outer.addView(controls)
        outer.addView(TextView(this).apply {
            text = "Uygulamalar · seçtiklerini işaretle"
            textSize = 18f; setTextColor(Color.WHITE); setPadding(0, 18, 0, 8)
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer)
        showApps()
    }
    private fun showApps() {
        list.removeAllViews()
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        for (app in apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 8, 8, 8)
            }
            val name = TextView(this).apply {
                text = pm.getApplicationLabel(app).toString() + "\n" + app.packageName
                textSize = 13f; setTextColor(Color.WHITE)
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            val check = CheckBox(this).apply {
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(90, 200, 160))
                isChecked = blocked.contains(app.packageName)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) blocked.add(app.packageName) else blocked.remove(app.packageName)
                    status.text = if (blocked.isEmpty()) "VPN açık · engellenecek uygulama seçilmedi"
                                  else "Seçili uygulama: ${blocked.size} (ilk sürüm)"
                }
            }
            row.addView(check)
            list.addView(row)
            list.addView(ViewDivider(this))
        }
    }
    private fun startVpn() {
        startService(Intent(this, FirewallVpnService::class.java))
        status.text = "VPN başlatıldı · trafik filtreleme henüz etkin değil"
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestVpn && resultCode == RESULT_OK) startVpn()
    }
}
