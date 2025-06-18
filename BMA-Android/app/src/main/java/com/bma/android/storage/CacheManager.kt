package com.bma.android.storage

import android.content.Context
import android.util.Log
import com.bma.android.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages automatic caching of recently played songs using LRU (Least Recently Used) strategy
 * Caches last 30 played songs in internal storage for quick access
 */
class CacheManager private constructor(private val context: Context) {
    
    companion object {
        private const val CACHE_LIMIT = 30
        private const val CACHE_DIR_NAME = "music_cache"
        private const val METADATA_FILE = "cache_metadata.json"
        
        @Volatile
        private var INSTANCE: CacheManager? = null
        
        fun getInstance(context: Context): CacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CacheManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }
    
    // LRU cache tracking - maps song ID to cache entry metadata
    private val cacheMetadata = ConcurrentHashMap<String, CacheEntry>()
    
    /**
     * Represents a cached song entry with access tracking
     */
    data class CacheEntry(
        val songId: String,
        val filename: String,
        val cacheTime: Long,
        var lastAccessed: Long,
        val originalUrl: String,
        val fileSize: Long
    )
    
    /**
     * Cache statistics for monitoring
     */
    data class CacheStats(
        val totalFiles: Int,
        val totalSizeBytes: Long,
        val oldestFile: Long,
        val newestFile: Long
    )
    
    init {
        // Load existing cache metadata on initialization
        loadCacheMetadata()
    }
    
    /**
     * Check if a song is cached locally
     * @param songId ID of the song to check
     * @return true if cached, false otherwise
     */
    suspend fun isCached(songId: String): Boolean = withContext(Dispatchers.IO) {
        val entry = cacheMetadata[songId]
        if (entry != null) {
            val file = File(cacheDir, entry.filename)
            val exists = file.exists() && file.length() > 0
            if (!exists) {
                // Remove invalid cache entry
                cacheMetadata.remove(songId)
                saveCacheMetadata()
            }
            return@withContext exists
        }
        return@withContext false
    }
    
    /**
     * Get cached file for a song
     * @param songId ID of the song
     * @return File object if cached, null otherwise
     */
    suspend fun getCachedFile(songId: String): File? = withContext(Dispatchers.IO) {
        val entry = cacheMetadata[songId]
        if (entry != null) {
            val file = File(cacheDir, entry.filename)
            if (file.exists() && file.length() > 0) {
                // Update last accessed time
                entry.lastAccessed = System.currentTimeMillis()
                saveCacheMetadata()
                return@withContext file
            } else {
                // Remove invalid cache entry
                cacheMetadata.remove(songId)
                saveCacheMetadata()
            }
        }
        return@withContext null
    }
    
    /**
     * Cache a song after it has been played
     * @param song Song object to cache
     * @param streamUrl URL where the audio can be downloaded
     */
    suspend fun cacheAfterPlayback(song: Song, streamUrl: String) = withContext(Dispatchers.IO) {
        try {
            // Check if already cached
            if (isCached(song.id)) {
                Log.d("CacheManager", "Song ${song.title} already cached")
                return@withContext
            }
            
            // Download and cache the song
            downloadToCache(song, streamUrl)
            
        } catch (e: Exception) {
            Log.e("CacheManager", "Error caching song ${song.title}: ${e.message}", e)
        }
    }
    
    /**
     * Download song to cache
     */
    private suspend fun downloadToCache(song: Song, streamUrl: String) = withContext(Dispatchers.IO) {
        try {
            val filename = generateCacheFilename(song)
            val cacheFile = File(cacheDir, filename)
            
            // Download file
            URL(streamUrl).openStream().use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (cacheFile.exists() && cacheFile.length() > 0) {
                // Add to cache metadata
                val entry = CacheEntry(
                    songId = song.id,
                    filename = filename,
                    cacheTime = System.currentTimeMillis(),
                    lastAccessed = System.currentTimeMillis(),
                    originalUrl = streamUrl,
                    fileSize = cacheFile.length()
                )
                
                cacheMetadata[song.id] = entry
                
                // Check cache limit and cleanup if needed
                enforeCacheLimit()
                
                saveCacheMetadata()
                
                Log.d("CacheManager", "Successfully cached: ${song.title} (${cacheFile.length()} bytes)")
            } else {
                Log.w("CacheManager", "Failed to cache song: ${song.title} - file is empty")
                cacheFile.delete()
            }
            
        } catch (e: Exception) {
            Log.e("CacheManager", "Error downloading song to cache: ${e.message}", e)
        }
    }
    
    /**
     * Enforce cache limit by removing least recently used files
     */
    private suspend fun enforeCacheLimit() = withContext(Dispatchers.IO) {
        while (cacheMetadata.size > CACHE_LIMIT) {
            // Find least recently used entry
            val lruEntry = cacheMetadata.values.minByOrNull { it.lastAccessed }
            if (lruEntry != null) {
                // Remove file and metadata
                val file = File(cacheDir, lruEntry.filename)
                if (file.exists()) {
                    file.delete()
                }
                cacheMetadata.remove(lruEntry.songId)
                Log.d("CacheManager", "Removed LRU cached song: ${lruEntry.songId}")
            }
        }
    }
    
    /**
     * Generate cache filename for a song
     */
    private fun generateCacheFilename(song: Song): String {
        // Use song ID and sanitized title for filename
        val sanitizedTitle = song.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "${song.id}_${sanitizedTitle}.mp3"
    }
    
    /**
     * Clear all cached files
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
            cacheMetadata.clear()
            saveCacheMetadata()
            Log.d("CacheManager", "Cache cleared successfully")
        } catch (e: Exception) {
            Log.e("CacheManager", "Error clearing cache: ${e.message}", e)
        }
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        val entries = cacheMetadata.values
        return@withContext CacheStats(
            totalFiles = entries.size,
            totalSizeBytes = entries.sumOf { it.fileSize },
            oldestFile = entries.minOfOrNull { it.cacheTime } ?: 0L,
            newestFile = entries.maxOfOrNull { it.cacheTime } ?: 0L
        )
    }
    
    /**
     * Load cache metadata from persistent storage
     */
    private fun loadCacheMetadata() {
        // For now, we'll implement a simple approach
        // In a production app, you might want to use a database or JSON file
        Log.d("CacheManager", "Cache metadata loaded (placeholder implementation)")
    }
    
    /**
     * Save cache metadata to persistent storage
     */
    private fun saveCacheMetadata() {
        // For now, we'll implement a simple approach
        // In a production app, you might want to use a database or JSON file
        Log.d("CacheManager", "Cache metadata saved (placeholder implementation)")
    }
}