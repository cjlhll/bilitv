package com.bili.bilitv.danmaku.live

import com.bili.bilitv.danmaku.DanmakuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.util.DanmakuUtils

class DanmakuLiveManager(
    private val danmakuManager: DanmakuManager
) {
    private var client: LiveDanmakuWebSocketClient? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun start(roomId: Long) {
        scope.launch {
            val info = DanmakuLiveRepository.getDanmuInfo(roomId) ?: return@launch
            
            client = LiveDanmakuWebSocketClient(roomId, info.token, info.host_list)
            client?.connect()
            
            launch {
                client?.danmakuFlow?.collect { item ->
                    addDanmaku(item)
                }
            }
        }
    }

    private fun addDanmaku(item: LiveDanmakuItem) {
        // Accessing DanmakuContext from DanmakuManager is tricky because it's private.
        // I should expose a method in DanmakuManager to add a single danmaku.
        // For now, I will modify DanmakuManager to support this.
        danmakuManager.addLiveDanmaku(item)
    }

    fun stop() {
        client?.close()
    }
}
