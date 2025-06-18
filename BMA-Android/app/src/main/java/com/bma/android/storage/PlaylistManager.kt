package com.bma.android.storage

import android.content.Context
import android.net.Uri
import com.bma.android.models.Playlist
import com.bma.android.models.Song
import com.bma.android.models.Album
import com.bma.android.api.ApiClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages local storage of playlists and streaming stats using JSON files
 */
class PlaylistManager private constructor(private val context: Context) {
    
    companion object {
        private const val PLAYLISTS_FILE = "playlists.json"
        private const val BACKUP_VERSION = 1
        private const val MIN_BACKUP_VERSION = 1
        
        @Volatile
        private var INSTANCE: PlaylistManager? = null
        
        fun getInstance(context: Context): PlaylistManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaylistManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val playlistsFile = File(context.filesDir, PLAYLISTS_FILE)
    
    /**
     * Load complete app data from storage
     * @return AppData containing playlists and streaming stats
     */
    private suspend fun loadAppData(): AppData = withContext(Dispatchers.IO) {
        try {
            if (!playlistsFile.exists()) {
                return@withContext AppData()
            }
            
            val json = playlistsFile.readText()
            if (json.isBlank()) {
                return@withContext AppData()
            }
            
            // Try to parse as new AppData format first
            return@withContext try {
                gson.fromJson(json, AppData::class.java) ?: AppData()
            } catch (e: Exception) {
                // Fall back to old playlist-only format for backward compatibility
                android.util.Log.d("PlaylistManager", "Converting old playlist format to new AppData format")
                val type = object : TypeToken<List<Playlist>>() {}.type
                val playlists = gson.fromJson<List<Playlist>>(json, type) ?: emptyList()
                AppData(playlists = playlists)
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error loading app data", e)
            return@withContext AppData()
        }
    }
    
    /**
     * Load all playlists from storage
     * @return List of all playlists
     */
    suspend fun loadPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        return@withContext loadAppData().playlists
    }
    
    /**
     * Save complete app data to storage
     * @param appData The app data to save
     */
    private suspend fun saveAppData(appData: AppData) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(appData)
            playlistsFile.writeText(json)
            android.util.Log.d("PlaylistManager", "Saved app data: ${appData.playlists.size} playlists, ${appData.streamingStats.totalMinutesListened} minutes listened")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error saving app data", e)
            throw e
        }
    }
    
    /**
     * Save all playlists to storage
     * @param playlists List of playlists to save
     */
    suspend fun savePlaylists(playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        try {
            val currentAppData = loadAppData()
            val updatedAppData = currentAppData.copy(playlists = playlists)
            saveAppData(updatedAppData)
            android.util.Log.d("PlaylistManager", "Saved ${playlists.size} playlists")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error saving playlists", e)
            throw e
        }
    }
    
    /**
     * Create a new playlist
     * @param name Name of the new playlist
     * @param songIds Optional list of song IDs to initialize the playlist with
     * @return The created playlist
     */
    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Playlist {
        val playlists = loadPlaylists().toMutableList()
        
        // Check for duplicate names
        val baseName = name.trim()
        var finalName = baseName
        var counter = 1
        
        while (playlists.any { it.name == finalName }) {
            finalName = "$baseName ($counter)"
            counter++
        }
        
        val newPlaylist = Playlist(
            name = finalName,
            songIds = songIds
        )
        
        playlists.add(newPlaylist)
        savePlaylists(playlists)
        
        android.util.Log.d("PlaylistManager", "Created playlist: $finalName with ${songIds.size} songs")
        return newPlaylist
    }
    
    /**
     * Update an existing playlist
     * @param updatedPlaylist The playlist with updated information
     */
    suspend fun updatePlaylist(updatedPlaylist: Playlist) {
        val playlists = loadPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == updatedPlaylist.id }
        
        if (index >= 0) {
            playlists[index] = updatedPlaylist
            savePlaylists(playlists)
            android.util.Log.d("PlaylistManager", "Updated playlist: ${updatedPlaylist.name}")
        } else {
            android.util.Log.w("PlaylistManager", "Playlist not found for update: ${updatedPlaylist.id}")
        }
    }
    
    /**
     * Delete a playlist
     * @param playlistId ID of the playlist to delete
     */
    suspend fun deletePlaylist(playlistId: String) {
        val playlists = loadPlaylists().toMutableList()
        val removed = playlists.removeAll { it.id == playlistId }
        
        if (removed) {
            savePlaylists(playlists)
            android.util.Log.d("PlaylistManager", "Deleted playlist: $playlistId")
        } else {
            android.util.Log.w("PlaylistManager", "Playlist not found for deletion: $playlistId")
        }
    }
    
    /**
     * Add a song to a playlist
     * @param playlistId ID of the playlist
     * @param songId ID of the song to add
     */
    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        val playlists = loadPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        
        if (index >= 0) {
            val updatedPlaylist = playlists[index].addSong(songId)
            playlists[index] = updatedPlaylist
            savePlaylists(playlists)
            android.util.Log.d("PlaylistManager", "Added song $songId to playlist: ${updatedPlaylist.name}")
        } else {
            android.util.Log.w("PlaylistManager", "Playlist not found: $playlistId")
        }
    }
    
    /**
     * Remove a song from a playlist
     * @param playlistId ID of the playlist
     * @param songId ID of the song to remove
     */
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        val playlists = loadPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        
        if (index >= 0) {
            val updatedPlaylist = playlists[index].removeSong(songId)
            playlists[index] = updatedPlaylist
            savePlaylists(playlists)
            android.util.Log.d("PlaylistManager", "Removed song $songId from playlist: ${updatedPlaylist.name}")
        } else {
            android.util.Log.w("PlaylistManager", "Playlist not found: $playlistId")
        }
    }
    
    /**
     * Get a specific playlist by ID
     * @param playlistId ID of the playlist
     * @return The playlist if found, null otherwise
     */
    suspend fun getPlaylist(playlistId: String): Playlist? {
        return loadPlaylists().find { it.id == playlistId }
    }
    
    /**
     * Check if a song is in any playlist
     * @param songId ID of the song to check
     * @return List of playlist IDs that contain this song
     */
    suspend fun getPlaylistsContainingSong(songId: String): List<String> {
        return loadPlaylists()
            .filter { it.containsSong(songId) }
            .map { it.id }
    }
    
    /**
     * Clear all playlists (for testing or reset purposes)
     */
    suspend fun clearAllPlaylists() = withContext(Dispatchers.IO) {
        try {
            val currentAppData = loadAppData()
            val clearedAppData = currentAppData.copy(playlists = emptyList())
            saveAppData(clearedAppData)
            android.util.Log.d("PlaylistManager", "Cleared all playlists")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error clearing playlists", e)
        }
    }
    
    /**
     * Get current streaming stats
     * @return StreamingStats containing total minutes listened
     */
    suspend fun getStreamingStats(): StreamingStats = withContext(Dispatchers.IO) {
        return@withContext loadAppData().streamingStats
    }
    
    /**
     * Add minutes to the streaming stats
     * @param minutes Number of minutes to add
     */
    suspend fun addListeningMinutes(minutes: Long) = withContext(Dispatchers.IO) {
        try {
            val currentAppData = loadAppData()
            val currentStats = currentAppData.streamingStats
            val updatedStats = currentStats.copy(
                totalMinutesListened = currentStats.totalMinutesListened + minutes
            )
            val updatedAppData = currentAppData.copy(streamingStats = updatedStats)
            saveAppData(updatedAppData)
            android.util.Log.d("PlaylistManager", "Added $minutes minutes to streaming stats. Total: ${updatedStats.totalMinutesListened}")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error updating streaming stats", e)
            throw e
        }
    }
    
    /**
     * Reset streaming stats (for testing or reset purposes)
     */
    suspend fun resetStreamingStats() = withContext(Dispatchers.IO) {
        try {
            val currentAppData = loadAppData()
            val resetAppData = currentAppData.copy(streamingStats = StreamingStats())
            saveAppData(resetAppData)
            android.util.Log.d("PlaylistManager", "Reset streaming stats")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error resetting streaming stats", e)
        }
    }
    
    /**
     * Get current download stats
     * @return DownloadStats containing download information
     */
    suspend fun getDownloadStats(): DownloadStats = withContext(Dispatchers.IO) {
        return@withContext loadAppData().downloadStats
    }
    
    /**
     * Mark a song as downloaded
     * @param songId ID of the downloaded song
     * @param fileSize Size of the downloaded file in bytes
     */
    suspend fun markSongAsDownloaded(songId: String, fileSize: Long, song: Song? = null) = withContext(Dispatchers.IO) {
        try {
            val currentAppData = loadAppData()
            val currentStats = currentAppData.downloadStats
            val updatedStats = currentStats.copy(
                downloadedSongs = currentStats.downloadedSongs + songId,
                totalDownloadedFiles = currentStats.totalDownloadedFiles + 1,
                totalDownloadSize = currentStats.totalDownloadSize + fileSize
            )
            val updatedAppData = currentAppData.copy(downloadStats = updatedStats)
            saveAppData(updatedAppData)
            
            // CRITICAL FIX: Cache the song metadata for offline access
            song?.let { cacheDownloadedSong(it) }
            
            android.util.Log.d("PlaylistManager", "Marked song $songId as downloaded and cached metadata")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error marking song as downloaded", e)
            throw e
        }
    }
    
    /**
     * Cache downloaded song metadata for offline access
     */
    private suspend fun cacheDownloadedSong(song: Song) = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.filesDir, "downloaded_songs_cache.json")
            val existingCache = if (cacheFile.exists()) {
                val json = cacheFile.readText()
                gson.fromJson(json, object : com.google.gson.reflect.TypeToken<MutableMap<String, Song>>() {}.type) 
                    ?: mutableMapOf<String, Song>()
            } else {
                mutableMapOf<String, Song>()
            }
            
            existingCache[song.id] = song
            cacheFile.writeText(gson.toJson(existingCache))
            android.util.Log.d("PlaylistManager", "Cached song metadata: ${song.title}")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error caching song metadata", e)
        }
    }
    
    /**
     * Get cached downloaded song metadata (for offline mode)
     */
    private suspend fun getCachedDownloadedSongs(): Map<String, Song> = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.filesDir, "downloaded_songs_cache.json")
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val cache: Map<String, Song> = gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Map<String, Song>>() {}.type) 
                    ?: emptyMap()
                android.util.Log.d("PlaylistManager", "Retrieved ${cache.size} cached song objects")
                return@withContext cache
            } else {
                android.util.Log.d("PlaylistManager", "No song cache file found")
                return@withContext emptyMap()
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error reading cached songs", e)
            return@withContext emptyMap()
        }
    }
    
    /**
     * Mark a playlist as downloaded
     * @param playlistId ID of the downloaded playlist
     */
    suspend fun markPlaylistAsDownloaded(playlistId: String) = withContext(Dispatchers.IO) {
        try {
            val currentAppData = loadAppData()
            val currentStats = currentAppData.downloadStats
            val updatedStats = currentStats.copy(
                downloadedPlaylists = currentStats.downloadedPlaylists + playlistId
            )
            val updatedAppData = currentAppData.copy(downloadStats = updatedStats)
            saveAppData(updatedAppData)
            android.util.Log.d("PlaylistManager", "Marked playlist $playlistId as downloaded")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error marking playlist as downloaded", e)
            throw e
        }
    }
    
    /**
     * Remove song from downloaded list
     * @param songId ID of the song to remove
     * @param fileSize Size of the file that was removed
     */
    suspend fun removeSongDownload(songId: String, fileSize: Long) = withContext(Dispatchers.IO) {
        try {
            val currentAppData = loadAppData()
            val currentStats = currentAppData.downloadStats
            val updatedStats = currentStats.copy(
                downloadedSongs = currentStats.downloadedSongs - songId,
                totalDownloadedFiles = (currentStats.totalDownloadedFiles - 1).coerceAtLeast(0),
                totalDownloadSize = (currentStats.totalDownloadSize - fileSize).coerceAtLeast(0)
            )
            val updatedAppData = currentAppData.copy(downloadStats = updatedStats)
            saveAppData(updatedAppData)
            android.util.Log.d("PlaylistManager", "Removed song $songId from downloaded list")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error removing song download", e)
            throw e
        }
    }
    
    /**
     * Check if a song is marked as downloaded
     * @param songId ID of the song to check
     * @return true if downloaded, false otherwise
     */
    suspend fun isSongDownloaded(songId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext loadAppData().downloadStats.downloadedSongs.contains(songId)
    }
    
    /**
     * Check if a playlist is marked as downloaded
     * @param playlistId ID of the playlist to check
     * @return true if downloaded, false otherwise
     */
    suspend fun isPlaylistDownloaded(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext loadAppData().downloadStats.downloadedPlaylists.contains(playlistId)
    }
    
    /**
     * Export playlists to a backup file
     * @param outputUri URI where the backup should be saved
     * @return true if backup was successful, false otherwise
     */
    suspend fun exportBackup(outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val playlists = loadPlaylists()
            val backupData = createBackupData(playlists)
            val json = gson.toJson(backupData)
            
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
                outputStream.flush()
            }
            
            android.util.Log.d("PlaylistManager", "Successfully exported ${playlists.size} playlists to backup")
            return@withContext true
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error exporting backup", e)
            return@withContext false
        }
    }
    
    /**
     * Import playlists from a backup file
     * @param inputUri URI of the backup file to import
     * @param mergeWithExisting if true, merge with existing playlists; if false, replace all
     * @return ImportResult indicating success and details
     */
    suspend fun importBackup(inputUri: Uri, mergeWithExisting: Boolean = true): ImportResult = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext ImportResult.Error("Could not read backup file")
            
            val backupData = gson.fromJson(json, BackupData::class.java)
                ?: return@withContext ImportResult.Error("Invalid backup file format")
            
            // Validate backup data
            if (!isValidBackupData(backupData)) {
                return@withContext ImportResult.Error("Invalid or corrupted backup data")
            }
            
            val existingPlaylists = if (mergeWithExisting) loadPlaylists().toMutableList() else mutableListOf()
            val importedPlaylists = backupData.playlists.toMutableList()
            
            // Handle duplicate names when merging
            if (mergeWithExisting) {
                importedPlaylists.forEach { importedPlaylist ->
                    var newName = importedPlaylist.name
                    var counter = 1
                    
                    while (existingPlaylists.any { it.name == newName }) {
                        newName = "${importedPlaylist.name} ($counter)"
                        counter++
                    }
                    
                    if (newName != importedPlaylist.name) {
                        val index = importedPlaylists.indexOf(importedPlaylist)
                        importedPlaylists[index] = importedPlaylist.copy(
                            name = newName,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
            }
            
            val finalPlaylists = existingPlaylists + importedPlaylists
            
            // Handle streaming stats from backup
            val currentAppData = loadAppData()
            val importedStats = backupData.streamingStats
            
            val finalAppData = if (importedStats != null && !mergeWithExisting) {
                // Replace existing stats when doing full replace
                currentAppData.copy(
                    playlists = finalPlaylists,
                    streamingStats = importedStats
                )
            } else if (importedStats != null && mergeWithExisting) {
                // Merge stats by adding the minutes together
                val mergedStats = StreamingStats(
                    totalMinutesListened = currentAppData.streamingStats.totalMinutesListened + importedStats.totalMinutesListened
                )
                currentAppData.copy(
                    playlists = finalPlaylists,
                    streamingStats = mergedStats
                )
            } else {
                // No stats in backup or old backup format - keep existing stats
                currentAppData.copy(playlists = finalPlaylists)
            }
            
            saveAppData(finalAppData)
            
            val statsMessage = if (importedStats != null) {
                val action = if (mergeWithExisting) "merged" else "restored"
                " and streaming stats $action"
            } else ""
            
            val message = if (mergeWithExisting) {
                "Successfully imported ${importedPlaylists.size} playlists (merged with existing)$statsMessage"
            } else {
                "Successfully imported ${importedPlaylists.size} playlists (replaced existing)$statsMessage"
            }
            
            android.util.Log.d("PlaylistManager", message)
            return@withContext ImportResult.Success(importedPlaylists.size, message)
            
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error importing backup", e)
            return@withContext ImportResult.Error("Error importing backup: ${e.message}")
        }
    }
    
    /**
     * Generate a suggested filename for backup
     */
    fun generateBackupFilename(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "BMA_Playlists_Backup_$timestamp.json"
    }
    
    /**
     * Create backup data structure with metadata
     */
    private suspend fun createBackupData(playlists: List<Playlist>): BackupData {
        val currentStreamingStats = getStreamingStats()
        return BackupData(
            version = BACKUP_VERSION,
            exportDate = System.currentTimeMillis(),
            playlistCount = playlists.size,
            playlists = playlists,
            streamingStats = currentStreamingStats
        )
    }
    
    /**
     * Validate backup data structure
     */
    private fun isValidBackupData(backupData: BackupData): Boolean {
        return try {
            backupData.version >= MIN_BACKUP_VERSION &&
            backupData.playlists.isNotEmpty() &&
            backupData.playlistCount == backupData.playlists.size &&
            backupData.playlists.all { it.name.isNotBlank() }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Data class for complete app data structure
     */
    data class AppData(
        val playlists: List<Playlist> = emptyList(),
        val streamingStats: StreamingStats = StreamingStats(),
        val downloadStats: DownloadStats = DownloadStats()
    )
    
    /**
     * Data class for streaming statistics
     */
    data class StreamingStats(
        val totalMinutesListened: Long = 0L
    )
    
    /**
     * Data class for download statistics
     */
    data class DownloadStats(
        val downloadedSongs: Set<String> = emptySet(),
        val downloadedPlaylists: Set<String> = emptySet(),
        val totalDownloadedFiles: Int = 0,
        val totalDownloadSize: Long = 0L
    )
    
    /**
     * Data class for backup file structure
     */
    data class BackupData(
        val version: Int,
        val exportDate: Long,
        val playlistCount: Int,
        val playlists: List<Playlist>,
        val streamingStats: StreamingStats? = null  // Optional for backward compatibility
    )
    
    /**
     * Result of import operation
     */
    sealed class ImportResult {
        data class Success(val importedCount: Int, val message: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
    
    // TODO: These methods are placeholders for download functionality
    // In a real implementation, these would interact with the music API
    
    /**
     * Get all songs in the music library
     */
    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        try {
            val authHeader = ApiClient.getAuthHeader()
            if (authHeader == null || ApiClient.isTokenExpired(context)) {
                android.util.Log.w("PlaylistManager", "Not authenticated or token expired")
                return@withContext emptyList()
            }
            
            val songs = ApiClient.api.getSongs(authHeader)
            android.util.Log.d("PlaylistManager", "Fetched ${songs.size} songs from API")
            return@withContext songs.sortedBy { it.sortOrder }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error fetching songs from API", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Get songs for a specific album
     */
    suspend fun getSongsForAlbum(albumName: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val allSongs = getAllSongs()
            val albumSongs = allSongs.filter { it.album == albumName }
            android.util.Log.d("PlaylistManager", "Found ${albumSongs.size} songs for album: $albumName")
            return@withContext albumSongs
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error getting songs for album: $albumName", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Get songs for a specific playlist
     */
    suspend fun getSongsForPlaylist(playlistId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val playlist = getPlaylist(playlistId)
            if (playlist == null) {
                android.util.Log.w("PlaylistManager", "Playlist not found: $playlistId")
                return@withContext emptyList()
            }
            
            val allSongs = getAllSongs()
            val playlistSongs = playlist.getSongs(allSongs)
            android.util.Log.d("PlaylistManager", "Found ${playlistSongs.size} songs for playlist: ${playlist.name}")
            return@withContext playlistSongs
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error getting songs for playlist: $playlistId", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Get all albums organized from songs
     */
    suspend fun getAllAlbums(): List<Album> = withContext(Dispatchers.IO) {
        try {
            val allSongs = getAllSongs()
            
            // Group songs by album (excluding empty/unknown albums)
            val albumGroups = allSongs
                .filter { it.album.isNotEmpty() && it.album != "Unknown Album" }
                .groupBy { it.album }
            
            // Create album objects for groups with 2+ songs
            val albums = albumGroups
                .filter { (_, songs) -> songs.size >= 2 }
                .map { (albumName, songs) ->
                    Album(
                        name = albumName,
                        artist = songs.firstOrNull()?.artist,
                        songs = songs
                    )
                }
                .sortedBy { it.name }
            
            android.util.Log.d("PlaylistManager", "Found ${albums.size} albums from ${allSongs.size} songs")
            return@withContext albums
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error getting albums", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Mark a song as downloaded
     */
    suspend fun markAsDownloaded(songId: String) = withContext(Dispatchers.IO) {
        markSongAsDownloaded(songId, 0L) // Use existing method with placeholder size
    }
    
    /**
     * Mark a song as not downloaded
     */
    suspend fun markAsNotDownloaded(songId: String) = withContext(Dispatchers.IO) {
        removeSongDownload(songId, 0L) // Use existing method with placeholder size
    }
    
    /**
     * Clear all download status
     */
    suspend fun clearAllDownloadStatus() = withContext(Dispatchers.IO) {
        val appData = loadAppData()
        val clearedStats = appData.downloadStats.copy(
            downloadedSongs = emptySet(),
            downloadedPlaylists = emptySet(),
            totalDownloadSize = 0L
        )
        saveAppData(appData.copy(downloadStats = clearedStats))
    }
    
    // OFFLINE MODE DATA FILTERING METHODS
    
    /**
     * Get only downloaded songs when in offline mode
     * @return List of songs that have been downloaded
     */
    suspend fun getAllSongsOffline(): List<Song> = withContext(Dispatchers.IO) {
        try {
            // CRITICAL FIX: Use cached song metadata instead of API calls for true offline mode
            val cachedSongs = getCachedDownloadedSongs()
            val downloadStats = getDownloadStats()
            
            android.util.Log.d("PlaylistManager", "=== OFFLINE SONGS DEBUG (FIXED) ===")
            android.util.Log.d("PlaylistManager", "Cached song objects: ${cachedSongs.size}")
            android.util.Log.d("PlaylistManager", "Download stats - downloaded song IDs: ${downloadStats.downloadedSongs}")
            android.util.Log.d("PlaylistManager", "Download stats - total downloaded files: ${downloadStats.totalDownloadedFiles}")
            
            // Filter cached songs by what's actually marked as downloaded
            val downloadedSongs = cachedSongs.values.filter { song ->
                val isDownloaded = downloadStats.downloadedSongs.contains(song.id)
                if (isDownloaded) {
                    android.util.Log.d("PlaylistManager", "Found offline song: ${song.title} (${song.id})")
                }
                isDownloaded
            }
            
            android.util.Log.d("PlaylistManager", "True offline mode: ${downloadedSongs.size} downloaded songs available")
            return@withContext downloadedSongs.sortedBy { it.sortOrder }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error getting offline songs", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Get only albums that have downloaded songs when in offline mode
     * @return List of albums with at least one downloaded song
     */
    suspend fun getAllAlbumsOffline(): List<Album> = withContext(Dispatchers.IO) {
        try {
            val downloadedSongs = getAllSongsOffline()
            
            android.util.Log.d("PlaylistManager", "=== OFFLINE ALBUMS DEBUG ===")
            android.util.Log.d("PlaylistManager", "Downloaded songs for album grouping: ${downloadedSongs.size}")
            
            if (downloadedSongs.isEmpty()) {
                android.util.Log.d("PlaylistManager", "Offline mode: No downloaded songs, no albums available")
                return@withContext emptyList()
            }
            
            // Group downloaded songs by album
            val albumGroups = downloadedSongs
                .filter { it.album.isNotEmpty() && it.album != "Unknown Album" }
                .groupBy { it.album }
            
            android.util.Log.d("PlaylistManager", "Album groups found: ${albumGroups.keys}")
            
            // Create album objects for groups with 1+ songs (more permissive for offline)
            val albums = albumGroups.map { (albumName, songs) ->
                android.util.Log.d("PlaylistManager", "Creating offline album: $albumName with ${songs.size} songs")
                Album(
                    name = albumName,
                    artist = songs.firstOrNull()?.artist,
                    songs = songs
                )
            }.sortedBy { it.name }
            
            android.util.Log.d("PlaylistManager", "Offline mode: ${albums.size} albums with downloaded songs")
            return@withContext albums
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error getting offline albums", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Get only playlists that have downloaded songs when in offline mode
     * @return List of playlists with at least one downloaded song
     */
    suspend fun getPlaylistsOffline(): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            val allPlaylists = loadPlaylists()
            val downloadStats = getDownloadStats()
            val downloadedSongIds = downloadStats.downloadedSongs
            
            if (downloadedSongIds.isEmpty()) {
                android.util.Log.d("PlaylistManager", "Offline mode: No downloaded songs, no playlists available")
                return@withContext emptyList()
            }
            
            // Filter playlists that have at least one downloaded song
            val offlinePlaylists = allPlaylists.filter { playlist ->
                playlist.songIds.any { songId -> downloadedSongIds.contains(songId) }
            }
            
            android.util.Log.d("PlaylistManager", "Offline mode: ${offlinePlaylists.size} playlists with downloaded songs")
            return@withContext offlinePlaylists
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error getting offline playlists", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Get songs for a specific playlist in offline mode (only downloaded songs)
     * @param playlistId ID of the playlist
     * @return List of downloaded songs from the playlist
     */
    suspend fun getSongsForPlaylistOffline(playlistId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val playlist = getPlaylist(playlistId)
            if (playlist == null) {
                android.util.Log.w("PlaylistManager", "Playlist not found: $playlistId")
                return@withContext emptyList()
            }
            
            val downloadedSongs = getAllSongsOffline()
            val playlistSongs = playlist.getSongs(downloadedSongs)
            
            android.util.Log.d("PlaylistManager", "Offline mode: ${playlistSongs.size} downloaded songs for playlist: ${playlist.name}")
            return@withContext playlistSongs
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error getting offline songs for playlist: $playlistId", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Get songs for a specific album in offline mode (only downloaded songs)
     * @param albumName Name of the album
     * @return List of downloaded songs from the album
     */
    suspend fun getSongsForAlbumOffline(albumName: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val downloadedSongs = getAllSongsOffline()
            val albumSongs = downloadedSongs.filter { it.album == albumName }
            
            android.util.Log.d("PlaylistManager", "Offline mode: ${albumSongs.size} downloaded songs for album: $albumName")
            return@withContext albumSongs
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error getting offline songs for album: $albumName", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Search downloaded songs only when in offline mode
     * @param query Search query
     * @return List of downloaded songs matching the query
     */
    suspend fun searchSongsOffline(query: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) {
                return@withContext getAllSongsOffline()
            }
            
            val downloadedSongs = getAllSongsOffline()
            val searchQuery = query.lowercase().trim()
            
            val matchingSongs = downloadedSongs.filter { song ->
                song.title.lowercase().contains(searchQuery) ||
                song.artist.lowercase().contains(searchQuery) ||
                song.album.lowercase().contains(searchQuery)
            }
            
            android.util.Log.d("PlaylistManager", "Offline search for '$query': ${matchingSongs.size} results")
            return@withContext matchingSongs.sortedBy { it.sortOrder }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error searching offline songs", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Get count of downloaded items for offline mode stats
     * @return Triple of (downloaded songs count, albums with downloads count, playlists with downloads count)
     */
    suspend fun getOfflineCounts(): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        try {
            val downloadedSongs = getAllSongsOffline().size
            val albumsWithDownloads = getAllAlbumsOffline().size
            val playlistsWithDownloads = getPlaylistsOffline().size
            
            return@withContext Triple(downloadedSongs, albumsWithDownloads, playlistsWithDownloads)
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error getting offline counts", e)
            return@withContext Triple(0, 0, 0)
        }
    }
}