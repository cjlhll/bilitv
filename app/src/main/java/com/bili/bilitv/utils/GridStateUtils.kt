package com.bili.bilitv.utils

import android.util.Log
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.bili.bilitv.VideoGridStateManager

/**
 * Creates a [LazyGridState] that automatically persists its scroll position to [VideoGridStateManager].
 *
 * @param stateManager The manager to save/restore state.
 * @param key The unique key for this state.
 * @param refreshSignal Signal to reset scroll to top (increment to trigger).
 * @param dataSize Optional size of data. Used to retry restoration if data loads asynchronously.
 * @return A [LazyGridState] with persistence capabilities.
 */
@Composable
fun rememberRestorableLazyGridState(
    stateManager: VideoGridStateManager,
    key: Any,
    refreshSignal: Int = 0,
    dataSize: Int = 0
): LazyGridState {
    val (initialIndex, initialOffset) = remember(key) { 
        stateManager.getScrollState(key) 
    }
    
    val listState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )
    
    // Sync state to manager
    LaunchedEffect(listState, key) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                stateManager.updateScrollState(key, index, offset)
            }
    }
    
    // Handle refresh
    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) {
            listState.scrollToItem(0)
            stateManager.updateScrollState(key, 0, 0)
        }
    }
    
    // Handle delayed data load restoration
    // If we have an initial index to restore, but it wasn't applied (e.g. data wasn't ready), apply it now.
    LaunchedEffect(dataSize) {
        if (dataSize > 0 && initialIndex > 0) {
             val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == initialIndex }
             if (!isVisible) {
                 // Double check bounds to avoid crash
                 // Grid index logic is complex (spans), but scrollToItem handles large indices gracefully usually.
                 // But for safety, checking against dataSize might be inaccurate if spans vary.
                 // We'll trust the saved index.
                 try {
                     listState.scrollToItem(initialIndex, initialOffset)
                 } catch (e: Exception) {
                     Log.w("GridStateUtils", "Failed to restore scroll state", e)
                 }
             }
        }
    }

    return listState
}

/**
 * Creates a [LazyListState] that automatically persists its scroll position to [VideoGridStateManager].
 * 
 * Similar to [rememberRestorableLazyGridState] but for LazyRow/LazyColumn.
 */
@Composable
fun rememberRestorableLazyListState(
    stateManager: VideoGridStateManager,
    key: Any,
    refreshSignal: Int = 0,
    dataSize: Int = 0
): LazyListState {
    val (initialIndex, initialOffset) = remember(key) { 
        stateManager.getScrollState(key) 
    }
    
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )
    
    // Sync state to manager
    LaunchedEffect(listState, key) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                stateManager.updateScrollState(key, index, offset)
            }
    }
    
    // Handle refresh
    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) {
            listState.scrollToItem(0)
            stateManager.updateScrollState(key, 0, 0)
        }
    }
    
    LaunchedEffect(dataSize) {
        if (dataSize > 0 && initialIndex > 0) {
             val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == initialIndex }
             if (!isVisible) {
                 try {
                     listState.scrollToItem(initialIndex, initialOffset)
                 } catch (e: Exception) {
                     Log.w("GridStateUtils", "Failed to restore list scroll state", e)
                 }
             }
        }
    }

    return listState
}
