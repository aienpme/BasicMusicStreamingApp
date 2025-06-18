package com.bma.android.service.components

import android.util.Log
import com.bma.android.models.Song

/**
 * Handles queue navigation logic including skip next/previous with repeat modes
 */
class QueueNavigator(
    private val queue: MusicQueue,
    private val playbackCallback: PlaybackCallback
) {
    
    interface PlaybackCallback {
        fun getCurrentSong(): Song?
        fun playNewSong(song: Song)
        fun seekTo(position: Int)
        fun stop()
        fun getRepeatMode(): Int
    }
    
    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ALL = 1
        const val REPEAT_MODE_ONE = 2
    }
    
    /**
     * Skip to the next song in the queue
     * @return The next song to play, or null if end of queue is reached
     */
    fun skipToNext(): Song? {
        Log.d("QueueNavigator", "🎵 === SKIP TO NEXT ===")
        val repeatMode = playbackCallback.getRepeatMode()
        Log.d("QueueNavigator", "Repeat mode: $repeatMode")
        Log.d("QueueNavigator", "Current queue position: ${queue.getCurrentPosition()}")
        Log.d("QueueNavigator", "Queue size: ${queue.size()}")
        Log.d("QueueNavigator", "Has next: ${queue.hasNext()}")
        
        // Handle repeat one mode
        if (repeatMode == REPEAT_MODE_ONE) {
            Log.d("QueueNavigator", "🔂 Repeat one - seeking to 0")
            playbackCallback.seekTo(0)
            return playbackCallback.getCurrentSong()
        }
        
        // Try to move to next song
        val nextSong = queue.next()
        if (nextSong != null) {
            Log.d("QueueNavigator", "▶️ Playing next song: ${nextSong.title}")
            playbackCallback.playNewSong(nextSong)
            return nextSong
        } 
        
        // Handle end of queue with repeat all
        if (repeatMode == REPEAT_MODE_ALL) {
            Log.d("QueueNavigator", "🔁 Repeat all - resetting to beginning")
            return handleRepeatAll()
        }
        
        // End of queue, no repeat
        Log.d("QueueNavigator", "⏹️ End of queue, no repeat - stopping")
        playbackCallback.stop()
        
        Log.d("QueueNavigator", "🏁 === SKIP TO NEXT COMPLETE ===")
        return null
    }
    
    /**
     * Skip to the previous song in the queue
     * @return The previous song to play, or null if at beginning
     */
    fun skipToPrevious(): Song? {
        val previousSong = queue.previous()
        if (previousSong != null) {
            // Move to previous song in queue
            playbackCallback.playNewSong(previousSong)
            return previousSong
        }
        
        // Handle beginning of queue with repeat all
        if (playbackCallback.getRepeatMode() == REPEAT_MODE_ALL) {
            return handleRepeatAllReverse()
        }
        
        // If no previous and no repeat, just stay at current song
        return playbackCallback.getCurrentSong()
    }
    
    /**
     * Handle repeat all mode when reaching end of queue
     */
    private fun handleRepeatAll(): Song? {
        val playlist = queue.getOriginalPlaylist()
        Log.d("QueueNavigator", "📝 Playlist from queue (size: ${playlist.size}):")
        playlist.forEachIndexed { index, song ->
            Log.d("QueueNavigator", "  [$index] ${song.title}")
        }
        
        val wasShuffled = queue.getIsShuffled()
        Log.d("QueueNavigator", "Was shuffled: $wasShuffled")
        
        // Reset to beginning
        queue.setPlaylist(playlist, 0)
        if (wasShuffled) {
            queue.shuffle()
        }
        
        val firstSong = queue.getCurrentSong()
        if (firstSong != null) {
            Log.d("QueueNavigator", "▶️ Playing first song: ${firstSong.title}")
            playbackCallback.playNewSong(firstSong)
            return firstSong
        }
        
        return null
    }
    
    /**
     * Handle repeat all mode when going previous from beginning
     */
    private fun handleRepeatAllReverse(): Song? {
        // Reset queue position to end
        val playlist = queue.getOriginalPlaylist()
        val wasShuffled = queue.getIsShuffled()
        
        queue.setPlaylist(playlist, playlist.size - 1)
        if (wasShuffled) {
            queue.shuffle()
            // Navigate to last position in shuffled queue
            repeat(playlist.size - 1) { queue.next() }
        }
        
        val lastSong = queue.getCurrentSong()
        if (lastSong != null) {
            playbackCallback.playNewSong(lastSong)
            return lastSong
        }
        
        return null
    }
} 