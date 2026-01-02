# HDR 和杜比视界播放分析

## 概述

本项目使用 Android Media3（ExoPlayer）框架实现视频播放，支持 HDR 和杜比视界（Dolby Vision）视频的播放。播放器基于 ExoPlayer 构建，通过配置 MediaCodec 选择器来支持不同的视频编码格式。

## 技术架构

### 播放器框架

- **框架**: Android Media3 (ExoPlayer)
- **版本**: 1.8.0
- **实现类**: `ExoMediaPlayer` (位于 `bv-player/src/main/kotlin/dev/aaa1115910/bv/player/impl/exo/ExoMediaPlayer.kt`)

### 依赖配置

在 `bv-player/build.gradle.kts` 中定义：

```kotlin
dependencies {
    implementation(androidx.media3.common)
    implementation(androidx.media3.datasource.okhttp)
    implementation(androidx.media3.decoder)
    implementation(androidx.media3.exoplayer)
    implementation(androidx.media3.exoplayer.dash)
    implementation(androidx.media3.exoplayer.hls)
    implementation(androidx.media3.ui)
}
```

## 支持的视频编码

项目定义了以下视频编码类型（`app/src/main/kotlin/dev/aaa1115910/bv/entity/VideoCodec.kt`）：

```kotlin
enum class VideoCodec(private val strRes: Int, val prefix: String, val codecId: Int) {
    AVC(R.string.video_codec_avc, "avc1", 7),      // H.264
    HEVC(R.string.video_codec_hevc, "hev1", 12),   // H.265
    AV1(R.string.video_codec_av1, "av01", 13),     // AV1
    DVH1(R.string.video_codec_dvh1, "dvh1", 0),    // 杜比视界
    HVC1(R.string.video_codec_hvc1, "hvc", 0);     // H.265 另一种格式
}
```

### HDR 和杜比视界编码

1. **HDR**
   - 编码格式：HEVC (H.265)
   - 分辨率代码：125
   - 使用 `VideoCodec.HEVC` 编码

2. **杜比视界 (Dolby Vision)**
   - 编码格式：DVH1
   - 分辨率代码：126
   - 使用 `VideoCodec.DVH1` 编码

## 支持的分辨率

项目定义了以下分辨率类型（`app/src/main/kotlin/dev/aaa1115910/bv/entity/Resolution.kt`）：

```kotlin
enum class Resolution(val code: Int, private val strResLong: Int, private val strResShort: Int) {
    R240P(6, ...),
    R360P(16, ...),
    R480P(32, ...),
    R720P(64, ...),
    R720P60(74, ...),
    R1080P(80, ...),
    R1080PPlus(112, ...),
    R1080P60(116, ...),
    R4K(120, ...),
    RHdr(125, ...),              // HDR
    RDolby(126, ...),            // 杜比视界
    R8K(127, ...);
}
```

## 播放器配置

### VideoPlayerOptions

播放器选项配置（`bv-player/src/main/kotlin/dev/aaa1115910/bv/player/VideoPlayerOptions.kt`）：

```kotlin
data class VideoPlayerOptions(
    val userAgent: String? = null,
    val referer: String? = null,
    val enableFfmpegAudioRenderer: Boolean,
    val enableSoftwareVideoDecoder: Boolean
)
```

### 关键配置

1. **软件视频解码器开关**
   - 配置项：`enableSoftwareVideoDecoder`
   - 默认值：`false`（使用硬件解码）
   - 存储位置：`Prefs.enableSoftwareVideoDecoder`

2. **FFmpeg 音频渲染器**
   - 配置项：`enableFfmpegAudioRenderer`
   - 用于支持更多音频格式

### 解码器选择器实现

在 `ExoMediaPlayer.kt` 中的关键代码：

```kotlin
@OptIn(UnstableApi::class)
override fun initPlayer() {
    val renderersFactory = DefaultRenderersFactory(context).apply {
        setExtensionRendererMode(
            when (options.enableFfmpegAudioRenderer) {
                true -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                false -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            }
        )
        
        if (options.enableSoftwareVideoDecoder) {
            // 强制软件解码
            setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val allDecoders = MediaCodecUtil.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
                val softwareDecoders = allDecoders.filter {
                    it.name.startsWith("OMX.google.") || it.name.startsWith("c2.android.")
                }
                // 兜底回退
                softwareDecoders.ifEmpty { allDecoders }
            }
        } else {
            // 默认硬件解码
            setMediaCodecSelector(MediaCodecSelector.DEFAULT)
        }
    }
    
    mPlayer = ExoPlayer
        .Builder(context)
        .setRenderersFactory(renderersFactory)
        .setSeekForwardIncrementMs(1000 * 10)
        .setSeekBackIncrementMs(1000 * 5)
        .build()

    initListener()
}
```

