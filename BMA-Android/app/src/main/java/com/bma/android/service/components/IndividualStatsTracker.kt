package com.bma.android.service.components

import android.util.Log
import com.bma.android.models.Song
import com.bma.android.storage.IndividualStatsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Tracks individual song listening statistics including minutes and play counts
 * Operates completely separately from PlaybackStatsTracker to avoid conflicts
 */
class IndividualStatsTracker(
    private val statsManager: IndividualStatsManager,
    private val coroutineScope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "IndividualStatsTracker"
        private const val MIN_LISTENING_THRESHOLD_MS = 30000L // 30 seconds minimum
        private const val PLAY_COMPLETION_THRESHOLD = 0.95f // 95% completion for a "play"
    }
    
    // Current tracking state
    private var currentSong: Song? = null
    private var songStartTime: Long = 0L
    private var songDuration: Long = 0L
    private var accumulatedListeningTime: Long = 0L
    private var isCurrentlyTracking = false
    private var hasMetMinimumThreshold = false
    
    /**
     * Start tracking a new song
     * @param song The song that started playing
     * @param duration Duration of the song in milliseconds (can be 0 if unknown initially)
     */
    fun startTrackingSong(song: Song, duration: Long) {
        // Stop tracking previous song if any
        // This should now be safe since MusicService calls stopTracking() before getting here
        if (currentSong != null) {
            Log.d(TAG, "🔄 SWITCHING songs: ${currentSong?.title} → ${song.title}")
            Log.d(TAG, "🔄 Previous song should already be saved by MusicService.skipToNext/Previous")
            Log.d(TAG, "🔄 ⚠️ WARNING: currentSong is not null - this means double tracking might occur!")
            stopTracking() // Still need this as safety fallback
        } else {
            Log.d(TAG, "🔄 No current song to stop - starting fresh tracking")
        }
        
        currentSong = song
        songDuration = duration
        songStartTime = System.currentTimeMillis()
        accumulatedListeningTime = 0L
        isCurrentlyTracking = true
        hasMetMinimumThreshold = false
        
        Log.d(TAG, "🎵 STARTED tracking song: ${song.title} by ${song.artist} (ID: ${song.id}, duration: ${duration}ms)")
    }
    
    /**
     * Update the duration of the currently tracking song
     * This is useful when duration becomes available after song preparation
     * @param duration Duration in milliseconds
     */
    fun updateSongDuration(duration: Long) {
        if (currentSong != null) {
            songDuration = duration
            Log.d(TAG, "Updated duration for ${currentSong?.title}: ${duration}ms")
        }
    }
    
    /**
     * Resume tracking (e.g., after pause)
     */
    fun resumeTracking() {
        if (currentSong != null && !isCurrentlyTracking) {
            songStartTime = System.currentTimeMillis()
            isCurrentlyTracking = true
            Log.d(TAG, "▶️ RESUMED tracking: ${currentSong?.title}")
            Log.d(TAG, "▶️ Previous accumulated time: ${accumulatedListeningTime}ms (${accumulatedListeningTime/1000}s)")
            Log.d(TAG, "▶️ Threshold already met: $hasMetMinimumThreshold")
        } else if (currentSong == null) {
            Log.d(TAG, "▶️ resumeTracking called but currentSong is null")
        } else if (isCurrentlyTracking) {
            Log.d(TAG, "▶️ resumeTracking called but already tracking: ${currentSong?.title}")
        }
    }
    
    /**
     * Pause tracking (e.g., when song is paused)
     */
    fun pauseTracking() {
        if (isCurrentlyTracking) {
            val currentTime = System.currentTimeMillis()
            val sessionTime = currentTime - songStartTime
            accumulatedListeningTime += sessionTime
            
            Log.d(TAG, "⏸️ PAUSE TRACKING: ${currentSong?.title}")
            Log.d(TAG, "⏸️ Session time: ${sessionTime}ms (${sessionTime/1000}s)")
            Log.d(TAG, "⏸️ Total accumulated: ${accumulatedListeningTime}ms (${accumulatedListeningTime/1000}s)")
            Log.d(TAG, "⏸️ Threshold (30s): ${MIN_LISTENING_THRESHOLD_MS}ms")
            
            // Check if we've met the minimum threshold
            if (!hasMetMinimumThreshold && accumulatedListeningTime >= MIN_LISTENING_THRESHOLD_MS) {
                hasMetMinimumThreshold = true
                Log.d(TAG, "✅ THRESHOLD REACHED for: ${currentSong?.title} (${accumulatedListeningTime}ms >= ${MIN_LISTENING_THRESHOLD_MS}ms)")
                Log.d(TAG, "✅ This song is now ELIGIBLE FOR TRACKING if skipped later!")
            } else if (!hasMetMinimumThreshold) {
                val remaining = MIN_LISTENING_THRESHOLD_MS - accumulatedListeningTime
                Log.d(TAG, "⏳ Threshold NOT yet met - need ${remaining}ms (${remaining/1000}s) more")
            }
            
            isCurrentlyTracking = false
            Log.d(TAG, "⏸️ PAUSED tracking: ${currentSong?.title}, session: ${sessionTime}ms, total: ${accumulatedListeningTime}ms, thresholdMet: $hasMetMinimumThreshold")
        } else {
            Log.d(TAG, "⏸️ pauseTracking called but isCurrentlyTracking=false for: ${currentSong?.title}")
        }
    }
    
    /**
     * Stop tracking current song and save statistics
     * @param position Current position in the song (for play completion detection)
     */
    fun stopTracking(position: Long = 0L) {
        val stackTrace = Thread.currentThread().stackTrace
        Log.d(TAG, "🛑 stopTracking CALLED from: ${stackTrace[3].className}.${stackTrace[3].methodName}:${stackTrace[3].lineNumber}")
        Log.d(TAG, "🛑 Call stack: ${stackTrace[4].className}.${stackTrace[4].methodName}:${stackTrace[4].lineNumber}")
        
        if (currentSong == null) {
            Log.d(TAG, "🚫 stopTracking called but currentSong is null")
            Log.d(TAG, "🚫 This suggests stopTracking was already called earlier - check logs above for the previous call")
            return
        }
        
        Log.d(TAG, "🛑 STOP TRACKING called for: ${currentSong?.title}")
        Log.d(TAG, "🛑 Current tracking state: isTracking=$isCurrentlyTracking, accumulated=${accumulatedListeningTime}ms, threshold=$hasMetMinimumThreshold")
        Log.d(TAG, "🛑 Position: ${position}ms, Duration: ${songDuration}ms")
        
        // Calculate final listening time
        if (isCurrentlyTracking) {
            val currentTime = System.currentTimeMillis()
            val sessionTime = currentTime - songStartTime
            accumulatedListeningTime += sessionTime
            Log.d(TAG, "🛑 Added final session time: ${sessionTime}ms, new total: ${accumulatedListeningTime}ms")
        }
        
        // Check if we've met the minimum threshold
        if (!hasMetMinimumThreshold && accumulatedListeningTime >= MIN_LISTENING_THRESHOLD_MS) {
            hasMetMinimumThreshold = true
            Log.d(TAG, "✅ THRESHOLD NOW MET during stopTracking: ${accumulatedListeningTime}ms >= ${MIN_LISTENING_THRESHOLD_MS}ms")
        }
        
        Log.d(TAG, "🛑 Final accumulated time: ${accumulatedListeningTime}ms (${accumulatedListeningTime/1000}s)")
        Log.d(TAG, "🛑 Threshold met: $hasMetMinimumThreshold")
        
        // Save statistics if minimum threshold was met
        if (hasMetMinimumThreshold) {
            val song = currentSong!!
            val listeningMinutes = accumulatedListeningTime / 60000L // Convert to minutes
            
            // Check if this qualifies as a completed play
            val completionPercentage = if (songDuration > 0) {
                (position.toFloat() / songDuration.toFloat()).coerceAtMost(1.0f)
            } else {
                0f
            }
            
            val isCompletedPlay = completionPercentage >= PLAY_COMPLETION_THRESHOLD
            
            Log.d(TAG, "💾 SAVING stats for ${song.title}: ${listeningMinutes}min, completion: ${(completionPercentage * 100).toInt()}%, isPlay: $isCompletedPlay")
            Log.d(TAG, "💾 PAUSE+SKIP SCENARIO: Song was paused and then skipped - this SHOULD be tracked!")
            
            // Save to storage asynchronously
            coroutineScope.launch {
                try {
                    if (listeningMinutes > 0) {
                        Log.d(TAG, "💾 Adding ${listeningMinutes} minutes for song ${song.id}")
                        statsManager.addListeningTime(song.id, listeningMinutes)
                    }
                    if (isCompletedPlay) {
                        Log.d(TAG, "💾 Adding play count for song ${song.id}")
                        statsManager.addPlayCount(song.id)
                    }
                    Log.d(TAG, "✅ Successfully saved stats for ${song.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error saving individual stats: ${e.message}", e)
                }
            }
        } else {
            Log.d(TAG, "❌ Song ${currentSong?.title} did not meet minimum threshold (${accumulatedListeningTime}ms < ${MIN_LISTENING_THRESHOLD_MS}ms)")
            Log.d(TAG, "❌ PAUSE+SKIP ISSUE: This song should have been tracked but wasn't due to insufficient time!")
        }
        
        // Reset tracking state
        Log.d(TAG, "🔄 RESETTING TRACKER STATE - currentSong set to null")
        currentSong = null
        songStartTime = 0L
        songDuration = 0L
        accumulatedListeningTime = 0L
        isCurrentlyTracking = false
        hasMetMinimumThreshold = false
    }
    
    /**
     * Handle song completion (natural end)
     * This counts as 100% completion for play count purposes
     */
    fun onSongCompleted() {
        if (currentSong != null) {
            Log.d(TAG, "Song completed naturally: ${currentSong?.title}")
            stopTracking(songDuration) // Use full duration as position
        }
    }
    
    /**
     * Get current tracking status
     */
    fun isTracking(): Boolean = isCurrentlyTracking && currentSong != null
    
    /**
     * Get current song being tracked
     */
    fun getCurrentSong(): Song? = currentSong
    
    /**
     * Get accumulated listening time for current session (in seconds)
     */
    fun getCurrentAccumulatedSeconds(): Long {
        var accumulated = accumulatedListeningTime
        if (isCurrentlyTracking) {
            val currentTime = System.currentTimeMillis()
            val sessionTime = currentTime - songStartTime
            accumulated += sessionTime
        }
        return accumulated / 1000L
    }
    
    /**
     * Reset all tracking state (e.g., when service is destroyed)
     */
    fun reset() {
        if (currentSong != null) {
            Log.d(TAG, "Resetting tracker, saving current song stats")
            stopTracking()
        }
    }
}