package com.supreme.smartclip.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.supreme.smartclip.analyzer.ContentAnalyzer
import com.supreme.smartclip.manager.FloatingManager
import com.supreme.smartclip.model.AnalyzeResult
import com.supreme.smartclip.model.ContentType
import com.supreme.smartclip.utils.AppJumpUtils

class ClipboardService : AccessibilityService() {

    private lateinit var clipboardManager: ClipboardManager
    private lateinit var floatingManager: FloatingManager
    private lateinit var contentAnalyzer: ContentAnalyzer
    private val mainHandler = Handler(Looper.getMainLooper())

    // 【核心修复】：实行最严格的内容锁机制，不再依赖时间冷却
    private var lastContent: String = ""

    private var activePackageName: String = ""

    // 【新增】：用于合并短时间内并发的多次复制通知
    private var pendingReadRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        floatingManager = FloatingManager(this)
        contentAnalyzer = ContentAnalyzer(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (AppJumpUtils.isRewritingClipboard) {
            return
        }

        event.packageName?.let {
            val pkg = it.toString()
            if (pkg != "android" && !pkg.contains("systemui") && !pkg.contains("inputmethod")) {
                activePackageName = pkg
            }
        }

        val eventText = event.text.joinToString(" ")

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val nodeText = event.source?.text?.toString() ?: ""
                val nodeDesc = event.source?.contentDescription?.toString() ?: ""

                if (eventText.contains("复制") || eventText.contains("拷贝") ||
                    nodeText.contains("复制") || nodeDesc.contains("复制")) {
                    triggerClipboardRead(activePackageName)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (eventText.contains("已复制") || eventText.contains("复制成功") || eventText.contains("Copied")) {
                    triggerClipboardRead(activePackageName)
                }
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                if (eventText.contains("已复制") || eventText.contains("复制成功") || eventText.contains("拷贝")) {
                    triggerClipboardRead(activePackageName)
                }
            }
        }
    }

    /**
     * 【新增】：事件合并防抖拦截器
     * 如果短时间内（300ms内）系统和App连续发来多个“已复制”通知，
     * 我们只保留最后一次，把前面的全部取消掉，防止并发读取。
     */
    private fun triggerClipboardRead(sourcePackage: String) {
        pendingReadRunnable?.let { mainHandler.removeCallbacks(it) }

        pendingReadRunnable = Runnable {
            performGhostRead(sourcePackage)
        }

        // 延迟 300ms，让所有的并发通知“飞一会儿”，然后一并处理
        mainHandler.postDelayed(pendingReadRunnable!!, 300)
    }

    private fun performGhostRead(sourcePackage: String) {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                alpha = 0f
            }

            val focusView = View(this)
            wm.addView(focusView, params)
            focusView.requestFocus()

            mainHandler.postDelayed({
                checkClipboard(sourcePackage)
                try { wm.removeView(focusView) } catch (e: Exception) {}
            }, 250)

        } catch (e: Exception) {
            e.printStackTrace()
            checkClipboard(sourcePackage)
        }
    }

    private fun checkClipboard(sourcePackage: String) {
        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val textItem = clipData.getItemAt(0).text
                    if (textItem != null) {
                        val currentContent = textItem.toString().trim()

                        // 【核心修复】：执行你提出的终极对比检验
                        // 只要本次复制的内容和上次的一模一样，直接丢弃，绝不弹窗！
                        if (currentContent.isNotEmpty() && currentContent != lastContent) {
                            lastContent = currentContent // 更新记录
                            processContent(currentContent, sourcePackage)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processContent(text: String, sourcePackage: String) {
        Thread {
            val result = contentAnalyzer.analyze(text)

            if (result.type != ContentType.UNKNOWN) {
                if (shouldShowBubble(result, sourcePackage)) {
                    mainHandler.post {
                        floatingManager.showBubble(result)
                    }
                }
            }
        }.start()
    }

    private fun shouldShowBubble(result: AnalyzeResult, sourcePackage: String): Boolean {
        if (sourcePackage.isEmpty()) return true

        val targetPackage = when (result.type) {
            ContentType.URL, ContentType.ADDRESS -> return true
            ContentType.SHOPPING_TAOBAO -> "com.taobao.taobao"
            ContentType.SHOPPING_JD -> "com.jingdong.app.mall"
            ContentType.SHOPPING_PDD -> "com.xunmeng.pinduoduo"
            ContentType.APP_DOUYIN -> "com.ss.android.ugc.aweme"
            ContentType.APP_XIANYU -> "com.taobao.idlefish"
            ContentType.APP_BAIDU_PAN -> "com.baidu.netdisk"
            ContentType.APP_QUARK -> "com.quark.browser"
            ContentType.CUSTOM -> AppJumpUtils.getTargetPackageForCustomRule(this, result.originalText)
            ContentType.UNKNOWN -> return false
        }

        return targetPackage != sourcePackage
    }

    override fun onInterrupt() {}
}