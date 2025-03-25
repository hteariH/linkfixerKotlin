package com.mamoru.service

import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Service
class VideoCacheService {
    private val videoCache = ConcurrentHashMap<String, String>()

    /**
     * Check if a TikTok URL has already been processed
     */
    fun isVideoCached(tikTokUrl: String): Boolean {
        return videoCache.containsKey(tikTokUrl)
    }

    /**
     * Get cached video file path for a TikTok URL
     */
    fun getCachedVideo(tikTokUrl: String): File? {
        val filePath = videoCache[tikTokUrl] ?: return null
        val file = File(filePath)
        return if (file.exists()) file else null
    }

    /**
     * Cache a video file path for a TikTok URL
     */
    fun cacheVideo(tikTokUrl: String, file: File) {
        videoCache[tikTokUrl] = file.absolutePath
    }

    /**
     * Clear all cached video file paths
     */
    fun cleanCache() {
        videoCache.clear()
    }
    
    
}