package com.bili.bilitv

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import android.graphics.Color as AndroidColor
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Stable
import com.bili.bilitv.utils.FormatUtils

@Stable
@Composable
fun IconWithText(
    icon: ImageVector,
    text: String,
    iconSize: androidx.compose.ui.unit.Dp = 13.dp,
    textSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .offset(y = 0.5.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = textSize),
            color = tint
        )
    }
}

@Stable
data class Badge(
    val text: String = "",
    val textColor: String = "",
    val textColorNight: String = "",
    val bgColor: String = "",
    val bgColorNight: String = "",
    val borderColor: String = "",
    val borderColorNight: String = "",
    val bgStyle: Int = 0
)

@Stable
data class MediaEpisode(
    val id: Long = 0,
    val aid: Long = 0,
    val cid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val longTitle: String = "",
    val indexTitle: String = "",
    val cover: String = "",
    val url: String = "",
    val releaseDate: String = "",
    val badges: List<String> = emptyList()
)

data class Video(
    val id: String,
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String,
    val coverUrl: String,
    val author: String = "",
    val playCount: String = "",
    val danmakuCount: String = "",
    val duration: String = "",
    val durationSeconds: Long = 0,
    val pubDate: Long? = null,
    val desc: String = "",
    val badges: List<Badge> = emptyList(),
    val epSize: Int = 0,
    val mediaScore: Double = 0.0,
    val mediaScoreUsers: Int = 0,
    val mediaType: Int = 0,
    val seasonId: Long = 0,
    val mediaId: Long = 0,
    val seasonType: Int = 0,
    val seasonTypeName: String = "",
    val url: String = "",
    val buttonText: String = "",
    val isFollow: Boolean = false,
    val selectionStyle: String = "",
    val orgTitle: String = "",
    val cv: String = "",
    val staff: String = "",
    val episodes: List<MediaEpisode> = emptyList(),
    val bottomText: String? = null,
    val rightText: String? = null,
    val followCount: Long = 0
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@Stable
@Composable
fun VideoItem(
    video: Video,
    onClick: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .clip(cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick(video) }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            ),
            border = if (isFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Column {
                // 视频封面
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val imageModel = remember(video.coverUrl) {
                            ImageRequest.Builder(context)
                                .data(video.coverUrl)
                                .size(ImageConfig.VIDEO_COVER_SIZE)
                                .memoryCacheKey(video.coverUrl)
                                .diskCacheKey(video.coverUrl)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                .allowHardware(true)
                                .crossfade(false)
                                .build()
                        }
                        
                        AsyncImage(
                            model = imageModel,
                            contentDescription = video.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        
                        // Badge overlay (top-right)
                        val badge = video.badges.firstOrNull()
                        if (badge != null && badge.text.isNotBlank()) {
                            val bgColor = parseColorSafe(badge.bgColor, MaterialTheme.colorScheme.primary)
                            val textColor = parseColorSafe(badge.textColor, MaterialTheme.colorScheme.onPrimary)
                            val borderColor = parseColorSafe(badge.borderColor, bgColor)
                            
                            Surface(
                                color = bgColor,
                                contentColor = textColor,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .height(22.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = badge.text,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        // 底部渐变和信息展示
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                    )
                                )
                                .padding(start = 6.dp, end = 6.dp, bottom = 4.dp, top = 20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 播放数
                                if (video.playCount.isNotEmpty()) {
                                    IconWithText(
                                        icon = Icons.Filled.PlayArrow,
                                        text = FormatUtils.formatCount(video.playCount)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                // 弹幕数
                                if (video.danmakuCount.isNotEmpty()) {
                                    IconWithText(
                                        icon = Icons.Filled.Menu,
                                        text = FormatUtils.formatCount(video.danmakuCount)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.weight(1f))
                                
                                // 时长
                                if (video.duration.isNotEmpty()) {
                                    Text(
                                        text = video.duration,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall, // 增大字体
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp) // 调整间距
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.author,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    val rightContent = buildString {
                        if (!video.rightText.isNullOrEmpty()) {
                            append(video.rightText)
                        } else if (video.pubDate != null) {
                            append(formatUnixTimestamp(video.pubDate))
                        }
                    }
                    
                    if (rightContent.isNotEmpty()) {
                        Text(
                            text = rightContent,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalMediaCard(
    video: Video,
    onClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
    bottomContent: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .width(156.dp)
            .aspectRatio(3f / 4f)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick(video) }
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = if (isFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val imageModel = remember(video.coverUrl) {
                    ImageRequest.Builder(context)
                        .data(video.coverUrl)
                        .size(ImageConfig.VIDEO_COVER_SIZE)
                        .memoryCacheKey(video.coverUrl)
                        .diskCacheKey(video.coverUrl)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .allowHardware(true)
                        .crossfade(false)
                        .build()
                }

                AsyncImage(
                    model = imageModel,
                    contentDescription = video.title,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Badge overlay (top-right)
                val badge = video.badges.firstOrNull()
                if (badge != null && badge.text.isNotBlank()) {
                    val bgColor = parseColorSafe(badge.bgColor, MaterialTheme.colorScheme.primary)
                    val textColor = parseColorSafe(badge.textColor, MaterialTheme.colorScheme.onPrimary)
                    val borderColor = parseColorSafe(badge.borderColor, bgColor)

                    Surface(
                        color = bgColor,
                        contentColor = textColor,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .height(22.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = badge.text,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (bottomContent != null) {
                        bottomContent()
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (video.followCount > 0) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    Text(
                                        text = "${FormatUtils.formatCount(video.followCount.toString())}人追",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!video.bottomText.isNullOrBlank()) {
                                Text(
                                    text = video.bottomText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseColorSafe(hex: String, fallback: Color): Color {
    return try {
        if (hex.isBlank()) fallback else Color(AndroidColor.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}

// Utility function to format Unix timestamp
private fun formatUnixTimestamp(timestamp: Long): String {
    val date = Date(timestamp * 1000L) // Convert seconds to milliseconds
    return dateFormat.format(date)
}
