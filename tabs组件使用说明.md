# Tabs 组件使用说明

## 组件信息

当前项目使用的 Tabs 组件来自 AndroidX Compose TV 库：

- **组件名称**: `androidx.tv.material3.Tab` 和 `androidx.tv.material3.TabRow`
- **版本**: 
  - `androidx.tv:tv-material`: 1.1.0-alpha01
  - `androidx.tv:tv-foundation`: 1.0.0-alpha12

## 依赖配置

在 `gradle/libs.versions.toml` 中定义版本：

```toml
[versions]
compose-tv = "1.1.0-alpha01"
compose-tv-foundation = "1.0.0-alpha12"

[libraries]
compose-tv-foundation = { module = "androidx.tv:tv-foundation", version.ref = "compose-tv-foundation" }
compose-tv-material = { module = "androidx.tv:tv-material", version.ref = "compose-tv" }
```

在 `app/build.gradle.kts` 中添加依赖：

```kotlin
implementation(androidx.compose.tv.foundation)
implementation(androidx.compose.tv.material)
```

## 基本用法

### 1. 简单的 TabRow 示例

```kotlin
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

var selectedTabIndex by remember { mutableIntStateOf(0) }

TabRow(
    selectedTabIndex = selectedTabIndex,
    separator = { Spacer(modifier = Modifier.width(12.dp)) },
) {
    items.forEachIndexed { index, tab ->
        Tab(
            selected = index == selectedTabIndex,
            onFocus = { selectedTabIndex = index },
            onClick = { /* 处理点击事件 */ }
        ) {
            Text(
                text = tab.label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}
```

### 2. 带焦点的 TabRow（TV 优化）

```kotlin
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer

val focusRequester = remember { FocusRequester() }

TabRow(
    modifier = Modifier.focusRestorer(focusRequester),
    selectedTabIndex = selectedTabIndex,
    separator = { Spacer(modifier = Modifier.width(12.dp)) },
) {
    items.forEachIndexed { index, tab ->
        Tab(
            modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
            selected = index == selectedTabIndex,
            onFocus = { selectedTabIndex = index },
            onClick = { /* 处理点击事件 */ }
        ) {
            Text(text = tab.label)
        }
    }
}
```

## 重要参数说明

### TabRow 参数

- **selectedTabIndex**: 当前选中的 tab 索引（必需）
- **separator**: tab 之间的分隔符组件（可选）
- **modifier**: 修饰符，常用于添加焦点管理

### Tab 参数

- **selected**: 是否选中（必需）
- **onClick**: 点击回调（可选）
- **onFocus**: 焦点变化回调（可选，TV 场景重要）
- **modifier**: 修饰符，常用于添加焦点请求

## 使用注意事项

### 1. TV 场景焦点管理

由于这是 TV 专用的组件，焦点管理非常重要：

- 使用 `FocusRequester` 来管理初始焦点
- 使用 `focusRestorer` 修饰符来恢复焦点
- 在第一个 tab 上设置 `focusRequester`
- 监听 `onFocus` 事件来更新选中状态

### 2. 状态管理

使用 `mutableIntStateOf` 来跟踪当前选中的 tab 索引：

```kotlin
var selectedTabIndex by remember { mutableIntStateOf(0) }
```

### 3. 动态内容更新

当 tab 切换时，可以使用 `LaunchedEffect` 来更新内容：

```kotlin
LaunchedEffect(selectedTabIndex) {
    // 根据 selectedTabIndex 更新显示的内容
}
```

### 4. 自定义样式

Tab 的内容可以自定义，但保持一致的样式：

```kotlin
Tab(
    selected = index == selectedTabIndex,
    onFocus = { selectedTabIndex = index },
    onClick = { onClick(tab) }
) {
    Text(
        modifier = Modifier
            .height(32.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        text = tab.label,
        color = LocalContentColor.current,
        style = MaterialTheme.typography.labelLarge
    )
}
```

## 项目中的实际使用案例

### 案例 1: TopNav 组件

