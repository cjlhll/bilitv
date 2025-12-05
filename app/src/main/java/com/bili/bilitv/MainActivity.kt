package com.bili.bilitv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import kotlin.system.exitProcess

/**
 * 主 Activity - 应用入口
 * 使用Jetpack Compose构建 UI
 */
class MainActivity : ComponentActivity(), ImageLoaderFactory {

    private var backPressedTime: Long = 0
    private var backToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 处理双击返回退出逻辑
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    backToast?.cancel()
                    finishAffinity()
                } else {
                    backToast = Toast.makeText(this@MainActivity, "再按一次返回键退出程序", Toast.LENGTH_SHORT)
                    backToast?.show()
                }
                backPressedTime = System.currentTimeMillis()
            }
        })

        // 初始化SessionManager，从本地存储恢复登录状态
        SessionManager.init(this)
        setContent {
            CompositionLocalProvider(LocalBackPressedDispatcher provides this) {
                BiliTVTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen()
                    }
                }
            }
        }
    }

    /**
     * 自定义Coil ImageLoader配置
     * 优化视频列表滚动性能：
     * 1. 增加内存缓存大小到设备内存的25%
     * 2. 配置磁盘缓存为250MB
     * 3. 禁用crossfade动画，减少滚动时的重绘开销
     * 4. 启用所有缓存策略，优先使用缓存
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // 内存缓存大小为设备内存的25% (默认是25%,这里显式设置)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    // 磁盘缓存250MB
                    .maxSizeBytes(250 * 1024 * 1024)
                    .build()
            }
            // 禁用crossfade动画，减少滚动时的重绘
            .crossfade(false)
            // 启用所有缓存策略
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // 不尊重缓存头，总是使用缓存
            .respectCacheHeaders(false)
            .build()
    }
}

val LocalBackPressedDispatcher = staticCompositionLocalOf<MainActivity> {
    error("No BackPressedDispatcher provided")
}
