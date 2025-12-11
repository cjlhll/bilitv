package com.bili.bilitv

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bili.bilitv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
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

@Serializable
data class BangumiIndexResponse(
    val code: Int = -1,
    val message: String = "",
    val data: BangumiIndexData? = null
)

@Serializable
data class BangumiIndexData(
    val list: List<BangumiIndexItem> = emptyList(),
    val total: Int = 0,
    val num: Int = 0,
    val size: Int = 0,
    val page: Int = 0
)

@Serializable
data class BangumiIndexItem(
    val badge: String = "",
    @SerialName("badge_info")
    val badgeInfo: BangumiBadgeInfo? = null,
    @SerialName("badge_type")
    val badgeType: Int = 0,
    val cover: String = "",
    @SerialName("first_ep")
    val firstEp: BangumiFirstEp? = null,
    @SerialName("index_show")
    val indexShow: String = "",
    @SerialName("is_finish")
    val isFinish: Int = 0,
    val link: String = "",
    @SerialName("media_id")
    val mediaId: Long = 0,
    val order: String = "",
    @SerialName("order_type")
    val orderType: String = "",
    val score: String = "",
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("season_status")
    val seasonStatus: Int = 0,
    @SerialName("season_type")
    val seasonType: Int = 0,
    @SerialName("subTitle")
    val subTitle: String = "",
    val title: String = "",
    @SerialName("title_icon")
    val titleIcon: String = ""
)

@Serializable
data class BangumiBadgeInfo(
    val text: String = "",
    @SerialName("text_color")
    val textColor: String = "",
    @SerialName("bg_color")
    val bgColor: String = "",
    @SerialName("border_color")
    val borderColor: String = "",
    @SerialName("bg_style")
    val bgStyle: Int = 0
)

@Serializable
data class BangumiFirstEp(
    val cover: String = "",
    val title: String = "",
    @SerialName("ep_id")
    val epId: Long = 0
)

@Serializable
data class TimelineResponse(
    val code: Int,
    val message: String,
    val result: List<TimelineDayDto>? = null
)

@Serializable
data class TimelineDayDto(
    val date: String = "",
    @SerialName("date_ts")
    val dateTs: Long = 0L,
    @SerialName("day_of_week")
    val dayOfWeek: Int = 1,
    @SerialName("is_today")
    val isToday: Int = 0,
    val episodes: List<TimelineEpisodeDto> = emptyList()
)

@Serializable
data class TimelineEpisodeDto(
    @SerialName("episode_id")
    val episodeId: Long = 0,
    @SerialName("season_id")
    val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("pub_index")
    val pubIndex: String = "",
    @SerialName("pub_time")
    val pubTime: String = "",
    @SerialName("follow")
    val follow: Int = 0,
    @SerialName("plays")
    val plays: String? = null,
    @SerialName("follows")
    val follows: String? = null
)

@Serializable
data class TimelineGlobalResponse(
    val code: Int = -1,
    val message: String = "",
    val result: List<TimelineGlobalDay> = emptyList()
)

@Serializable
data class TimelineGlobalDay(
    val date: String = "",
    @SerialName("date_ts")
    val dateTs: Long = 0L,
    @SerialName("day_of_week")
    val dayOfWeek: Int = 1,
    @SerialName("is_today")
    val isToday: Int = 0,
    val seasons: List<TimelineGlobalSeason> = emptyList()
)

@Serializable
data class TimelineGlobalSeason(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("ep_id")
    val episodeId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("pub_index")
    val pubIndex: String = "",
    @SerialName("pub_time")
    val pubTime: String = "",
    @SerialName("is_published")
    val isPublished: Int = 0,
    @SerialName("is_follow")
    val isFollow: Int = 0,
    @SerialName("plays")
    val plays: String? = null,
    @SerialName("follows")
    val follows: String? = null,
    val type: Int = 1
)

data class TimelineEpisode(
    val episodeId: Long,
    val seasonId: Long,
    val title: String,
    val cover: String,
    val episodeIndex: String,
    val publishTime: String,
    val isFollowed: Boolean,
    val viewCount: Long,
    val followCount: Long
)

