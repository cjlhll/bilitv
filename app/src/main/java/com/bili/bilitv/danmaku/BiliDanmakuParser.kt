package com.bili.bilitv.danmaku

import com.bilibili.community.service.dm.v1.DanmakuElem
import com.bilibili.community.service.dm.v1.DmSegMobileReply
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.model.IDanmakus
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.parser.IDataSource
import master.flame.danmaku.danmaku.util.DanmakuUtils

class BiliDanmakuParser : BaseDanmakuParser() {

    override fun parse(): IDanmakus {
        val danmakus = Danmakus()
        if (mDataSource != null && mDataSource is BiliDanmakuDataSource) {
            val source = mDataSource as BiliDanmakuDataSource
            val reply = source.data()

            reply?.elemsList?.forEach { elem ->
                val item = createDanmaku(elem, mContext)
                if (item != null) {
                    danmakus.addItem(item)
                }
            }
        }
        return danmakus
    }

    private fun createDanmaku(elem: DanmakuElem, context: DanmakuContext): BaseDanmaku? {
        val type = when (elem.mode) {
            1, 2, 3 -> BaseDanmaku.TYPE_SCROLL_RL
            4 -> BaseDanmaku.TYPE_FIX_BOTTOM
            5 -> BaseDanmaku.TYPE_FIX_TOP
            6 -> BaseDanmaku.TYPE_SCROLL_LR
            7 -> BaseDanmaku.TYPE_SPECIAL
            else -> BaseDanmaku.TYPE_SCROLL_RL
        }

        val item = context.mDanmakuFactory.createDanmaku(type, context) ?: return null
        
        // Progress is in milliseconds in protobuf
        item.time = elem.progress.toLong()
        
        // Fontsize mapping
        val textSize = if (elem.fontsize > 0) elem.fontsize else 25
        item.textSize = textSize * (context.displayer.density - 0.6f)
        
        // Color (ensure alpha is FF)
        item.textColor = (elem.color.toInt() or -16777216) 
        item.textShadowColor = if (item.textColor <= -1) 0 else -16777216
        
        DanmakuUtils.fillText(item, elem.content)
        item.index = 0
        item.flags = context.mGlobalFlagValues
        item.timer = mTimer
        return item
    }
}

class BiliDanmakuDataSource(private val reply: DmSegMobileReply?) : IDataSource<DmSegMobileReply?> {
    override fun release() {
    }

    override fun data(): DmSegMobileReply? {
        return reply
    }
}