位置：`app/src/main/kotlin/dev/aaa1115910/bv/component/TopNav.kt`

用于顶部导航栏，支持不同类型的导航项（首页、UGC、PGC、个人中心等）。

```kotlin
@Composable
fun TopNav(
    modifier: Modifier = Modifier,
    items: List<TopNavItem>,
    isLargePadding: Boolean,
    onSelectedChanged: (TopNavItem) -> Unit = {},
    onClick: (TopNavItem) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    var selectedNav by remember { mutableStateOf(items.first()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp, verticalPadding),
        horizontalArrangement = Arrangement.Center
    ) {
        TabRow(
            modifier = Modifier.focusRestorer(focusRequester),
            selectedTabIndex = selectedTabIndex,
            separator = { Spacer(modifier = Modifier.width(12.dp)) },
        ) {
            items.forEachIndexed { index, tab ->
                NavItemTab(
                    modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(focusRequester)),
                    topNavItem = tab,
                    selected = index == selectedTabIndex,
                    onFocus = {
                        selectedNav = tab
                        selectedTabIndex = index
                        onSelectedChanged(tab)
                    },
                    onClick = { onClick(tab) }
                )
            }
        }
    }
}
```

### 案例 2: VideoInfoScreen 分集选择

位置：`app/src/main/kotlin/dev/aaa1115910/bv/screen/VideoInfoScreen.kt:1333`

用于视频分集选择对话框，支持分页显示（每页20个分集）。

```kotlin
var selectedTabIndex by remember { mutableIntStateOf(0) }
val tabCount by remember { mutableIntStateOf(ceil(pages.size / 20.0).toInt()) }
val selectedVideoPart = remember { mutableStateListOf<VideoPage>() }

LaunchedEffect(selectedTabIndex) {
    val fromIndex = selectedTabIndex * 20
    var toIndex = (selectedTabIndex + 1) * 20
    if (toIndex >= pages.size) {
        toIndex = pages.size
    }
    selectedVideoPart.swapListWithMainContext(pages.subList(fromIndex, toIndex))
}

TabRow(
    modifier = Modifier.onFocusChanged {
        if (it.hasFocus) {
            scope.launch(Dispatchers.Main) {
                listState.scrollToItem(0)
            }
        }
    },
    selectedTabIndex = selectedTabIndex,
    separator = { Spacer(modifier = Modifier.width(12.dp)) },
) {
    for (i in 0 until tabCount) {
        Tab(
            modifier = if (i == 0) Modifier.focusRequester(tabRowFocusRequester) else Modifier,
            selected = i == selectedTabIndex,
            onFocus = { selectedTabIndex = i },
        ) {
            Text(
                text = "P${i * 20 + 1}-${(i + 1) * 20}",
                fontSize = 12.sp,
                color = LocalContentColor.current,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}
```

## 与普通 Material3 Tab 的区别

1. **TV 优化**: 专门为 TV 设备优化，支持 D-pad 导航
2. **焦点管理**: 内置焦点管理功能，更适合遥控器操作
3. **视觉反馈**: 提供更好的焦点视觉反馈
4. **尺寸适配**: 针对 10-foot UI 优化

## 常见问题

### Q: 为什么不使用普通的 Material3 Tab？

A: 这是一个 TV 应用，需要支持遥控器导航。`androidx.tv.material3.Tab` 专门为 TV 设备优化，提供了更好的焦点管理和视觉反馈。

### Q: 如何处理大量 tab？

A: 可以使用分页或横向滚动。参考 VideoInfoScreen 的实现，使用 `LazyRow` 或者分页显示。

### Q: 如何自定义 tab 的外观？

A: 在 Tab 的内容中自定义，但建议保持与 Material Design 一致，使用 `LocalContentColor.current` 和 `MaterialTheme.typography`。

## 相关资源

- [AndroidX Compose TV 文档](https://developer.android.com/jetpack/androidx/releases/tv)
- [Compose Material3 指南](https://m3.material.io/)