## HDR/杜比视界播放流程

### 1. 视频数据加载

在 `VideoPlayerV3ViewModel.kt` 中加载视频数据：

```kotlin
suspend fun loadVideo(
    avid: Long,
    cid: Long,
    preferApiType: ApiType = Prefs.apiType
) {
    // 加载视频播放数据
    playData = videoPlayRepository.getPlayUrl(
        aid = avid,
        cid = cid,
        preferCodec = Prefs.defaultVideoCodec.toBiliApiCodeType(),
        preferAudioCodec = Prefs.defaultAudio.code,
        preferApiType = preferApiType
    )
    
    // 读取可用的清晰度
    val resolutionMap = mutableMapOf<Int, String>()
    playData.dashVideos.forEach {
        Resolution.fromCode(it.quality)?.let { resolution ->
            resolutionMap[it.quality] = resolution.getDisplayName(BVApp.context)
        }
    }
    availableQuality.swapMapWithMainContext(resolutionMap)
    
    // 读取可用的视频编码
    updateAvailableCodec()
    
    // 开始播放
    playQuality(qn = currentQuality, codec = currentVideoCodec)
}
```

### 2. 编码选择

```kotlin
suspend fun updateAvailableCodec() {
    val supportedCodec = playData!!.codec
    val codecList = supportedCodec[currentQuality]!!
        .mapNotNull { VideoCodec.fromCodecString(it) }
    
    availableVideoCodec.swapListWithMainContext(codecList)
    
    val currentVideoCodec = if (codecList.contains(Prefs.defaultVideoCodec)) {
        Prefs.defaultVideoCodec
    } else {
        codecList.minByOrNull { it.ordinal }!!
    }
    withContext(Dispatchers.Main) {
        this@VideoPlayerV3ViewModel.currentVideoCodec = currentVideoCodec
    }
}
```

### 3. 播放指定质量和编码

```kotlin
suspend fun playQuality(
    qn: Int = currentQuality,
    codec: VideoCodec = currentVideoCodec,
    audio: Audio = currentAudio
) {
    val videoItem = playData!!.dashVideos.find {
        when (Prefs.apiType) {
            ApiType.Web -> it.quality == qn && it.codecs!!.startsWith(codec.prefix)
            ApiType.App -> {
                if (playData!!.codec.isEmpty()) it.quality == qn
                else it.quality == qn && it.codecs!!.startsWith(codec.prefix)
            }
        }
    }
    
    var videoUrl = videoItem?.baseUrl ?: playData!!.dashVideos.first().baseUrl
    
    // 音频选择（包括杜比全景声）
    val audioItem = playData!!.dashAudios.find { it.codecId == audio.code }
        ?: playData!!.dolby.takeIf { it?.codecId == audio.code }
        ?: playData!!.flac.takeIf { it?.codecId == audio.code }
        ?: playData!!.dashAudios.minByOrNull { it.codecId }
    
    var audioUrl = audioItem?.baseUrl ?: playData!!.dashAudios.first().baseUrl
    
    withContext(Dispatchers.Main) {
        currentVideoHeight = videoItem?.height ?: 0
        currentVideoWidth = videoItem?.width ?: 0
        videoPlayer!!.playUrl(videoUrl, audioUrl)
        videoPlayer!!.prepare()
        showBuffering = true
    }
}
```

## 杜比全景声支持

项目支持杜比全景声（Dolby Atmos）音频：

```kotlin
enum class Audio(val code: Int, private val strRes: Int) {
    A64K(30216, R.string.audio_64k),
    A132K(30232, R.string.audio_132k),
    A192K(30280, R.string.audio_192k),
    ADolbyAtoms(30250, R.string.audio_dolby_atoms),  // 杜比全景声
    AHiRes(30251, R.string.audio_hi_res);
}
```

在播放时会优先选择杜比全景声：

```kotlin
playData.dolby?.let {
    Audio.fromCode(it.codecId)?.let { audio ->
        audioList.add(audio)
    }
}
```

## 解码器能力查询

项目提供了解码器能力查询功能（`app/src/main/kotlin/dev/aaa1115910/bv/util/CodecUtil.kt`）：

```kotlin
object CodecUtil {
    fun parseCodecs(): List<CodecInfoData> {
        return MediaCodecList(MediaCodecList.ALL_CODECS)
            .codecInfos.toList()
            .map { CodecInfoData.fromCodecInfo(it) }
    }
}

data class CodecInfoData(
    val name: String,
    val mimeType: String,
    val type: CodecType,
    val mode: CodecMode,           // Hardware 或 Software
    val media: CodecMedia,         // Audio 或 Video
    val maxSupportedInstances: Int?,
    val colorFormats: List<Int>,   // 支持的颜色格式
    val audioBitrateRange: IntRange?,
    val videoBitrateRange: IntRange?,
    val videoFrame: IntRange?,
    val supportedFrameRates: List<SupportedFrameRate>,
    val achievableFrameRates: List<SupportedFrameRate>
)
```

