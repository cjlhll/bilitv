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
    const val PRELOAD_CONCURRENCY = 4
    
    // 首屏预加载数量（4列×4行）
    const val FIRST_SCREEN_PRELOAD_COUNT = 16
    
    // 滚动预加载范围（前后行数）
    const val SCROLL_PRELOAD_ROWS = 2
    
    // 延迟预加载时间（毫秒）
    const val DELAYED_PRELOAD_MS = 500L
    
    // 滚动预加载阈值（还剩多少个item时开始预加载）
    const val SCROLL_PRELOAD_THRESHOLD = 50
    
    // 滚动速度检测间隔（毫秒）
    const val SCROLL_SPEED_CHECK_INTERVAL = 500L
    
    // 快速滚动阈值（每秒滚动超过20个item）
    const val FAST_SCROLL_THRESHOLD = 20
    
    // 内存缓存大小（字节）
    const val MEMORY_CACHE_SIZE_BYTES = 50 * 1024 * 1024 // 50MB
    
    // 磁盘缓存大小（字节）
    const val DISK_CACHE_SIZE_BYTES = 200 * 1024 * 1024 // 200MB
}