package com.bili.bilitv

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 通用图标+文字组件（用于视频卡片和直播卡片的统计信息显示）
 */
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
                .offset(y = 0.5.dp) // 统一向下偏移0.5dp与文字对齐
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = textSize),
            color = tint
        )
    }
}

/**
 * 视频数据模型
 */
data class Video(
    val id: String,
    val aid: Long = 0, // 视频AV号
    val bvid: String = "", // B站视频BV号
    val cid: Long = 0, // 视频CID
    val title: String,
    val coverUrl: String,
    val author: String = "",
    val playCount: String = "",
    val danmakuCount: String = "", // Add danmakuCount field
    val duration: String = "", // Add duration field
    val pubDate: Long? = null // Add pubDate field
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/**
 * 视频列表项组件
 * @param video 视频数据
 * @param onClick 点击事件
 */
@Composable
fun VideoItem(
    video: Video,
    onClick: (Video) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick(video) }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isFocused) 1f else 0f),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isFocused) 8.dp else 2.dp
            ),
            border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null
        ) {
            Column {
                // 视频封面
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // 占位图：始终显示，缩小居中
                        Image(
                            painter = painterResource(id = R.drawable.video_placeholder),
                            contentDescription = video.title,
                            modifier = Modifier.fillMaxSize(0.8f),
                            contentScale = ContentScale.Fit
                        )
                        
                        // 实际图片：加载成功后覆盖占位图，铺满容器
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(video.coverUrl)
                                // 使用统一配置的图片尺寸
                                .size(ImageConfig.VIDEO_COVER_SIZE)
                                // 缓存key使用URL，确保缓存命中
                                .memoryCacheKey(video.coverUrl)
                                .diskCacheKey(video.coverUrl)
                                // 启用内存缓存和磁盘缓存
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                .crossfade(true)
                                .build(),
                            contentDescription = video.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        
                        // 底部渐变和信息展示
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
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
                                        text = video.playCount
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                
                                // 弹幕数
                                if (video.danmakuCount.isNotEmpty()) {
                                    IconWithText(
                                        icon = Icons.Filled.Menu,
                                        text = video.danmakuCount
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

                Text(
                    text = buildString {
                        append(video.author)
                        video.pubDate?.let {
                            append(" · ${formatUnixTimestamp(it)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// Utility function to format Unix timestamp
private fun formatUnixTimestamp(timestamp: Long): String {
    val date = Date(timestamp * 1000L) // Convert seconds to milliseconds
    return dateFormat.format(date)
}
