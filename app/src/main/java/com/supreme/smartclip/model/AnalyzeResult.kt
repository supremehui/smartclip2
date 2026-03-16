package com.supreme.smartclip.model

/**
 * 内容分析结果封装类
 */
data class AnalyzeResult(
    val type: ContentType,     // 识别出的类型
    val originalText: String   // 用户复制的原始文本
)