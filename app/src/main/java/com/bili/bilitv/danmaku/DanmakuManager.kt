package com.bili.bilitv.danmaku

import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import com.bilibili.community.service.dm.v1.DmSegMobileReply
import com.bilibili.community.service.dm.v1.DanmakuElem
import master.flame.danmaku.danmaku.util.DanmakuUtils
import com.bili.bilitv.danmaku.live.LiveDanmakuItem // Import new data class

class DanmakuManager(private val danmakuView: IDanmakuView) {

    private val danmakuContext: DanmakuContext = DanmakuContext.create()
    private var parser: BaseDanmakuParser? = null
    private val loadedSegments = mutableSetOf<Int>() // 跟踪已加载的分段

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

    fun loadDanmaku(data: DmSegMobileReply, segmentIndex: Int = 1) {
        if (loadedSegments.isEmpty()) {
            // 首次加载，准备弹幕视图
            parser = BiliDanmakuParser().apply {
                load(BiliDanmakuDataSource(data))
            }
            danmakuView.prepare(parser, danmakuContext)
            danmakuView.show()
        } else {
            // 追加弹幕到已存在的弹幕池
            addDanmakuSegment(data)
        }
        loadedSegments.add(segmentIndex)
    }
    
    /**
     * 添加弹幕分段到已存在的弹幕池
     */
    private fun addDanmakuSegment(data: DmSegMobileReply) {
        if (!danmakuView.isPrepared) return
        
        data.elemsList.forEach { elem ->
            val danmaku = createDanmakuFromElem(elem)
            if (danmaku != null) {
                danmakuView.addDanmaku(danmaku)
            }
        }
    }
    
    /**
     * 从DanmakuElem创建弹幕对象
     */
    private fun createDanmakuFromElem(elem: DanmakuElem): BaseDanmaku? {
        val type = when (elem.mode) {
            1, 2, 3 -> BaseDanmaku.TYPE_SCROLL_RL
            4 -> BaseDanmaku.TYPE_FIX_BOTTOM
            5 -> BaseDanmaku.TYPE_FIX_TOP
            6 -> BaseDanmaku.TYPE_SCROLL_LR
            7 -> BaseDanmaku.TYPE_SPECIAL
            else -> BaseDanmaku.TYPE_SCROLL_RL
        }

        val item = danmakuContext.mDanmakuFactory.createDanmaku(type, danmakuContext) ?: return null
        
        // Progress is in milliseconds in protobuf
        item.time = elem.progress.toLong()
        
        // Fontsize mapping
        val textSize = if (elem.fontsize > 0) elem.fontsize else 25
        item.textSize = textSize * (danmakuContext.displayer.density - 0.6f)
        
        // Color (ensure alpha is FF)
        item.textColor = (elem.color.toInt() or -16777216) 
        // 给弹幕文字添加黑灰色描边，以便更清晰可见
        item.textShadowColor = 0xFF333333.toInt()
        
        DanmakuUtils.fillText(item, elem.content)
        item.index = 0
        item.flags = danmakuContext.mGlobalFlagValues
        // 注意：item.timer 不需要设置，DanmakuView会自动管理
        return item
    }
    
    /**
     * 检查某个分段是否已加载
     */
    fun isSegmentLoaded(segmentIndex: Int): Boolean {
        return loadedSegments.contains(segmentIndex)
    }
    
    /**
     * 清空已加载分段记录
     */
    fun clearLoadedSegments() {
        loadedSegments.clear()
    }

    fun addLiveDanmaku(item: LiveDanmakuItem) {
        if (!danmakuView.isPrepared) return

        val danmaku = danmakuContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL, danmakuContext)
        // Use current time from danmakuView's timer
        danmaku.time = danmakuView.currentTime
        danmaku.textSize = 25f * (danmakuContext.displayer.density - 0.6f) // Approximate scaling
        danmaku.textColor = item.color.toInt() or -16777216 // Ensure alpha is FF
        // 给直播弹幕添加黑灰色描边
        danmaku.textShadowColor = 0xFF333333.toInt()
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
        loadedSegments.clear()
    }
    
    fun isPrepared(): Boolean = danmakuView.isPrepared

    // Expose danmakuContext for direct DanmakuView preparation for live streams
    fun getDanmakuContext(): DanmakuContext = danmakuContext
}
