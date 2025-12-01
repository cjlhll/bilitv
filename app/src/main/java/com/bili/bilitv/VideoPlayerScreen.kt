package com.bili.bilitv

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * 视频播放页面（使用ExoPlayer）
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Suppress("DEPRECATION")
@Composable
fun VideoPlayerScreen(
    videoPlayInfo: VideoPlayInfo,
    videoTitle: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 创建ExoPlayer实例
    val exoPlayer = remember {
        createExoPlayer(context, videoPlayInfo, 
            onReady = { isLoading = false },
            onError = { error -> 
                isLoading = false
                errorMessage = error
            }
        )
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            Log.d("BiliTV", "ExoPlayer released")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(videoTitle) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black,
        content = { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            // ExoPlayer播放器
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center)
            )

            // 加载指示器
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            // 错误信息
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "播放错误: $error",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // 视频信息
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "格式: ${videoPlayInfo.format.uppercase()}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "清晰度: ${getQualityName(videoPlayInfo.quality)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "时长: ${formatDuration(videoPlayInfo.duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        }
    )
}

/**
 * 创建ExoPlayer实例
 */
@OptIn(UnstableApi::class)
private fun createExoPlayer(
    context: Context,
    videoPlayInfo: VideoPlayInfo,
    onReady: () -> Unit,
    onError: (String) -> Unit
): ExoPlayer {
    val exoPlayer = ExoPlayer.Builder(context).build()
    
    // 设置播放监听
    exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                androidx.media3.common.Player.STATE_READY -> {
                    Log.d("BiliTV", "ExoPlayer ready")
                    onReady()
                }
                androidx.media3.common.Player.STATE_ENDED -> {
                    Log.d("BiliTV", "Playback ended")
                }
            }
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e("BiliTV", "ExoPlayer error: ${error.message}")
            onError("播放失败: ${error.message}")
        }
    })
    
    // 根据格式设置MediaItem
    val mediaItem = when (videoPlayInfo.format) {
        "dash" -> {
            // DASH格式：音视频分离，这里先只播放视频流
            // TODO: 完整的DASH支持需要合并音视频
            Log.d("BiliTV", "Playing DASH video stream: ${videoPlayInfo.videoUrl}")
            MediaItem.fromUri(videoPlayInfo.videoUrl)
        }
        "mp4" -> {
            // MP4格式：音视频已合并
            Log.d("BiliTV", "Playing MP4: ${videoPlayInfo.videoUrl}")
            MediaItem.fromUri(videoPlayInfo.videoUrl)
        }
        else -> {
            Log.w("BiliTV", "Unknown format: ${videoPlayInfo.format}, trying direct play")
            MediaItem.fromUri(videoPlayInfo.videoUrl)
        }
    }
    
    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.prepare()
    exoPlayer.playWhenReady = true
    
    return exoPlayer
}

/**
 * 获取清晰度名称
 */
private fun getQualityName(quality: Int): String {
    return when (quality) {
        127 -> "8K 超高清"
        126 -> "杜比视界"
        125 -> "HDR 真彩色"
        120 -> "4K 超清"
        116 -> "1080P60 高帧率"
        112 -> "1080P+ 高码率"
        100 -> "智能修复"
        80 -> "1080P 高清"
        74 -> "720P60 高帧率"
        64 -> "720P 高清"
        32 -> "480P 清晰"
        16 -> "360P 流畅"
        6 -> "240P 极速"
        else -> "未知 ($quality)"
    }
}

/**
 * 格式化时长
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}
