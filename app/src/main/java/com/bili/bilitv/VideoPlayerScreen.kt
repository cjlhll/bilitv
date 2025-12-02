package com.bili.bilitv

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    
    // 播放器状态
    var currentTime by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    
    // 控制器状态
    var isOverlayVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    // 预览功能状态
    var videoshotData by remember { mutableStateOf<VideoshotData?>(null) }
    var previewTime by remember { mutableLongStateOf(-1L) } // -1表示不显示预览
    var isSeeking by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }
    
    // 长按Seek协程
    val coroutineScope = rememberCoroutineScope()
    var seekJob by remember { mutableStateOf<Job?>(null) }
    
    // 焦点控制
    val focusRequester = remember { FocusRequester() }
    
    // 请求焦点，确保能接收按键事件
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // 获取预览图数据
    LaunchedEffect(videoPlayInfo.bvid, videoPlayInfo.cid) {
        videoshotData = VideoPlayUrlFetcher.fetchVideoshot(videoPlayInfo.bvid, videoPlayInfo.cid)
    }

    // 3秒后自动隐藏
    LaunchedEffect(isOverlayVisible, lastInteractionTime, isSeeking, isLongPressing) {
        if (isSeeking || isLongPressing) {
            isOverlayVisible = true
        } else {
            if (isOverlayVisible) {
                delay(3000)
                isOverlayVisible = false
                previewTime = -1L // 隐藏预览
            }
        }
    }
    
    // 创建ExoPlayer实例
    val exoPlayer = remember {
        createExoPlayer(context, videoPlayInfo, 
            onReady = { 
                isLoading = false 
                duration = it.duration
            },
            onError = { error -> 
                isLoading = false
                errorMessage = error
            }
        )
    }
    
    // 定时更新进度
    LaunchedEffect(exoPlayer) {
        while (true) {
            // 只有在不处于seeking状态时才更新currentTime
            if (exoPlayer.isPlaying && !isSeeking && !isLongPressing) {
                currentTime = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0)
            }
            delay(1000)
        }
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            Log.d("BiliTV", "ExoPlayer released")
        }
    }

    Scaffold(
        containerColor = Color.Black,
        content = { paddingValues ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Black)
                    .focusRequester(focusRequester) // 绑定FocusRequester
                    .focusable() // 确保可聚焦
                    // 监听按键事件
                    .onPreviewKeyEvent { event ->
                        // 处理返回键
                        if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                if (isSeeking) {
                                    isSeeking = false
                                    previewTime = -1L
                                    isLongPressing = false
                                    seekJob?.cancel()
                                    isOverlayVisible = false
                                } else if (isOverlayVisible) {
                                    isOverlayVisible = false
                                } else {
                                    onBackClick()
                                }
                            }
                            return@onPreviewKeyEvent true // 消费事件，防止系统处理
                        }

                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            lastInteractionTime = System.currentTimeMillis()
                            
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER -> {
                                    // 显示菜单或确认
                                    isOverlayVisible = true
                                    if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || 
                                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                                        if (isSeeking) {
                                            // 确认seek
                                            exoPlayer.seekTo(previewTime)
                                            // 保持seek状态一小段时间，防止进度条跳变
                                            coroutineScope.launch {
                                                delay(500) // 等待播放器调整
                                                isSeeking = false
                                                previewTime = -1L
                                            }
                                        } else {
                                            // 切换播放/暂停
                                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                            isPlaying = !isPlaying
                                        }
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    // 标记为seeking状态
                                    if (!isSeeking) {
                                        isSeeking = true
                                        if (previewTime == -1L) previewTime = currentTime
                                    }
                                    isOverlayVisible = true
                                    
                                    // 长按逻辑
                                    if (event.nativeKeyEvent.repeatCount > 0) {
                                        if (!isLongPressing) {
                                            isLongPressing = true
                                            
                                            seekJob?.cancel()
                                            seekJob = coroutineScope.launch {
                                                while (isLongPressing) {
                                                    previewTime = (previewTime - 5000).coerceAtLeast(0)
                                                    delay(100) // 100ms 刷新一次
                                                }
                                            }
                                        }
                                    } else {
                                        // 短按逻辑 - 立即步进
                                        previewTime = (previewTime - 5000).coerceAtLeast(0)
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    // 标记为seeking状态
                                    if (!isSeeking) {
                                        isSeeking = true
                                        if (previewTime == -1L) previewTime = currentTime
                                    }
                                    isOverlayVisible = true
                                    
                                    // 长按逻辑
                                    if (event.nativeKeyEvent.repeatCount > 0) {
                                        if (!isLongPressing) {
                                            isLongPressing = true
                                            
                                            seekJob?.cancel()
                                            seekJob = coroutineScope.launch {
                                                while (isLongPressing) {
                                                    previewTime = (previewTime + 5000).coerceAtMost(duration)
                                                    delay(100) // 100ms 刷新一次
                                                }
                                            }
                                        }
                                    } else {
                                        // 短按逻辑 - 立即步进
                                        previewTime = (previewTime + 5000).coerceAtMost(duration)
                                    }
                                }
                            }
                        } else if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                            // 松开按键处理
                            if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || 
                                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                
                                if (isLongPressing) {
                                    // 长按结束
                                    isLongPressing = false
                                    seekJob?.cancel()
                                    // 松开后跳转播放
                                    exoPlayer.seekTo(previewTime)
                                    currentTime = previewTime
                                    
                                    // 延迟退出seeking状态，给播放器一点缓冲时间
                                    coroutineScope.launch {
                                        delay(1000)
                                        isSeeking = false
                                        previewTime = -1L
                                    }
                                } else if (isSeeking) {
                                    // 短按结束
                                    exoPlayer.seekTo(previewTime)
                                    currentTime = previewTime
                                    
                                    seekJob?.cancel()
                                    seekJob = coroutineScope.launch {
                                        delay(500) 
                                        isSeeking = false
                                        previewTime = -1L
                                    }
                                }
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        }
                        false
                    }
            ) {
                // 1. ExoPlayer播放器
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false // 禁用默认控制器
                            // 禁用焦点，确保Box接收按键事件
                            isFocusable = false
                            isClickable = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )

                // 2. 顶部标题栏 (移除了返回按钮)
                AnimatedVisibility(
                    visible = isOverlayVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = videoTitle,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    }
                }

                // 3. 加载指示器
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }

                // 4. 底部控制栏
                AnimatedVisibility(
                    visible = isOverlayVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                            .padding(start = 32.dp, end = 32.dp, bottom = 32.dp, top = 16.dp) // 增加边距，使其悬浮
                    ) {
                        // 预览窗口
                        if (isSeeking && previewTime >= 0 && videoshotData != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                            ) {
                                // 计算预览位置
                                val progress = if (duration > 0) previewTime.toFloat() / duration else 0f
                                
                                VideoshotPreview(
                                    data = videoshotData!!,
                                    time = previewTime,
                                    totalDuration = duration,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = (progress * 800).dp) // 这里的偏移量需要根据实际屏幕宽度动态计算，暂时简化
                                        .border(2.dp, Color.White, RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // 进度条和时间
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatDuration(if (isSeeking) previewTime else currentTime),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            // 自定义进度条样式
                            Slider(
                                value = if (isSeeking) previewTime.toFloat() else currentTime.toFloat(),
                                onValueChange = { 
                                    isSeeking = true
                                    previewTime = it.toLong()
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onValueChangeFinished = {
                                    exoPlayer.seekTo(previewTime)
                                    currentTime = previewTime
                                    coroutineScope.launch {
                                        delay(1000)
                                        isSeeking = false
                                        previewTime = -1L
                                    }
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(20.dp), // 增加触摸区域高度
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Text(
                                text = formatDuration(duration),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 视频信息
                        Text(
                            text = "${videoPlayInfo.format.uppercase()} | ${getQualityName(videoPlayInfo.quality)}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                
                // 错误显示
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier.align(Alignment.Center),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text("错误: $error", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    )
}

/**
 * 视频预览组件
 */
@Composable
fun VideoshotPreview(
    data: VideoshotData,
    time: Long,
    totalDuration: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    // 计算当前时间对应的图片索引
    val totalImages = (data.image?.size ?: 0) * data.img_x_len * data.img_y_len
    if (totalImages == 0) return
    
    val index = if (totalDuration > 0) {
        ((time.toDouble() / totalDuration) * totalImages).toInt().coerceIn(0, totalImages - 1)
    } else 0
    
    // 计算所在的拼图索引
    val sheetSize = data.img_x_len * data.img_y_len
    val sheetIndex = index / sheetSize
    val internalIndex = index % sheetSize
    
    // 计算在拼图中的行列
    val row = internalIndex / data.img_x_len
    val col = internalIndex % data.img_x_len
    
    // 加载图片
    LaunchedEffect(sheetIndex) {
        if (data.image != null && sheetIndex < data.image.size) {
            val url = data.image[sheetIndex]
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // 需要软Bitmap进行Canvas绘制
                .build()
            
            val result = loader.execute(request)
            if (result is SuccessResult) {
                bitmap = (result.drawable as BitmapDrawable).bitmap.asImageBitmap()
            }
        }
    }
    
    if (bitmap != null) {
        Canvas(modifier = modifier.size(160.dp, 90.dp)) {
            val srcX = col * data.img_x_size
            val srcY = row * data.img_y_size
            
            drawImage(
                image = bitmap!!,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(data.img_x_size, data.img_y_size),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )
        }
    }
}

/**
 * 创建ExoPlayer实例
 */
@OptIn(UnstableApi::class)
private fun createExoPlayer(
    context: Context,
    videoPlayInfo: VideoPlayInfo,
    onReady: (ExoPlayer) -> Unit,
    onError: (String) -> Unit
): ExoPlayer {
    // 配置请求头
    val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    val headers = mapOf(
        "Referer" to "https://www.bilibili.com",
        "User-Agent" to userAgent
    )

    val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(headers)

    val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSourceFactory)

    val exoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
    
    exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                androidx.media3.common.Player.STATE_READY -> {
                    onReady(exoPlayer)
                }
            }
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            onError("播放失败: ${error.message}")
        }
    })
    
    // 根据格式创建不同的MediaSource
    if (videoPlayInfo.format == "dash" && !videoPlayInfo.audioUrl.isNullOrEmpty()) {
        // DASH格式：合并视频和音频流
        val videoMediaItem = MediaItem.fromUri(videoPlayInfo.videoUrl)
        val audioMediaItem = MediaItem.fromUri(videoPlayInfo.audioUrl)
        
        val videoMediaSource = mediaSourceFactory.createMediaSource(videoMediaItem)
        val audioMediaSource = mediaSourceFactory.createMediaSource(audioMediaItem)
        
        // 合并音视频源
        val mergedMediaSource = androidx.media3.exoplayer.source.MergingMediaSource(
            videoMediaSource,
            audioMediaSource
        )
        
        exoPlayer.setMediaSource(mergedMediaSource)
    } else {
        // MP4格式或其他情况：单一视频流
        val mediaItem = MediaItem.fromUri(videoPlayInfo.videoUrl)
        exoPlayer.setMediaItem(mediaItem)
    }
    
    exoPlayer.prepare()
    exoPlayer.playWhenReady = true
    
    return exoPlayer
}

private fun getQualityName(quality: Int): String {
    return when (quality) {
        127 -> "8K"
        120 -> "4K"
        116 -> "1080P60"
        80 -> "1080P"
        64 -> "720P"
        32 -> "480P"
        else -> "未知"
    }
}

private fun formatDuration(seconds: Long): String {
    val totalSeconds = seconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
