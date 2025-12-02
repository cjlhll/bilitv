package com.bili.bilitv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log

// --- Data Models ---

@Serializable
data class LiveAreaResponse(
    val code: Int,
    val msg: String,
    val message: String,
    val data: List<LiveAreaGroup>
)

@Serializable
data class LiveAreaGroup(
    val id: Int,
    val name: String,
    val list: List<LiveAreaItem>
)

@Serializable
data class LiveAreaItem(
    val id: String,
    val parent_id: String,
    val old_area_id: String,
    val name: String,
    val pic: String,
    val parent_name: String,
    val area_type: Int
)

// --- ViewModel ---

class LiveAreaViewModel : ViewModel() {
    private val _areaGroups = MutableStateFlow<List<LiveAreaGroup>>(emptyList())
    val areaGroups: StateFlow<List<LiveAreaGroup>> = _areaGroups.asStateFlow()

    private val _selectedGroup = MutableStateFlow<LiveAreaGroup?>(null)
    val selectedGroup: StateFlow<LiveAreaGroup?> = _selectedGroup.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // Persistence state
    private val _scrollStates = mutableStateMapOf<Int, Pair<Int, Int>>()
    private val _focusStates = mutableStateMapOf<Int, Int>()
    var shouldRestoreFocusToGrid by mutableStateOf(false)

    init {
        fetchLiveAreas()
    }

    private fun fetchLiveAreas() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://api.live.bilibili.com/room/v1/Area/getList")
                        .build()
                    httpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val areaResponse = json.decodeFromString<LiveAreaResponse>(body)
                        if (areaResponse.code == 0) {
                            _areaGroups.value = areaResponse.data
                            if (areaResponse.data.isNotEmpty() && _selectedGroup.value == null) {
                                _selectedGroup.value = areaResponse.data.first()
                            }
                        } else {
                            Log.e("LiveAreaViewModel", "API error: ${areaResponse.msg}")
                        }
                    }
                } else {
                    Log.e("LiveAreaViewModel", "HTTP error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("LiveAreaViewModel", "Error fetching live areas", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectGroup(group: LiveAreaGroup) {
        _selectedGroup.value = group
        shouldRestoreFocusToGrid = false
    }

    fun updateScrollState(groupId: Int, index: Int, offset: Int) {
        _scrollStates[groupId] = index to offset
    }

    fun getScrollState(groupId: Int): Pair<Int, Int> {
        return _scrollStates[groupId] ?: (0 to 0)
    }

    fun updateFocusedIndex(groupId: Int, index: Int) {
        _focusStates[groupId] = index
    }

    fun getFocusedIndex(groupId: Int): Int {
        return _focusStates[groupId] ?: -1
    }

    fun onEnterLiveRoom() {
        shouldRestoreFocusToGrid = true
    }
}

// --- UI Components ---

@Composable
fun LiveAreaScreen(
    viewModel: LiveAreaViewModel = viewModel(),
    onAreaClick: (LiveAreaItem) -> Unit = {}
) {
    val areaGroups by viewModel.areaGroups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 24.dp)
    ) {
        // Tabs (Main Areas)
        if (areaGroups.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp)
            ) {
                items(areaGroups) { group ->
                    LiveAreaTabButton(
                        text = group.name,
                        selected = selectedGroup == group,
                        onClick = { viewModel.selectGroup(group) }
                    )
                }
            }
        }

        // Content (Sub Areas)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                selectedGroup?.let { group ->
                    LiveAreaGrid(
                        areas = group.list,
                        groupId = group.id,
                        viewModel = viewModel,
                        onAreaClick = { item ->
                            viewModel.onEnterLiveRoom()
                            onAreaClick(item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveAreaTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f, label = "scale")

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                          else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
        modifier = modifier
            .height(40.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun LiveAreaGrid(
    areas: List<LiveAreaItem>,
    groupId: Int,
    viewModel: LiveAreaViewModel,
    onAreaClick: (LiveAreaItem) -> Unit
) {
    val (initialIndex, initialOffset) = remember(groupId) { viewModel.getScrollState(groupId) }
    val initialFocusIndex = remember(groupId) { viewModel.getFocusedIndex(groupId) }
    val shouldRestoreFocus = viewModel.shouldRestoreFocusToGrid

    val listState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )

    LaunchedEffect(listState, groupId) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.updateScrollState(groupId, index, offset)
            }
    }

    LazyVerticalGrid(
        state = listState,
        columns = GridCells.Fixed(8), // Requirement: 8 columns
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp)
    ) {
        itemsIndexed(areas) { index, area ->
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(shouldRestoreFocus) {
                if (shouldRestoreFocus) {
                    if (index == initialFocusIndex || (initialFocusIndex == -1 && index == 0)) {
                        focusRequester.requestFocus()
                    }
                }
            }

            LiveAreaItemView(
                area = area, 
                onClick = { onAreaClick(area) },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { 
                        if (it.isFocused) {
                            viewModel.updateFocusedIndex(groupId, index)
                        }
                    }
            )
        }
    }
}

@Composable
private fun LiveAreaItemView(
    area: LiveAreaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f, label = "scale")
    // Removed unused internal FocusRequester

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { 
                onClick()
                Log.d("LiveAreaScreen", "Clicked area: ${area.name}")
            }
            .padding(4.dp)
            .zIndex(if (isFocused) 1f else 0f)
    ) {
        // Inner scale container
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale)
        ) {
            // Image
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .aspectRatio(1f) // Square image area or adjust as needed
                    .fillMaxWidth(),
                border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null
            ) {
                AsyncImage(
                    model = area.pic,
                    contentDescription = area.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Text
            Text(
                text = area.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

