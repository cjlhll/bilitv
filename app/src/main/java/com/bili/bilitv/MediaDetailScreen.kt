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
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var isAscending by remember { mutableStateOf(false) }
    var otherSeasons by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isSeasonsLoading by remember { mutableStateOf(false) }
    var seasonsError by remember { mutableStateOf<String?>(null) }

    val coverModel = remember(media.coverUrl) {
        ImageRequest.Builder(context)
            .data(media.coverUrl)
            .size(ImageConfig.VIDEO_COVER_SIZE)
            .build()
    }
    
    LaunchedEffect(media.seasonId) {
        if (media.seasonId != 0L) {
            isSeasonsLoading = true
            seasonsError = null
            try {
                otherSeasons = fetchSeasonList(media.seasonId)
            } catch (e: Exception) {
                seasonsError = e.localizedMessage ?: "加载更多剧集失败"
            } finally {
                isSeasonsLoading = false
            }
        } else {
            otherSeasons = emptyList()
        }
    }
    
    val displayedEpisodes = remember(media.episodes, isAscending) {
        if (isAscending) media.episodes else media.episodes.reversed()
    }

    val primaryEpisode = media.episodes.firstOrNull()

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
                            contentDescription = media.title,
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
                            text = media.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = buildString {
                                if (media.epSize > 0) append("全${media.epSize}话")
                                if (media.mediaScore > 0) append(" | 评分 ${String.format("%.1f", media.mediaScore)}")
                                if (media.mediaScoreUsers > 0) append("（${media.mediaScoreUsers}人评分）")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        Text(
                            text = media.desc.ifBlank { media.staff },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            var isPlayFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    val targetUrl = primaryEpisode?.url?.ifBlank { media.url } ?: media.url
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

                        if (media.cv.isNotBlank()) {
                            Text(
                                text = "CV：${media.cv}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (media.staff.isNotBlank()) {
                            Text(
                                text = "STAFF：${media.staff}",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "正片剧集",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

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
            Spacer(modifier = Modifier.height(10.dp))

            if (displayedEpisodes.isEmpty()) {
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
                    items(displayedEpisodes) { ep ->
                        EpisodeCard(
                            episode = ep,
                            onClick = {
                                val targetUrl = ep.url.ifBlank { media.url }
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
            
            when {
                otherSeasons.isNotEmpty() -> {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "其他剧集",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(otherSeasons) { season ->
                            VerticalMediaCard(
                                video = season,
                                onClick = {
                                    val targetUrl = season.url.ifBlank { media.url }
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
                isSeasonsLoading -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "正在加载更多剧集…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                seasonsError != null -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = seasonsError ?: "",
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

private suspend fun fetchSeasonList(seasonId: Long): List<Video> {
    val client = OkHttpClient()
    val url = "https://api.bilibili.com/pgc/view/web/simple/season?season_id=$seasonId"
    val req = Request.Builder().url(url).get().build()
    val json = Json { ignoreUnknownKeys = true }
    return withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return@use emptyList()
            val parsed = json.decodeFromString<SeasonSimpleResponse>(body)
            if (parsed.code != 0) return@use emptyList()
            val list = parsed.result?.seasons.orEmpty()
            list.map { item ->
                val badgeText = item.badgeInfo?.text?.ifBlank { item.badge } ?: item.badge
                val badge = badgeText.takeIf { it.isNotBlank() }?.let {
                    Badge(
                        text = it,
                        textColor = item.badgeInfo?.textColor ?: "#FFFFFF",
                        bgColor = item.badgeInfo?.bgColor ?: "#FB7299",
                        borderColor = item.badgeInfo?.bgColor ?: "#FB7299"
                    )
                }
                val cover = item.cover.ifBlank { item.horizontalCover169.ifBlank { item.horizontalCover1610 } }
                Video(
                    id = item.seasonId.toString(),
                    title = item.seasonTitle,
                    coverUrl = cover,
                    desc = item.newEp?.indexShow ?: "",
                    badges = badge?.let { listOf(it) } ?: emptyList(),
                    seasonId = item.seasonId,
                    url = item.link
                )
            }
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

