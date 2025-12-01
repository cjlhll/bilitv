package com.bili.bilitv

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class RecommendVideoResponse(
    val code: Int,
    val message: String,
    val ttl: Int,
    val data: RecommendVideoData? = null
)

@Serializable
data class RecommendVideoData(
    val item: List<VideoItemData>? = null
)

class HomeViewModel : ViewModel() {
    var selectedTab by mutableStateOf(TabType.RECOMMEND)
    var hotVideos by mutableStateOf<List<VideoItemData>>(emptyList())
    
    // Recommend states
    var recommendVideos by mutableStateOf<List<VideoItemData>>(emptyList())
    var isRecommendLoading by mutableStateOf(false)
    private var recommendFreshIdx = 1

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // Store state per tab
    // Pair(index, offset)
    private val _tabScrollStates = mutableStateMapOf<TabType, Pair<Int, Int>>()
    // Focused index
    private val _tabFocusStates = mutableStateMapOf<TabType, Int>()

    // Flag to control focus restoration logic
    // When switching tabs explicitly, we want focus to stay on the tab bar (false)
    // When returning from player or other screens, we want focus to restore to the list (true)
    var shouldRestoreFocusToGrid by mutableStateOf(false)

    init {
        // Initial load for recommend videos
        loadMoreRecommend()
    }

    fun loadMoreRecommend() {
        if (isRecommendLoading) return
        isRecommendLoading = true
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Using the Wbi endpoint or standard endpoint. 
                // Trying standard first as per common Bilibili API behavior for web clients.
                // If this fails, we might need to handle Wbi signing.
                // Note: API documentation suggests /x/web-interface/wbi/index/top/feed/rcmd
                // We will try the non-wbi path first if available, or wbi path without signature (might work for some data).
                // Let's try the one from the search result.
                val url = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd?fresh_idx=$recommendFreshIdx&ps=20"
                
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36")
                
                SessionManager.getCookieString()?.let {
                    requestBuilder.header("Cookie", it)
                }

                val response = httpClient.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    response.body?.string()?.let { body ->
                        try {
                            val resp = json.decodeFromString<RecommendVideoResponse>(body)
                            if (resp.code == 0 && resp.data?.item != null) {
                                val newVideos = resp.data.item
                                withContext(Dispatchers.Main) {
                                    // Filter duplicates based on bvid just in case
                                    val currentBvids = recommendVideos.map { it.bvid }.toSet()
                                    val uniqueNewVideos = newVideos.filter { !currentBvids.contains(it.bvid) }
                                    
                                    recommendVideos = recommendVideos + uniqueNewVideos
                                    recommendFreshIdx++
                                    Log.d("BiliTV", "Loaded ${uniqueNewVideos.size} recommend videos. Total: ${recommendVideos.size}")
                                }
                            } else {
                                Log.e("BiliTV", "Recommend API error: ${resp.message} (Code: ${resp.code})")
                            }
                        } catch (e: Exception) {
                            Log.e("BiliTV", "Failed to parse recommend response", e)
                        }
                    }
                } else {
                    Log.e("BiliTV", "Recommend HTTP error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("BiliTV", "Recommend Network error", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isRecommendLoading = false
                }
            }
        }
    }

    fun updateScrollState(tab: TabType, index: Int, offset: Int) {
        _tabScrollStates[tab] = index to offset
    }

    fun updateFocusedIndex(tab: TabType, index: Int) {
        _tabFocusStates[tab] = index
    }
    
    fun getScrollState(tab: TabType): Pair<Int, Int> {
        return _tabScrollStates[tab] ?: (0 to 0)
    }
    
    fun getFocusedIndex(tab: TabType): Int {
        return _tabFocusStates[tab] ?: -1
    }
    
    fun onTabChanged(newTab: TabType) {
        selectedTab = newTab
        // Switch tab -> Don't restore focus to grid immediately (keep on tab)
        shouldRestoreFocusToGrid = false
    }
    
    fun onEnterFullScreen() {
        // Prepare to restore focus when coming back
        shouldRestoreFocusToGrid = true
    }
}
