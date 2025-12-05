# BiliTV Android Application - Project Context

## Project Overview

BiliTV is an Android TV application built using modern Android development practices. It's a Bilibili video player application designed specifically for TV interfaces, using Kotlin and Jetpack Compose for the UI framework. The app provides features such as browsing Bilibili videos, searching, playing videos and live streams, and user authentication via QR code login.

## Technical Stack

- **Language**: Kotlin (v2.0.21)
- **Build System**: Gradle with Kotlin DSL (Version Catalogs for dependency management)
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Network**: OkHttp for HTTP requests
- **JSON Parsing**: Kotlinx Serialization
- **Video Playback**: ExoPlayer (Media3 library) with GSYVideoPlayer integration
- **Image Loading**: Coil with custom caching configuration
- **QR Code Generation**: ZXing for login functionality
- **Danmaku**: DanmakuFlameMaster for comment display
- **Protocol Buffers**: Used for data serialization
- **Minimum SDK**: 24 (Android 7.0 Nougat)
- **Target SDK**: 36 (Android 14)

## Project Structure

```
bilitv/
├── app/                          # Main application module
│   ├── src/main/
│   │   ├── java/com/bili/bilitv/
│   │   │   ├── MainActivity.kt          # Application entry point
│   │   │   ├── MainScreen.kt            # Main navigation and routing logic
│   │   │   ├── HomeScreen.kt            # Home page with video lists
│   │   │   ├── CategoryScreen.kt        # Video categories
│   │   │   ├── DynamicScreen.kt         # Dynamic feed
│   │   │   ├── LiveAreaScreen.kt        # Live streaming areas
│   │   │   ├── LiveRoomListScreen.kt    # Live room lists
│   │   │   ├── VideoPlayerScreen.kt     # Video player implementation
│   │   │   ├── VideoItem.kt             # Video item display component
│   │   │   ├── VideoPlayUrlFetcher.kt   # Video URL fetching and API calls
│   │   │   ├── LiveStreamUrlFetcher.kt  # Live stream URL fetching
│   │   │   ├── HomeViewModel.kt         # Home screen ViewModel
│   │   │   ├── DynamicViewModel.kt      # Dynamic feed ViewModel
│   │   │   ├── Models.kt                # Data models (serializable)
│   │   │   ├── Theme.kt                 # Application theme configuration
│   │   │   ├── danmaku/                 # Danmaku (comment) related code
│   │   │   └── utils/                   # Utility functions
│   │   │       ├── QRCodeGenerator.kt   # QR code generation for login
│   │   │       └── WbiUtil.kt           # WBI signature utilities for API access
│   │   ├── res/                         # Resources (drawables, strings, etc.)
│   │   └── AndroidManifest.xml          # Application manifest
├── gradle/
│   └── libs.versions.toml              # Dependency version management
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Project settings
├── gradlew, gradlew.bat                # Gradle wrapper scripts
└── IFLOW.md, GEMINI.md                 # Project documentation
```

## Core Features

### 1. User Authentication
- QR code-based login using Bilibili's authentication system
- Session management with cookie handling
- Persistent login state using SharedPreferences
- Automatic restoration of login status on app start

### 2. Content Browsing
- Home screen with popular videos
- Category-based video browsing
- Dynamic feed display
- Live streaming areas and rooms

### 3. Video Playback
- Full-screen video player using ExoPlayer
- Support for multiple video formats (MP4, DASH)
- Quality selection
- Playback URL fetching from Bilibili API

### 4. Live Streaming
- Live room browsing and selection
- Live stream playback functionality
- Integration with Bilibili's live streaming API

### 5. TV-Specific UI
- Navigation rail optimized for TV interfaces
- Focus management for TV remotes
- Large button designs and accessibility features

## Key Implementation Details

### Session Management
The app uses a global `SessionManager` singleton to manage login state both in memory and persistently in SharedPreferences. The login process involves:
1. Generating a QR code via Bilibili's API
2. Polling the API to check if the QR code has been scanned
3. Parsing the session cookies from the success response
4. Storing and maintaining the session for future API calls

### WBI Signature
Bilibili uses WBI (Web Business Interface) signature for API requests. The app implements the signature algorithm to access protected endpoints by:
1. Retrieving WBI keys from user navigation info
2. Signing request parameters using the WBI algorithm
3. Making authenticated API calls with the signed parameters

### Video Playback Architecture
The video player implementation uses ExoPlayer with multiple fallbacks:
1. DASH format with separate audio/video streams
2. MP4 format as a fallback
3. Support for both regular videos and live streams
4. Quality selection and adaptive streaming

### Image Loading Optimization
Coil is customized with optimized caching:
- Memory cache set to 25% of device RAM
- Disk cache of 250MB
- Disabled crossfade animations to reduce scroll lag on TV interfaces
- All caching policies enabled for offline availability

## Building and Running

### Environment Requirements
- Android Studio Hedgehog | 2023.1.1 or higher
- JDK 11 or higher
- Android SDK API 36

### Build Commands
```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease

# Install to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

### Development Setup
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle project
4. Build and run on a physical device or emulator

## Development Conventions

### Code Style
- Follow Kotlin official coding conventions
- Use Jetpack Compose best practices
- MVVM architecture pattern
- Kotlin Coroutines for asynchronous operations
- Kotlinx Serialization for data parsing

### Network Requests
- Use OkHttp for network operations
- Implement proper error handling
- Use coroutines for asynchronous calls
- Include appropriate headers for Bilibili API access

### UI Development
- Pure Jetpack Compose UI (no traditional Views)
- Material Design 3 guidelines
- Responsive design for various screen sizes
- Focus-friendly navigation for TV interfaces
- Accessibility features for impaired users

## Important Files Reference

- `MainScreen.kt`: Central navigation hub with session management
- `VideoPlayUrlFetcher.kt`: Handles video URL fetching from Bilibili API
- `VideoPlayerScreen.kt`: Video player implementation using ExoPlayer
- `SessionManager.kt`: Global session management (embedded in MainScreen.kt)
- `WbiUtil.kt`: WBI signature implementation for API access
- `libs.versions.toml`: All dependency versions managed in one place
- `Settings.gradle.kt`: Project configuration and repository management

## Known Limitations

1. API access may be restricted by Bilibili's anti-bot measures
2. Video playback depends on Bilibili's API availability and quality
3. Some features may require premium account access
4. Live streaming functionality might have region restrictions

## Security Considerations

1. Session cookies are stored in SharedPreferences, which is reasonably secure for Android TV
2. Network traffic is encrypted and uses HTTPS
3. The app includes network security configuration for additional security
4. API requests include proper headers to mimic browser behavior