package com.supreme.smartclip

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnEditRules: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        btnOverlay = findViewById(R.id.btn_permission_overlay)
        btnAccessibility = findViewById(R.id.btn_permission_accessibility)
        tvStatus = findViewById(R.id.tv_status)
        btnEditRules = findViewById(R.id.btn_edit_rules)
    }

    private fun setupListeners() {
        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        btnAccessibility.setOnClickListener {
            if (!isAccessibilitySettingsOn(this)) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } else {
                Toast.makeText(this, "辅助功能已开启，若需重启请在系统中开关", Toast.LENGTH_SHORT).show()
            }
        }

        // 跳转到二级规则编辑页面
        btnEditRules.setOnClickListener {
            startActivity(Intent(this, EditRulesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilitySettingsOn(this)

        btnOverlay.isEnabled = !hasOverlay
        btnOverlay.text = if (hasOverlay) "1. 悬浮窗权限 (已开启)" else "1. 开启悬浮窗权限"

        btnAccessibility.isEnabled = !hasAccessibility
        btnAccessibility.text = if (hasAccessibility) "2. 辅助功能 (已开启)" else "2. 开启辅助功能权限"

        if (hasOverlay && hasAccessibility) {
            tvStatus.text = "服务状态：运行中，随时待命！"
            tvStatus.setTextColor(Color.parseColor("#009900"))
        } else {
            tvStatus.text = "服务状态：需要权限才能工作"
            tvStatus.setTextColor(Color.parseColor("#FF0000"))
        }
    }

    private fun isAccessibilitySettingsOn(mContext: Context): Boolean {
        var accessibilityEnabled = 0
        val service = packageName + "/" + com.supreme.smartclip.service.ClipboardService::class.java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(mContext.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) { }

        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(mContext.applicationContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (settingValue != null) {
                val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) return true
                }
            }
        }
        return false
    }
}