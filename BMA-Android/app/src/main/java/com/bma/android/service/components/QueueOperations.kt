package com.bma.android.service.components

import android.util.Log
import com.bma.android.models.Song

/**
 * Wraps MusicQueue operations with notification callbacks for UI updates
 */
class QueueOperations(
    private val queue: MusicQueue,
    private val callback: QueueOperationsCallback
) {
    
    interface QueueOperationsCallback {
        fun onQueueChanged()
        fun onSongChanged(song: Song?)
        fun onPlayNewSong(song: Song)
    }
    
    /**
     * Add a song to the end of the queue
     */
    fun addToQueue(song: Song) {
        queue.addToQueue(song)
        Log.d("QueueOperations", "Added to queue: ${song.title}")
        callback.onQueueChanged()
    }
    
    /**
     * Add multiple songs to the end of the queue
     */
    fun addToQueue(songs: List<Song>) {
        queue.addToQueue(songs)
        Log.d("QueueOperations", "Added ${songs.size} songs to queue")
        callback.onQueueChanged()
    }
    
    /**
     * Add a song to play next (after current song)
     */
    fun addNext(song: Song) {
        queue.addNext(song)
        Log.d("QueueOperations", "Added next: ${song.title}")
        callback.onQueueChanged()
    }
    
    /**
     * Get the current queue contents
     */
    fun getCurrentQueue(): List<Song> = queue.getQueueContents()
    
    /**
     * Get the upcoming queue (songs after current position)
     */
    fun getUpcomingQueue(): List<Song> {
        val currentPos = queue.getCurrentPosition()
        val upcomingQueue = queue.getQueueFromPosition(currentPos + 1)
        Log.d("QueueOperations", "📋 getUpcomingQueue() - currentPos=$currentPos, upcoming size=${upcomingQueue.size}")
        return upcomingQueue
    }
    
    /**
     * Remove a song from the queue at the specified position
     * @param position The position in the queue to remove (0-based)
     * @return true if removal was successful
     */
    fun removeFromQueue(position: Int): Boolean {
        val success = queue.removeFromQueue(position)
        if (success) {
            Log.d("QueueOperations", "Removed song from queue at position: $position")
            callback.onQueueChanged()
        }
        return success
    }
    
    /**
     * Move a song in the queue from one position to another
     * @param fromPosition Source position (0-based)
     * @param toPosition Target position (0-based)
     * @return true if move was successful
     */
    fun moveQueueItem(fromPosition: Int, toPosition: Int): Boolean {
        Log.d("QueueOperations", "🎯 === MOVE QUEUE ITEM ===")
        Log.d("QueueOperations", "fromPosition=$fromPosition, toPosition=$toPosition")
        Log.d("QueueOperations", "Current queue size: ${queue.size()}")
        Log.d("QueueOperations", "Current position in queue: ${queue.getCurrentPosition()}")
        
        try {
            // Log current queue before move
            Log.d("QueueOperations", "📝 Queue before move:")
            val currentQueueContents = queue.getQueueContents()
            currentQueueContents.forEachIndexed { index, song ->
                Log.d("QueueOperations", "  [$index] ${song.title}")
            }
            
            val success = queue.moveQueueItem(fromPosition, toPosition)
            
            if (success) {
                Log.d("QueueOperations", "✅ Successfully moved queue item from $fromPosition to $toPosition")
                
                // Log queue after move
                Log.d("QueueOperations", "📝 Queue after move:")
                val newQueueContents = queue.getQueueContents()
                newQueueContents.forEachIndexed { index, song ->
                    Log.d("QueueOperations", "  [$index] ${song.title}")
                }
                
                // Immediate notification to ensure UI updates quickly
                Log.d("QueueOperations", "🔔 Notifying queue changed immediately")
                callback.onQueueChanged()
            } else {
                Log.w("QueueOperations", "❌ Failed to move queue item from $fromPosition to $toPosition")
            }
            
            Log.d("QueueOperations", "🏁 === MOVE QUEUE ITEM COMPLETE ===")
            return success
        } catch (e: Exception) {
            Log.e("QueueOperations", "💥 Error moving queue item: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Jump to a specific position in the queue and start playing
     * @param position The queue position to jump to (0-based)
     * @return true if jump was successful
     */
    fun jumpToQueuePosition(position: Int): Boolean {
        val song = queue.jumpToQueuePosition(position)
        if (song != null) {
            Log.d("QueueOperations", "Jumped to queue position: $position, song: ${song.title}")
            // Start playing the new song
            callback.onPlayNewSong(song)
            // Notify listeners of song change
            callback.onSongChanged(song)
            callback.onQueueChanged()
            return true
        }
        return false
    }
} 