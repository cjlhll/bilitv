# BiliTV Android 应用项目

## 项目概述

BiliTV 是一个基于 Android TV 平台开发的哔哩哔哩视频应用，使用 Kotlin 语言和 Jetpack Compose 构建。该应用提供哔哩哔哩视频内容的浏览、搜索、播放和用户登录功能，专为电视大屏体验优化。

### 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **架构**: MVVM (Model-View-ViewModel)
- **网络请求**: OkHttp
- **JSON 解析**: Kotlinx Serialization
- **视频播放**: ExoPlayer (media3)
- **二维码生成**: ZXing
- **图片加载**: Coil
- **最低 SDK**: 24 (Android 7.0)
- **目标 SDK**: 36 (Android 14)

## 项目结构

```
app/src/main/java/com/bili/bilitv/
├── MainActivity.kt          # 应用入口 Activity
├── MainScreen.kt            # 主界面和导航逻辑
├── HomeScreen.kt            # 首页视频列表
├── CategoryScreen.kt        # 分类页面
├── DynamicScreen.kt         # 动态页面
├── LiveAreaScreen.kt        # 直播分区页面
├── LiveRoomListScreen.kt    # 直播间列表
├── VideoPlayerScreen.kt     # 视频播放器
├── VideoItem.kt             # 视频项组件
├── VideoPlayUrlFetcher.kt   # 视频播放链接获取
├── Theme.kt                 # 应用主题配置
├── Models.kt                # 数据模型定义
├── HomeViewModel.kt         # 首页 ViewModel
├── DynamicViewModel.kt      # 动态页面 ViewModel
└── utils/
    ├── QRCodeGenerator.kt   # 二维码生成工具
    └── WbiUtil.kt           # WBI 签名工具
```

## 核心功能

### 1. 用户认证
- 二维码扫码登录
- 登录状态持久化
- 用户信息获取和显示
- Session 管理

### 2. 内容浏览
- 热门视频列表
- 分类浏览
- 动态信息流
- 直播分区和直播间列表

### 3. 视频播放
- 全屏视频播放
- 播放链接获取
- 播放状态管理

### 4. 导航系统
- 侧边导航栏
- 页面路由管理
- 返回键处理

## 构建与运行

### 环境要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 11 或更高版本
- Android SDK API 36

### 构建命令
```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

### 测试命令
```bash
# 运行单元测试
./gradlew test

# 运行仪器化测试
./gradlew connectedAndroidTest
```

## 开发规范

### 代码风格
- 遵循 Kotlin 官方编码规范
- 使用 Jetpack Compose 最佳实践
- 采用 MVVM 架构模式
- 使用 Kotlinx Serialization 进行 JSON 处理

### 网络请求
- 使用 OkHttp 进行网络请求
- 统一的错误处理机制
- 支持异步请求和协程

### UI 开发
- 全 Compose UI，不使用传统 View
- 响应式设计，适配不同屏幕尺寸
- Material Design 3 设计规范
- 支持焦点导航和遥控器操作

### 数据持久化
- 使用 SharedPreferences 存储登录状态
- ViewModel 保存界面状态
- 单例模式管理全局会话

## 依赖管理

项目使用 Gradle Version Catalogs 进行依赖版本管理，配置文件位于 `gradle/libs.versions.toml`。

主要依赖包括：
- AndroidX Core KTX
- Jetpack Compose UI
- Material Design 3
- OkHttp 网络库
- ExoPlayer 视频播放
- Coil 图片加载
- ZXing 二维码生成
- Kotlinx Serialization

## 注意事项

1. 应用需要网络权限访问哔哩哔哩 API
2. 使用了自定义签名配置用于 Release 构建
3. 支持电视端 Leanback 启动器
4. 实现了完整的登录状态管理和持久化
5. 视频播放功能支持全屏模式切换