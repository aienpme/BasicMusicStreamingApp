package com.bma.android.storage

import android.content.Context
import android.util.Log
import com.bma.android.api.ApiClient
import com.bma.android.models.IndividualStatsData
import com.bma.android.models.SongStats
import com.bma.android.models.ArtistStats
import com.bma.android.models.AlbumStats
import com.bma.android.models.Song
import com.bma.android.storage.OfflineModeManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages storage and retrieval of individual streaming statistics
 * Uses separate JSON file storage independent of PlaylistManager
 */
class IndividualStatsManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "IndividualStatsManager"
        private const val STATS_FILE = "individual_stats.json"
        private const val CURRENT_VERSION = 1
        
        @Volatile
        private var INSTANCE: IndividualStatsManager? = null
        
        fun getInstance(context: Context): IndividualStatsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IndividualStatsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val statsFile = File(context.filesDir, STATS_FILE)
    
    /**
     * Load individual stats data from storage
     */
    private suspend fun loadStatsData(): IndividualStatsData = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Looking for stats file at: ${statsFile.absolutePath}")
            if (!statsFile.exists()) {
                Log.d(TAG, "❌ Stats file doesn't exist at ${statsFile.absolutePath}, returning empty data")
                return@withContext IndividualStatsData()
            }
            
            val json = statsFile.readText()
            if (json.isBlank()) {
                Log.d(TAG, "Stats file is empty, returning empty data")
                return@withContext IndividualStatsData()
            }
            
            val data = gson.fromJson(json, IndividualStatsData::class.java) ?: IndividualStatsData()
            Log.d(TAG, "Loaded stats data: ${data.songStats.size} songs tracked")
            return@withContext data
        } catch (e: Exception) {
            Log.e(TAG, "Error loading stats data: ${e.message}", e)
            return@withContext IndividualStatsData()
        }
    }
    
    /**
     * Save individual stats data to storage
     */
    private suspend fun saveStatsData(data: IndividualStatsData) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 About to save stats data to: ${statsFile.absolutePath}")
            val updatedData = data.copy(lastUpdated = System.currentTimeMillis())
            val json = gson.toJson(updatedData)
            Log.d(TAG, "💾 JSON data to save: ${json.take(200)}...")
            statsFile.writeText(json)
            Log.d(TAG, "💾 File write completed. File exists: ${statsFile.exists()}, Size: ${statsFile.length()}")
            Log.d(TAG, "✅ Successfully saved stats data: ${data.songStats.size} songs tracked")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving stats data: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Add listening time to a song's statistics
     * @param songId The unique ID of the song
     * @param minutes Minutes to add to the song's total
     */
    suspend fun addListeningTime(songId: String, minutes: Long) = withContext(Dispatchers.IO) {
        try {
            val data = loadStatsData()
            val currentStats = data.songStats[songId] ?: SongStats(songId)
            val updatedStats = currentStats.copy(totalMinutes = currentStats.totalMinutes + minutes)
            
            val updatedSongStats = data.songStats.toMutableMap()
            updatedSongStats[songId] = updatedStats
            
            val updatedData = data.copy(songStats = updatedSongStats)
            saveStatsData(updatedData)
            
            Log.d(TAG, "Added ${minutes}min to song $songId (total: ${updatedStats.totalMinutes}min)")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding listening time for song $songId: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Add a play count to a song's statistics
     * @param songId The unique ID of the song
     */
    suspend fun addPlayCount(songId: String) = withContext(Dispatchers.IO) {
        try {
            val data = loadStatsData()
            val currentStats = data.songStats[songId] ?: SongStats(songId)
            val updatedStats = currentStats.copy(playCount = currentStats.playCount + 1)
            
            val updatedSongStats = data.songStats.toMutableMap()
            updatedSongStats[songId] = updatedStats
            
            val updatedData = data.copy(songStats = updatedSongStats)
            saveStatsData(updatedData)
            
            Log.d(TAG, "Added play to song $songId (total: ${updatedStats.playCount} plays)")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding play count for song $songId: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get statistics for a specific song
     * @param songId The unique ID of the song
     * @return SongStats for the song, or null if no stats exist
     */
    suspend fun getSongStats(songId: String): SongStats? = withContext(Dispatchers.IO) {
        return@withContext try {
            val data = loadStatsData()
            data.songStats[songId]
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stats for song $songId: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get all song statistics sorted by total minutes (descending)
     * @return List of SongStats sorted by most listened
     */
    suspend fun getAllSongStats(): List<SongStats> = withContext(Dispatchers.IO) {
        return@withContext try {
            val data = loadStatsData()
            data.songStats.values.sortedByDescending { it.totalMinutes }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all song stats: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Split artist string into individual artists using common separators
     * @param artistString The full artist string (e.g., "Kanye West and Jay-Z")
     * @return List of individual artist names
     */
    private fun splitArtistString(artistString: String): List<String> {
        val separators = listOf(
            " and ", " & ", ", ", " feat. ", " feat ", " ft. ", " ft ", 
            " featuring ", " with ", " vs. ", " vs ", " x ", " X "
        )
        
        var artists = listOf(artistString.trim())
        
        // Apply each separator
        separators.forEach { separator ->
            artists = artists.flatMap { artist ->
                artist.split(separator, ignoreCase = true)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
        }
        
        // Remove duplicates and return
        return artists.distinct()
    }

    /**
     * Extract the primary/main artist from a full artist string
     * @param artistString The full artist string (e.g., "Jason Derulo feat. Meghan Trainor")
     * @return The primary artist name (e.g., "Jason Derulo")
     */
    private fun extractPrimaryArtist(artistString: String): String {
        val featureSeparators = listOf(
            " feat. ", " feat ", " ft. ", " ft ", " featuring ", 
            " with ", " and ", " & ", ", "
        )
        
        var primaryArtist = artistString.trim()
        
        // Find the first occurrence of any feature separator and take everything before it
        featureSeparators.forEach { separator ->
            val index = primaryArtist.indexOf(separator, ignoreCase = true)
            if (index != -1) {
                primaryArtist = primaryArtist.substring(0, index).trim()
                return@forEach // Exit loop after first match
            }
        }
        
        return primaryArtist
    }

    /**
     * Get aggregated artist statistics from song data
     * @return List of ArtistStats sorted by total minutes (descending)
     */
    suspend fun getArtistStats(): List<ArtistStats> = withContext(Dispatchers.IO) {
        return@withContext try {
            val songStats = getAllSongStats()
            if (songStats.isEmpty()) return@withContext emptyList()
            
            // Get all songs to map IDs to artist names
            val allSongs = if (OfflineModeManager.isOfflineMode()) {
                Log.d(TAG, "Loading songs in offline mode for artist stats aggregation")
                PlaylistManager.getInstance(context).getAllSongsOffline()
            } else {
                Log.d(TAG, "Loading songs in online mode for artist stats aggregation")
                PlaylistManager.getInstance(context).getAllSongs()
            }
            
            val songIdToArtist = allSongs.associateBy({ it.id }, { it.artist })
            Log.d(TAG, "🎵 Artist stats: Loaded ${allSongs.size} songs for mapping, processing ${songStats.size} song stats")
            
            // Group song stats by individual artists and aggregate
            val artistStatsMap = mutableMapOf<String, Pair<Long, Int>>() // artist -> (totalMinutes, totalPlays)
            
            songStats.forEach { songStat ->
                val fullArtistString = songIdToArtist[songStat.songId]
                if (fullArtistString != null) {
                    // Split the artist string into individual artists
                    val individualArtists = splitArtistString(fullArtistString)
                    Log.d(TAG, "🎤 Split '$fullArtistString' → ${individualArtists.joinToString(", ")}")
                    
                    // Give full credit to each artist (not split credit)
                    val artistCount = individualArtists.size
                    
                    // Each artist gets the full minutes and plays (not divided)
                    val minutesPerArtist = songStat.totalMinutes
                    val playsPerArtist = songStat.playCount
                    
                    Log.d(TAG, "🎤 Giving full credit: ${songStat.totalMinutes}min/${songStat.playCount}plays → ${minutesPerArtist}min/${playsPerArtist}plays to each of ${artistCount} artists")
                    
                    individualArtists.forEach { artistName ->
                        val current = artistStatsMap[artistName] ?: Pair(0L, 0)
                        val newMinutes = current.first + minutesPerArtist
                        val newPlays = current.second + playsPerArtist
                        artistStatsMap[artistName] = Pair(newMinutes, newPlays)
                        
                        Log.d(TAG, "🎤 '$artistName': ${current.first}min + ${minutesPerArtist}min = ${newMinutes}min, ${current.second}plays + ${playsPerArtist}plays = ${newPlays}plays")
                    }
                }
            }
            
            // Convert to ArtistStats and sort
            val result = artistStatsMap.map { (artistName, stats) ->
                Log.d(TAG, "🎤 Final artist stats for '$artistName': ${stats.first}min, ${stats.second}plays")
                ArtistStats(artistName, stats.first, stats.second)
            }.sortedByDescending { it.totalMinutes }
            
            Log.d(TAG, "🎤 Artist stats: Generated ${result.size} individual artist entries from ${artistStatsMap.size} artists")
            Log.d(TAG, "🎤 Top 3 artists by minutes: ${result.take(3).map { "${it.artistName}: ${it.totalMinutes}min" }}")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting artist stats: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get aggregated album statistics from song data
     * @return List of AlbumStats sorted by total minutes (descending)
     */
    suspend fun getAlbumStats(): List<AlbumStats> = withContext(Dispatchers.IO) {
        return@withContext try {
            val songStats = getAllSongStats()
            if (songStats.isEmpty()) return@withContext emptyList()
            
            // Get all songs to map IDs to album/artist names
            val allSongs = if (OfflineModeManager.isOfflineMode()) {
                Log.d(TAG, "Loading songs in offline mode for album stats aggregation")
                PlaylistManager.getInstance(context).getAllSongsOffline()
            } else {
                Log.d(TAG, "Loading songs in online mode for album stats aggregation")
                PlaylistManager.getInstance(context).getAllSongs()
            }
            
            val songIdToAlbumInfo = allSongs.associateBy({ it.id }, { Pair(it.album, it.artist) })
            Log.d(TAG, "💿 Album stats: Loaded ${allSongs.size} songs for mapping, processing ${songStats.size} song stats")
            
            // Group song stats by album name only (not album+artist pairs)
            val albumStatsMap = mutableMapOf<String, Pair<Long, Int>>() // albumName -> (totalMinutes, totalPlays)
            val albumPrimaryArtist = mutableMapOf<String, String>() // albumName -> primaryArtist
            
            songStats.forEach { songStat ->
                val albumInfo = songIdToAlbumInfo[songStat.songId]
                if (albumInfo != null) {
                    val albumName = albumInfo.first
                    val artistName = albumInfo.second
                    
                    // Track primary artist for this album (extract main artist, not featured artists)
                    if (!albumPrimaryArtist.containsKey(albumName)) {
                        val primaryArtist = extractPrimaryArtist(artistName)
                        albumPrimaryArtist[albumName] = primaryArtist
                        Log.d(TAG, "💿 Setting primary artist for '$albumName': '$primaryArtist' (extracted from '$artistName')")
                    }
                    
                    // Aggregate stats by album name only
                    val current = albumStatsMap[albumName] ?: Pair(0L, 0)
                    albumStatsMap[albumName] = Pair(
                        current.first + songStat.totalMinutes,
                        current.second + songStat.playCount
                    )
                }
            }
            
            // Convert to AlbumStats and sort
            val result = albumStatsMap.map { (albumName, stats) ->
                val primaryArtist = albumPrimaryArtist[albumName] ?: "Unknown Artist"
                AlbumStats(albumName, primaryArtist, stats.first, stats.second)
            }.sortedByDescending { it.totalMinutes }
            
            Log.d(TAG, "💿 Album stats: Generated ${result.size} unique album entries (was ${albumStatsMap.size} before grouping)")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting album stats: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get total count of songs with statistics
     * @return Number of songs that have been tracked
     */
    suspend fun getTotalTrackedSongs(): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val data = loadStatsData()
            data.songStats.size
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total tracked songs: ${e.message}", e)
            0
        }
    }
    
    /**
     * Clear all individual statistics (for testing or reset purposes)
     */
    suspend fun clearAllStats() = withContext(Dispatchers.IO) {
        try {
            val emptyData = IndividualStatsData()
            saveStatsData(emptyData)
            Log.d(TAG, "Cleared all individual stats")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing stats: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Export stats data for backup
     * @return IndividualStatsData that can be included in backup files
     */
    suspend fun exportForBackup(): IndividualStatsData = withContext(Dispatchers.IO) {
        return@withContext loadStatsData()
    }
    
    /**
     * Import stats data from backup
     * @param statsData The IndividualStatsData to restore
     */
    suspend fun importFromBackup(statsData: IndividualStatsData) = withContext(Dispatchers.IO) {
        try {
            saveStatsData(statsData)
            Log.d(TAG, "Imported stats from backup: ${statsData.songStats.size} songs")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing stats from backup: ${e.message}", e)
            throw e
        }
    }
}