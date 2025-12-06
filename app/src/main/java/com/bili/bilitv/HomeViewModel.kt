package com.bili.bilitv

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bili.bilitv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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
    var isRefreshing by mutableStateOf(false)
    var refreshSignal by mutableStateOf(0)
    
    // Hot states - 使用 SnapshotStateList 提高性能
    private val _hotVideos = mutableStateListOf<VideoItemData>()
    val hotVideos: List<VideoItemData> = _hotVideos
    var isHotLoading by mutableStateOf(false)
    private var hotPage = 1
    private var hotHasMore = true

    // Recommend states - 使用 SnapshotStateList 提高性能
    private val _recommendVideos = mutableStateListOf<VideoItemData>()
    val recommendVideos: List<VideoItemData> = _recommendVideos
    var isRecommendLoading by mutableStateOf(false)
    private var recommendFreshIdx = 1
    private var recommendHasMore = true

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
        // 初始加载推荐视频，确保数据充足
        if (canLoadMore(TabType.RECOMMEND)) {
            viewModelScope.launch {
                loadNextPage(TabType.RECOMMEND)
            }
        }
    }

    /**
     * 优化的分页加载函数 - 使用后台IO线程和SnapshotStateList
     */
    suspend fun loadNextPage(tabType: TabType) {
        if (isCurrentlyLoading(tabType) || !hasMoreData(tabType)) return
        
        setLoading(tabType, true)
        
        // 使用后台IO线程执行网络请求
        withContext(Dispatchers.IO) {
            try {
                val result = when (tabType) {
                    TabType.RECOMMEND -> fetchRecommendPage()
                    TabType.HOT -> fetchHotPage()
                }
                
                // 在主线程更新UI状态
                withContext(Dispatchers.Main) {
                    result?.let { newVideos ->
                        if (newVideos.isNotEmpty()) {
                            when (tabType) {
                                TabType.RECOMMEND -> {
                                    _recommendVideos.addAll(newVideos)
                                    recommendFreshIdx++
                                    recommendHasMore = newVideos.size >= 30
                                }
                                TabType.HOT -> {
                                    _hotVideos.addAll(newVideos)
                                    hotPage++
                                    hotHasMore = newVideos.size >= 20
                                }
                            }
                            
                            if (BuildConfig.DEBUG) {
                                Log.d("BiliTV", "Loaded ${newVideos.size} ${tabType.name} videos, total: ${getCurrentVideoCount(tabType)}")
                            }
                        } else {
                            // 没有更多数据
                            when (tabType) {
                                TabType.RECOMMEND -> recommendHasMore = false
                                TabType.HOT -> hotHasMore = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BiliTV", "Failed to load ${tabType.name} page", e)
            } finally {
                withContext(Dispatchers.Main) {
                    setLoading(tabType, false)
                }
            }
        }
    }

    /**
     * 获取推荐视频页面数据
     */
    private suspend fun fetchRecommendPage(dedup: Boolean = true): List<VideoItemData>? {
        val currentBvids = if (dedup) _recommendVideos.map { it.bvid }.toSet() else emptySet()
        
        val url = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd?fresh_idx=$recommendFreshIdx&ps=30"
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
                        return resp.data.item.filter { !currentBvids.contains(it.bvid) }
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
        return null
    }

    /**
     * 获取热门视频页面数据
     */
    private suspend fun fetchHotPage(dedup: Boolean = true): List<VideoItemData>? {
        val currentBvids = if (dedup) _hotVideos.map { it.bvid }.toSet() else emptySet()
        
        if (BuildConfig.DEBUG) {
            Log.d("BiliTV", "Fetching popular videos page $hotPage...")
        }
        
        val url = "https://api.bilibili.com/x/web-interface/popular?pn=$hotPage&ps=50"
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
                        return popularResponse.data.list.filter { !currentBvids.contains(it.bvid) }
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
        return null
    }

    // 辅助函数
    private fun isCurrentlyLoading(tabType: TabType): Boolean {
        return when (tabType) {
            TabType.HOT -> isHotLoading
            TabType.RECOMMEND -> isRecommendLoading
        }
    }

    private fun hasMoreData(tabType: TabType): Boolean {
        return when (tabType) {
            TabType.HOT -> hotHasMore
            TabType.RECOMMEND -> recommendHasMore
        }
    }

    private fun setLoading(tabType: TabType, loading: Boolean) {
        when (tabType) {
            TabType.HOT -> isHotLoading = loading
            TabType.RECOMMEND -> isRecommendLoading = loading
        }
    }

    private fun getCurrentVideoCount(tabType: TabType): Int {
        return when (tabType) {
            TabType.HOT -> _hotVideos.size
            TabType.RECOMMEND -> _recommendVideos.size
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
    
    // 检查是否可以加载更多数据
    fun canLoadMore(tabType: TabType): Boolean {
        return !isCurrentlyLoading(tabType) && hasMoreData(tabType)
    }

    fun refreshCurrentTab() {
        if (isRefreshing) return
        val targetTab = selectedTab
        isRefreshing = true
        shouldRestoreFocusToGrid = true
        viewModelScope.launch {
            setLoading(targetTab, true)
            try {
                when (targetTab) {
                    TabType.RECOMMEND -> {
                        recommendFreshIdx = 1
                        recommendHasMore = true
                        val result = withContext(Dispatchers.IO) { fetchRecommendPage(dedup = false) }
                        withContext(Dispatchers.Main) {
                            result?.let { newVideos ->
                                _recommendVideos.clear()
                                _recommendVideos.addAll(newVideos)
                                recommendFreshIdx = 2
                                recommendHasMore = newVideos.size >= 30
                                _tabScrollStates[targetTab] = 0 to 0
                                _tabFocusStates[targetTab] = 0
                                refreshSignal++
                            }
                        }
                    }
                    TabType.HOT -> {
                        hotPage = 1
                        hotHasMore = true
                        val result = withContext(Dispatchers.IO) { fetchHotPage(dedup = false) }
                        withContext(Dispatchers.Main) {
                            result?.let { newVideos ->
                                _hotVideos.clear()
                                _hotVideos.addAll(newVideos)
                                hotPage = 2
                                hotHasMore = newVideos.size >= 20
                                _tabScrollStates[targetTab] = 0 to 0
                                _tabFocusStates[targetTab] = 0
                                refreshSignal++
                            }
                        }
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    setLoading(targetTab, false)
                    isRefreshing = false
                }
            }
        }
    }
}