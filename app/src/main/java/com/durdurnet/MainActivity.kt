package com.durdurnet

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.net.Uri
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
    private val blocked = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 18, 32)
        window.navigationBarColor = Color.rgb(12, 18, 32)
        val prefs = getSharedPreferences("durdurnet", MODE_PRIVATE)
        blocked.addAll(prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet())

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 12)
            setBackgroundColor(Color.rgb(12, 18, 32))
        }
        outer.addView(TextView(this).apply {
            text = "DurdurNet"; textSize = 30f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        })
        outer.addView(TextView(this).apply {
            text = "Seçili uygulamaların internetini kes. Kısıtla düğmesiyle Android ayarlarından izinlerini ve çalışmasını yönet."
            textSize = 14f; setTextColor(Color.LTGRAY); setPadding(0, 4, 0, 14)
        })
        status = TextView(this).apply {
            text = "VPN kapalı"; textSize = 16f; setTextColor(Color.rgb(255, 190, 90))
            setPadding(0, 8, 0, 12)
        }
        outer.addView(status)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(Button(this).apply {
            text = "Engellemeyi başlat / güncelle"
            setOnClickListener {
                if (blocked.isEmpty()) {
                    status.text = "Önce internetini engellemek istediğin uygulamaları seç."
                } else {
                    val intent = VpnService.prepare(this@MainActivity)
                    if (intent != null) startActivityForResult(intent, requestVpn) else startVpn()
                }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f))
        controls.addView(Button(this).apply {
            text = "Durdur"
            setOnClickListener {
                // Tell the VPN service to close its tunnel before stopping the service.
                val stopIntent = Intent(this@MainActivity, FirewallVpnService::class.java)
                    .setAction(FirewallVpnService.ACTION_STOP)
                try {
                    startService(stopIntent)
                } catch (_: Exception) {
                    // Fallback if the service cannot receive the explicit stop command.
                }
                stopService(Intent(this@MainActivity, FirewallVpnService::class.java))
                status.text = "VPN durdurma isteği gönderildi. Ayarlardan bağlantıyı kontrol et."
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        outer.addView(controls)
        outer.addView(Button(this).apply {
            text = "Android uygulama ayarları"
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }
        })
        outer.addView(TextView(this).apply {
            text = "Uygulamalar · engellenecekleri işaretle"
            textSize = 18f; setTextColor(Color.WHITE); setPadding(0, 14, 0, 8)
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
        // Include disabled apps/components too; apps in another Android user/profile
        // or truly hidden by the operating system may still be inaccessible.
        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
        val apps = pm.getInstalledApplications(flags)
            .filter { it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        for (app in apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 7, 8, 7)
            }
            val appLabel = try {
                pm.getApplicationLabel(app).toString()
            } catch (_: Exception) {
                app.packageName
            }
            val name = TextView(this).apply {
                text = appLabel + "\n" + app.packageName
                textSize = 13f; setTextColor(Color.WHITE)
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "Kısıtla"
                textSize = 11f
                setPadding(5, 0, 5, 0)
                contentDescription = "$appLabel Android uygulama ayarlarını aç"
                setOnClickListener { openAppRestrictions(app.packageName) }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            val check = CheckBox(this).apply {
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(90, 200, 160))
                isChecked = blocked.contains(app.packageName)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) blocked.add(app.packageName) else blocked.remove(app.packageName)
                    getSharedPreferences("durdurnet", MODE_PRIVATE).edit()
                        .putStringSet("blocked_apps", HashSet(blocked)).apply()
                    status.text = if (blocked.isEmpty()) "Seçili uygulama yok"
                                  else "${blocked.size} uygulama seçildi. Değişiklik için başlat/güncelle."
                }
            }
            row.addView(check)
            list.addView(row)
            list.addView(ViewDivider(this))
        }
    }

    /**
     * Android does not let ordinary apps silently force-stop another app or revoke
     * its permissions. Open the system-owned app details screen so the user can
     * manage permissions, background use, mobile data and Force stop where available.
     */
    private fun openAppRestrictions(packageName: String) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (_: Exception) {
            status.text = "Bu uygulamanın Android ayarları açılamadı."
        }
    }

    private fun startVpn() {
        val intent = Intent(this, FirewallVpnService::class.java)
            .putStringArrayListExtra(FirewallVpnService.EXTRA_BLOCKED_PACKAGES, ArrayList(blocked))
        startService(intent)
        status.text = "${blocked.size} uygulama için internet engelleme etkinleştiriliyor."
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestVpn && resultCode == RESULT_OK) startVpn()
        else if (requestCode == requestVpn) status.text = "VPN izni verilmedi."
    }
}