package com.bili.bilitv.danmaku

import com.bilibili.community.service.dm.v1.DmSegMobileReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object DanmakuRepository {
    private val client = OkHttpClient()

    /**
     * Fetches danmaku data (protobuf format) from Bilibili API.
     * @param cid The content ID (oid) of the video.
     * @param segmentIndex The segment index (usually 1 for short videos).
     */
    suspend fun fetchDanmaku(cid: Long, segmentIndex: Int): DmSegMobileReply? = withContext(Dispatchers.IO) {
        // Using the known endpoint for seg.so
        val url = "https://api.bilibili.com/x/v2/dm/list/seg.so?type=1&oid=$cid&segment_index=$segmentIndex"
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null) {
                    return@withContext DmSegMobileReply.parseFrom(bytes)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
