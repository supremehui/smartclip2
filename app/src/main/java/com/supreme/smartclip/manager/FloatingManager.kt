package com.supreme.smartclip.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.supreme.smartclip.R
import com.supreme.smartclip.model.AnalyzeResult
import com.supreme.smartclip.model.ContentType
import com.supreme.smartclip.utils.AppJumpUtils
import kotlin.math.abs

class FloatingManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences = context.getSharedPreferences("smartclip_bubble_prefs", Context.MODE_PRIVATE)

    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable { hideBubbleInternal(null) }

    /**
     * 对外暴露的显示方法：处理“新消息顶替旧消息”的逻辑
     */
    fun showBubble(result: AnalyzeResult) {
        mainHandler.removeCallbacks(autoDismissRunnable)

        if (floatingView != null && floatingView?.windowToken != null) {
            // 如果当前有悬浮窗，先带动画消失，消失后再展示新的
            hideBubbleInternal {
                showBubbleActual(result)
            }
        } else {
            showBubbleActual(result)
        }
    }

    /**
     * 实际的创建与弹出逻辑
     */
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showBubbleActual(result: AnalyzeResult) {
        // 计算状态栏高度及默认 Y 坐标 (状态栏高度 + 8dp避让)
        val statusBarHeight = getStatusBarHeight()
        val defaultY = statusBarHeight + dpToPx(8)

        // 读取记忆位置，若没有则居中靠上
        val savedX = prefs.getInt("bubble_x", 0)
        val savedY = prefs.getInt("bubble_y", defaultY)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = savedX
            // 确保拖动保存的 Y 不会遮挡状态栏
            y = if (savedY < defaultY) defaultY else savedY
        }

        floatingView = LayoutInflater.from(context).inflate(R.layout.layout_floating_bubble, null)

        val tvAction = floatingView?.findViewById<TextView>(R.id.tv_action)
        val ivIcon = floatingView?.findViewById<ImageView>(R.id.iv_icon)

        // 强制图标为纯白色以契合暗色毛玻璃
        ivIcon?.setColorFilter(Color.WHITE)

        bindViewData(result, tvAction, ivIcon)
        setDragAndClickListener(floatingView, result)

        try {
            windowManager.addView(floatingView, layoutParams)

            // 【动画：入场】从顶部滑入 + 淡入 + 弹性放大
            floatingView?.alpha = 0f
            floatingView?.scaleX = 0.8f
            floatingView?.scaleY = 0.8f
            floatingView?.translationY = -150f // 从更上方滑下

            floatingView?.animate()
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.translationY(0f)
                ?.setDuration(300)
                ?.setInterpolator(OvershootInterpolator(1.2f)) // Q弹效果
                ?.start()

            // 4秒无操作自动淡出
            mainHandler.postDelayed(autoDismissRunnable, 4000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 隐藏悬浮窗（带动画）
     */
    private fun hideBubbleInternal(onHidden: (() -> Unit)?) {
        val viewToRemove = floatingView ?: return
        floatingView = null // 立刻置空，防止快速连按导致异常

        viewToRemove.animate()
            ?.alpha(0f)
            ?.scaleX(0.9f)
            ?.scaleY(0.9f)
            ?.setDuration(200)
            ?.withEndAction {
                try {
                    windowManager.removeView(viewToRemove)
                    onHidden?.invoke()
                } catch (e: Exception) {}
            }?.start()
    }

    private fun bindViewData(result: AnalyzeResult, tvAction: TextView?, ivIcon: ImageView?) {
        when (result.type) {
            ContentType.URL -> { tvAction?.text = "打开网页"; ivIcon?.setImageResource(android.R.drawable.ic_menu_search) }
            ContentType.SHOPPING_TAOBAO -> { tvAction?.text = "去淘宝"; ivIcon?.setImageResource(android.R.drawable.ic_menu_agenda) }
            ContentType.SHOPPING_JD -> { tvAction?.text = "去京东"; ivIcon?.setImageResource(android.R.drawable.ic_menu_agenda) }
            ContentType.SHOPPING_PDD -> { tvAction?.text = "去拼多多"; ivIcon?.setImageResource(android.R.drawable.ic_menu_agenda) }
            ContentType.ADDRESS -> { tvAction?.text = "地图导航"; ivIcon?.setImageResource(android.R.drawable.ic_menu_mapmode) }
            ContentType.CUSTOM -> { tvAction?.text = "打开应用"; ivIcon?.setImageResource(android.R.drawable.ic_menu_manage) }
            ContentType.APP_DOUYIN -> { tvAction?.text = "去抖音"; ivIcon?.setImageResource(android.R.drawable.ic_media_play) }
            ContentType.APP_XIANYU -> { tvAction?.text = "去闲鱼"; ivIcon?.setImageResource(android.R.drawable.ic_menu_gallery) }
            ContentType.APP_BAIDU_PAN -> { tvAction?.text = "打开网盘"; ivIcon?.setImageResource(android.R.drawable.ic_menu_save) }
            ContentType.APP_QUARK -> { tvAction?.text = "去夸克"; ivIcon?.setImageResource(android.R.drawable.ic_menu_compass) }
            ContentType.UNKNOWN -> {}
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setDragAndClickListener(view: View?, result: AnalyzeResult) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false

                    // 按下时移除自动消失定时器，并触发缩小（按下反馈）
                    mainHandler.removeCallbacks(autoDismissRunnable)
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isDragging = true
                        layoutParams?.x = initialX + deltaX
                        layoutParams?.y = initialY + deltaY
                        windowManager.updateViewLayout(view, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 抬起时恢复原始大小
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                    if (!isDragging && event.action == MotionEvent.ACTION_UP) {
                        // 如果没有拖动，认为是点击
                        hideBubbleInternal(null)
                        executeJump(result)
                    } else {
                        // 如果是拖动结束，保存新位置并重启4秒自动消失定时器
                        prefs.edit()
                            .putInt("bubble_x", layoutParams?.x ?: 0)
                            .putInt("bubble_y", layoutParams?.y ?: 0)
                            .apply()
                        mainHandler.postDelayed(autoDismissRunnable, 4000)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun executeJump(result: AnalyzeResult) {
        when (result.type) {
            ContentType.URL -> AppJumpUtils.jumpToUrl(context, result.originalText)
            ContentType.SHOPPING_TAOBAO -> AppJumpUtils.jumpToTaobao(context, result.originalText)
            ContentType.SHOPPING_JD -> AppJumpUtils.jumpToJd(context, result.originalText)
            ContentType.SHOPPING_PDD -> AppJumpUtils.jumpToPdd(context, result.originalText)
            ContentType.ADDRESS -> AppJumpUtils.openMapSearch(context, result.originalText)
            ContentType.CUSTOM -> AppJumpUtils.jumpToCustomApp(context, result.originalText)
            ContentType.APP_DOUYIN -> AppJumpUtils.jumpToAppWithUrl(context, result.originalText, "com.ss.android.ugc.aweme")
            ContentType.APP_XIANYU -> AppJumpUtils.jumpToAppWithUrl(context, result.originalText, "com.taobao.idlefish")
            ContentType.APP_BAIDU_PAN -> AppJumpUtils.jumpToAppWithUrl(context, result.originalText, "com.baidu.netdisk")
            ContentType.APP_QUARK -> AppJumpUtils.jumpToAppWithUrl(context, result.originalText, "com.quark.browser")
            ContentType.UNKNOWN -> {}
        }
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return if (result > 0) result else dpToPx(30)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}