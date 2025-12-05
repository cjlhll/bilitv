package com.bili.bilitv

import android.content.Context
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.*

object ImagePreloader {
    private val preloadedUrls = mutableSetOf<String>()
    private val preloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
                .filter { url -> 
                    synchronized(preloadedUrls) {
                        !preloadedUrls.contains(url)
                    }
                }
            
            if (urlsToPreload.isEmpty()) return@withContext
            
            // 优先预加载首屏图片
            val priorityUrls = urlsToPreload.take(ImageConfig.FIRST_SCREEN_PRELOAD_COUNT)
            val remainingUrls = urlsToPreload.drop(ImageConfig.FIRST_SCREEN_PRELOAD_COUNT)
            
            // 预加载首屏图片
            preloadBatch(context, imageLoader, priorityUrls)
            
            // 延迟预加载剩余图片，避免影响首屏加载
            if (remainingUrls.isNotEmpty()) {
                delay(ImageConfig.DELAYED_PRELOAD_MS)
                preloadBatch(context, imageLoader, remainingUrls)
            }
        }
    }
    
    private suspend fun preloadBatch(context: Context, imageLoader: ImageLoader, urls: List<String>) {
        urls.chunked(ImageConfig.PRELOAD_CONCURRENCY).forEach { batch ->
            val deferred = batch.map { url ->
                preloadScope.async {
                    try {
                        synchronized(preloadedUrls) {
                            if (preloadedUrls.contains(url)) return@async
                            preloadedUrls.add(url)
                        }
                        
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .size(ImageConfig.VIDEO_COVER_SIZE)
                            .memoryCacheKey(url)
                            .diskCacheKey(url)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                        imageLoader.enqueue(request)
                    } catch (e: Exception) {
                        synchronized(preloadedUrls) {
                            preloadedUrls.remove(url)
                        }
                    }
                }
            }
            deferred.awaitAll()
        }
    }
    
    fun clearPreloadedUrls() {
        synchronized(preloadedUrls) {
            preloadedUrls.clear()
        }
    }
}