## 用户设置

### 默认视频编码

用户可以在设置中选择默认的视频编码：

```kotlin
var defaultVideoCodec: VideoCodec
    get() = VideoCodec.fromCode(
        runBlocking { dsm.getPreferenceFlow(PrefKeys.prefDefaultVideoCodecRequest).first() }
    )
    set(value) {
        scope.launch {
            dsm.editPreference(PrefKeys.prefDefaultVideoCodecKey, value.ordinal)
        }
    }
```

### 软件解码开关

用户可以启用或禁用软件视频解码：

```kotlin
var enableSoftwareVideoDecoder: Boolean
    get() = runBlocking {
        dsm.getPreferenceFlow(PrefKeys.prefEnableSoftwareVideoDecoderRequest).first()
    }
    set(value) {
        scope.launch {
            dsm.editPreference(PrefKeys.prefEnableSoftwareVideoDecoder, value)
        }
    }
```

## 注意事项

### 1. 硬件解码优先

- 默认使用硬件解码，性能更好
- 硬件解码器由系统 MediaCodec 提供
- 不同设备的硬件解码能力不同

### 2. 软件解码回退

- 当硬件解码不可用时自动回退到软件解码
- 软件解码使用 `OMX.google.*` 或 `c2.android.*` 解码器
- 软件解码性能较差，但兼容性更好

### 3. HDR/杜比视界支持

- HDR 和杜比视界需要设备硬件支持
- 需要设备支持相应的颜色格式（如 HDR10、HDR10+、Dolby Vision）
- 部分设备可能只支持 HDR，不支持杜比视界

### 4. 编码选择策略

```kotlin
// 确认最终所选音质
val existDefaultAudio = availableAudio.contains(Prefs.defaultAudio)
if (!existDefaultAudio) {
    val currentAudio = when {
        Prefs.defaultAudio == Audio.ADolbyAtoms && availableAudio.contains(Audio.AHiRes) -> Audio.AHiRes
        Prefs.defaultAudio == Audio.AHiRes && availableAudio.contains(Audio.ADolbyAtoms) -> Audio.ADolbyAtoms
        availableAudio.contains(Audio.A192K) -> Audio.A192K
        availableAudio.contains(Audio.A132K) -> Audio.A132K
        availableAudio.contains(Audio.A64K) -> Audio.A64K
        else -> availableAudio.first()
    }
    withContext(Dispatchers.Main) {
        this@VideoPlayerV3ViewModel.currentAudio = currentAudio
    }
}
```

### 5. 颜色格式支持

解码器支持的颜色格式是 HDR/杜比视界播放的关键：

- 需要支持 HDR10 (BT.2020)
- 需要支持 10-bit 色深
- 杜比视界需要设备支持 DVH1 编码

## 调试信息

播放器提供详细的调试信息：

```kotlin
override val debugInfo: String
    get() {
        return """
            player: ${androidx.media3.common.MediaLibraryInfo.VERSION_SLASHY}
            time: ${currentPosition.formatMinSec()} / ${duration.formatMinSec()}
            buffered: $bufferedPercentage%
            resolution: ${mPlayer?.videoSize?.width} x ${mPlayer?.videoSize?.height}
            audio: ${mPlayer?.audioFormat?.bitrate ?: 0} kbps
            video codec: ${mPlayer?.videoFormat?.sampleMimeType ?: "null"}
            audio codec: ${mPlayer?.audioFormat?.sampleMimeType ?: "null"} (${getAudioRendererName()})
        """.trimIndent()
    }
```

## 总结

本项目通过以下方式实现 HDR 和杜比视界视频播放：

1. **使用 Media3 框架**：基于 ExoPlayer 构建播放器
2. **支持多种编码**：包括 AVC、HEVC、AV1、DVH1（杜比视界）
3. **灵活的解码器选择**：支持硬件解码和软件解码，可自动回退
4. **完整的编码支持**：支持 HDR（code 125）和杜比视界（code 126）分辨率
5. **杜比全景声支持**：支持 ADolbyAtoms 音频编码
6. **用户可配置**：允许用户选择默认编码和软件解码开关

项目的 HDR/杜比视界播放能力主要依赖于：
- 设备硬件支持（MediaCodec）
- Android 系统版本
- ExoPlayer 的自动解码能力
- 正确的解码器配置和选择策略