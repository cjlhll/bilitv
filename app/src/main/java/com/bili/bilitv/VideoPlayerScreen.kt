package com.bili.bilitv

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.View
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.bili.bilitv.danmaku.DanmakuManager
import com.bili.bilitv.danmaku.DanmakuRepository
import com.bili.bilitv.danmaku.live.DanmakuLiveManager // Live Danmaku
import master.flame.danmaku.ui.widget.DanmakuView
import com.bilibili.community.service.dm.v1.DmSegMobileReply // For dummy parser
import com.bili.bilitv.danmaku.BiliDanmakuParser // For dummy parser
import com.bili.bilitv.danmaku.BiliDanmakuDataSource // For dummy parser

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
    isLiveStream: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 设置保持屏幕常亮
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
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
    
    // Danmaku Manager
    val danmakuView = remember { DanmakuView(context) }
    val danmakuManager = remember { DanmakuManager(danmakuView) }
    val danmakuLiveManager = remember { DanmakuLiveManager(danmakuManager) } // Live Danmaku Manager
    
    // 弹幕分段加载状态
    var currentCid by remember { mutableLongStateOf(0L) }

    // 请求焦点，确保能接收按键事件
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // 获取预览图数据（仅限普通视频）和加载弹幕
    LaunchedEffect(videoPlayInfo.bvid, videoPlayInfo.cid, isLiveStream) {
        danmakuManager.release() // Always release previous danmaku resources
        danmakuManager.pause() // Ensure danmaku is paused before new loading
        danmakuManager.clearLoadedSegments() // 清空已加载分段
        currentCid = videoPlayInfo.cid

        if (!isLiveStream) {
            // VOD Video logic
            videoshotData = VideoPlayUrlFetcher.fetchVideoshot(videoPlayInfo.bvid, videoPlayInfo.cid)
            Log.d("BiliTV", "Fetched videoshotData: ${videoshotData != null}")
            if (videoshotData == null) {
                Log.e("BiliTV", "Videoshot data is null for bvid=${videoPlayInfo.bvid}, cid=${videoPlayInfo.cid}")
            } else {
                videoshotData?.let { data ->
                    Log.d("BiliTV", "VideoshotData details: image count=${data.image?.size}, img_x_len=${data.img_x_len}, img_y_len=${data.img_y_len}, img_x_size=${data.img_x_size}, img_y_size=${data.img_y_size}")
                    data.image?.forEachIndexed { index, url ->
                        Log.d("BiliTV", "Videoshot image $index: $url")
                    }
                }
            }
            
            // Fetch VOD Danmaku (will prepare danmakuView internally)
            launch(kotlinx.coroutines.Dispatchers.Main) {
                val danmakuData = DanmakuRepository.fetchDanmaku(videoPlayInfo.cid, 1)
                if (danmakuData != null) {
                    danmakuManager.loadDanmaku(danmakuData, 1) // 加载第一个分段
                    danmakuManager.resume() // Resume drawing after loading
                }
            }
            danmakuLiveManager.stop() // Stop live danmaku if playing
        } else {
            // Live Stream logic
            videoshotData = null // No videoshot for live
            
            // Prepare danmakuView for live stream (manual adding)
            val dummyParser = BiliDanmakuParser().apply {
                load(BiliDanmakuDataSource(DmSegMobileReply.getDefaultInstance())) // Empty data source
            }
            danmakuView.prepare(dummyParser, danmakuManager.getDanmakuContext())
            danmakuView.show() // Make sure DanmakuView is shown
            danmakuManager.resume() // Resume drawing for live danmaku

            danmakuLiveManager.start(videoPlayInfo.cid) // Start live danmaku with room ID
        }
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
                danmakuManager.resume() // Resume danmaku drawing regardless of type
            },
            onError = { error -> 
                isLoading = false
                errorMessage = error
            }
        )
    }
    
    // 定时更新进度（直播流不显示进度）并动态加载弹幕分段
    LaunchedEffect(exoPlayer, isLiveStream) {
        while (true) {
            // 只有在不处于seeking状态且不是直播流时才更新currentTime
            if (exoPlayer.isPlaying && !isSeeking && !isLongPressing && !isLiveStream) {
                currentTime = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0)
                
                // 动态加载弹幕分段（每6分钟为一个分段）
                val currentSegment = (currentTime / (6 * 60 * 1000)) + 1 // 分段索引从1开始
                val nextSegment = currentSegment + 1 // 预加载下一个分段
                
                // 加载当前分段
                if (!danmakuManager.isSegmentLoaded(currentSegment.toInt())) {
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        val danmakuData = DanmakuRepository.fetchDanmaku(currentCid, currentSegment.toInt())
                        if (danmakuData != null) {
                            danmakuManager.loadDanmaku(danmakuData, currentSegment.toInt())
                        }
                    }
                }
                
                // 预加载下一个分段（当当前分段播放超过一半时）
                val segmentProgress = (currentTime % (6 * 60 * 1000)).toFloat() / (6 * 60 * 1000)
                if (segmentProgress > 0.5f && !danmakuManager.isSegmentLoaded(nextSegment.toInt())) {
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        val danmakuData = DanmakuRepository.fetchDanmaku(currentCid, nextSegment.toInt())
                        if (danmakuData != null) {
                            danmakuManager.loadDanmaku(danmakuData, nextSegment.toInt())
                        }
                    }
                }
            }
            delay(200)
        }
    }
    
    // 清理资源 - ExoPlayer必须在主线程释放
    DisposableEffect(Unit) {
        onDispose {
            // ExoPlayer必须在主线程释放
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    exoPlayer.release()
                    danmakuManager.release()
                    danmakuLiveManager.stop()
                    Log.d("BiliTV", "ExoPlayer and Danmaku resources released")
                } catch (e: Exception) {
                    Log.e("BiliTV", "Error releasing resources", e)
                }
            }
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
                                        if (isSeeking && !isLiveStream) {
                                            // 确认seek（仅限普通视频）
                                            exoPlayer.seekTo(previewTime)
                                            danmakuManager.seekTo(previewTime) // Sync Danmaku
                                            // 保持seek状态一小段时间，防止进度条跳变
                                            coroutineScope.launch {
                                                delay(500) // 等待播放器调整
                                                isSeeking = false
                                                previewTime = -1L
                                            }
                                        } else {
                                            // 切换播放/暂停
                                            if (exoPlayer.isPlaying) {
                                                exoPlayer.pause()
                                                danmakuManager.pause()
                                            } else {
                                                exoPlayer.play()
                                                danmakuManager.resume()
                                            }
                                            isPlaying = !isPlaying
                                        }
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    // 直播流不支持快退
                                    if (isLiveStream) return@onPreviewKeyEvent false
                                    
                                    // 标记为seeking状态
                                    if (!isSeeking) {
                                        isSeeking = true
                                        if (previewTime == -1L) previewTime = currentTime
                                    }
                                    isOverlayVisible = true
                                    Log.d("BiliTV", "DPAD_LEFT DOWN: isSeeking=$isSeeking, previewTime=$previewTime")
                                    
                                    // 长按逻辑
                                    if (event.nativeKeyEvent.repeatCount > 0) {
                                        if (!isLongPressing) {
                                            isLongPressing = true
                                            
                                            seekJob?.cancel()
                                            seekJob = coroutineScope.launch {
                                                while (isLongPressing) {
                                                    previewTime = (previewTime - 5000).coerceAtLeast(0)
                                                    Log.d("BiliTV", "DPAD_LEFT LONG PRESS: previewTime=$previewTime")
                                                    delay(100) // 100ms 刷新一次
                                                }
                                            }
                                        }
                                    } else {
                                        // 短按逻辑 - 立即步进
                                        previewTime = (previewTime - 5000).coerceAtLeast(0)
                                        Log.d("BiliTV", "DPAD_LEFT SHORT PRESS: previewTime=$previewTime")
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    // 直播流不支持快进
                                    if (isLiveStream) return@onPreviewKeyEvent false
                                    
                                    // 标记为seeking状态
                                    if (!isSeeking) {
                                        isSeeking = true
                                        if (previewTime == -1L) previewTime = currentTime
                                    }
                                    isOverlayVisible = true
                                    Log.d("BiliTV", "DPAD_RIGHT DOWN: isSeeking=$isSeeking, previewTime=$previewTime")
                                    
                                    // 长按逻辑
                                    if (event.nativeKeyEvent.repeatCount > 0) {
                                        if (!isLongPressing) {
                                            isLongPressing = true
                                            
                                            seekJob?.cancel()
                                            seekJob = coroutineScope.launch {
                                                while (isLongPressing) {
                                                    previewTime = (previewTime + 5000).coerceAtMost(duration)
                                                    Log.d("BiliTV", "DPAD_RIGHT LONG PRESS: previewTime=$previewTime")
                                                    delay(100) // 100ms 刷新一次
                                                }
                                            }
                                        }
                                    } else {
                                        // 短按逻辑 - 立即步进
                                        previewTime = (previewTime + 5000).coerceAtMost(duration)
                                        Log.d("BiliTV", "DPAD_RIGHT SHORT PRESS: previewTime=$previewTime")
                                    }
                                }
                            }
                        } else if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                            // 松开按键处理
                            if ((event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || 
                                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && !isLiveStream) {
                                
                                if (isLongPressing) {
                                    // 长按结束
                                    isLongPressing = false
                                    seekJob?.cancel()
                                    // 松开后跳转播放
                                    exoPlayer.seekTo(previewTime)
                                    danmakuManager.seekTo(previewTime) // Sync Danmaku
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
                                    danmakuManager.seekTo(previewTime) // Sync Danmaku
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

                // 1.5 Danmaku View
                AndroidView(
                    factory = { 
                        danmakuView.apply {
                            isFocusable = false
                            isFocusableInTouchMode = false
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
                            .padding(start = 12.dp, end = 12.dp, bottom = 32.dp, top = 16.dp) // 增加边距，使其悬浮
                    ) {
                        // 进度条和时间
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!isLiveStream) {
                                Text(
                                    text = formatDuration(if (isSeeking) previewTime else currentTime),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))

                                // 预览窗口容器 + 进度条
                                Box(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    var sliderWidth by remember { mutableStateOf(0) }
                                    
                                    // 自定义进度条样式
                                    val primaryColor = MaterialTheme.colorScheme.primary
                                    val inactiveColor = Color.White.copy(alpha = 0.3f)
                                    
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
                                            .fillMaxWidth()
                                            .height(20.dp) // 增加触摸区域高度
                                            .onGloballyPositioned { coordinates ->
                                                sliderWidth = coordinates.size.width
                                            },
                                        colors = SliderDefaults.colors(
                                            thumbColor = primaryColor,
                                            activeTrackColor = primaryColor,
                                            inactiveTrackColor = inactiveColor
                                        ),
                                        thumb = {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .offset(y = 2.dp)
                                                    .background(primaryColor, androidx.compose.foundation.shape.CircleShape)
                                            )
                                        },
                                        track = { sliderState ->
                                            val fraction = (sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                                            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                                                // 绘制背景轨道
                                                drawRoundRect(
                                                    color = inactiveColor,
                                                    size = size,
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                                                )
                                                // 绘制已播放轨道
                                                drawRoundRect(
                                                    color = primaryColor,
                                                    size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                                                )
                                            }
                                        }
                                    )
                                    
                                    // 预览窗口容器（绝对定位在Slider上方）
                                    if (isSeeking && previewTime >= 0 && videoshotData != null) {
                                        // 计算预览位置
                                        val progress = if (duration > 0) previewTime.toFloat() / duration else 0f
                                        
                                        // 转换sliderWidth从px到dp
                                        val density = LocalDensity.current
                                        val sliderWidthDp = with(density) { sliderWidth.toDp() }
                                        val previewWidth = 160.dp
                                        val previewHeight = 90.dp
                                        
                                        // 计算offset X: (sliderWidth * progress) - (previewWidth / 2)
                                        val offsetX = (sliderWidthDp * progress) - (previewWidth / 2)
                                        
                                        // 限制边界
                                        val constrainedOffsetX = minOf(maxOf(offsetX, 0.dp), sliderWidthDp - previewWidth)
                                        
                                        Log.d("BiliTV", "VideoshotPreview visible check: isSeeking=$isSeeking, previewTime=$previewTime, videoshotData=${videoshotData != null}")
                                        
                                        // 使用绝对定位，将预览窗口放在进度条上方
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .offset(y = (-previewHeight - 10.dp), x = constrainedOffsetX)
                                                .size(previewWidth, previewHeight)
                                                .border(2.dp, Color.White, RoundedCornerShape(4.dp))
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Black)
                                        ) {
                                            // 添加调试信息显示
                                            if (videoshotData != null) {
                                                VideoshotPreview(
                                                    data = videoshotData!!,
                                                    time = previewTime,
                                                    totalDuration = duration,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "No preview data",
                                                        color = Color.White,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Text(
                                    text = formatDuration(duration),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                            // 直播流显示"直播中"标识
                            Text(
                                text = "直播中",
                                color = Color.Red,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 视频信息
                        Text(
                            text = if (isLiveStream) "直播流 | ${videoPlayInfo.format.uppercase()}" 
                                  else "${videoPlayInfo.format.uppercase()} | ${getQualityName(videoPlayInfo.quality)}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // 5. 沉浸式播放时的底部进度条 (仅限非直播)
                AnimatedVisibility(
                    visible = !isOverlayVisible && !isLiveStream,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5f.dp)
                            .background(Color.Transparent)
                    ) {
                        val progress = if (duration > 0) currentTime.toFloat() / duration.toFloat() else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                                .background(Color.White.copy(alpha = 0.5f))
                        )
                    }
                }
                
                // 6. 暂停图标显示
                AnimatedVisibility(
                    visible = !isPlaying,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(40.dp)) {
                            val barWidth = size.width / 3
                            val barHeight = size.height
                            val gap = barWidth / 2
                            
                            // 绘制左侧暂停条
                            drawRect(
                                color = Color.White,
                                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                            )
                            
                            // 绘制右侧暂停条
                            drawRect(
                                color = Color.White,
                                topLeft = androidx.compose.ui.geometry.Offset(barWidth + gap, 0f),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                            )
                        }
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
 * 视频预览组件（带缓存优化）
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
    Log.d("BiliTV", "VideoshotPreview: totalImages=$totalImages, time=$time, totalDuration=$totalDuration")
    if (totalImages == 0) {
        Log.e("BiliTV", "VideoshotPreview: totalImages is 0, returning early")
        return
    }
    
    val index = if (totalDuration > 0) {
        ((time.toDouble() / totalDuration) * totalImages).toInt().coerceIn(0, totalImages - 1)
    } else 0
    
    Log.d("BiliTV", "VideoshotPreview: index=$index, totalImages=$totalImages")
    
    // 计算所在的拼图索引
    val sheetSize = data.img_x_len * data.img_y_len
    val sheetIndex = index / sheetSize
    val internalIndex = index % sheetSize
    
    // 计算在拼图中的行列
    val row = internalIndex / data.img_x_len
    val col = internalIndex % data.img_x_len
    
    Log.d("BiliTV", "VideoshotPreview: sheetIndex=$sheetIndex, internalIndex=$internalIndex, row=$row, col=$col")
    Log.d("BiliTV", "VideoshotPreview: img_x_len=${data.img_x_len}, img_y_len=${data.img_y_len}, img_x_size=${data.img_x_size}, img_y_size=${data.img_y_size}")
    
    // 使用缓存加载图片
    val cachedBitmap = remember(sheetIndex) {
        mutableStateOf<ImageBitmap?>(null)
    }
    
    // 如果缓存中有，直接使用
    if (cachedBitmap.value != null) {
        bitmap = cachedBitmap.value
        Log.d("BiliTV", "VideoshotPreview: Using cached bitmap for sheetIndex=$sheetIndex")
    } else {
        // 加载图片
        LaunchedEffect(sheetIndex) {
            if (data.image != null && sheetIndex < data.image.size) {
                val url = if (data.image[sheetIndex].startsWith("//")) "https:${data.image[sheetIndex]}" else data.image[sheetIndex]
                Log.d("BiliTV", "VideoshotPreview: Loading image for sheetIndex=$sheetIndex, URL=$url")
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                
                try {
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val loadedBitmap = (result.drawable as BitmapDrawable).bitmap.asImageBitmap()
                        Log.d("BiliTV", "VideoshotPreview: Bitmap loaded successfully, size=${loadedBitmap.width}x${loadedBitmap.height}")
                        cachedBitmap.value = loadedBitmap
                        bitmap = loadedBitmap
                    } else {
                        Log.e("BiliTV", "VideoshotPreview: Failed to load bitmap, result=$result")
                    }
                } catch (e: Exception) {
                    Log.e("BiliTV", "VideoshotPreview: Exception loading image", e)
                }
            } else {
                Log.e("BiliTV", "VideoshotPreview: data.image is null or sheetIndex out of bounds. data.image=${data.image}, sheetIndex=$sheetIndex")
            }
        }
    }
    
    if (bitmap != null) {
        Canvas(modifier = modifier.size(160.dp, 90.dp)) {
            // 根据实际bitmap尺寸重新计算小图尺寸
            val actualImgWidth = bitmap!!.width / data.img_x_len
            val actualImgHeight = bitmap!!.height / data.img_y_len
            
            val srcX = col * actualImgWidth
            val srcY = row * actualImgHeight
            
            val dstWidth = size.width.roundToInt()
            val dstHeight = size.height.roundToInt()

            Log.d("BiliTV", "VideoshotPreview: Drawing image. srcOffset=($srcX,$srcY), srcSize=($actualImgWidth,$actualImgHeight), dstSize=($dstWidth,$dstHeight)")
            Log.d("BiliTV", "VideoshotPreview: Bitmap size=${bitmap!!.width}x${bitmap!!.height}, actual tile size=${actualImgWidth}x${actualImgHeight}")
            
            // 检查裁剪区域是否在bitmap范围内
            if (srcX + actualImgWidth <= bitmap!!.width && srcY + actualImgHeight <= bitmap!!.height) {
                drawImage(
                    image = bitmap!!,
                    srcOffset = IntOffset(srcX, srcY),
                    srcSize = IntSize(actualImgWidth, actualImgHeight),
                    dstSize = IntSize(dstWidth, dstHeight)
                )
                Log.d("BiliTV", "VideoshotPreview: Image drawn successfully")
            } else {
                Log.e("BiliTV", "VideoshotPreview: Crop region out of bounds! srcX=$srcX, srcY=$srcY, actualImgWidth=$actualImgWidth, actualImgHeight=$actualImgHeight, bitmap size=${bitmap!!.width}x${bitmap!!.height}")
                // 绘制错误占位符
                drawRect(Color.DarkGray.copy(alpha = 0.8f))
            }
        }
    } else {
        Log.d("BiliTV", "VideoshotPreview: Bitmap is null, not drawing Canvas.")
        // 显示加载状态
        Box(
            modifier = modifier.size(160.dp, 90.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "加载中...",
                color = Color.White,
                fontSize = 12.sp
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
        "Referer" to if (videoPlayInfo.bvid.startsWith("live_")) "https://live.bilibili.com" else "https://www.bilibili.com",
        "User-Agent" to userAgent,
        "Origin" to if (videoPlayInfo.bvid.startsWith("live_")) "https://live.bilibili.com" else "https://www.bilibili.com"
    )

    val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(headers)

    // 创建支持多种格式的MediaSourceFactory
    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

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
        val videoMediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(videoPlayInfo.videoUrl))
        val audioMediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(videoPlayInfo.audioUrl))
        
        // 合并音视频源
        val mergedMediaSource = androidx.media3.exoplayer.source.MergingMediaSource(
            videoMediaSource,
            audioMediaSource
        )
        
        exoPlayer.setMediaSource(mergedMediaSource)
    } else {
        // HLS、FLV、MP4格式：单一视频流
        // 为HLS流设置特殊的MediaItem
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(videoPlayInfo.videoUrl)
        
        // 如果是HLS流，设置MIME类型
        if (videoPlayInfo.videoUrl.endsWith(".m3u8")) {
            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }
        
        val mediaItem = mediaItemBuilder.build()
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
