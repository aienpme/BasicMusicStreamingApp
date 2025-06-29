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
    
    /**
     * Force save any accumulated playback time, even if less than 1 minute
     * This is useful when user navigates to settings to see real-time stats
     */
    fun forceSaveAccumulatedTime() {
        if (isCurrentlyTracking) {
            val currentTime = System.currentTimeMillis()
            val playbackDuration = currentTime - playbackStartTime
            val tempTotalTime = totalPlaybackTime + playbackDuration
            
            // Save accumulated time as minutes (including partial minutes)
            val totalMinutes = tempTotalTime / 60000L // Convert to minutes
            if (totalMinutes > 0) {
                coroutineScope.launch {
                    try {
                        playlistManager.addListeningMinutes(totalMinutes)
                        Log.d("PlaybackStatsTracker", "Force saved $totalMinutes minutes to streaming stats")
                        // Reset accumulated time after saving
                        totalPlaybackTime = tempTotalTime % 60000L // Keep remainder less than 1 minute
                        // Update start time to current time
                        playbackStartTime = currentTime
                    } catch (e: Exception) {
                        Log.e("PlaybackStatsTracker", "Error force saving streaming stats: ${e.message}", e)
                    }
                }
            }
            
            Log.d("PlaybackStatsTracker", "Force save: Duration: ${playbackDuration}ms, Total would be: ${tempTotalTime}ms")
        }
    }
    
    /**
     * Get current accumulated time in seconds (for display purposes)
     * This includes both saved time and current session time
     */
    fun getCurrentAccumulatedSeconds(): Long {
        var accumulated = totalPlaybackTime
        if (isCurrentlyTracking) {
            val currentTime = System.currentTimeMillis()
            val playbackDuration = currentTime - playbackStartTime
            accumulated += playbackDuration
        }
        return accumulated / 1000L // Convert to seconds
    }
} 