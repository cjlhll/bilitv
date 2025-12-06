package com.bili.bilitv

import android.view.KeyEvent

/**
 * 限制按键事件频率，防止快速滚动导致ANR
 * 主要用于限制遥控器长按时的事件分发速度
 */
class KeyEventThrottle(
    private val throttleMs: Long = 100L
) {
    private var lastEventTime = 0L

    /**
     * 判断是否允许分发该按键事件
     * @param event 按键事件
     * @return true: 允许分发; false: 拦截事件
     */
    fun allowEvent(event: KeyEvent): Boolean {
        // 不限制按键抬起事件
        if (event.action != KeyEvent.ACTION_DOWN) return true

        // 只限制方向键
        val isDirectionKey = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> false
        }

        if (!isDirectionKey) return true

        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastEventTime

        return if (elapsed >= throttleMs) {
            lastEventTime = currentTime
            true
        } else {
            false
        }
    }
    
    fun reset() {
        lastEventTime = 0L
    }
}

