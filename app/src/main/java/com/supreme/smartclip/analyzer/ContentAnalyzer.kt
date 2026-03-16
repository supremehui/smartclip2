package com.supreme.smartclip.analyzer

import android.content.Context
import android.util.Patterns
import com.supreme.smartclip.manager.RuleManager
import com.supreme.smartclip.model.AnalyzeResult
import com.supreme.smartclip.model.ContentType
import java.util.regex.Pattern

class ContentAnalyzer(context: Context) {

    private val ruleManager = RuleManager(context)
    private val URL_PATTERN = Pattern.compile("(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")

    fun analyze(text: String): AnalyzeResult {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return AnalyzeResult(ContentType.UNKNOWN, trimmedText)

        val flatText = trimmedText.replace("\n", " ").replace("\r", " ").replace(Regex("\\s+"), " ")

        var bestType = ContentType.UNKNOWN
        var maxRuleLength = 0

        fun checkRule(rule: String, type: ContentType, isCustom: Boolean = false) {
            val len = matchWildcard(flatText, rule, isCustom)
            if (len > maxRuleLength) {
                bestType = type
                maxRuleLength = len
            }
        }

        // 1. 匹配自定义规则与电商规则
        checkRule(ruleManager.customRules, ContentType.CUSTOM, true)
        checkRule(ruleManager.taobaoRule, ContentType.SHOPPING_TAOBAO)
        checkRule(ruleManager.jdRule, ContentType.SHOPPING_JD)
        checkRule(ruleManager.pddRule, ContentType.SHOPPING_PDD)

        // 2. 匹配新增四大常用 App 规则 (改为从 RuleManager 读取)
        checkRule(ruleManager.douyinRule, ContentType.APP_DOUYIN)
        checkRule(ruleManager.xianyuRule, ContentType.APP_XIANYU)
        checkRule(ruleManager.baiduPanRule, ContentType.APP_BAIDU_PAN)
        checkRule(ruleManager.quarkRule, ContentType.APP_QUARK)

        // 3. 匹配地址规则
        checkRule(ruleManager.addressRule, ContentType.ADDRESS)

        if (bestType != ContentType.UNKNOWN) {
            return AnalyzeResult(bestType, trimmedText)
        }

        // 4. 兜底判断网址
        if (URL_PATTERN.matcher(trimmedText).find() || Patterns.WEB_URL.matcher(trimmedText).matches()) {
            val matcher = URL_PATTERN.matcher(trimmedText)
            if (matcher.find()) {
                return AnalyzeResult(ContentType.URL, matcher.group())
            }
            return AnalyzeResult(ContentType.URL, trimmedText)
        }

        return AnalyzeResult(ContentType.UNKNOWN, trimmedText)
    }

    private fun matchWildcard(text: String, rules: String, isCustomRule: Boolean): Int {
        val ruleList = rules.split(Regex("[\\n|]")).map { it.trim() }.filter { it.isNotEmpty() }
            .sortedByDescending { (if (isCustomRule && it.contains(":")) it.substringBefore(":") else it).length }

        for (rule in ruleList) {
            val actualRule = if (isCustomRule && rule.contains(":")) rule.substringBefore(":") else rule
            val parts = actualRule.split("*")
            val regex = parts.joinToString(".*") { Pattern.quote(it) }

            if (Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                return actualRule.length
            }
        }
        return 0
    }
}