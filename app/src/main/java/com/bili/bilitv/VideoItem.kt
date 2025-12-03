package com.bili.bilitv

import java.text.SimpleDateFormat
import java.util.Date
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import coil.size.Size

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
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(video.coverUrl)
                            // 明确指定图片大小，避免解码大图后再缩放，减少内存开销
                            .size(Size.ORIGINAL) // 使用原始尺寸，由ContentScale.Crop处理缩放
                            // 缓存key使用URL，确保缓存命中
                            .memoryCacheKey(video.coverUrl)
                            .diskCacheKey(video.coverUrl)
                            .build(),
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
                
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 3.dp)
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
    val formatter = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return formatter.format(date)
}
