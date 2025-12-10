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
    var detailMedia by mutableStateOf<Video?>(null)
    var isAscending by mutableStateOf(false)
    var isDetailLoading by mutableStateOf(false)
    var detailError by mutableStateOf<String?>(null)
    var sections by mutableStateOf<List<DetailSection>>(emptyList())
    
    // Scroll state persistence
    var scrollPosition by mutableStateOf(0)
    var lastFocusedId by mutableStateOf<String?>(null)
    
    // LazyRow scroll states map: key is section index (or some unique key), value is (index, offset)
    var rowScrollStates = mutableMapOf<String, Pair<Int, Int>>()
    
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    fun loadMedia(media: Video) {
        // If we already have details for this video, don't reload unless force refresh (not implemented yet)
        if (detailMedia?.mediaId == media.mediaId && detailMedia?.seasonId == media.seasonId) {
            // Check if loaded details are complete (implied by sections not empty or detailMedia having episodes)
            if (sections.isNotEmpty() || (detailMedia?.episodes?.isNotEmpty() == true)) {
                return
            }
        }
        
        // Initial setup with passed media
        detailMedia = media
        scrollPosition = 0 // Reset scroll position for new media
        lastFocusedId = null // Reset focus for new media
        rowScrollStates.clear() // Reset horizontal scroll states
        isDetailLoading = true
        detailError = null
        
        viewModelScope.launch {
            try {
                val (detail, sectionList) = loadMediaDetail(media)
                Log.d("MediaDetail", "detail loaded id=${detail.id} sections=${sectionList.size}")
                detailMedia = detail
                sections = sectionList
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
}
