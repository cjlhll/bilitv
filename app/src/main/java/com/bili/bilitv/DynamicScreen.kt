package com.bili.bilitv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

import android.util.Log
import com.bili.bilitv.BuildConfig

@Composable
fun DynamicScreen(
    viewModel: DynamicViewModel = viewModel(),
    loggedInSession: LoggedInSession?,
    onEnterFullScreen: (VideoPlayInfo, String) -> Unit = { _, _ -> }
) {
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }
    
    // 进入动画
    LaunchedEffect(Unit) {
        isVisible = true
    }

    LaunchedEffect(loggedInSession) {
        if (BuildConfig.DEBUG) {
            Log.d("BiliTV", "DynamicScreen: loggedInSession is ${if (loggedInSession == null) "null" else "not null"}")
        }
        if (loggedInSession != null) {
            viewModel.fetchFollowings(loggedInSession.dedeUserID, loggedInSession.toCookieString())
            if (viewModel.selectedUser == null && !viewModel.isAllDynamicsSelected) {
                viewModel.selectAllDynamics()
            }
        }
    }

    if (loggedInSession == null) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("请先登录", style = MaterialTheme.typography.headlineMedium)
            }
        }
        return
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {

    // Focus handling for the "Left Side" container to prevent focus skipping
    val itemFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val allDynamicsRequester = remember { FocusRequester() }
    val containerRequester = remember { FocusRequester() }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Side: Following List
        Box(
            modifier = Modifier
                .width(170.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .focusRequester(containerRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        // When the container itself gets focus (e.g. from geometric search hitting empty space),
                        // redirect focus to the selected user or the first user.
                        if (viewModel.isAllDynamicsSelected) {
                            allDynamicsRequester.requestFocus()
                        } else {
                            val targetUser = viewModel.selectedUser ?: viewModel.followingList.firstOrNull()
                            targetUser?.let { user ->
                                itemFocusRequesters[user.mid]?.requestFocus()
                            }
                        }
                    }
                }
                .focusable()
        ) {
            if (viewModel.isLoading && viewModel.followingList.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewModel.error != null) {
                Text(
                    text = viewModel.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else if (viewModel.followingList.isEmpty() && !viewModel.isAllDynamicsSelected) {
                 // If following list is empty but all dynamics is not selected (initial state might be tricky)
                 // But usually following list being empty means no followings.
                 // However, we now have "All Dynamics" item, so we should show the list anyway.
                 // Let's just show the list.
            }
            
            LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                item {
                    AllDynamicsItem(
                        isSelected = viewModel.isAllDynamicsSelected,
                        onClick = { viewModel.selectAllDynamics() },
                        modifier = Modifier.focusRequester(allDynamicsRequester)
                    )
                }
                items(
                    items = viewModel.followingList,
                    key = { it.mid }
                ) { user ->
                    val requester = remember(user.mid) {
                        itemFocusRequesters.getOrPut(user.mid) { FocusRequester() }
                    }
                    FollowingItem(
                        user = user,
                        isSelected = viewModel.selectedUser?.mid == user.mid,
                        onClick = { viewModel.selectUser(user) },
                        modifier = Modifier.focusRequester(requester)
                    )
                }
            }
        }

        // Right Side: Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopStart
        ) {
            if (viewModel.isAllDynamicsSelected || viewModel.selectedUser != null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    
                    if (viewModel.isVideoLoading && viewModel.userVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (viewModel.userVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无视频")
                        }
                    } else {
                        CommonVideoGrid(
                            videos = viewModel.userVideos,
                            stateManager = viewModel,
                            stateKey = "dynamic",
                            columns = 3,
                            onVideoClick = { video ->
                                coroutineScope.launch {
                                    var cid = video.cid
                                    if (cid == 0L && video.bvid.isNotEmpty()) {
                                        val details = VideoPlayUrlFetcher.fetchVideoDetails(video.bvid)
                                        if (details != null) {
                                            cid = details.cid
                                        }
                                    }
                                    
                                    if (cid != 0L && video.bvid.isNotEmpty()) {
                                         val playInfo = VideoPlayUrlFetcher.fetchPlayUrl(
                                            bvid = video.bvid,
                                            cid = cid,
                                            qn = 80, // 1080P - 非大会员最高清晰度
                                            fnval = 4048, // DASH格式
                                            cookie = SessionManager.getCookieString()
                                        )
                                        if (playInfo != null) {
                                            viewModel.onEnterFullScreen()
                                            onEnterFullScreen(playInfo, video.title)
                                        }
                                    }
                                }
                            },
                            onLoadMore = { viewModel.loadMoreVideos() },
                            horizontalSpacing = 12.dp,
                            verticalSpacing = 12.dp,
                            contentPadding = PaddingValues(top = 8.dp, bottom = 56.dp, start = 12.dp, end = 12.dp) // 统一顶部间距
                        )
                    }
                }
            } else {
                Text(
                    text = "请选择用户",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
    }
}

@Composable
fun FollowingItem(
    user: FollowingUser,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        AsyncImage(
            model = user.face,
            contentDescription = user.uname,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = user.uname,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AllDynamicsItem(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = "全部动态",
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(4.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "全部动态",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

