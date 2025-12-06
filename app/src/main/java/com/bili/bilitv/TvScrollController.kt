package com.bili.bilitv

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import kotlinx.coroutines.*

class TvFocusThrottle {
    private var lastFocusChangeTime = 0L
    private val throttleMs = 90L
    
    fun shouldProcessFocusChange(): Boolean {
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastFocusChangeTime
        
        return if (elapsed >= throttleMs) {
            lastFocusChangeTime = currentTime
            true
        } else {
            false
        }
    }
    
    fun reset() {
        lastFocusChangeTime = 0L
    }
}

@Composable
fun rememberTvFocusThrottle(): TvFocusThrottle {
    return remember { TvFocusThrottle() }
}

