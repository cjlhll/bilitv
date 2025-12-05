package com.bili.bilitv

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class PlaybackReporter(
    private val aid: Long,
    private val bvid: String,
    private val cid: Long,
    private val currentPositionProvider: () -> Long // Returns current position in seconds
) {
    private val client = OkHttpClient()
    private var reportJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var startTime: Long = 0
    private var startRealTime: Long = 0 
    
    private var isPlaying: Boolean = false

    fun start() {
        if (isPlaying) return
        isPlaying = true
        startRealTime = System.currentTimeMillis() / 1000
        startTime = System.currentTimeMillis() / 1000
        
        scope.launch {
            reportStart()
            startHeartbeat()
        }
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        reportJob?.cancel()
    }
    
    fun stop() {
        isPlaying = false
        reportJob?.cancel()
        scope.launch {
             val currentPos = currentPositionProvider()
             reportHistory(currentPos)
        }
    }
    
    fun reportSeek(targetPosition: Long) {
        scope.launch {
            // Heartbeat with play_type = 0 (as per requirement for seek end)
            reportHeartbeat(
                playedTime = targetPosition,
                playType = 0 
            )
            // History report
            reportHistory(targetPosition)
        }
    }

    fun release() {
        isPlaying = false
        reportJob?.cancel()
        scope.cancel()
    }

    private suspend fun reportStart() {
        try {
             val csrf = getCsrf()
             Log.d("PlaybackReporter", "Reporting start: aid=$aid, cid=$cid")
             val body = FormBody.Builder()
                 .add("aid", aid.toString())
                 .add("bvid", bvid)
                 .add("cid", cid.toString())
                 .add("part", "1")
                 .add("csrf", csrf)
                 .add("stime", startTime.toString())
                 .build()

             val request = Request.Builder()
                 .url("https://api.bilibili.com/x/click-interface/click/web/h5")
                 .post(body)
                 .addHeader("Cookie", SessionManager.getCookieString() ?: "")
                 .build()
                 
             client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e("PlaybackReporter", "Start report failed", e)
        }
    }

    private suspend fun startHeartbeat() {
        reportJob = scope.launch {
            while (isActive && isPlaying) {
                 try {
                     val currentPos = currentPositionProvider()
                     reportHeartbeat(currentPos, 0) // 0 for normal playback
                 } catch (e: Exception) {
                     Log.e("PlaybackReporter", "Heartbeat loop error", e)
                 }
                 delay(15000)
            }
        }
    }
    
    private suspend fun reportHeartbeat(playedTime: Long, playType: Int) {
        try {
             val csrf = getCsrf()
             val now = System.currentTimeMillis() / 1000
             val realTime = now - startRealTime
             
             Log.d("PlaybackReporter", "Heartbeat: pos=$playedTime, type=$playType")

             val body = FormBody.Builder()
                 .add("aid", aid.toString())
                 .add("bvid", bvid)
                 .add("cid", cid.toString())
                 .add("played_time", playedTime.toString())
                 .add("realtime", realTime.toString())
                 .add("start_ts", startTime.toString())
                 .add("type", "3")
                 .add("dt", "2")
                 .add("play_type", playType.toString())
                 .add("csrf", csrf)
                 .build()

             val request = Request.Builder()
                 .url("https://api.bilibili.com/x/click-interface/web/heartbeat")
                 .post(body)
                 .addHeader("Cookie", SessionManager.getCookieString() ?: "")
                 .build()
                 
             client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e("PlaybackReporter", "Heartbeat failed", e)
        }
    }
    
    private suspend fun reportHistory(progress: Long) {
        try {
             val csrf = getCsrf()
             Log.d("PlaybackReporter", "Reporting history: progress=$progress")
             val body = FormBody.Builder()
                 .add("aid", aid.toString())
                 .add("cid", cid.toString())
                 .add("progress", progress.toString())
                 .add("platform", "android")
                 .add("csrf", csrf)
                 .build()

             val request = Request.Builder()
                 .url("https://api.bilibili.com/x/v2/history/report")
                 .post(body)
                 .addHeader("Cookie", SessionManager.getCookieString() ?: "")
                 .build()
                 
             client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e("PlaybackReporter", "History report failed", e)
        }
    }
    
    private fun getCsrf(): String {
        val cookie = SessionManager.getCookieString() ?: return ""
        val cookies = cookie.split(";")
        for (c in cookies) {
            val pair = c.trim().split("=")
            if (pair.size == 2 && pair[0] == "bili_jct") {
                return pair[1]
            }
        }
        return ""
    }
}
