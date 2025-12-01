package com.bili.bilitv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

import android.util.Log

@Composable
fun DynamicScreen(
    viewModel: DynamicViewModel = viewModel(),
    loggedInSession: LoggedInSession?
) {
    LaunchedEffect(loggedInSession) {
        Log.d("BiliTV", "DynamicScreen: loggedInSession is ${if (loggedInSession == null) "null" else "not null"}")
        if (loggedInSession != null) {
            viewModel.fetchFollowings(loggedInSession.dedeUserID, loggedInSession.toCookieString())
        }
    }

    if (loggedInSession == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先登录", style = MaterialTheme.typography.headlineMedium)
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Side: Following List
        Box(
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            if (viewModel.isLoading && viewModel.followingList.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewModel.error != null) {
                Text(
                    text = viewModel.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else if (viewModel.followingList.isEmpty()) {
                Text(
                    text = "暂无关注",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(viewModel.followingList) { user ->
                        FollowingItem(
                            user = user,
                            isSelected = viewModel.selectedUser?.mid == user.mid,
                            onClick = { viewModel.selectUser(user) }
                        )
                    }
                }
            }
        }

        // Right Side: Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(24.dp),
            contentAlignment = Alignment.TopStart
        ) {
            if (viewModel.selectedUser != null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // User Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (viewModel.isVideoLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (viewModel.userVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无视频")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 200.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(viewModel.userVideos) { video ->
                                VideoItem(
                                    video = video,
                                    onClick = { /* TODO: Navigate to player */ }
                                )
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
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AsyncImage(
            model = user.face,
            contentDescription = user.uname,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = user.uname,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

