package com.bili.bilitv

import coil.size.Size

object ImageConfig {
    val VIDEO_COVER_SIZE = Size(320, 180)
    
    const val PRELOAD_CONCURRENCY = 4
    
    const val FIRST_SCREEN_PRELOAD_COUNT = 20
    
    const val DELAYED_PRELOAD_MS = 300L
    
    const val SCROLL_PRELOAD_THRESHOLD = 8
    
    const val SCROLL_PRELOAD_AHEAD_COUNT = 12
    
    const val MEMORY_CACHE_SIZE_BYTES = 128 * 1024 * 1024
    
    const val DISK_CACHE_SIZE_BYTES = 500 * 1024 * 1024
}