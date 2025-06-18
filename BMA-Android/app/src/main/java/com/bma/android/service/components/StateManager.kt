package com.bma.android.service.components

import android.util.Log
import com.bma.android.models.Song
import com.bma.android.storage.PlaybackState
import com.bma.android.storage.PlaybackStateManager
import com.bma.android.storage.OfflineModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages saving and restoring playback state
 */
class StateManager(
    private val playbackStateManager: PlaybackStateManager,
    private val coroutineScope: CoroutineScope,
    private val stateCallback: StateCallback
) {
    
    interface StateCallback {
        fun getCurrentSong(): Song?
        fun getCurrentPosition(): Int
        fun getQueueContents(): List<Song>
        fun getQueuePosition(): Int
        fun isShuffled(): Boolean
        fun getRepeatMode(): Int
        fun setPlaylist(songs: List<Song>, position: Int)
        fun setShuffle(enabled: Boolean)
        fun setRepeatMode(mode: Int)
        fun setCurrentSong(song: Song)
        fun preparePlayer()
        fun setupOfflinePlayback(songId: String, position: Int)
        fun setupOnlinePlayback(songId: String, position: Int)
    }
    
    /**
     * Save the current playback state
     */
    fun saveCurrentPlaybackState() {
        try {
            val currentSong = stateCallback.getCurrentSong()
            val queueContents = stateCallback.getQueueContents()
            
            if (currentSong != null && queueContents.isNotEmpty()) {
                playbackStateManager.savePlaybackState(
                    currentSong = currentSong,
                    currentPosition = stateCallback.getCurrentPosition(),
                    queue = queueContents,
                    queuePosition = stateCallback.getQueuePosition(),
                    isShuffled = stateCallback.isShuffled(),
                    repeatMode = stateCallback.getRepeatMode()
                )
                Log.d("StateManager", "Playback state saved successfully")
            } else {
                Log.d("StateManager", "No playback state to save (no song or empty queue)")
            }
        } catch (e: Exception) {
            Log.e("StateManager", "Error saving playback state: ${e.message}", e)
        }
    }
    
    /**
     * Restore previously saved playback state
     * @return true if state was successfully restored
     */
    fun restorePlaybackState(): Boolean {
        try {
            val savedState = playbackStateManager.getPlaybackState()
            if (savedState != null && savedState.currentSong != null && savedState.queue.isNotEmpty()) {
                Log.d("StateManager", "Restoring playback state: ${savedState.currentSong.title}")
                
                // Restore the queue and position
                stateCallback.setPlaylist(savedState.queue, savedState.queuePosition)
                if (savedState.isShuffled) {
                    stateCallback.setShuffle(true)
                }
                
                // Restore other settings
                stateCallback.setRepeatMode(savedState.repeatMode)
                stateCallback.setCurrentSong(savedState.currentSong)
                
                // Prepare the player for the saved song
                stateCallback.preparePlayer()
                
                // Check if we're in offline mode and use local files for restore
                val isOffline = OfflineModeManager.isOfflineMode()
                
                if (isOffline) {
                    Log.d("StateManager", "Offline mode: Restoring with local file")
                    setupOfflineRestore(savedState)
                } else {
                    Log.d("StateManager", "Online mode: Restoring with streaming URL")
                    setupOnlineRestore(savedState)
                }
                
                Log.d("StateManager", "Playback state restored successfully")
                return true
            } else {
                Log.d("StateManager", "No valid playback state to restore")
            }
        } catch (e: Exception) {
            Log.e("StateManager", "Error restoring playback state: ${e.message}", e)
        }
        return false
    }
    
    private fun setupOfflineRestore(savedState: PlaybackState) {
        coroutineScope.launch(Dispatchers.Main) {
            stateCallback.setupOfflinePlayback(savedState.currentSong?.id ?: "", savedState.currentPosition)
        }
    }
    
    private fun setupOnlineRestore(savedState: PlaybackState) {
        stateCallback.setupOnlinePlayback(savedState.currentSong?.id ?: "", savedState.currentPosition)
    }
} 