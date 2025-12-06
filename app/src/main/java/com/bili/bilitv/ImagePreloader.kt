package com.bili.bilitv

import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object ImagePreloader {
    private val preloadedUrls = ConcurrentHashMap.newKeySet<String>()
    private val preloadScope = CoroutineScope(
        Dispatchers.IO + 
        SupervisorJob() + 
        CoroutineName("ImagePreloader")
    )
    @Volatile private var currentJob: Job? = null
    
    fun preloadImages(context: Context, videos: List<Video>) {
        if (videos.isEmpty()) return
        
        synchronized(this) {
            currentJob?.cancel()
            currentJob = preloadScope.launch {
                runPreload(context, videos)
            }
        }
    }

    private suspend fun runPreload(context: Context, videos: List<Video>) {
        withContext(Dispatchers.IO) {
            val imageLoader = context.imageLoader
            val urlsToPreload = videos
                .mapNotNull { it.coverUrl.takeIf { url -> url.isNotEmpty() } }
                .filter { url -> !preloadedUrls.contains(url) }
            
            if (urlsToPreload.isEmpty()) return@withContext
            
            val priorityUrls = urlsToPreload.take(ImageConfig.FIRST_SCREEN_PRELOAD_COUNT)
            val remainingUrls = urlsToPreload.drop(ImageConfig.FIRST_SCREEN_PRELOAD_COUNT)
            
            preloadBatch(context, imageLoader, priorityUrls, highPriority = true)
            
            if (remainingUrls.isNotEmpty()) {
                delay(ImageConfig.DELAYED_PRELOAD_MS)
                preloadBatch(context, imageLoader, remainingUrls, highPriority = false)
            }
        }
    }
    
    private suspend fun preloadBatch(
        context: Context, 
        imageLoader: ImageLoader, 
        urls: List<String>,
        highPriority: Boolean
    ) {
        urls.chunked(ImageConfig.PRELOAD_CONCURRENCY).forEach { batch ->
            val deferred = batch.map { url ->
                preloadScope.async(if (highPriority) Dispatchers.IO else Dispatchers.IO.limitedParallelism(2)) {
                    try {
                        if (preloadedUrls.contains(url)) return@async
                        
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .size(ImageConfig.VIDEO_COVER_SIZE)
                            .memoryCacheKey(url)
                            .diskCacheKey(url)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .allowHardware(true)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .build()
                        
                        val result = imageLoader.execute(request)
                        if (result.drawable != null) {
                            preloadedUrls.add(url)
                        }
                    } catch (e: Exception) {
                        preloadedUrls.remove(url)
                    }
                }
            }
            deferred.awaitAll()
        }
    }
    
    fun clearPreloadedUrls() {
        preloadedUrls.clear()
    }
    
    fun cancelAll() {
        currentJob?.cancel()
        currentJob = null
    }
}

