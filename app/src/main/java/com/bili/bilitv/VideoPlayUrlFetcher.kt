package com.bili.bilitv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 视频播放地址响应
 */
@Serializable
data class PlayUrlResponse(
    val code: Int,
    val message: String,
    val ttl: Int,
    val data: PlayUrlData? = null
)

@Serializable
data class PlayUrlData(
    val quality: Int, // 清晰度标识
    val format: String, // 视频格式
    val timelength: Long, // 视频长度（毫秒）
    val accept_quality: List<Int>, // 支持的清晰度列表
    val accept_description: List<String>, // 清晰度描述
    val video_codecid: Int, // 视频编码ID
    val durl: List<DurlItem>? = null, // MP4/FLV格式的视频流
    val dash: DashData? = null // DASH格式的视频流
)

/**
 * MP4/FLV格式视频流
 */
@Serializable
data class DurlItem(
    val order: Int,
    val length: Long,
    val size: Long,
    val url: String,
    val backup_url: List<String>? = null
)

/**
 * DASH格式视频流
 */
@Serializable
data class DashData(
    val duration: Int, // 视频时长（秒）
    val video: List<DashVideo>, // 视频流列表
    val audio: List<DashAudio>? = null, // 音频流列表
    val dolby: DolbyData? = null, // 杜比音效
    val flac: FlacData? = null // 无损音轨
)

@Serializable
data class DashVideo(
    val id: Int, // 清晰度代码
    val baseUrl: String, // 视频流URL
    val base_url: String? = null,
    val backupUrl: List<String>? = null,
    val backup_url: List<String>? = null,
    val bandwidth: Long, // 所需带宽
    val mimeType: String,
    val mime_type: String? = null,
    val codecs: String, // 编码格式
    val width: Int,
    val height: Int,
    val frameRate: String,
    val frame_rate: String? = null,
    val codecid: Int // 编码ID
)

@Serializable
data class DashAudio(
    val id: Int, // 音质代码
    val baseUrl: String,
    val base_url: String? = null,
    val backupUrl: List<String>? = null,
    val backup_url: List<String>? = null,
    val bandwidth: Long,
    val mimeType: String,
    val mime_type: String? = null,
    val codecs: String
)

@Serializable
data class DolbyData(
    val type: Int,
    val audio: List<DashAudio>? = null
)

@Serializable
data class FlacData(
    val display: Boolean,
    val audio: DashAudio? = null
)

/**
 * 视频播放信息
 */
data class VideoPlayInfo(
    val bvid: String,
    val cid: Long,
    val videoUrl: String, // 视频流URL
    val audioUrl: String? = null, // 音频流URL（DASH格式需要）
    val quality: Int, // 当前清晰度
    val format: String, // 格式（mp4/dash）
    val duration: Long // 时长（秒）
)

/**
 * 视频播放地址获取器
 */
object VideoPlayUrlFetcher {
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * 获取视频播放地址
     * @param bvid 视频BV号
     * @param cid 视频CID
     * @param qn 清晰度 (默认80=1080P)
     * @param fnval 格式标识 (默认4048=所有DASH)
     * @return 视频播放信息，失败返回null
     */
    suspend fun fetchPlayUrl(
        bvid: String,
        cid: Long,
        qn: Int = 80, // 默认1080P
        fnval: Int = 4048 // 默认DASH格式，获取所有可用流
    ): VideoPlayInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.bilibili.com/x/player/playurl?" +
                        "bvid=$bvid&cid=$cid&qn=$qn&fnval=$fnval&fnver=0&fourk=1"
                
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.bilibili.com")
                
                // 添加Cookie（如果已登录）
                val cookieString = SessionManager.getCookieString()
                if (cookieString != null) {
                    requestBuilder.header("Cookie", cookieString)
                    Log.d("BiliTV", "Fetching play URL with Cookie for bvid=$bvid, cid=$cid")
                } else {
                    Log.d("BiliTV", "Fetching play URL without Cookie for bvid=$bvid, cid=$cid")
                }
                
                val request = requestBuilder.build()
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        Log.d("BiliTV", "Play URL Response: ${responseBody.take(500)}...")
                        val playUrlResponse = json.decodeFromString<PlayUrlResponse>(responseBody)
                        
                        if (playUrlResponse.code == 0 && playUrlResponse.data != null) {
                            return@withContext parsePlayUrlData(bvid, cid, playUrlResponse.data)
                        } else {
                            Log.e("BiliTV", "Play URL API error: ${playUrlResponse.message}")
                        }
                    }
                } else {
                    Log.e("BiliTV", "Play URL HTTP error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("BiliTV", "Play URL fetch error: ${e.message}", e)
            }
            null
        }
    }
    
    /**
     * 解析播放地址数据
     */
    private fun parsePlayUrlData(bvid: String, cid: Long, data: PlayUrlData): VideoPlayInfo? {
        Log.d("BiliTV", "Available qualities: ${data.accept_quality}")
        Log.d("BiliTV", "Current quality: ${data.quality}")
        
        // 优先使用DASH格式
        data.dash?.let { dash ->
            Log.d("BiliTV", "DASH video streams: ${dash.video.map { "id=${it.id}, width=${it.width}x${it.height}" }}")
            
            // 选择最高清晰度的视频流（id值最大的）
            val videoStream = dash.video.maxByOrNull { it.id } ?: dash.video.firstOrNull()
            val audioStream = dash.audio?.firstOrNull() // 获取第一个音频流
            
            if (videoStream != null) {
                val videoUrl = videoStream.base_url ?: videoStream.baseUrl
                val audioUrl = audioStream?.let { it.base_url ?: it.baseUrl }
                
                Log.d("BiliTV", "Selected DASH video stream - Quality ID: ${videoStream.id}, Resolution: ${videoStream.width}x${videoStream.height}")
                Log.d("BiliTV", "DASH format - Video: $videoUrl")
                Log.d("BiliTV", "DASH format - Audio: $audioUrl")
                
                return VideoPlayInfo(
                    bvid = bvid,
                    cid = cid,
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    quality = videoStream.id, // 使用实际视频流的清晰度ID
                    format = "dash",
                    duration = dash.duration.toLong()
                )
            }
        }
        
        // 降级到MP4格式
        data.durl?.firstOrNull()?.let { durl ->
            Log.d("BiliTV", "MP4 format - URL: ${durl.url}")
            return VideoPlayInfo(
                bvid = bvid,
                cid = cid,
                videoUrl = durl.url,
                audioUrl = null,
                quality = data.quality,
                format = "mp4",
                duration = data.timelength / 1000 // 转换为秒
            )
        }
        
        Log.e("BiliTV", "No valid play URL found in response")
        return null
    }
}
