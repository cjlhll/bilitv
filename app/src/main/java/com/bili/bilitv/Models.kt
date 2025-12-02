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
