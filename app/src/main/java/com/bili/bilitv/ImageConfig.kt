package com.bili.bilitv

import coil.size.Size

/**
 * 图片加载配置类
 * 统一管理图片加载的优化参数
 */
object ImageConfig {
    // 视频封面图片显示尺寸（4:3比例）
    val VIDEO_COVER_SIZE = Size(320, 180)
    
    // 预加载并发数
    const val PRELOAD_CONCURRENCY = 2
    
    // 首屏预加载数量（4列×4行）
    const val FIRST_SCREEN_PRELOAD_COUNT = 16
    
    // 延迟预加载时间（毫秒）
    const val DELAYED_PRELOAD_MS = 500L
    
    // 滚动预加载阈值（还剩多少个item时开始预加载）
    const val SCROLL_PRELOAD_THRESHOLD = 10
    
    // 滚动提前预加载数量（屏幕外提前加载的数量）
    const val SCROLL_PRELOAD_AHEAD_COUNT = 8
    
    // 内存缓存大小（字节）
    const val MEMORY_CACHE_SIZE_BYTES = 50 * 1024 * 1024 // 50MB
    
    // 磁盘缓存大小（字节）
    const val DISK_CACHE_SIZE_BYTES = 200 * 1024 * 1024 // 200MB
}