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
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.DecodeUtils
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
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

    override fun newImageLoader(): ImageLoader {
        val diskCache = DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(ImageConfig.DISK_CACHE_SIZE_BYTES.toLong())
            .build()
        
        val memoryCache = MemoryCache.Builder(this)
            .maxSizeBytes(ImageConfig.MEMORY_CACHE_SIZE_BYTES)
            .strongReferencesEnabled(true)
            .weakReferencesEnabled(true)
            .build()
        
        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher(Executors.newFixedThreadPool(4)).apply {
                maxRequests = 64
                maxRequestsPerHost = 8
            })
            .build()
        
        return ImageLoader.Builder(this)
            .memoryCache(memoryCache)
            .diskCache(diskCache)
            .okHttpClient(okHttpClient)
            .crossfade(false)
            .allowHardware(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .build()
    }
}

val LocalBackPressedDispatcher = staticCompositionLocalOf<MainActivity> {
    error("No BackPressedDispatcher provided")
}
