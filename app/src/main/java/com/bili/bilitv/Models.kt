package com.bili.bilitv

import kotlinx.serialization.Serializable

@Serializable
data class WbiImg(
    val img_url: String,
    val sub_url: String
)

/**
 * 直播间播放信息
 */
data class LivePlayInfo(
    val roomId: Int,
    val title: String,
    val uname: String,
    val playUrl: String,
    val format: String = "flv"
) {
    /**
     * 转换为VideoPlayInfo以便复用VideoPlayerScreen
     */
    fun toVideoPlayInfo(): VideoPlayInfo {
        return VideoPlayInfo(
            bvid = "live_${roomId}",
            cid = roomId.toLong(),
            quality = 10000,
            format = format,
            duration = Long.MAX_VALUE, // 直播没有固定时长
            videoUrl = playUrl,
            audioUrl = null
        )
    }
}

/**
 * 直播流信息响应
 */
@Serializable
data class LiveStreamResponse(
    val code: Int,
    val message: String,
    val data: LiveStreamData? = null
)

@Serializable
data class LiveStreamData(
    val room_id: Int,
    val live_status: Int,
    val playurl_info: LivePlayUrlInfo? = null
)

@Serializable
data class LivePlayUrlInfo(
    val playurl: LivePlayUrl? = null
)

@Serializable
data class LivePlayUrl(
    val stream: List<LiveStream>? = null
)

@Serializable
data class LiveStream(
    val protocol_name: String,
    val format: List<LiveFormat>? = null
)

@Serializable
data class LiveFormat(
    val format_name: String,
    val codec: List<LiveCodec>? = null
)

@Serializable
data class LiveCodec(
    val codec_name: String,
    val current_qn: Int,
    val base_url: String,
    val url_info: List<LiveUrlInfo>? = null
)

@Serializable
data class LiveUrlInfo(
    val host: String,
    val extra: String
)

@Serializable
data class LiveStreamUrl(
    val url: String,
    val backup_url: List<String>? = null,
    val length: Long? = null,
    val order: Int? = null,
    val stream_type: Int? = null,
    val protocol: String? = null,
    val format: String? = null,
    val codec: String? = null,
    val current_qn: Int? = null,
    val accept_qn: List<Int>? = null
)

@Serializable
data class BangumiTabResponse(
    val code: Int,
    val message: String,
    val data: BangumiTabData? = null
)

@Serializable
data class BangumiTabData(
    val has_next: Int,
    val modules: List<BangumiModule>,
    val next_cursor: String? = null
)

@Serializable
data class BangumiModule(
    val module_id: Int,
    val title: String,
    val style: String,
    val items: List<BangumiModuleItem> = emptyList(),
    val headers: List<BangumiModuleHeader> = emptyList(),
    val size: Int = 0,
    val attr: BangumiModuleAttr? = null
)

@Serializable
data class BangumiModuleHeader(
    val title: String,
    val url: String? = null
)

@Serializable
data class BangumiModuleAttr(
    val auto: Int = 0,
    val follow: Int = 0,
    val header: Int = 0,
    val random: Int = 0,
    val show_timeline: Int = 0
)

@Serializable
data class BangumiModuleItem(
    val season_id: Long,
    val title: String,
    val cover: String,
    val desc: String? = null,
    val link: String? = null,
    val season_type: Int = 0,
    val badge_info: BangumiItemBadgeInfo? = null,
    val bottom_right_badge: BangumiItemBadge? = null,
    val bottom_left_badge: BangumiItemBadge? = null,
    val stat: BangumiItemStat? = null,
    val oid: Long = 0,
    val link_type: Int = 0,
    val link_value: Long = 0,
    val is_preview: Int = 0,
    val sub_title: String? = null,
    val episode_id: Long = 0,
    val desc_type: Int = 0,
    val index_show: String? = null,
    val new_ep: BangumiNewEp? = null
)

@Serializable
data class BangumiNewEp(
    val index_show: String? = null,
    val id: Long = 0
)

@Serializable
data class BangumiItemBadgeInfo(
    val text: String,
    val bg_color: String? = null,
    val bg_color_night: String? = null,
    val text_color: String? = null,
    val text_color_night: String? = null,
    val img: String? = null,
    val text_size: Int = 0
)

@Serializable
data class BangumiItemBadge(
    val text: String? = null,
    val img: String? = null,
    val text_size: Int = 0
)

@Serializable
data class BangumiItemStat(
    val follow: Long = 0,
    val view: Long = 0,
    val danmaku: Long = 0,
    val follow_view: String? = null
)

@Serializable
data class HistoryResponse(
    val code: Int,
    val message: String,
    val ttl: Int,
    val data: HistoryData? = null
)

@Serializable
data class HistoryData(
    val cursor: HistoryCursor,
    val tab: List<HistoryTab>,
    val list: List<HistoryItem> = emptyList()
)

@Serializable
data class HistoryCursor(
    val max: Long,
    val view_at: Long,
    val business: String? = null,
    val ps: Int
)

@Serializable
data class HistoryTab(
    val type: String,
    val name: String
)

@Serializable
data class HistoryItem(
    val title: String,
    val long_title: String = "",
    val cover: String = "",
    val covers: List<String>? = null,
    val uri: String = "",
    val history: HistoryDetail,
    val videos: Int = 0,
    val author_name: String = "",
    val author_face: String = "",
    val author_mid: Long = 0,
    val view_at: Long,
    val progress: Long = 0,
    val badge: String = "",
    val show_title: String = "",
    val duration: Long = 0,
    val new_desc: String = "",
    val is_finish: Int = 0,
    val is_fav: Int = 0,
    val kid: Long = 0,
    val tag_name: String = "",
    val live_status: Int = 0
)

@Serializable
data class HistoryDetail(
    val oid: Long,
    val epid: Long = 0,
    val bvid: String = "",
    val page: Int = 0,
    val cid: Long = 0,
    val part: String = "",
    val business: String,
    val dt: Int = 0
)
