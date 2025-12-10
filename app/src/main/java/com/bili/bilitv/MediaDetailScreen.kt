package com.bili.bilitv

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun MediaDetailScreen(
    media: Video,
    onBack: () -> Unit
) {
    val logTag = "MediaDetail"
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var detailMedia by remember { mutableStateOf(media) }
    var isAscending by remember { mutableStateOf(false) }
    var isDetailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var sections by remember { mutableStateOf<List<DetailSection>>(emptyList()) }

    val coverModel = remember(detailMedia.coverUrl) {
        ImageRequest.Builder(context)
            .data(detailMedia.coverUrl)
            .size(ImageConfig.VIDEO_COVER_SIZE)
            .build()
    }
    
    LaunchedEffect(media.id) {
        isDetailLoading = true
        detailError = null
        try {
            val (detail, sectionList) = loadMediaDetail(media)
            Log.d(logTag, "detail loaded id=${detail.id} sections=${sectionList.size}")
            detailMedia = detail
            sections = sectionList
        } catch (e: Exception) {
            detailError = e.localizedMessage ?: "加载详情失败"
        } finally {
            isDetailLoading = false
        }
    }
    
    val displayedMainEpisodes = remember(sections, isAscending) {
        val main = (sections.firstOrNull() as? EpisodeCardSection)?.episodes.orEmpty()
        if (isAscending) main else main.reversed()
    }

    val primaryEpisode = displayedMainEpisodes.firstOrNull()

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AsyncImage(
            model = coverModel,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.25f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState)
        ) {
            BoxWithConstraints {
                val infoHeight = (this@BoxWithConstraints.maxHeight * 0.4f).coerceIn(100.dp, 240.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(infoHeight)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 6.dp
                    ) {
                        AsyncImage(
                            model = coverModel,
                            contentDescription = detailMedia.title,
                            modifier = Modifier
                                .height(infoHeight)
                                .aspectRatio(3f / 4f),
                            contentScale = ContentScale.Crop
                        )
                    }

                    val infoWidth = (this@BoxWithConstraints.maxWidth - 238.dp).coerceAtLeast(0.dp)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .width(infoWidth)
                            .height(infoHeight)
                    ) {
                        Text(
                            text = detailMedia.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = buildString {
                                if (detailMedia.epSize > 0) append("全${detailMedia.epSize}话")
                                if (detailMedia.mediaScore > 0) append(" | 评分 ${String.format("%.1f", detailMedia.mediaScore)}")
                                if (detailMedia.mediaScoreUsers > 0) append("（${detailMedia.mediaScoreUsers}人评分）")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        Text(
                            text = detailMedia.desc.ifBlank { detailMedia.staff },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            var isPlayFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    val targetUrl = primaryEpisode?.url?.ifBlank { detailMedia.url } ?: detailMedia.url
                                    if (targetUrl.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.onFocusChanged { isPlayFocused = it.isFocused },
                                border = if (isPlayFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimary) else null
                            ) {
                                Text(text = media.buttonText.ifBlank { "开始观看" }, fontSize = 16.sp)
                            }

                            var isFollowFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.onFocusChanged { isFollowFocused = it.isFocused },
                                border = if (isFollowFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Text(text = "追番/追剧", fontSize = 16.sp)
                            }
                        }

                        if (detailMedia.cv.isNotBlank()) {
                            Text(
                                text = "CV：${detailMedia.cv}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (detailMedia.staff.isNotBlank()) {
                            Text(
                                text = "STAFF：${detailMedia.staff}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            sections.forEachIndexed { index, section ->
                when (section) {
                    is EpisodeCardSection -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = section.title.ifBlank { if (section.isMain) "正片剧集" else "其他分区" },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )

                            if (section.isMain) {
                                var isSortFocused by remember { mutableStateOf(false) }
                                TextButton(
                                    onClick = { isAscending = !isAscending },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.onFocusChanged { isSortFocused = it.isFocused },
                                    border = if (isSortFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Icon(
                                        imageVector = if (isAscending) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isAscending) "正序" else "倒序",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        val episodesToShow = if (section.isMain) displayedMainEpisodes else section.episodes
                        if (episodesToShow.isEmpty()) {
                            Text(
                                text = "暂无剧集信息",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                            ) {
                                items(episodesToShow) { ep ->
                                    EpisodeCard(
                                        episode = ep,
                                        onClick = {
                                            val targetUrl = ep.url.ifBlank { detailMedia.url }
                                            if (targetUrl.isNotBlank()) {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            }
                                        },
                                        modifier = Modifier.width(240.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }
                    is SeasonCardSection -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = section.title.ifBlank { "系列/多季" },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (section.seasons.isEmpty()) {
                            Text(
                                text = "暂无剧集信息",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(section.seasons) { season ->
                                    VerticalMediaCard(
                                        video = season,
                                        onClick = {
                                            val targetUrl = season.url.ifBlank { detailMedia.url }
                                            if (targetUrl.isNotBlank()) {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            }
                                        },
                                        modifier = Modifier.width(156.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
            
            when {
                isDetailLoading -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "正在加载详情…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                detailError != null -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = detailError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Serializable
private data class SeasonSimpleResponse(
    val code: Int,
    val message: String = "",
    val result: SeasonSimpleResult? = null
)

@Serializable
private data class SeasonSimpleResult(
    val seasons: List<SeasonSimpleItem>? = null
)

@Serializable
private data class SeasonSimpleItem(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("season_title") val seasonTitle: String = "",
    val cover: String = "",
    @SerialName("horizontal_cover_169") val horizontalCover169: String = "",
    @SerialName("horizontal_cover_1610") val horizontalCover1610: String = "",
    val badge: String = "",
    @SerialName("badge_info") val badgeInfo: SeasonBadgeInfo? = null,
    @SerialName("new_ep") val newEp: SeasonNewEp? = null,
    val link: String = ""
)

@Serializable
private data class SeasonBadgeInfo(
    val text: String = "",
    @SerialName("bg_color") val bgColor: String = "",
    @SerialName("text_color") val textColor: String = ""
)

@Serializable
private data class SeasonNewEp(
    @SerialName("index_show") val indexShow: String = ""
)

@Serializable
private data class MediaBasicResponse(
    val code: Int,
    val message: String = "",
    val result: MediaBasicResult? = null
)

@Serializable
private data class MediaBasicResult(
    val media: MediaBasicInfo? = null
)

@Serializable
private data class MediaBasicInfo(
    @SerialName("media_id") val mediaId: Long = 0,
    @SerialName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("horizontal_picture") val horizontalPicture: String = "",
    @SerialName("share_url") val shareUrl: String = "",
    val rating: MediaRating? = null,
    @SerialName("new_ep") val newEp: MediaNewEp? = null,
    val areas: List<MediaArea> = emptyList(),
    val type: Int = 0,
    @SerialName("type_name") val typeName: String = ""
)

@Serializable
private data class MediaRating(
    val score: Double = 0.0,
    val count: Int = 0
)

@Serializable
private data class MediaNewEp(
    @SerialName("index_show") val indexShow: String = ""
)

@Serializable
private data class MediaArea(
    val id: Int = 0,
    val name: String = ""
)

private sealed interface DetailSection {
    val title: String
}

private data class EpisodeCardSection(
    override val title: String = "",
    val type: Int = 0,
    val episodes: List<MediaEpisode> = emptyList(),
    val isMain: Boolean = false
) : DetailSection

private data class SeasonCardSection(
    override val title: String = "",
    val seasons: List<Video> = emptyList()
) : DetailSection

private enum class CardStyle { Video16x10, Cover3x4 }

@Serializable
private data class SeasonDetailResponse(
    val code: Int,
    val message: String = "",
    val result: SeasonDetailResult? = null
)

@Serializable
private data class SeasonDetailResult(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("media_id") val mediaId: Long = 0,
    val title: String = "",
    @SerialName("season_title") val seasonTitle: String = "",
    val cover: String = "",
    @SerialName("horizontal_cover_169") val horizontalCover169: String = "",
    @SerialName("horizontal_cover_1610") val horizontalCover1610: String = "",
    val evaluate: String = "",
    val rating: MediaRating? = null,
    @SerialName("new_ep") val newEp: SeasonNewEp? = null,
    val episodes: List<SeasonEpisode> = emptyList(),
    @SerialName("section") val section: List<SeasonSection> = emptyList(),
    @SerialName("sections") val sections: List<SeasonSection> = emptyList(),
    val seasons: List<SeasonBrief> = emptyList(),
    val series: SeasonSeries? = null,
    val type: Int = 0,
    @SerialName("type_name") val typeName: String = "",
    val link: String = "",
    val staff: String = "",
    val cv: String = ""
)

@Serializable
private data class SeasonEpisode(
    val id: Long = 0,
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    @SerialName("long_title") val longTitle: String = "",
    val cover: String = "",
    @SerialName("share_url") val shareUrl: String = "",
    val badge: String = "",
    @SerialName("badge_info") val badgeInfo: SeasonBadgeInfo? = null
)

@Serializable
private data class SeasonSection(
    val episodes: List<SeasonEpisode> = emptyList(),
    val title: String = "",
    val type: Int = 0
)

@Serializable
private data class SeasonBrief(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("season_title") val seasonTitle: String = "",
    val cover: String = "",
    @SerialName("horizontal_cover_169") val horizontalCover169: String = "",
    @SerialName("horizontal_cover_1610") val horizontalCover1610: String = "",
    val badge: String = "",
    @SerialName("badge_info") val badgeInfo: SeasonBadgeInfo? = null,
    @SerialName("new_ep") val newEp: SeasonNewEp? = null,
    val link: String = ""
) 

@Serializable
private data class SeasonSeries(
    @SerialName("series_id") val seriesId: Long = 0,
    val title: String = "",
    val seasons: List<SeasonBrief> = emptyList()
)

private suspend fun loadMediaDetail(initial: Video): Pair<Video, List<DetailSection>> {
    val client = OkHttpClient()
    val json = Json { ignoreUnknownKeys = true }
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

@Composable
private fun EpisodeCard(
    episode: MediaEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .onFocusChanged { isFocused = it.isFocused },
        border = if (isFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        onClick = onClick
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = episode.cover,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Bottom Shadow with Episode Number
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = episode.indexTitle.ifBlank { episode.title },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }

                if (episode.badges.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        episode.badges.take(2).forEach { badge ->
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = episode.longTitle.ifBlank { "${episode.indexTitle} ${episode.title}" },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

