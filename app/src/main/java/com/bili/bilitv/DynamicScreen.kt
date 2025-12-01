package com.bili.bilitv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import kotlinx.coroutines.flow.collect

import android.util.Log

@Composable
fun DynamicScreen(
    viewModel: DynamicViewModel = viewModel(),
    loggedInSession: LoggedInSession?,
    onEnterFullScreen: (VideoPlayInfo, String) -> Unit = { _, _ -> }
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(loggedInSession) {
        Log.d("BiliTV", "DynamicScreen: loggedInSession is ${if (loggedInSession == null) "null" else "not null"}")
        if (loggedInSession != null) {
            viewModel.fetchFollowings(loggedInSession.dedeUserID, loggedInSession.toCookieString())
            if (viewModel.selectedUser == null && !viewModel.isAllDynamicsSelected) {
                viewModel.selectAllDynamics()
            }
        }
    }

    if (loggedInSession == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先登录", style = MaterialTheme.typography.headlineMedium)
        }
        return
    }

    // Focus handling for the "Left Side" container to prevent focus skipping
    val itemFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val allDynamicsRequester = remember { FocusRequester() }
    val containerRequester = remember { FocusRequester() }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Side: Following List
        Box(
            modifier = Modifier
                .width(200.dp)
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
                contentPadding = PaddingValues(vertical = 8.dp)
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
                    // User Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp)
                    ) {
                        if (viewModel.isAllDynamicsSelected) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "全部动态",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        } else {
                            AsyncImage(
                                model = viewModel.selectedUser!!.face,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = viewModel.selectedUser!!.uname,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                if (viewModel.selectedUser!!.sign.isNotEmpty()) {
                                    Text(
                                        text = viewModel.selectedUser!!.sign,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (viewModel.isVideoLoading && viewModel.userVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (viewModel.userVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无视频")
                        }
                    } else {
                        val listState = rememberLazyGridState()
                        
                        // Load more detection
                        LaunchedEffect(listState) {
                            snapshotFlow {
                                val layoutInfo = listState.layoutInfo
                                val totalItems = layoutInfo.totalItemsCount
                                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                totalItems > 0 && lastVisibleItem >= totalItems - 6 // Trigger when 2 rows left
                            }.collect { shouldLoad ->
                                if (shouldLoad) {
                                    viewModel.loadMoreVideos()
                                }
                            }
                        }

                        LazyVerticalGrid(
                            state = listState,
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            contentPadding = PaddingValues(top = 24.dp, bottom = 56.dp, start = 48.dp, end = 48.dp)
                        ) {
                            items(
                                items = viewModel.userVideos,
                                key = { video -> video.id }
                            ) { video ->
                                VideoItem(
                                    video = video,
                                    onClick = { clickedVideo ->
                                        coroutineScope.launch {
                                            var cid = clickedVideo.cid
                                            if (cid == 0L && clickedVideo.bvid.isNotEmpty()) {
                                                val details = VideoPlayUrlFetcher.fetchVideoDetails(clickedVideo.bvid)
                                                if (details != null) {
                                                    cid = details.cid
                                                }
                                            }
                                            
                                            if (cid != 0L && clickedVideo.bvid.isNotEmpty()) {
                                                 val playInfo = VideoPlayUrlFetcher.fetchPlayUrl(
                                                    bvid = clickedVideo.bvid,
                                                    cid = cid
                                                )
                                                if (playInfo != null) {
                                                    onEnterFullScreen(playInfo, clickedVideo.title)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            
                            if (viewModel.isVideoLoading) {
                                item(span = { GridItemSpan(3) }) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        AsyncImage(
            model = user.face,
            contentDescription = user.uname,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = "全部动态",
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(6.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.width(8.dp))
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

