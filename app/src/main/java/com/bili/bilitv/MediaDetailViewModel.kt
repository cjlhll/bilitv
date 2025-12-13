package com.bili.bilitv

import android.app.Application
import android.util.Log
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class MediaDetailViewModel(application: Application) : AndroidViewModel(application) {
    // 当前选中的媒体
    var currentMediaId by mutableStateOf<String?>(null)
    
    // 为每个媒体维护独立的状态
    private val mediaStates = mutableMapOf<String, MediaState>()
    
    // 当前状态，基于当前选中的媒体
    var detailMedia: Video? by mutableStateOf(null)
    var isAscending: Boolean by mutableStateOf(false)
    var isDetailLoading: Boolean by mutableStateOf(false)
    var detailError: String? by mutableStateOf(null)
    var sections: List<DetailSection> by mutableStateOf(emptyList())
    
    // Scroll state persistence
    var scrollPosition: Int by mutableStateOf(0)
    var lastFocusedId: String? by mutableStateOf(null)
    var lastFocusedButton: String? by mutableStateOf(null)
    var shouldRestoreFocus: Boolean by mutableStateOf(false)
    
    // LazyRow scroll states map: key is section index (or some unique key), value is (index, offset)
    var rowScrollStates: MutableMap<String, Pair<Int, Int>> by mutableStateOf(mutableMapOf())
    
    // 媒体状态数据类
    data class MediaState(
        var detailMedia: Video? = null,
        var isAscending: Boolean = false,
        var isDetailLoading: Boolean = false,
        var detailError: String? = null,
        var sections: List<DetailSection> = emptyList(),
        var scrollPosition: Int = 0,
        var lastFocusedId: String? = null,
        var lastFocusedButton: String? = null,
        var shouldRestoreFocus: Boolean = false,
        var rowScrollStates: MutableMap<String, Pair<Int, Int>> = mutableMapOf()
    )
    
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    // 保存当前媒体的状态
    private fun saveCurrentState() {
        currentMediaId?.let { id ->
            mediaStates[id] = MediaState(
                detailMedia = detailMedia,
                isAscending = isAscending,
                isDetailLoading = isDetailLoading,
                detailError = detailError,
                sections = sections,
                scrollPosition = scrollPosition,
                lastFocusedId = lastFocusedId,
                lastFocusedButton = lastFocusedButton,
                shouldRestoreFocus = shouldRestoreFocus,
                rowScrollStates = rowScrollStates.toMutableMap()
            )
        }
    }
    
    // 切换到指定媒体的状态
    private fun switchToMedia(mediaId: String) {
        saveCurrentState()
        currentMediaId = mediaId
        
        // 获取或创建媒体状态
        val state = mediaStates.getOrPut(mediaId) { MediaState() }
        
        // 恢复状态
        detailMedia = state.detailMedia
        isAscending = state.isAscending
        isDetailLoading = state.isDetailLoading
        detailError = state.detailError
        sections = state.sections
        scrollPosition = state.scrollPosition
        lastFocusedId = state.lastFocusedId
        lastFocusedButton = state.lastFocusedButton
        shouldRestoreFocus = state.shouldRestoreFocus
        rowScrollStates = state.rowScrollStates.toMutableMap()
    }
    
    fun loadMedia(media: Video) {
        val mediaId = "${media.seasonId}_${media.mediaId}"
        
        // 切换到该媒体的状态
        switchToMedia(mediaId)
        
        // 如果已经有完整的详情数据，不需要重新加载
        if (detailMedia?.mediaId == media.mediaId && detailMedia?.seasonId == media.seasonId) {
            if (sections.isNotEmpty() || (detailMedia?.episodes?.isNotEmpty() == true)) {
                return
            }
        }
        
        // 如果是第一次加载该媒体，重置状态
        if (mediaStates[mediaId]?.detailMedia == null) {
            scrollPosition = 0
            lastFocusedId = null
            lastFocusedButton = null
            shouldRestoreFocus = false
            rowScrollStates.clear()
        }
        
        // 设置初始媒体数据
        detailMedia = media
        isDetailLoading = true
        detailError = null
        
        viewModelScope.launch {
            try {
                val (detail, sectionList) = loadMediaDetail(media)
                Log.d("MediaDetail", "detail loaded id=${detail.id} sections=${sectionList.size}")
                detailMedia = detail
                sections = sectionList
                
                // 保存加载完成的状态
                saveCurrentState()
            } catch (e: Exception) {
                detailError = e.localizedMessage ?: "加载详情失败"
                e.printStackTrace()
            } finally {
                isDetailLoading = false
            }
        }
    }
    
    private suspend fun loadMediaDetail(initial: Video): Pair<Video, List<DetailSection>> {
        var seasonId = initial.seasonId
        var mediaId = initial.mediaId
        var basic: MediaBasicInfo? = null
        if (seasonId == 0L && mediaId != 0L) {
            basic = fetchMediaBasic(client, json, mediaId)
            seasonId = basic?.seasonId ?: 0L
        }
        if (seasonId == 0L) return initial to emptyList()
        val detail = fetchSeasonDetail(client, json, seasonId) ?: return initial to emptyList()
        val episodes = detail.episodes.ifEmpty { detail.sections.firstOrNull()?.episodes.orEmpty() }
        val allSections = buildList {
            if (detail.section.isNotEmpty()) addAll(detail.section)
            if (detail.sections.isNotEmpty()) addAll(detail.sections)
        }
        val mapEpisode: (SeasonEpisode) -> MediaEpisode = { ep ->
            val badgeText = ep.badgeInfo?.text?.ifBlank { ep.badge } ?: ep.badge
            MediaEpisode(
                id = ep.id,
                aid = ep.aid,
                cid = ep.cid,
                bvid = ep.bvid.ifBlank { detail.bvid }, // Use episode's bvid, fallback to season's bvid
                title = ep.title,
                longTitle = ep.longTitle,
                indexTitle = ep.title,
                cover = ep.cover,
                url = ep.shareUrl,
                releaseDate = "",
                badges = badgeText.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            )
        }
        val mappedEpisodes = episodes.map(mapEpisode)
        val cover = detail.cover.ifBlank { detail.horizontalCover169.ifBlank { detail.horizontalCover1610 } }
        val mapped = Video(
            id = detail.seasonId.takeIf { it != 0L }?.toString() ?: initial.id,
            aid = initial.aid,
            bvid = initial.bvid,
            cid = initial.cid,
            title = detail.seasonTitle.ifBlank { detail.title.ifBlank { initial.title } },
            coverUrl = cover.ifBlank { initial.coverUrl },
            author = initial.author,
            playCount = initial.playCount,
            danmakuCount = initial.danmakuCount,
            duration = initial.duration,
            durationSeconds = initial.durationSeconds,
            pubDate = initial.pubDate,
            desc = detail.evaluate.ifBlank { initial.desc },
            badges = initial.badges,
            epSize = episodes.size.takeIf { it > 0 } ?: initial.epSize,
            mediaScore = detail.rating?.score ?: initial.mediaScore,
            mediaScoreUsers = detail.rating?.count ?: initial.mediaScoreUsers,
            mediaType = detail.type.takeIf { it != 0 } ?: initial.mediaType,
            seasonId = detail.seasonId.takeIf { it != 0L } ?: initial.seasonId,
            mediaId = detail.mediaId.takeIf { it != 0L } ?: basic?.mediaId ?: initial.mediaId,
            seasonType = initial.seasonType,
            seasonTypeName = detail.typeName.ifBlank { initial.seasonTypeName },
            url = detail.link.ifBlank { basic?.shareUrl ?: initial.url },
            buttonText = initial.buttonText,
            isFollow = initial.isFollow,
            selectionStyle = initial.selectionStyle,
            orgTitle = initial.orgTitle,
            cv = detail.cv.ifBlank { initial.cv },
            staff = detail.staff.ifBlank { initial.staff },
            episodes = mappedEpisodes
        )
        val seasonMap = linkedMapOf<Long, Video>()
        val appendSeason: (SeasonBrief) -> Unit = { item ->
            if (item.seasonId != mapped.seasonId && !seasonMap.containsKey(item.seasonId)) {
                val badgeText = item.badgeInfo?.text?.ifBlank { item.badge } ?: item.badge
                val badge = badgeText.takeIf { it.isNotBlank() }?.let {
                    Badge(
                        text = it,
                        textColor = item.badgeInfo?.textColor ?: "#FFFFFF",
                        bgColor = item.badgeInfo?.bgColor ?: "#FB7299",
                        borderColor = item.badgeInfo?.bgColor ?: "#FB7299"
                    )
                }
                val itemCover = item.cover.ifBlank { item.horizontalCover169.ifBlank { item.horizontalCover1610 } }
                seasonMap[item.seasonId] = Video(
                    id = item.seasonId.toString(),
                    title = item.seasonTitle,
                    coverUrl = itemCover,
                    desc = item.newEp?.indexShow ?: "",
                    badges = badge?.let { listOf(it) } ?: emptyList(),
                    seasonId = item.seasonId,
                    url = item.link
                )
            }
        }
        detail.seasons.forEach { appendSeason(it) }
        detail.series?.seasons?.forEach { appendSeason(it) }

        val sectionList = buildList<DetailSection> {
            if (mappedEpisodes.isNotEmpty()) {
                add(EpisodeCardSection(title = "正片剧集", type = 0, episodes = mappedEpisodes, isMain = true))
            }
            allSections.forEach { sec ->
                Log.d("MediaDetail", "section title=${sec.title} type=${sec.type} eps=${sec.episodes.size}")
                val eps = sec.episodes.map(mapEpisode)
                if (eps.isNotEmpty()) {
                    add(EpisodeCardSection(title = sec.title, type = sec.type, episodes = eps, isMain = false))
                }
            }
            if (seasonMap.isNotEmpty()) {
                add(SeasonCardSection(title = detail.series?.title ?: "系列/多季", seasons = seasonMap.values.toList()))
            }
        }
        return mapped to sectionList
    }

    private suspend fun fetchMediaBasic(client: OkHttpClient, json: Json, mediaId: Long): MediaBasicInfo? {
        val url = "https://api.bilibili.com/pgc/review/user?media_id=$mediaId"
        val req = Request.Builder().url(url).get().build()
        return withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@use null
                val parsed = json.decodeFromString<MediaBasicResponse>(body)
                if (parsed.code != 0) return@use null
                parsed.result?.media
            }
        }
    }

    private suspend fun fetchSeasonDetail(client: OkHttpClient, json: Json, seasonId: Long): SeasonDetailResult? {
        val url = "https://api.bilibili.com/pgc/view/web/season?season_id=$seasonId"
        val req = Request.Builder().url(url).get().build()
        return withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@use null
                Log.d("MediaDetail", "season detail len=${body.length} snippet=${body.take(2000)}")
                val parsed = json.decodeFromString<SeasonDetailResponse>(body)
                if (parsed.code != 0) return@use null
                parsed.result
            }
        }
    }

    fun clearState() {
        saveCurrentState()
        currentMediaId = null
        
        detailMedia = null
        isAscending = false
        isDetailLoading = false
        detailError = null
        sections = emptyList()
        scrollPosition = 0
        lastFocusedId = null
        lastFocusedButton = null
        shouldRestoreFocus = false
        rowScrollStates.clear()
    }
    
    // 清空所有媒体状态（用于完全退出详情页时）
    fun clearAllStates() {
        mediaStates.clear()
        clearState()
    }
}
