package com.bma.android.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bma.android.models.Song
import com.bma.android.models.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages manual downloads of songs, albums, and playlists to external storage
 * Organizes files by artist/album structure for better management
 */
class DownloadManager private constructor(private val context: Context) {
    
    companion object {
        private const val DOWNLOAD_DIR_NAME = "BMA_Downloads"
        private const val METADATA_FILE = "download_metadata.json"
        
        @Volatile
        private var INSTANCE: DownloadManager? = null
        
        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val downloadDir = File(context.getExternalFilesDir(null), DOWNLOAD_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }
    
    
    // Track downloaded files - maps song ID to download entry
    private val downloadMetadata = ConcurrentHashMap<String, DownloadEntry>()
    
    // Track download progress - maps song ID to progress (0-100)
    private val downloadProgress = ConcurrentHashMap<String, Int>()
    
    /**
     * Represents a downloaded song entry
     */
    data class DownloadEntry(
        val songId: String,
        val filePath: String,
        val downloadTime: Long,
        val originalUrl: String,
        val fileSize: Long,
        val artist: String,
        val album: String,
        val title: String
    )
    
    /**
     * Download status for UI
     */
    enum class DownloadStatus {
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED,
        FAILED
    }
    
    /**
     * Download statistics
     */
    data class DownloadStats(
        val totalDownloads: Int,
        val totalSizeBytes: Long,
        val storageUsed: String,
        val availableSpace: String
    )
    
    init {
        // Load existing download metadata
        loadDownloadMetadata()
    }
    
    /**
     * Check if a song is downloaded
     */
    suspend fun isDownloaded(songId: String): Boolean = withContext(Dispatchers.IO) {
        val entry = downloadMetadata[songId]
        if (entry != null) {
            val file = File(entry.filePath)
            val exists = file.exists() && file.length() > 0
            if (!exists) {
                // Clean up invalid entry
                downloadMetadata.remove(songId)
                saveDownloadMetadata()
            }
            return@withContext exists
        }
        
        // CRITICAL FIX: Fallback check using PlaylistManager and expected file location
        // This handles cases where metadata is lost but files still exist
        try {
            Log.d("DownloadManager", "Checking fallback download status for song: $songId")
            val playlistManager = PlaylistManager.getInstance(this@DownloadManager.context)
            if (playlistManager.isSongDownloaded(songId)) {
                Log.d("DownloadManager", "PlaylistManager confirms download, checking file...")
                
                // Try to get song from offline cache first (no API call)
                val song = try {
                    playlistManager.getAllSongsOffline().find { it.id == songId }
                } catch (e: Exception) {
                    Log.w("DownloadManager", "getAllSongsOffline failed in isDownloaded check", e)
                    playlistManager.getAllSongs().find { it.id == songId }
                }
                
                if (song != null) {
                    val expectedFile = getDownloadFile(song)
                    if (expectedFile.exists() && expectedFile.length() > 0) {
                        Log.w("DownloadManager", "Detected orphaned download file for: ${song.title}")
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadManager", "Error in fallback download check for song $songId", e)
        }
        
        return@withContext false
    }
    
    
    /**
     * Mark a song as downloaded (used by DownloadWorker after successful download)
     */
    suspend fun markAsDownloaded(song: Song, downloadFile: File) {
        val entry = DownloadEntry(
            songId = song.id,
            filePath = downloadFile.absolutePath,
            downloadTime = System.currentTimeMillis(),
            originalUrl = "", // Not needed for worker downloads
            fileSize = downloadFile.length(),
            artist = song.artist,
            album = song.album,
            title = song.title
        )
        
        downloadMetadata[song.id] = entry
        saveDownloadMetadata()
        
        // CRITICAL FIX: Also mark in PlaylistManager for offline mode filtering AND cache metadata
        try {
            val playlistManager = PlaylistManager.getInstance(context)
            playlistManager.markSongAsDownloaded(song.id, downloadFile.length(), song)
            Log.d("DownloadManager", "Marked as downloaded and cached metadata: ${song.title}")
        } catch (e: Exception) {
            Log.e("DownloadManager", "Error marking song as downloaded in PlaylistManager", e)
        }
        
        Log.d("DownloadManager", "Marked as downloaded: ${song.title}")
    }
    
    /**
     * Get downloaded file for a song
     */
    suspend fun getDownloadedFile(songId: String): File? = withContext(Dispatchers.IO) {
        val entry = downloadMetadata[songId]
        if (entry != null) {
            val file = File(entry.filePath)
            if (file.exists() && file.length() > 0) {
                return@withContext file
            } else {
                // Clean up invalid entry
                downloadMetadata.remove(songId)
                saveDownloadMetadata()
            }
        }
        
        // CRITICAL FIX: Fallback check using PlaylistManager and expected file location
        // This handles cases where metadata is lost but files still exist
        try {
            Log.d("DownloadManager", "Attempting fallback recovery for song: $songId")
            val playlistManager = PlaylistManager.getInstance(this@DownloadManager.context)
            
            if (playlistManager.isSongDownloaded(songId)) {
                Log.d("DownloadManager", "PlaylistManager confirms song is downloaded, searching for Song object...")
                
                // Try to get song from offline cache first (no API call)
                val song = try {
                    playlistManager.getAllSongsOffline().find { it.id == songId }
                } catch (e: Exception) {
                    Log.w("DownloadManager", "getAllSongsOffline failed, trying regular getAllSongs", e)
                    // Fallback to regular method if offline method fails
                    playlistManager.getAllSongs().find { it.id == songId }
                }
                
                if (song != null) {
                    Log.d("DownloadManager", "Found Song object: ${song.title}, checking expected file...")
                    val expectedFile = getDownloadFile(song)
                    Log.d("DownloadManager", "Expected file path: ${expectedFile.absolutePath}")
                    Log.d("DownloadManager", "File exists: ${expectedFile.exists()}, Size: ${if (expectedFile.exists()) expectedFile.length() else 0}")
                    
                    if (expectedFile.exists() && expectedFile.length() > 0) {
                        Log.w("DownloadManager", "Found orphaned download file, rebuilding metadata: ${song.title}")
                        
                        // Rebuild metadata entry
                        val recoveredEntry = DownloadEntry(
                            songId = song.id,
                            filePath = expectedFile.absolutePath,
                            downloadTime = expectedFile.lastModified(),
                            originalUrl = "", // Not available for recovery
                            fileSize = expectedFile.length(),
                            artist = song.artist,
                            album = song.album,
                            title = song.title
                        )
                        downloadMetadata[songId] = recoveredEntry
                        saveDownloadMetadata()
                        
                        Log.d("DownloadManager", "Successfully recovered download file: ${expectedFile.absolutePath}")
                        return@withContext expectedFile
                    } else {
                        Log.w("DownloadManager", "Expected file does not exist or is empty")
                    }
                } else {
                    Log.w("DownloadManager", "Could not find Song object for ID: $songId")
                }
            } else {
                Log.d("DownloadManager", "PlaylistManager says song is NOT downloaded")
            }
        } catch (e: Exception) {
            Log.e("DownloadManager", "Error in fallback file check for song $songId", e)
        }
        
        return@withContext null
    }
    
    /**
     * Get file path where a song should be downloaded
     */
    fun getDownloadFile(song: Song): File {
        // Always return File object for default location (for backward compatibility)
        val artistDir = File(downloadDir, sanitizeFilename(song.artist))
        val albumDir = File(artistDir, sanitizeFilename(song.album))
        val filename = "${sanitizeFilename(song.title)}.mp3"
        return File(albumDir, filename)
    }
    
    
    /**
     * Get file path where album art should be downloaded
     */
    fun getArtworkFile(song: Song): File {
        val artistDir = File(downloadDir, sanitizeFilename(song.artist))
        val albumDir = File(artistDir, sanitizeFilename(song.album))
        val filename = "${sanitizeFilename(song.title)}_artwork.jpg"
        return File(albumDir, filename)
    }
    
    /**
     * Download a single song
     */
    suspend fun downloadSong(song: Song, streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isDownloaded(song.id)) {
                Log.d("DownloadManager", "Song ${song.title} already downloaded")
                return@withContext true
            }
            
            // Set initial progress
            downloadProgress[song.id] = 0
            
            // Get download file location
            val downloadFile = getDownloadFile(song).apply {
                parentFile?.mkdirs()
            }
            
            // Download the file
            val success = downloadFileWithProgress(streamUrl, downloadFile, song.id)
            
            if (success) {
                // Add to download metadata
                val entry = DownloadEntry(
                    songId = song.id,
                    filePath = downloadFile.absolutePath,
                    downloadTime = System.currentTimeMillis(),
                    originalUrl = streamUrl,
                    fileSize = downloadFile.length(),
                    artist = song.artist,
                    album = song.album,
                    title = song.title
                )
                
                downloadMetadata[song.id] = entry
                saveDownloadMetadata()
                
                Log.d("DownloadManager", "Successfully downloaded: ${song.title}")
                return@withContext true
            } else {
                Log.w("DownloadManager", "Failed to download: ${song.title}")
                downloadFile.delete()
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e("DownloadManager", "Error downloading song ${song.title}: ${e.message}", e)
            return@withContext false
        } finally {
            downloadProgress.remove(song.id)
        }
    }
    
    /**
     * Download all songs in a playlist
     */
    suspend fun downloadPlaylist(playlist: Playlist, songUrlMap: Map<String, String>): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        for (songId in playlist.songIds) {
            val streamUrl = songUrlMap[songId]
            if (streamUrl != null) {
                // We need the Song object to download - this would typically come from API
                // For now, we'll create a placeholder implementation
                Log.d("DownloadManager", "Would download song $songId from playlist ${playlist.name}")
                // TODO: Implement when Song objects are available from API
                successCount++
            }
        }
        return@withContext successCount
    }
    
    /**
     * Download entire music library
     */
    suspend fun downloadEntireLibrary(songs: List<Song>, songUrlMap: Map<String, String>): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        for (song in songs) {
            val streamUrl = songUrlMap[song.id]
            if (streamUrl != null) {
                if (downloadSong(song, streamUrl)) {
                    successCount++
                }
            }
        }
        return@withContext successCount
    }
    
    /**
     * Get download status for a song
     */
    fun getDownloadStatus(songId: String): DownloadStatus {
        return when {
            downloadProgress.containsKey(songId) -> DownloadStatus.DOWNLOADING
            downloadMetadata.containsKey(songId) -> DownloadStatus.DOWNLOADED
            else -> DownloadStatus.NOT_DOWNLOADED
        }
    }
    
    /**
     * Get download progress for a song (0-100)
     */
    fun getDownloadProgress(songId: String): Int {
        return downloadProgress[songId] ?: 0
    }
    
    /**
     * Delete downloaded song
     */
    suspend fun deleteDownload(songId: String): Boolean = withContext(Dispatchers.IO) {
        val entry = downloadMetadata[songId]
        if (entry != null) {
            val file = File(entry.filePath)
            val deleted = file.delete()
            if (deleted) {
                downloadMetadata.remove(songId)
                saveDownloadMetadata()
                
                // Clean up empty directories
                cleanupEmptyDirectories(file.parentFile)
                
                Log.d("DownloadManager", "Deleted download: ${entry.title}")
            }
            return@withContext deleted
        }
        return@withContext false
    }
    
    /**
     * Clear all downloads
     */
    suspend fun clearAllDownloads() = withContext(Dispatchers.IO) {
        try {
            downloadDir.deleteRecursively()
            downloadDir.mkdirs()
            downloadMetadata.clear()
            saveDownloadMetadata()
            Log.d("DownloadManager", "All downloads cleared")
        } catch (e: Exception) {
            Log.e("DownloadManager", "Error clearing downloads: ${e.message}", e)
        }
    }
    
    /**
     * Get download statistics
     */
    suspend fun getDownloadStats(): DownloadStats = withContext(Dispatchers.IO) {
        val entries = downloadMetadata.values
        val totalSize = entries.sumOf { it.fileSize }
        val availableSpace = downloadDir.freeSpace
        
        return@withContext DownloadStats(
            totalDownloads = entries.size,
            totalSizeBytes = totalSize,
            storageUsed = formatFileSize(totalSize),
            availableSpace = formatFileSize(availableSpace)
        )
    }
    
    /**
     * Download file with progress tracking (File API)
     */
    private suspend fun downloadFileWithProgress(url: String, file: File, songId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            connection.connect()
            val fileLength = connection.contentLength
            
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(4096)
                    var totalBytesRead = 0
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Update progress
                        if (fileLength > 0) {
                            val progress = (totalBytesRead * 100 / fileLength).toInt()
                            downloadProgress[songId] = progress
                        }
                    }
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("DownloadManager", "Error downloading file: ${e.message}", e)
            return@withContext false
        }
    }
    
    
    /**
     * Sanitize filename for filesystem
     */
    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\-\\s]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * Clean up empty directories
     */
    private fun cleanupEmptyDirectories(dir: File?) {
        if (dir != null && dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files.isNullOrEmpty()) {
                dir.delete()
                // Recursively clean parent if it's also empty
                cleanupEmptyDirectories(dir.parentFile)
            }
        }
    }
    
    /**
     * Format file size for display
     */
    private fun formatFileSize(bytes: Long): String {
        val kiloBytes = bytes / 1024.0
        val megaBytes = kiloBytes / 1024.0
        val gigaBytes = megaBytes / 1024.0
        
        return when {
            gigaBytes >= 1.0 -> String.format("%.1f GB", gigaBytes)
            megaBytes >= 1.0 -> String.format("%.1f MB", megaBytes)
            kiloBytes >= 1.0 -> String.format("%.1f KB", kiloBytes)
            else -> "$bytes bytes"
        }
    }
    
    /**
     * Load download metadata from storage
     */
    private fun loadDownloadMetadata() {
        // TODO: Implement JSON persistence similar to PlaylistManager
        Log.d("DownloadManager", "Download metadata loaded (placeholder implementation)")
    }
    
    /**
     * Save download metadata to storage
     */
    private fun saveDownloadMetadata() {
        // TODO: Implement JSON persistence similar to PlaylistManager
        Log.d("DownloadManager", "Download metadata saved (placeholder implementation)")
    }
}