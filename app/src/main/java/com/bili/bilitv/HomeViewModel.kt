package com.bili.bilitv

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bili.bilitv.BuildConfig
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

class HomeViewModel(application: Application) : AndroidViewModel(application), VideoGridStateManager {
    var selectedTab by mutableStateOf(TabType.RECOMMEND)
    
    // Hot states
    var hotVideos by mutableStateOf<List<VideoItemData>>(emptyList())
    var isHotLoading by mutableStateOf(false)
    private var hotPage = 1
    private var hotTargetCount = 100  // 目标加载100条数据

    // Recommend states
    var recommendVideos by mutableStateOf<List<VideoItemData>>(emptyList())
    var isRecommendLoading by mutableStateOf(false)
    private var recommendFreshIdx = 1
    private var recommendTargetCount = 100  // 目标加载100条数据

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
    override var shouldRestoreFocusToGrid by mutableStateOf(false)

    init {
        // 初始加载推荐视频，目标100条
        if (canLoadMore(TabType.RECOMMEND)) {
            loadMoreRecommend()
        }
    }

    fun loadMoreRecommend() {
        if (isRecommendLoading) return
        isRecommendLoading = true
        
        // 使用低优先级协程，避免影响UI渲染
        viewModelScope.launch(Dispatchers.IO + kotlinx.coroutines.CoroutineName("RecommendLoader") + kotlinx.coroutines.NonCancellable) {
            try {
                val currentBvids = withContext(Dispatchers.Main) { recommendVideos.map { it.bvid }.toSet() }
                
                // Fetch just ONE page (20 items)
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
                                val uniqueNewVideos = newVideos.filter { !currentBvids.contains(it.bvid) }
                                
                                recommendFreshIdx++
                                if (BuildConfig.DEBUG) {
                                    Log.d("BiliTV", "Loaded ${uniqueNewVideos.size} recommend videos.")
                                }

                                withContext(Dispatchers.Main) {
                                    if (uniqueNewVideos.isNotEmpty()) {
                                        recommendVideos = recommendVideos + uniqueNewVideos
                                    }
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

    fun loadMoreHot() {
        if (isHotLoading) return
        isHotLoading = true

        // 使用低优先级协程，避免影响UI渲染
        viewModelScope.launch(Dispatchers.IO + kotlinx.coroutines.CoroutineName("HotLoader") + kotlinx.coroutines.NonCancellable) {
            try {
                val currentBvids = withContext(Dispatchers.Main) { hotVideos.map { it.bvid }.toSet() }
                
                // Fetch just ONE page (20 items)
                if (BuildConfig.DEBUG) {
                    Log.d("BiliTV", "Fetching popular videos page $hotPage...")
                }
                val url = "https://api.bilibili.com/x/web-interface/popular?pn=$hotPage&ps=20"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36")

                SessionManager.getCookieString()?.let {
                    requestBuilder.header("Cookie", it)
                }

                val request = requestBuilder.build()
                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        try {
                            val popularResponse = json.decodeFromString<PopularVideoResponse>(responseBody)
                            if (popularResponse.code == 0 && popularResponse.data != null) {
                                val newVideos = popularResponse.data.list
                                val uniqueNewVideos = newVideos.filter { !currentBvids.contains(it.bvid) }
                                
                                hotPage++
                                if (BuildConfig.DEBUG) {
                                    Log.d("BiliTV", "Loaded ${uniqueNewVideos.size} popular videos.")
                                }

                                withContext(Dispatchers.Main) {
                                    if (uniqueNewVideos.isNotEmpty()) {
                                        hotVideos = hotVideos + uniqueNewVideos
                                    }
                                }
                            } else {
                                Log.e("BiliTV", "Popular Videos API error: ${popularResponse.message}")
                            }
                        } catch (e: Exception) {
                            Log.e("BiliTV", "Popular Videos JSON parse error", e)
                        }
                    }
                } else {
                    Log.e("BiliTV", "Popular Videos HTTP error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("BiliTV", "Popular Videos Network error: ${e.localizedMessage}")
            } finally {
                withContext(Dispatchers.Main) {
                    isHotLoading = false
                }
            }
        }
    }

    // VideoGridStateManager 接口实现
    override fun updateScrollState(key: Any, index: Int, offset: Int) {
        if (key is TabType) {
            _tabScrollStates[key] = index to offset
        }
    }

    override fun updateFocusedIndex(key: Any, index: Int) {
        if (key is TabType) {
            _tabFocusStates[key] = index
        }
    }
    
    override fun getScrollState(key: Any): Pair<Int, Int> {
        return if (key is TabType) {
            _tabScrollStates[key] ?: (0 to 0)
        } else {
            (0 to 0)
        }
    }
    
    override fun getFocusedIndex(key: Any): Int {
        return if (key is TabType) {
            _tabFocusStates[key] ?: -1
        } else {
            -1
        }
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
    
    // 检查是否可以加载更多数据（不限制100条上限）
    fun canLoadMore(tabType: TabType): Boolean {
        return when (tabType) {
            TabType.HOT -> !isHotLoading
            TabType.RECOMMEND -> !isRecommendLoading
            else -> false
        }
    }
    
    // 重置目标数量，用于加载更多时
    fun resetTargetCount(tabType: TabType) {
        when (tabType) {
            TabType.HOT -> hotTargetCount = hotVideos.size + 20
            TabType.RECOMMEND -> recommendTargetCount = recommendVideos.size + 20
        }
    }
}