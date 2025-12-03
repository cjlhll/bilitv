package com.bili.bilitv.danmaku

import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import com.bilibili.community.service.dm.v1.DmSegMobileReply

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
}
