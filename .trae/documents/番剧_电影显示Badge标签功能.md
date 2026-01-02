# 番剧/电影显示Badge标签方案

## 实现思路
VideoItem组件已经支持在卡片右上角显示badge标签（通过`video.badges`字段），只需要在创建Video对象时添加badge信息即可。

## 具体实现步骤

### 1. 修改MainScreen.kt中的Video创建逻辑
在将ToviewItem转换为Video时：
- 判断`badge`字段是否不为空
- 如果不为空，创建Badge对象并添加到`badges`列表中
- 设置badge的样式（背景色、文字色等）

### 2. Badge对象创建逻辑
```kotlin
val badges = if (toviewItem.badge.isNotEmpty()) {
    listOf(Badge(
        text = toviewItem.badge,
        bgColor = "#FF00A1D9", // 番剧专用绿色
        textColor = "#FFFFFFFF"
    ))
} else {
    emptyList()
}
```

### 3. 编译测试
运行`.\gradlew.bat assembleDebug`确保编译成功