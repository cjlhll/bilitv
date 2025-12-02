package com.bili.bilitv

import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import com.bili.bilitv.utils.WbiUtil
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 直播流URL获取工具
 */
object LiveStreamUrlFetcher {
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 获取直播间播放信息
     */
    suspend fun fetchLivePlayInfo(roomId: Int, title: String, uname: String): LivePlayInfo? {
        return try {
            val playUrl = fetchLiveStreamUrl(roomId)
            if (playUrl != null) {
                LivePlayInfo(
                    roomId = roomId,
                    title = title,
                    uname = uname,
                    playUrl = playUrl,
                    format = "flv" // 直播通常使用flv格式
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("LiveStreamUrlFetcher", "获取直播流信息失败", e)
            null
        }
    }

    /**
     * 获取直播流URL
     */
    private suspend fun fetchLiveStreamUrl(roomId: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 构建请求参数
                val params = mutableMapOf(
                    "room_id" to roomId.toString(),
                    "qn" to "10000", // 最高画质
                    "platform" to "web",
                    "protocol" to "0,1", // 0:HTTP-FLV, 1:HLS
                    "format" to "0,1,2", // 0:flv, 1:ts, 2:fmp4
                    "codec" to "0,1", // 0:avc, 1:hevc
                    "dolby" to "5",
                    "panorama" to "1"
                )

                // 构建查询字符串
                val queryBuilder = StringBuilder()
                params.forEach { (key, value) ->
                    if (queryBuilder.isNotEmpty()) {
                        queryBuilder.append("&")
                    }
                    queryBuilder.append(WbiUtil.encodeURIComponent(key))
                    queryBuilder.append("=")
                    queryBuilder.append(WbiUtil.encodeURIComponent(value))
                }
                val query = queryBuilder.toString()

                val url = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?$query"
                Log.d("LiveStreamUrlFetcher", "Request URL: $url")

                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://live.bilibili.com")
                
                SessionManager.getCookieString()?.let {
                    requestBuilder.header("Cookie", it)
                }
                    
                val request = requestBuilder.build()

                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        Log.d("LiveStreamUrlFetcher", "API Response: $body")
                        try {
                            val apiResp = json.decodeFromString<LiveStreamResponse>(body)
                            if (apiResp.code == 0 && apiResp.data != null) {
                                // 检查直播状态
                                if (apiResp.data.live_status != 1) {
                                    Log.e("LiveStreamUrlFetcher", "直播间未开播")
                                    return@withContext null
                                }
                                
                                val playUrl = apiResp.data.playurl_info?.playurl
                                if (playUrl != null && !playUrl.stream.isNullOrEmpty()) {
                                    // 优先选择HTTP-FLV协议，其次是HLS
                                    val httpStream = playUrl.stream.find { it.protocol_name == "http_stream" }
                                    val hlsStream = httpStream ?: playUrl.stream.find { it.protocol_name == "http_hls" }
                                    
                                    val selectedStream = hlsStream ?: playUrl.stream.first()
                                    
                                    if (!selectedStream.format.isNullOrEmpty()) {
                                        // 优先选择fmp4格式，其次是ts
                                        val fmp4Format = selectedStream.format.find { it.format_name == "fmp4" }
                                        val tsFormat = fmp4Format ?: selectedStream.format.find { it.format_name == "ts" }
                                        val flvFormat = tsFormat ?: selectedStream.format.find { it.format_name == "flv" }
                                        
                                        val selectedFormat = flvFormat ?: selectedStream.format.first()
                                        
                                        if (selectedFormat.codec != null && selectedFormat.codec.isNotEmpty()) {
                                            // 选择第一个可用的codec
                                            val selectedCodec = selectedFormat.codec.first()
                                            
                                            // 构建完整的URL
                                            val urlInfo = selectedCodec.url_info?.firstOrNull()
                                            if (urlInfo != null) {
                                                val fullUrl = urlInfo.host + selectedCodec.base_url + urlInfo.extra
                                                Log.d("LiveStreamUrlFetcher", "获取到直播流URL: $fullUrl")
                                                return@withContext fullUrl
                                            }
                                        }
                                    }
                                } else {
                                    Log.e("LiveStreamUrlFetcher", "直播流列表为空")
                                }
                            } else {
                                Log.e("LiveStreamUrlFetcher", "API Error: ${apiResp.message}")
                            }
                        } catch (e: Exception) {
                            Log.e("LiveStreamUrlFetcher", "解析直播流URL失败", e)
                        }
                    }
                } else {
                    Log.e("LiveStreamUrlFetcher", "HTTP Error: ${response.code}")
                }
                
                null
            } catch (e: Exception) {
                Log.e("LiveStreamUrlFetcher", "获取直播流URL异常", e)
                null
            }
        }
    }
}