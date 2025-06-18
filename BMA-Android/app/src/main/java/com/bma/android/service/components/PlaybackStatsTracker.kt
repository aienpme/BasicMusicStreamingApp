package com.bma.android.service.components

import android.util.Log
import com.bma.android.storage.PlaylistManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Tracks playback statistics and listening time
 */
class PlaybackStatsTracker(
    private val playlistManager: PlaylistManager,
    private val coroutineScope: CoroutineScope
) {
    
    // Streaming stats tracking
    private var playbackStartTime: Long = 0L
    private var totalPlaybackTime: Long = 0L
    private var isCurrentlyTracking = false
    
    /**
     * Start tracking playback time for the current session
     */
    fun startTracking() {
        if (!isCurrentlyTracking) {
            playbackStartTime = System.currentTimeMillis()
            isCurrentlyTracking = true
            Log.d("PlaybackStatsTracker", "Started playback tracking at $playbackStartTime")
        }
    }
    
    /**
     * Stop tracking and save accumulated listening time
     */
    fun stopTracking() {
        if (isCurrentlyTracking) {
            val currentTime = System.currentTimeMillis()
            val playbackDuration = currentTime - playbackStartTime
            totalPlaybackTime += playbackDuration
            
            // Save accumulated time as minutes when we reach at least 1 minute
            val totalMinutes = totalPlaybackTime / 60000L // Convert to minutes
            if (totalMinutes > 0) {
                coroutineScope.launch {
                    try {
                        playlistManager.addListeningMinutes(totalMinutes)
                        Log.d("PlaybackStatsTracker", "Saved $totalMinutes minutes to streaming stats")
                        // Reset accumulated time after saving
                        totalPlaybackTime = 0L
                    } catch (e: Exception) {
                        Log.e("PlaybackStatsTracker", "Error saving streaming stats: ${e.message}", e)
                    }
                }
            }
            
            isCurrentlyTracking = false
            Log.d("PlaybackStatsTracker", "Stopped playback tracking. Duration: ${playbackDuration}ms, Total accumulated: ${totalPlaybackTime}ms")
        }
    }
    
    /**
     * Reset tracking state (e.g., when service is destroyed)
     */
    fun reset() {
        if (isCurrentlyTracking) {
            stopTracking()
        }
        playbackStartTime = 0L
        totalPlaybackTime = 0L
        isCurrentlyTracking = false
    }
    
    /**
     * Get current tracking status
     */
    fun isTracking(): Boolean = isCurrentlyTracking
} 