data class TimelineDay(
    val date: String,
    val dateTs: Long,
    val dayOfWeek: Int,
    val isToday: Boolean,
    val episodes: List<TimelineEpisode>
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

    // Timeline states
    val timelineDays: SnapshotStateList<TimelineDay> = mutableStateListOf()
    var isTimelineLoading by mutableStateOf(false)
    var timelineError by mutableStateOf<String?>(null)
    var timelineSelectedIndex by mutableStateOf(0)

    // Bangumi recommend states
    val bangumiRecommendVideos: SnapshotStateList<Video> = mutableStateListOf()
    var isBangumiRecommendLoading by mutableStateOf(false)
    var bangumiRecommendError by mutableStateOf<String?>(null)
    private var bangumiRecommendPage = 1
    private var bangumiRecommendHasMore = true

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
        if (tabType == TabType.BANGUMI) return
        if (isCurrentlyLoading(tabType) || !hasMoreData(tabType)) return
        
        setLoading(tabType, true)
        
        // 使用后台IO线程执行网络请求
        withContext(Dispatchers.IO) {
            try {
                val result = when (tabType) {
                    TabType.RECOMMEND -> fetchRecommendPage()
                    TabType.HOT -> fetchHotPage()
                    TabType.BANGUMI -> null
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
                                TabType.BANGUMI -> {}
                            }
                            
                            if (BuildConfig.DEBUG) {
                                Log.d("BiliTV", "Loaded ${newVideos.size} ${tabType.name} videos, total: ${getCurrentVideoCount(tabType)}")
                            }
                        } else {
                            // 没有更多数据
                            when (tabType) {
                                TabType.RECOMMEND -> recommendHasMore = false
                                TabType.HOT -> hotHasMore = false
                                TabType.BANGUMI -> {}
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

    fun ensureTimelineLoaded() {
        if (timelineDays.isEmpty() && !isTimelineLoading) {
            viewModelScope.launch {
                loadTimeline()
            }
        }
    }

    fun ensureBangumiRecommendLoaded() {
        if (bangumiRecommendVideos.isEmpty() && !isBangumiRecommendLoading) {
            viewModelScope.launch {
                loadBangumiRecommend(reset = false)
            }
        }
    }

    suspend fun loadTimeline(forceRefresh: Boolean = false) {
        if (isTimelineLoading) return
        if (!forceRefresh && timelineDays.isNotEmpty()) return

        isTimelineLoading = true
        timelineError = null

        try {
            val merged = withContext(Dispatchers.IO) {
                val bangumi = fetchTimelineWithFallback(type = 1)
                val guochuang = fetchTimelineWithFallback(type = 4)
                mergeTimeline(bangumi, guochuang)
            }
            timelineDays.clear()
            timelineDays.addAll(merged)
            val todayIndex = merged.indexOfFirst { it.isToday }
            timelineSelectedIndex = if (todayIndex >= 0) todayIndex else 0
        } catch (e: Exception) {
            timelineError = e.message ?: "加载失败"
            if (BuildConfig.DEBUG) {
                Log.e("BiliTV", "timeline load error: ${e.message}", e)
            }
            if (forceRefresh) {
                timelineDays.clear()
            }
        } finally {
            isTimelineLoading = false
        }
    }

    suspend fun loadBangumiRecommend(reset: Boolean = false) {
        if (isBangumiRecommendLoading) return
        if (!reset && !bangumiRecommendHasMore) return

        isBangumiRecommendLoading = true
        if (reset) {
            bangumiRecommendPage = 1
            bangumiRecommendHasMore = true
        }
        bangumiRecommendError = null

        try {
            val targetPage = bangumiRecommendPage
            val data = withContext(Dispatchers.IO) {
                fetchBangumiRecommendPage(targetPage)
            }
            withContext(Dispatchers.Main) {
                data?.let { payload ->
                    bangumiRecommendError = null
                    if (reset) {
                        bangumiRecommendVideos.clear()
                    }
                    val mapped = payload.list.map { it.toVideo() }
                    if (mapped.isNotEmpty()) {
                        bangumiRecommendVideos.addAll(mapped)
                    }
                    val pageSize = payload.size.takeIf { it > 0 } ?: payload.list.size
                    val totalCount = payload.total
                    val currentPage = payload.page.takeIf { it > 0 } ?: targetPage
                    val consumed = if (pageSize > 0) currentPage * pageSize else currentPage * mapped.size
                    bangumiRecommendHasMore = when {
                        totalCount > 0 && pageSize > 0 -> consumed < totalCount
                        pageSize > 0 -> mapped.size >= pageSize
                        else -> mapped.isNotEmpty()
                    }
                    bangumiRecommendPage = targetPage + 1
                } ?: run {
                    bangumiRecommendError = bangumiRecommendError ?: "加载失败"
                    if (reset) bangumiRecommendHasMore = false
                }
            }
        } catch (e: Exception) {
            bangumiRecommendError = e.localizedMessage ?: "加载失败"
            if (BuildConfig.DEBUG) {
                Log.e("BiliTV", "bangumi recommend load error", e)
            }
        } finally {
            isBangumiRecommendLoading = false
        }
    }

    private suspend fun fetchBangumiRecommendPage(page: Int): BangumiIndexData? {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.bilibili.com")
            .addPathSegments("pgc/season/index/result")
            .addQueryParameter("st", "1")
            .addQueryParameter("order", "3")
            .addQueryParameter("season_version", "-1")
            .addQueryParameter("spoken_language_type", "-1")
            .addQueryParameter("area", "-1")
            .addQueryParameter("is_finish", "-1")
            .addQueryParameter("copyright", "-1")
            .addQueryParameter("season_status", "-1")
            .addQueryParameter("season_month", "-1")
            .addQueryParameter("year", "-1")
            .addQueryParameter("style_id", "-1")
            .addQueryParameter("sort", "0")
            .addQueryParameter("season_type", "1")
            .addQueryParameter("pagesize", "20")
            .addQueryParameter("type", "1")
            .addQueryParameter("page", page.toString())
            .build()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36")
            .header("Referer", "https://www.bilibili.com")

        SessionManager.getCookieString()?.let {
            requestBuilder.header("Cookie", it)
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            if (BuildConfig.DEBUG) {
                Log.e("BiliTV", "Bangumi recommend HTTP error: ${response.code}")
            }
            return null
        }
        val body = response.body?.string() ?: return null
        return try {
            val resp = json.decodeFromString<BangumiIndexResponse>(body)
            if (resp.code == 0) {
                resp.data
            } else {
                if (BuildConfig.DEBUG) {
                    Log.e("BiliTV", "Bangumi recommend API error: ${resp.message} (code=${resp.code})")
                }
                null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("BiliTV", "Bangumi recommend parse error", e)
            }
            null
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

    private suspend fun fetchTimelineWithFallback(type: Int, before: Int = 6, after: Int = 6): List<TimelineDay> {
        val v2 = fetchTimelineV2(type, before, after)
        if (v2 != null && v2.isNotEmpty()) return v2
        if (BuildConfig.DEBUG) {
            Log.d("BiliTV", "timeline v2 empty/fail, fallback legacy type=$type")
        }
        return fetchTimelineLegacy(type) ?: emptyList()
    }

    private suspend fun fetchTimelineV2(type: Int, before: Int = 6, after: Int = 6): List<TimelineDay>? {
        val url = "https://api.bilibili.com/pgc/web/timeline/v2?types=$type&before=$before&after=$after"
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36")
            .header("Referer", "https://www.bilibili.com")
            .header("Origin", "https://www.bilibili.com")

        SessionManager.getCookieString()?.let {
            requestBuilder.header("Cookie", it)
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        if (BuildConfig.DEBUG) {
            Log.d("BiliTV", "timeline req type=$type http=${response.code} url=$url")
        }
        if (!response.isSuccessful) {
            return null
        }
        val body = response.body?.string() ?: return null
        if (BuildConfig.DEBUG) {
            Log.d("BiliTV", "timeline body type=$type $body")
        }
        val resp = json.decodeFromString<TimelineResponse>(body)
        if (BuildConfig.DEBUG) {
            Log.d("BiliTV", "timeline api type=$type code=${resp.code} msg=${resp.message} days=${resp.result?.size}")
        }
        if (resp.code != 0 || resp.result == null) {
            return null
        }
        return resp.result.map { it.toDomain() }
    }

    private suspend fun fetchTimelineLegacy(type: Int): List<TimelineDay>? {
        val url = "https://bangumi.bilibili.com/web_api/timeline_global"
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36")
            .header("Referer", "https://www.bilibili.com")
            .header("Origin", "https://www.bilibili.com")

        SessionManager.getCookieString()?.let {
            requestBuilder.header("Cookie", it)
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        if (BuildConfig.DEBUG) {
            Log.d("BiliTV", "timeline legacy req type=$type http=${response.code} url=$url")
        }
        if (!response.isSuccessful) {
            return null
        }
        val body = response.body?.string() ?: return null
        if (BuildConfig.DEBUG) {
            Log.d("BiliTV", "timeline legacy body $body")
        }
        val resp = json.decodeFromString<TimelineGlobalResponse>(body)
        if (resp.code != 0) {
            return null
        }
        val filtered = resp.result.map { it.toDomain(type) }.filter { it.episodes.isNotEmpty() }
        if (BuildConfig.DEBUG) {
            Log.d("BiliTV", "timeline legacy parsed days=${filtered.size}")
        }
        return filtered
    }

    private fun mergeTimeline(bangumi: List<TimelineDay>, guochuang: List<TimelineDay>): List<TimelineDay> {
        if (bangumi.isEmpty()) return guochuang
        if (guochuang.isEmpty()) return bangumi

        val merged = bangumi.associateBy { it.dateTs }.toMutableMap()
        guochuang.forEach { day ->
            val existing = merged[day.dateTs]
            if (existing != null) {
                merged[day.dateTs] = existing.copy(
                    episodes = existing.episodes + day.episodes
                )
            } else {
                merged[day.dateTs] = day
            }
        }
        return merged.values.sortedBy { it.dateTs }
    }

    private fun TimelineDayDto.toDomain(): TimelineDay {
        return TimelineDay(
            date = date,
            dateTs = dateTs,
            dayOfWeek = dayOfWeek,
            isToday = isToday == 1,
            episodes = episodes.map { it.toDomain() }
        )
    }

    private fun TimelineEpisodeDto.toDomain(): TimelineEpisode {
        val view = plays?.toLongOrNull() ?: 0L
        val followCount = follows?.toLongOrNull() ?: 0L
        return TimelineEpisode(
            episodeId = episodeId,
            seasonId = seasonId,
            title = title,
            cover = cover,
            episodeIndex = pubIndex,
            publishTime = pubTime,
            isFollowed = follow == 1,
            viewCount = view,
            followCount = followCount
        )
    }

    private fun TimelineGlobalDay.toDomain(filterType: Int): TimelineDay {
        val preferred = seasons.filter { it.type == filterType }
        val source = if (preferred.isNotEmpty()) preferred else seasons
        val mappedEpisodes = source.map { it.toDomain() }
        return TimelineDay(
            date = date,
            dateTs = dateTs,
            dayOfWeek = dayOfWeek,
            isToday = isToday == 1,
            episodes = mappedEpisodes
        )
    }

    private fun TimelineGlobalSeason.toDomain(): TimelineEpisode {
        val view = plays?.toLongOrNull() ?: 0L
        val followCount = follows?.toLongOrNull() ?: 0L
        return TimelineEpisode(
            episodeId = episodeId,
            seasonId = seasonId,
            title = title,
            cover = cover,
            episodeIndex = pubIndex,
            publishTime = pubTime,
            isFollowed = isFollow == 1,
            viewCount = view,
            followCount = followCount
        )
    }

    private fun BangumiIndexItem.toVideo(): Video {
        val badgeText = badge.ifBlank { badgeInfo?.text.orEmpty() }
        val badge = badgeInfo?.let {
            Badge(
                text = badgeText,
                textColor = it.textColor,
                textColorNight = "",
                bgColor = it.bgColor,
                bgColorNight = "",
                borderColor = it.borderColor,
                borderColorNight = "",
                bgStyle = it.bgStyle
            )
        }?.takeIf { it.text.isNotBlank() }

        val desc = subTitle.ifBlank { order.ifBlank { indexShow } }
        val epSize = parseEpisodeCount(indexShow)
        val mediaScore = parseScoreValue(score)

        return Video(
            id = seasonId.takeIf { it != 0L }?.toString() ?: mediaId.takeIf { it != 0L }?.toString()
                ?: link.ifBlank { title },
            title = title,
            coverUrl = normalizeCoverUrl(cover),
            author = "",
            playCount = "",
            danmakuCount = "",
            duration = "",
            durationSeconds = 0,
            pubDate = null,
            desc = desc,
            badges = badge?.let { listOf(it) } ?: emptyList(),
            epSize = epSize,
            mediaScore = mediaScore,
            mediaScoreUsers = 0,
            mediaType = 0,
            seasonId = seasonId,
            mediaId = mediaId,
            seasonType = seasonType,
            seasonTypeName = "",
            url = link,
            buttonText = "",
            isFollow = isFinish == 1,
            cid = firstEp?.epId ?: 0
        )
    }

    private fun parseScoreValue(text: String): Double {
        if (text.isBlank()) return 0.0
        val cleaned = text.replace("分", "").replace(Regex("[^0-9.]"), "")
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    private fun parseEpisodeCount(text: String): Int {
        val match = Regex("(\\d+)").find(text)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun normalizeCoverUrl(url: String): String {
        if (url.isBlank()) return url
        return if (url.startsWith("//")) "https:$url" else url
    }

    // 辅助函数
    private fun isCurrentlyLoading(tabType: TabType): Boolean {
        return when (tabType) {
            TabType.HOT -> isHotLoading
            TabType.RECOMMEND -> isRecommendLoading
            TabType.BANGUMI -> isTimelineLoading
        }
    }

    private fun hasMoreData(tabType: TabType): Boolean {
        return when (tabType) {
            TabType.HOT -> hotHasMore
            TabType.RECOMMEND -> recommendHasMore
            TabType.BANGUMI -> false
        }
    }

    private fun setLoading(tabType: TabType, loading: Boolean) {
        when (tabType) {
            TabType.HOT -> isHotLoading = loading
            TabType.RECOMMEND -> isRecommendLoading = loading
            TabType.BANGUMI -> isTimelineLoading = loading
        }
    }

    private fun getCurrentVideoCount(tabType: TabType): Int {
        return when (tabType) {
            TabType.HOT -> _hotVideos.size
            TabType.RECOMMEND -> _recommendVideos.size
            TabType.BANGUMI -> timelineDays.sumOf { it.episodes.size }
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

    fun canLoadMoreBangumiRecommend(): Boolean {
        return !isBangumiRecommendLoading && bangumiRecommendHasMore
    }

    fun refreshCurrentTab(restoreFocusToGrid: Boolean = true) {
        if (isRefreshing) return
        val targetTab = selectedTab
        isRefreshing = true
        shouldRestoreFocusToGrid = restoreFocusToGrid
        viewModelScope.launch {
            if (targetTab != TabType.BANGUMI) {
                setLoading(targetTab, true)
            }
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
                    TabType.BANGUMI -> {
                        loadTimeline(forceRefresh = true)
                        bangumiRecommendPage = 1
                        bangumiRecommendHasMore = true
                        bangumiRecommendError = null
                        bangumiRecommendVideos.clear()
                        loadBangumiRecommend(reset = true)
                        _tabScrollStates[targetTab] = 0 to 0
                        _tabFocusStates[targetTab] = 0
                        refreshSignal++
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    if (targetTab != TabType.BANGUMI) {
                        setLoading(targetTab, false)
                    }
                    isRefreshing = false
                }
            }
        }
    }
}