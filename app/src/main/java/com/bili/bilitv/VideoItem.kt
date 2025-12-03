package com.bili.bilitv

import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage

/**
 * 视频数据模型
 */
data class Video(
    val id: String,
    val bvid: String = "", // B站视频BV号
    val cid: Long = 0, // 视频CID
    val title: String,
    val coverUrl: String,
    val author: String = "",
    val playCount: String = "",
    val pubDate: Long? = null // Add pubDate field
)

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
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f, label = "scale")

    // 使用 Box 作为布局容器，保持占位大小不变
    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick(video) }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth() // 填满父容器（Grid Cell）
                .zIndex(if (isFocused) 1f else 0f) // 聚焦时层级提高
                .scale(scale), // 仅缩放视觉内容
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
                    SubcomposeAsyncImage(
                        model = video.coverUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            // 加载中占位图 - 居中显示，占容器50%宽度
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.video_placeholder),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .aspectRatio(1f),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        },
                        error = {
                            // 加载失败占位图 - 居中显示，占容器50%宽度
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.video_placeholder),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .aspectRatio(1f),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    )
                }
                
                // 视频标题
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    minLines = 2, // Ensure consistent height
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // 作者和时间
                Text(
                    text = buildString {
                        append(video.author)
                        video.pubDate?.let {
                            append(" · ${formatUnixTimestamp(it)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// Utility function to format Unix timestamp
private fun formatUnixTimestamp(timestamp: Long): String {
    val date = Date(timestamp * 1000L) // Convert seconds to milliseconds
    val formatter = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return formatter.format(date)
}
