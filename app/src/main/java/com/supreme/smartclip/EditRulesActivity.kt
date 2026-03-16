package com.supreme.smartclip

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.supreme.smartclip.manager.RuleManager

class EditRulesActivity : AppCompatActivity() {

    private lateinit var ruleManager: RuleManager

    private lateinit var etTaobao: EditText
    // 【新增】淘宝API相关组件
    private lateinit var etTaobaoApiUrl: EditText
    private lateinit var etTaobaoApiKey: EditText

    private lateinit var etJd: EditText
    private lateinit var etPdd: EditText
    private lateinit var etDouyin: EditText
    private lateinit var etXianyu: EditText
    private lateinit var etBaiduPan: EditText
    private lateinit var etQuark: EditText
    private lateinit var etAddress: EditText

    private lateinit var llCustomContainer: LinearLayout
    private lateinit var btnAddCustomRule: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_rules)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ruleManager = RuleManager(this)
        initViews()
        loadRules()
    }

    private fun initViews() {
        etTaobao = findViewById(R.id.et_rule_taobao)
        etTaobaoApiUrl = findViewById(R.id.et_taobao_api_url)
        etTaobaoApiKey = findViewById(R.id.et_taobao_api_key)

        etJd = findViewById(R.id.et_rule_jd)
        etPdd = findViewById(R.id.et_rule_pdd)
        etDouyin = findViewById(R.id.et_rule_douyin)
        etXianyu = findViewById(R.id.et_rule_xianyu)
        etBaiduPan = findViewById(R.id.et_rule_baidu_pan)
        etQuark = findViewById(R.id.et_rule_quark)
        etAddress = findViewById(R.id.et_rule_address)

        llCustomContainer = findViewById(R.id.ll_custom_rules_container)
        btnAddCustomRule = findViewById(R.id.btn_add_custom_rule)

        findViewById<Button>(R.id.btn_save_rules).setOnClickListener { saveRules() }
        findViewById<Button>(R.id.btn_export).setOnClickListener { exportRules() }
        findViewById<Button>(R.id.btn_import).setOnClickListener { importRules() }

        btnAddCustomRule.setOnClickListener {
            addCustomRuleView("")
        }
    }

    private fun loadRules() {
        etTaobao.setText(ruleManager.taobaoRule.replace("|", "\n"))
        etTaobaoApiUrl.setText(ruleManager.taobaoApiUrl)
        etTaobaoApiKey.setText(ruleManager.taobaoApiKey)

        etJd.setText(ruleManager.jdRule.replace("|", "\n"))
        etPdd.setText(ruleManager.pddRule.replace("|", "\n"))
        etDouyin.setText(ruleManager.douyinRule.replace("|", "\n"))
        etXianyu.setText(ruleManager.xianyuRule.replace("|", "\n"))
        etBaiduPan.setText(ruleManager.baiduPanRule.replace("|", "\n"))
        etQuark.setText(ruleManager.quarkRule.replace("|", "\n"))
        etAddress.setText(ruleManager.addressRule.replace("|", "\n"))

        llCustomContainer.removeAllViews()
        val customRulesList = ruleManager.customRules.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        if (customRulesList.isEmpty()) {
            addCustomRuleView("")
        } else {
            for (rule in customRulesList) {
                addCustomRuleView(rule)
            }
        }
    }

    private fun addCustomRuleView(ruleText: String) {
        val view = layoutInflater.inflate(R.layout.item_custom_rule, llCustomContainer, false)
        val etRule = view.findViewById<EditText>(R.id.et_custom_rule_item)
        etRule.setText(ruleText)

        view.findViewById<View>(R.id.btn_delete_rule).setOnClickListener {
            llCustomContainer.removeView(view)
        }

        llCustomContainer.addView(view)
    }

    private fun saveRules() {
        ruleManager.taobaoRule = etTaobao.text.toString().trim().replace("\n", "|").replace(Regex("\\|+"), "|")
        ruleManager.taobaoApiUrl = etTaobaoApiUrl.text.toString().trim()
        ruleManager.taobaoApiKey = etTaobaoApiKey.text.toString().trim()

        ruleManager.jdRule = etJd.text.toString().trim().replace("\n", "|").replace(Regex("\\|+"), "|")
        ruleManager.pddRule = etPdd.text.toString().trim().replace("\n", "|").replace(Regex("\\|+"), "|")
        ruleManager.douyinRule = etDouyin.text.toString().trim().replace("\n", "|").replace(Regex("\\|+"), "|")
        ruleManager.xianyuRule = etXianyu.text.toString().trim().replace("\n", "|").replace(Regex("\\|+"), "|")
        ruleManager.baiduPanRule = etBaiduPan.text.toString().trim().replace("\n", "|").replace(Regex("\\|+"), "|")
        ruleManager.quarkRule = etQuark.text.toString().trim().replace("\n", "|").replace(Regex("\\|+"), "|")
        ruleManager.addressRule = etAddress.text.toString().trim().replace("\n", "|").replace(Regex("\\|+"), "|")

        val customRuleBuilder = java.lang.StringBuilder()
        for (i in 0 until llCustomContainer.childCount) {
            val view = llCustomContainer.getChildAt(i)
            val etRule = view.findViewById<EditText>(R.id.et_custom_rule_item)
            val text = etRule.text.toString().trim()

            if (text.isNotEmpty()) {
                val formattedText = text.replace("\n", "|").replace(Regex("\\|+"), "|")
                if (customRuleBuilder.isNotEmpty()) {
                    customRuleBuilder.append("|")
                }
                customRuleBuilder.append(formattedText)
            }
        }
        ruleManager.customRules = customRuleBuilder.toString()

        Toast.makeText(this, "规则已保存生效！", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun exportRules() {
        val jsonString = ruleManager.exportToJson()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SmartClip Rules", jsonString)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "规则已复制到剪贴板，请粘贴到备忘录保存", Toast.LENGTH_LONG).show()
    }

    private fun importRules() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && clipboard.primaryClip!!.itemCount > 0) {
            val pasteData = clipboard.primaryClip!!.getItemAt(0).text.toString()
            if (ruleManager.importFromJson(pasteData)) {
                loadRules()
                Toast.makeText(this, "规则导入成功！请点击保存", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "导入失败：剪贴板内容不是有效的规则格式", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
        }
    }
}