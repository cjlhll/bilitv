package com.bili.bilitv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

import com.bili.bilitv.utils.WbiUtil

/**
 * 视频详情响应
 */
@Serializable
data class VideoViewResponse(
    val code: Int,
    val message: String,
    val data: VideoViewData? = null
)

@Serializable
data class VideoViewData(
    val bvid: String,
    val cid: Long,
    val title: String
)

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
    @SerialName("base_url") val base_url: String? = null,
    val backupUrl: List<String>? = null,
    @SerialName("backup_url") val backup_url: List<String>? = null,
    val bandwidth: Long, // 所需带宽
    val mimeType: String,
    @SerialName("mime_type") val mime_type: String? = null,
    val codecs: String, // 编码格式
    val width: Int,
    val height: Int,
    val frameRate: String,
    @SerialName("frame_rate") val frame_rate: String? = null,
    val codecid: Int // 编码ID
)

@Serializable
data class DashAudio(
    val id: Int, // 音质代码
    val baseUrl: String,
    @SerialName("base_url") val base_url: String? = null,
    val backupUrl: List<String>? = null,
    @SerialName("backup_url") val backup_url: List<String>? = null,
    val bandwidth: Long,
    val mimeType: String,
    @SerialName("mime_type") val mime_type: String? = null,
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
    val quality: Int,
    val format: String,
    val duration: Long,
    val videoUrl: String,
    val audioUrl: String? = null
)

/**
 * 缩略图响应
 */
@Serializable
data class VideoshotResponse(
    val code: Int,
    val message: String,
    val data: VideoshotData? = null
)

@Serializable
data class VideoshotData(
    val image: List<String>? = null, // 缩略图拼图URL列表
    val img_x_len: Int = 10, // 每张拼图横向图片数量
    val img_y_len: Int = 10, // 每张拼图纵向图片数量
    val img_x_size: Int = 160, // 单张缩略图宽度
    val img_y_size: Int = 90  // 单张缩略图高度
)

object VideoPlayUrlFetcher {
    private val client = OkHttpClient()
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }
    
    // 缓存WBI keys
    private var imgKey: String? = null
    private var subKey: String? = null
    
    /**
     * 获取视频播放地址
     * @param bvid 视频BV号
     * @param cid 视频CID
     * @param qn 清晰度 (80: 1080P, 64: 720P, 32: 480P, 16: 360P)
     * @param fnval 格式标记 (1: MP4, 16: DASH, 4048: DASH + 4K)
     */
    suspend fun fetchPlayUrl(bvid: String, cid: Long, qn: Int = 64, fnval: Int = 4048): VideoPlayInfo? {
        return withContext(Dispatchers.IO) {
            try {
                // 确保有WBI keys
                if (imgKey == null || subKey == null) {
                    fetchNavInfo()
                }
                
                if (imgKey == null || subKey == null) {
                    Log.e("BiliTV", "Failed to get WBI keys")
                    return@withContext null
                }

                // 构造请求参数
                val params = mutableMapOf<String, String>(
                    "bvid" to bvid,
                    "cid" to cid.toString(),
                    "qn" to qn.toString(),
                    "fnval" to fnval.toString(),
                    "fnver" to "0",
                    "fourk" to "1"
                )
                
                // 计算WBI签名
                val signedParams = WbiUtil.sign(params, imgKey!!, subKey!!)
                val query = signedParams.entries.joinToString("&") { "${it.key}=${it.value}" }
                
                val url = "https://api.bilibili.com/x/player/wbi/playurl?$query"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.bilibili.com")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val playUrlResponse = json.decodeFromString<PlayUrlResponse>(body)
                        
                        if (playUrlResponse.code == 0 && playUrlResponse.data != null) {
                            val data = playUrlResponse.data
                            
                            // 优先使用DASH
                            if (data.dash != null) {
                                val video = data.dash.video.firstOrNull { it.id == data.quality } 
                                    ?: data.dash.video.firstOrNull()
                                val audio = data.dash.audio?.firstOrNull()
                                
                                if (video != null) {
                                    return@withContext VideoPlayInfo(
                                        bvid = bvid,
                                        cid = cid,
                                        quality = video.id,
                                        format = "dash",
                                        duration = data.timelength / 1000,
                                        videoUrl = video.baseUrl.ifEmpty { video.base_url ?: "" },
                                        audioUrl = audio?.baseUrl?.ifEmpty { audio.base_url ?: "" }
                                    )
                                }
                            }
                            
                            // 降级到MP4 (durl)
                            if (data.durl != null && data.durl.isNotEmpty()) {
                                val item = data.durl[0]
                                return@withContext VideoPlayInfo(
                                    bvid = bvid,
                                    cid = cid,
                                    quality = data.quality,
                                    format = "mp4",
                                    duration = data.timelength / 1000,
                                    videoUrl = item.url
                                )
                            }
                        } else {
                            Log.e("BiliTV", "API error: ${playUrlResponse.message}")
                        }
                    }
                } else {
                    Log.e("BiliTV", "HTTP error: ${response.code}")
                }
                null
            } catch (e: Exception) {
                Log.e("BiliTV", "Error fetching play URL", e)
                null
            }
        }
    }
    
    /**
     * 获取WBI keys
     */
    private fun fetchNavInfo() {
        try {
            val request = Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/nav")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
                
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    // 简单解析JSON获取wbi_img信息
                    // 注意：这里为了简化没有定义完整的NavResponse类，而是使用字符串查找
                    // 实际项目中应该定义完整的数据模型
                    val imgUrlStart = body.indexOf("\"img_url\":\"") + 11
                    val imgUrlEnd = body.indexOf("\"", imgUrlStart)
                    val subUrlStart = body.indexOf("\"sub_url\":\"") + 11
                    val subUrlEnd = body.indexOf("\"", subUrlStart)
                    
                    if (imgUrlStart > 10 && subUrlStart > 10) {
                        val imgUrl = body.substring(imgUrlStart, imgUrlEnd)
                        val subUrl = body.substring(subUrlStart, subUrlEnd)
                        
                        imgKey = imgUrl.substringAfterLast("/").substringBefore(".")
                        subKey = subUrl.substringAfterLast("/").substringBefore(".")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BiliTV", "Error fetching Nav info for WBI", e)
        }
    }

    /**
     * 获取视频详情（用于获取CID）
     */
    suspend fun fetchVideoDetails(bvid: String): VideoViewData? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                         val viewResp = json.decodeFromString<VideoViewResponse>(body)
                         if (viewResp.code == 0) {
                             return@withContext viewResp.data
                         } else {
                             Log.e("BiliTV", "Video view API error: ${viewResp.message}")
                         }
                    }
                } else {
                    Log.e("BiliTV", "Video view HTTP error: ${response.code}")
                }
                null
            } catch (e: Exception) {
                Log.e("BiliTV", "Error fetching video details", e)
                null
            }
        }
    }

    /**
     * 获取视频缩略图信息 (Videoshot)
     */
    suspend fun fetchVideoshot(bvid: String, cid: Long): VideoshotData? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.bilibili.com/x/player/videoshot?bvid=$bvid&cid=$cid"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val resp = json.decodeFromString<VideoshotResponse>(body)
                        if (resp.code == 0) {
                            return@withContext resp.data
                        }
                    }
                }
                null
            } catch (e: Exception) {
                Log.e("BiliTV", "Error fetching videoshot", e)
                null
            }
        }
    }
}
