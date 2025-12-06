package com.bili.bilitv

import android.view.KeyEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KeyEventThrottle(
    private val throttleMs: Long = 180L
) {
    private val mutex = Mutex()
    private var lastEventTime = 0L
    private var pendingDirection: Direction? = null
    
    enum class Direction {
        UP, DOWN, LEFT, RIGHT
    }
    
    suspend fun shouldProcess(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Direction.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Direction.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Direction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.RIGHT
            else -> return false
        }
        
        return mutex.withLock {
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - lastEventTime
            
            if (elapsed >= throttleMs) {
                lastEventTime = currentTime
                pendingDirection = direction
                true
            } else {
                pendingDirection = direction
                false
            }
        }
    }
    
    suspend fun getPendingDirection(): Direction? = mutex.withLock {
        pendingDirection.also { pendingDirection = null }
    }
    
    fun reset() {
        lastEventTime = 0L
        pendingDirection = null
    }
}

