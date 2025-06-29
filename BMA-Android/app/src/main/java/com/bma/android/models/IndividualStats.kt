package com.bma.android.models

/**
 * Data models for individual streaming statistics tracking
 * Tracks per-song listening data including minutes and play counts
 */

/**
 * Streaming statistics for an individual song
 * @param songId Unique identifier for the song
 * @param totalMinutes Total minutes listened (30+ second sessions aggregated)
 * @param playCount Number of completed plays (95%+ completion)
 */
data class SongStats(
    val songId: String,
    val totalMinutes: Long = 0L,
    val playCount: Int = 0
)

/**
 * Aggregated streaming statistics for an artist
 * @param artistName Name of the artist
 * @param totalMinutes Total minutes listened across all songs by this artist
 * @param playCount Total completed plays across all songs by this artist
 */
data class ArtistStats(
    val artistName: String,
    val totalMinutes: Long = 0L,
    val playCount: Int = 0
)

/**
 * Aggregated streaming statistics for an album
 * @param albumName Name of the album
 * @param artistName Name of the album artist
 * @param totalMinutes Total minutes listened across all songs in this album
 * @param playCount Total completed plays across all songs in this album
 */
data class AlbumStats(
    val albumName: String,
    val artistName: String,
    val totalMinutes: Long = 0L,
    val playCount: Int = 0
)

/**
 * Container for all individual streaming statistics data
 * @param songStats Map of song ID to song statistics
 * @param version Data format version for future migration support
 * @param lastUpdated Timestamp of last update
 */
data class IndividualStatsData(
    val songStats: Map<String, SongStats> = emptyMap(),
    val version: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis()
)