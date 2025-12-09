package com.bili.bilitv

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

@Composable
fun MediaDetailScreen(
    media: Video,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coverModel = remember(media.coverUrl) {
        ImageRequest.Builder(context)
            .data(media.coverUrl)
            .size(ImageConfig.VIDEO_COVER_SIZE)
            .build()
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            BoxWithConstraints {
                val infoHeight = (this@BoxWithConstraints.maxHeight * 0.5f).coerceIn(220.dp, 360.dp)
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
                        if (media.orgTitle.isNotBlank()) {
                            Text(
                                text = media.orgTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

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
                            Button(
                                onClick = {
                                    val targetUrl = primaryEpisode?.url?.ifBlank { media.url } ?: media.url
                                    if (targetUrl.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(text = media.buttonText.ifBlank { "开始观看" }, fontSize = 16.sp)
                            }

                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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

            Text(
                text = "正片剧集",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (media.episodes.isEmpty()) {
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
                        .height(220.dp)
                ) {
                    items(media.episodes) { ep ->
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
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: MediaEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        onClick = onClick
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            AsyncImage(
                model = episode.cover,
                contentDescription = episode.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "${episode.indexTitle.ifBlank { episode.title }} ${episode.longTitle}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    episode.badges.take(2).forEach { badge ->
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

