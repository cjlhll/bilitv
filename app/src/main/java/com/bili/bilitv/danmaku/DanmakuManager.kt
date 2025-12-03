package com.bili.bilitv.danmaku

import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import com.bilibili.community.service.dm.v1.DmSegMobileReply
import master.flame.danmaku.danmaku.util.DanmakuUtils
import com.bili.bilitv.danmaku.live.LiveDanmakuItem // Import new data class

class DanmakuManager(private val danmakuView: IDanmakuView) {

    private val danmakuContext: DanmakuContext = DanmakuContext.create()
    private var parser: BaseDanmakuParser? = null

    init {
        initDanmaku()
    }

    private fun initDanmaku() {
        danmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f)
            .setDuplicateMergingEnabled(false)
            .setScrollSpeedFactor(1.2f)
            .setScaleTextSize(1.2f)
        
        // Ensure it doesn't block focus for TV
        // Note: This is usually handled in the View layout attributes (focusable=false)
        
        danmakuView.setCallback(object : DrawHandler.Callback {
            override fun prepared() {
                danmakuView.start()
            }

            override fun updateTimer(timer: DanmakuTimer?) {}
            override fun danmakuShown(danmaku: BaseDanmaku?) {}
            override fun drawingFinished() {}
        })
        
        danmakuView.enableDanmakuDrawingCache(true)
    }

    fun loadDanmaku(data: DmSegMobileReply) {
        parser = BiliDanmakuParser().apply {
            load(BiliDanmakuDataSource(data))
        }
        danmakuView.prepare(parser, danmakuContext)
        danmakuView.show()
    }

    fun addLiveDanmaku(item: LiveDanmakuItem) {
        if (!danmakuView.isPrepared) return

        val danmaku = danmakuContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL, danmakuContext)
        // Use current time from danmakuView's timer
        danmaku.time = danmakuView.currentTime
        danmaku.textSize = 25f * (danmakuContext.displayer.density - 0.6f) // Approximate scaling
        danmaku.textColor = item.color.toInt() or -16777216 // Ensure alpha is FF
        danmaku.textShadowColor = if (danmaku.textColor <= -1) 0 else -16777216
        DanmakuUtils.fillText(danmaku, "${item.userName}: ${item.text}")
        danmakuView.addDanmaku(danmaku)
    }

    fun seekTo(time: Long) {
        danmakuView.seekTo(time)
    }

    fun pause() {
        if (danmakuView.isPrepared) {
            danmakuView.pause()
        }
    }

    fun resume() {
        if (danmakuView.isPrepared && danmakuView.isPaused) {
            danmakuView.resume()
        }
    }
    
    fun release() {
        danmakuView.release()
    }
    
    fun isPrepared(): Boolean = danmakuView.isPrepared

    // Expose danmakuContext for direct DanmakuView preparation for live streams
    fun getDanmakuContext(): DanmakuContext = danmakuContext
}
