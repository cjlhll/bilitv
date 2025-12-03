package com.bili.bilitv.danmaku.live

import com.bili.bilitv.utils.WbiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class LiveDanmuInfoResponse(
    val code: Int,
    val message: String,
    val data: LiveDanmuInfoData? = null
)

@Serializable
data class LiveDanmuInfoData(
    val group: String,
    val business_id: Int,
    val refresh_row_factor: Double,
    val refresh_rate: Int,
    val max_delay: Int,
    val token: String,
    val host_list: List<LiveHostItem>
)

@Serializable
data class LiveHostItem(
    val host: String,
    val port: Int,
    val wss_port: Int,
    val ws_port: Int
)

object DanmakuLiveRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // Hardcoded Bilibili Web Keys (from API docs, usually these should be fetched dynamically from nav,
    // but for simplicity we can use known ones or require fetching them.
    // However, WbiUtil requires imgKey and subKey.
    // To do this properly, we should fetch nav info. But to save round trips and complexity for this specific request,
    // we'll assume we can get them or they are passed in.
    // WAIT: The prompt says "implement newest WBI signing".
    // Usually WBI keys are rotated. We need to fetch them from nav endpoint.
    // `https://api.bilibili.com/x/web-interface/nav`
    
    // For this implementation, I will fetch Nav first to get keys if not cached.
    
    private var imgKey: String? = null
    private var subKey: String? = null

    private suspend fun ensureWbiKeys() {
        if (imgKey != null && subKey != null) return
        
        try {
            val request = Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/nav")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (body != null) {
                // Quick and dirty extraction using Regex to avoid complex data model for Nav
                // "wbi_img":{"img_url":".../wbi/7cd084941338484aae1ad9425b84077c","sub_url":".../wbi/4932caff0ff746eab6f01aa08b70aca2"}
                val imgUrlMatch = "\\\"img_url\\\":\\\"(.*?)\\\"".toRegex().find(body)
                val subUrlMatch = "\\\"sub_url\\\":\\\"(.*?)\\\"".toRegex().find(body)
                
                if (imgUrlMatch != null && subUrlMatch != null) {
                    val imgUrl = imgUrlMatch.groupValues[1]
                    val subUrl = subUrlMatch.groupValues[1]
                    
                    imgKey = imgUrl.substringAfterLast("/").substringBefore(".")
                    subKey = subUrl.substringAfterLast("/").substringBefore(".")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getDanmuInfo(roomId: Long): LiveDanmuInfoData? = withContext(Dispatchers.IO) {
        ensureWbiKeys()
        val iKey = imgKey ?: return@withContext null
        val sKey = subKey ?: return@withContext null

        val params = mapOf(
            "id" to roomId.toString(),
            "type" to "0"
        )
        
        val signedParams = WbiUtil.sign(params, iKey, sKey)
        
        val queryBuilder = StringBuilder()
        signedParams.forEach { (k, v) ->
            if (queryBuilder.isNotEmpty()) queryBuilder.append("&")
            queryBuilder.append("$k=$v") // WbiUtil already encoded values? No, sign() returns raw values?
            // Let's check WbiUtil.sign implementation in context.
            // WbiUtil.sign returns map with wts and w_rid. Values are NOT encoded in the returned map.
            // But WbiUtil.encodeURIComponent IS available.
        }
        
        // Re-build query string with encoding
        val finalQueryBuilder = StringBuilder()
        signedParams.forEach { (k, v) ->
            if (finalQueryBuilder.isNotEmpty()) finalQueryBuilder.append("&")
            finalQueryBuilder.append("${WbiUtil.encodeURIComponent(k)}=${WbiUtil.encodeURIComponent(v)}")
        }

        val url = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo?$finalQueryBuilder" 
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val apiResp = json.decodeFromString<LiveDanmuInfoResponse>(body)
                    if (apiResp.code == 0) {
                        return@withContext apiResp.data
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
