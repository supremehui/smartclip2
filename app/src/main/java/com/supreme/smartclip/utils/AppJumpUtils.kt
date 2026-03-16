package com.supreme.smartclip.utils

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import com.supreme.smartclip.manager.RuleManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

object AppJumpUtils {

    private const val PKG_TAOBAO = "com.taobao.taobao"
    private const val PKG_JD = "com.jingdong.app.mall"
    private const val PKG_PDD = "com.xunmeng.pinduoduo"

    private const val PKG_AMAP = "com.autonavi.minimap"
    private const val PKG_BAIDU = "com.baidu.BaiduMap"
    private const val PKG_TENCENT = "com.tencent.map"

    private val URL_PATTERN = Pattern.compile("(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")

    var isRewritingClipboard = false

    fun jumpToUrl(context: Context, url: String) {
        var finalUrl = url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            finalUrl = "http://$url"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开该网址", Toast.LENGTH_SHORT).show()
        }
    }

    fun jumpToTaobao(context: Context, text: String) {
        rewriteClipboard(context, text)

        val matcher = URL_PATTERN.matcher(text)
        if (matcher.find()) {
            val extractedUrl = matcher.group()
            launchTaobaoWithUrl(context, extractedUrl)
        } else {
            resolveTaobaoTokenAsync(context, text)
        }
    }

    private fun launchTaobaoWithUrl(context: Context, url: String) {
        if (isAppInstalled(context, PKG_TAOBAO)) {
            try {
                val taobaoUrl = url.replace("https://", "taobao://").replace("http://", "taobao://")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(taobaoUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.setPackage(PKG_TAOBAO)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    jumpToUrl(context, url)
                }
            }
        } else {
            Toast.makeText(context, "未安装淘宝，尝试使用浏览器打开", Toast.LENGTH_SHORT).show()
            jumpToUrl(context, url)
        }
    }

    private fun resolveTaobaoTokenAsync(context: Context, token: String) {
        Thread {
            try {
                // 【核心修改】：从 RuleManager 动态获取 API 网址和密钥
                val ruleManager = RuleManager(context)
                val apiKey = ruleManager.taobaoApiKey
                val baseUrl = ruleManager.taobaoApiUrl

                // 安全校验：如果用户还没填 Key，直接退回到常规的唤起逻辑，不要去请求云端
                if (apiKey.isEmpty() || baseUrl.isEmpty()) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "未配置淘宝解析密钥，尝试常规唤起", Toast.LENGTH_SHORT).show()
                        launchTaobaoNormally(context)
                    }
                    return@Thread
                }

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "正在云端解析淘口令...", Toast.LENGTH_SHORT).show()
                }

                val encodedToken = URLEncoder.encode(token, "UTF-8")
                // 兼容不同接口地址的拼接方式 (处理是否已经带有 ? 符号)
                val apiUrl = if (baseUrl.contains("?")) {
                    "$baseUrl&apikey=$apiKey&tkl=$encodedToken"
                } else {
                    "$baseUrl?apikey=$apiKey&tkl=$encodedToken"
                }

                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                if (connection.responseCode == 200) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    val urlRegex = "\"url\"\\s*:\\s*\"(.*?)\"".toRegex()
                    val matchResult = urlRegex.find(response)

                    if (matchResult != null) {
                        val realUrl = matchResult.groupValues[1].replace("\\/", "/")
                        Handler(Looper.getMainLooper()).post {
                            launchTaobaoWithUrl(context, realUrl)
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "解析不到真实链接，尝试常规唤起", Toast.LENGTH_SHORT).show()
                            launchTaobaoNormally(context)
                        }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "解析服务器维护中，尝试常规唤起", Toast.LENGTH_SHORT).show()
                        launchTaobaoNormally(context)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "网络错误，尝试常规唤起", Toast.LENGTH_SHORT).show()
                    launchTaobaoNormally(context)
                }
            }
        }.start()
    }

    private fun launchTaobaoNormally(context: Context) {
        if (isAppInstalled(context, PKG_TAOBAO)) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(PKG_TAOBAO)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "打开淘宝失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun jumpToJd(context: Context, text: String) {
        rewriteClipboard(context, text)
        if (isAppInstalled(context, PKG_JD)) {
            try {
                val matcher = URL_PATTERN.matcher(text)
                if (matcher.find()) {
                    val url = matcher.group()
                    val jdUrl = url.replace("https://", "openapp.jdmobile://virtual?params={\"category\":\"jump\",\"des\":\"m\",\"url\":\"").plus("\"}")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(jdUrl))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } else {
                    val intent = context.packageManager.getLaunchIntentForPackage(PKG_JD)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                jumpToUrl(context, text)
            }
        } else {
            Toast.makeText(context, "未安装京东", Toast.LENGTH_SHORT).show()
        }
    }

    fun jumpToPdd(context: Context, text: String) {
        rewriteClipboard(context, text)
        if (isAppInstalled(context, PKG_PDD)) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(PKG_PDD)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                jumpToUrl(context, text)
            }
        } else {
            Toast.makeText(context, "未安装拼多多", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMapSearch(context: Context, address: String) {
        try {
            val encodedAddress = URLEncoder.encode(address, "UTF-8")
            val installedMaps = mutableListOf<MapApp>()

            if (isAppInstalled(context, PKG_AMAP)) installedMaps.add(MapApp("高德地图", PKG_AMAP, "androidamap://poi?sourceApplication=SmartClip&keywords=$encodedAddress"))
            if (isAppInstalled(context, PKG_BAIDU)) installedMaps.add(MapApp("百度地图", PKG_BAIDU, "baidumap://map/geocoder?src=SmartClip&address=$encodedAddress"))
            if (isAppInstalled(context, PKG_TENCENT)) installedMaps.add(MapApp("腾讯地图", PKG_TENCENT, "qqmap://map/search?keyword=$encodedAddress"))

            when {
                installedMaps.isEmpty() -> {
                    val uri = Uri.parse("geo:0,0?q=$encodedAddress")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }
                installedMaps.size == 1 -> launchMapApp(context, installedMaps[0].scheme)
                else -> showMapChooserDialog(context, installedMaps)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "打开地图失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun getTargetPackageForCustomRule(context: Context, text: String): String {
        val ruleManager = RuleManager(context)
        val customRules = ruleManager.customRules

        val ruleList = customRules.split(Regex("[\\n|]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .sortedByDescending {
                (if (it.contains(":")) it.substringBefore(":") else it).length
            }

        val flatText = text.replace("\n", " ").replace("\r", " ").replace(Regex("\\s+"), " ")

        for (rule in ruleList) {
            if (rule.contains(":")) {
                val actualRule = rule.substringBefore(":")
                val pkg = rule.substringAfter(":").trim()
                val parts = actualRule.split("*")
                val regexString = parts.joinToString(".*") { Pattern.quote(it) }

                if (Regex(regexString, RegexOption.IGNORE_CASE).containsMatchIn(flatText)) {
                    return pkg
                }
            }
        }
        return ""
    }

    fun jumpToCustomApp(context: Context, text: String) {
        val targetPackage = getTargetPackageForCustomRule(context, text)

        if (targetPackage.isNotEmpty()) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(targetPackage)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "跳转失败：手机上未安装该应用 ($targetPackage)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "打开应用出错", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "未匹配到对应的自定义包名", Toast.LENGTH_SHORT).show()
        }
    }

    fun jumpToAppWithUrl(context: Context, text: String, targetPackage: String) {
        rewriteClipboard(context, text)

        val matcher = URL_PATTERN.matcher(text)
        if (matcher.find()) {
            val url = matcher.group()
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(targetPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (targetPackage == "com.ss.android.ugc.aweme") {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Ignore and fallback
            }
        }

        if (isAppInstalled(context, targetPackage)) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(targetPackage)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "跳转失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "未安装该应用，请先下载", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rewriteClipboard(context: Context, text: String) {
        try {
            isRewritingClipboard = true
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SmartClip_Rewrite", text)
            clipboard.setPrimaryClip(clip)

            Handler(Looper.getMainLooper()).postDelayed({
                isRewritingClipboard = false
            }, 500)

        } catch (e: Exception) {
            e.printStackTrace()
            isRewritingClipboard = false
        }
    }

    private fun launchMapApp(context: Context, scheme: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun showMapChooserDialog(context: Context, maps: List<MapApp>) {
        val mapNames = maps.map { it.name }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("选择地图应用")
            .setItems(mapNames) { dialog, which ->
                launchMapApp(context, maps[which].scheme)
                dialog.dismiss()
            }
            .create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                show()
            }
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private data class MapApp(val name: String, val packageName: String, val scheme: String)
}