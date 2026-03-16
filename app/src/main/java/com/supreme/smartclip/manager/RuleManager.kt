package com.supreme.smartclip.manager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class RuleManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smartclip_rules", Context.MODE_PRIVATE)

    // 基础电商规则
    var taobaoRule: String
        get() = prefs.getString("taobao_rule", "淘宝*tb.cn*|￥*￥|€*€")!!
        set(value) = prefs.edit().putString("taobao_rule", value).apply()

    // 【新增】：淘宝云端解析 API 配置
    var taobaoApiUrl: String
        get() = prefs.getString("taobao_api_url", "https://api.taokouling.com/tkl/viptkljm")!!
        set(value) = prefs.edit().putString("taobao_api_url", value).apply()

    var taobaoApiKey: String
        get() = prefs.getString("taobao_api_key", "")!! // 默认为空，需要用户自己填
        set(value) = prefs.edit().putString("taobao_api_key", value).apply()

    var jdRule: String
        get() = prefs.getString("jd_rule", "京东*jd.com*|*u.jd.com*")!!
        set(value) = prefs.edit().putString("jd_rule", value).apply()

    var pddRule: String
        get() = prefs.getString("pdd_rule", "拼多多*|*pinduoduo.com*")!!
        set(value) = prefs.edit().putString("pdd_rule", value).apply()

    var douyinRule: String
        get() = prefs.getString("douyin_rule", "抖音*v.douyin.com*|*打开抖音*|*抖音口令*")!!
        set(value) = prefs.edit().putString("douyin_rule", value).apply()

    var xianyuRule: String
        get() = prefs.getString("xianyu_rule", "闲鱼*m.tb.cn*|*fleamarket*")!!
        set(value) = prefs.edit().putString("xianyu_rule", value).apply()

    var baiduPanRule: String
        get() = prefs.getString("baidu_pan_rule", "百度网盘*pan.baidu.com*")!!
        set(value) = prefs.edit().putString("baidu_pan_rule", value).apply()

    var quarkRule: String
        get() = prefs.getString("quark_rule", "夸克*pan.quark.cn*")!!
        set(value) = prefs.edit().putString("quark_rule", value).apply()

    var addressRule: String
        get() = prefs.getString("address_rule", "*省*市*|*市*区*|*路*号*|*公寓*|*酒店*")!!
        set(value) = prefs.edit().putString("address_rule", value).apply()

    var customRules: String
        get() = prefs.getString("custom_rules", "查快递*顺丰:com.sfg.view")!!
        set(value) = prefs.edit().putString("custom_rules", value).apply()

    fun exportToJson(): String {
        val json = JSONObject()
        json.put("taobao", taobaoRule)
        json.put("taobao_api_url", taobaoApiUrl)
        json.put("taobao_api_key", taobaoApiKey)
        json.put("jd", jdRule)
        json.put("pdd", pddRule)
        json.put("douyin", douyinRule)
        json.put("xianyu", xianyuRule)
        json.put("baidu_pan", baiduPanRule)
        json.put("quark", quarkRule)
        json.put("address", addressRule)
        json.put("custom", customRules)
        return json.toString()
    }

    fun importFromJson(jsonString: String): Boolean {
        return try {
            val json = JSONObject(jsonString)
            taobaoRule = json.optString("taobao", taobaoRule)
            taobaoApiUrl = json.optString("taobao_api_url", taobaoApiUrl)
            taobaoApiKey = json.optString("taobao_api_key", taobaoApiKey)
            jdRule = json.optString("jd", jdRule)
            pddRule = json.optString("pdd", pddRule)
            douyinRule = json.optString("douyin", douyinRule)
            xianyuRule = json.optString("xianyu", xianyuRule)
            baiduPanRule = json.optString("baidu_pan", baiduPanRule)
            quarkRule = json.optString("quark", quarkRule)
            addressRule = json.optString("address", addressRule)
            customRules = json.optString("custom", customRules)
            true
        } catch (e: Exception) {
            false
        }
    }
}