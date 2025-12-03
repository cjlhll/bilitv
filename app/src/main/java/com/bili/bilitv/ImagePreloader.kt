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
    
    private const val CONCURRENT_LOAD_COUNT = 20
    
    fun preloadImages(context: Context, videos: List<Video>) {
        if (videos.isEmpty()) return
        
        preloadScope.launch {
            val imageLoader = context.imageLoader
            val urlsToPreload = videos
                .mapNotNull { it.coverUrl.takeIf { url -> url.isNotEmpty() } }
                .filter { url -> 
                    synchronized(preloadedUrls) {
                        !preloadedUrls.contains(url)
                    }
                }
            
            if (urlsToPreload.isEmpty()) return@launch
            
            urlsToPreload.chunked(CONCURRENT_LOAD_COUNT).forEach { batch ->
                val deferred = batch.map { url ->
                    async {
                        try {
                            synchronized(preloadedUrls) {
                                if (preloadedUrls.contains(url)) return@async
                                preloadedUrls.add(url)
                            }
                            
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .size(Size.ORIGINAL)
                                .memoryCacheKey(url)
                                .diskCacheKey(url)
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
    }
    
    fun clearPreloadedUrls() {
        synchronized(preloadedUrls) {
            preloadedUrls.clear()
        }
    }
}

