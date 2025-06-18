package com.bma.android.service.components

import com.bma.android.models.Song

/**
 * Queue management class for handling both normal and shuffled playback order
 */
class MusicQueue {
    private var originalPlaylist: List<Song> = emptyList()
    private var currentQueue: List<Song> = emptyList()
    private var queuePosition: Int = 0
    private var isShuffled: Boolean = false
    
    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        originalPlaylist = songs
        currentQueue = songs.toList()
        queuePosition = startIndex.coerceIn(0, songs.size - 1)
        isShuffled = false
    }
    
    fun shuffle() {
        if (originalPlaylist.isEmpty()) return
        
        // Get current song before shuffling
        val currentSong = getCurrentSong()
        
        if (currentSong != null) {
            // Create list of all songs except current song
            val songsToShuffle = originalPlaylist.filter { it.id != currentSong.id }.toMutableList()
            
            // Shuffle the remaining songs using Fisher-Yates algorithm
            for (i in songsToShuffle.size - 1 downTo 1) {
                val j = (0..i).random()
                val temp = songsToShuffle[i]
                songsToShuffle[i] = songsToShuffle[j]
                songsToShuffle[j] = temp
            }
            
            // Create new queue with current song at position 0, followed by shuffled songs
            currentQueue = mutableListOf<Song>().apply {
                add(currentSong)  // Current song at position 0
                addAll(songsToShuffle)  // Shuffled songs after
            }
            
            // Set position to 0 so current song is playing and all others are upcoming
            queuePosition = 0
        } else {
            // No current song - just shuffle entire playlist
            val shuffled = originalPlaylist.toMutableList()
            for (i in shuffled.size - 1 downTo 1) {
                val j = (0..i).random()
                val temp = shuffled[i]
                shuffled[i] = shuffled[j]
                shuffled[j] = temp
            }
            currentQueue = shuffled
            queuePosition = 0
        }
        
        isShuffled = true
    }
    
    fun unshuffle() {
        if (originalPlaylist.isEmpty()) return
        
        // Get current song before unshuffling
        val currentSong = getCurrentSong()
        
        // Restore original order
        currentQueue = originalPlaylist.toList()
        isShuffled = false
        
        // Find current song in original order and set position
        currentSong?.let { song ->
            queuePosition = currentQueue.indexOfFirst { it.id == song.id }
            if (queuePosition == -1) queuePosition = 0
        }
    }
    
    fun next(): Song? {
        android.util.Log.d("MusicQueue", "🎵 === NEXT() CALLED ===")
        android.util.Log.d("MusicQueue", "Current queue size: ${currentQueue.size}")
        android.util.Log.d("MusicQueue", "Current position: $queuePosition")
        android.util.Log.d("MusicQueue", "Can move next: ${queuePosition < currentQueue.size - 1}")
        
        if (currentQueue.isEmpty()) {
            android.util.Log.d("MusicQueue", "❌ Queue is empty")
            return null
        }
        
        if (queuePosition < currentQueue.size - 1) {
            queuePosition++
            val nextSong = currentQueue[queuePosition]
            android.util.Log.d("MusicQueue", "✅ Moving to position $queuePosition: ${nextSong.title}")
            
            // Log current queue state
            android.util.Log.d("MusicQueue", "📝 Current queue after next():")
            currentQueue.forEachIndexed { index, song ->
                val marker = if (index == queuePosition) "👉" else "  "
                android.util.Log.d("MusicQueue", "$marker[$index] ${song.title}")
            }
            
            return nextSong
        }
        
        android.util.Log.d("MusicQueue", "⏭️ Reached end of queue")
        return null // End of queue
    }
    
    fun previous(): Song? {
        if (currentQueue.isEmpty()) return null
        
        if (queuePosition > 0) {
            queuePosition--
            return currentQueue[queuePosition]
        }
        
        return null // Beginning of queue
    }
    
    fun getCurrentSong(): Song? {
        return if (currentQueue.isNotEmpty() && queuePosition in currentQueue.indices) {
            currentQueue[queuePosition]
        } else null
    }
    
    fun hasNext(): Boolean = queuePosition < currentQueue.size - 1
    
    fun hasPrevious(): Boolean = queuePosition > 0
    
    fun getIsShuffled(): Boolean = isShuffled
    
    fun getCurrentPosition(): Int = queuePosition
    
    fun size(): Int = currentQueue.size
    
    fun isEmpty(): Boolean = currentQueue.isEmpty()
    
    fun getOriginalPlaylist(): List<Song> = originalPlaylist.toList()
    
    // Dynamic queue management methods
    fun addToQueue(song: Song) {
        if (currentQueue.isEmpty()) return
        
        currentQueue = currentQueue.toMutableList().apply { add(song) }
        if (!isShuffled) {
            originalPlaylist = originalPlaylist.toMutableList().apply { add(song) }
        }
    }
    
    fun addToQueue(songs: List<Song>) {
        if (currentQueue.isEmpty() || songs.isEmpty()) return
        
        currentQueue = currentQueue.toMutableList().apply { addAll(songs) }
        if (!isShuffled) {
            originalPlaylist = originalPlaylist.toMutableList().apply { addAll(songs) }
        }
    }
    
    fun addNext(song: Song) {
        if (currentQueue.isEmpty()) return
        
        val nextPosition = queuePosition + 1
        currentQueue = currentQueue.toMutableList().apply { 
            add(nextPosition.coerceAtMost(size), song) 
        }
        if (!isShuffled) {
            // For original playlist, add after current song's original position
            val currentSong = getCurrentSong()
            val originalIndex = originalPlaylist.indexOfFirst { it.id == currentSong?.id }
            if (originalIndex >= 0) {
                originalPlaylist = originalPlaylist.toMutableList().apply { 
                    add(originalIndex + 1, song) 
                }
            }
        }
    }
    
    /**
     * Remove a song from the queue at the specified position
     * @param position The position in the queue to remove (0-based)
     * @return true if removal was successful, false if position invalid or current song
     */
    fun removeFromQueue(position: Int): Boolean {
        if (position < 0 || position >= currentQueue.size) return false
        if (position == queuePosition) return false // Don't remove currently playing song
        
        val mutableQueue = currentQueue.toMutableList()
        val songToRemove = mutableQueue[position]
        mutableQueue.removeAt(position)
        currentQueue = mutableQueue
        
        // Adjust queue position if removing before current position
        if (position < queuePosition) {
            queuePosition--
        }
        
        // Also remove from original playlist if not shuffled
        if (!isShuffled) {
            originalPlaylist = originalPlaylist.toMutableList().apply { 
                removeAll { it.id == songToRemove.id }
            }
        }
        
        return true
    }
    
    /**
     * Move a song in the queue from one position to another
     * @param fromPosition Source position (0-based)
     * @param toPosition Target position (0-based)
     * @return true if move was successful, false if positions invalid
     */
    fun moveQueueItem(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < 0 || fromPosition >= currentQueue.size) return false
        if (toPosition < 0 || toPosition >= currentQueue.size) return false
        if (fromPosition == toPosition) return false
        
        // Update the current queue
        val mutableQueue = currentQueue.toMutableList()
        val songToMove = mutableQueue.removeAt(fromPosition)
        mutableQueue.add(toPosition, songToMove)
        currentQueue = mutableQueue
        
        // CRITICAL FIX: Also update the original playlist if not shuffled
        // This ensures the queue stays consistent when skipping tracks
        if (!isShuffled) {
            val mutableOriginal = originalPlaylist.toMutableList()
            val originalSongToMove = mutableOriginal.removeAt(fromPosition)
            mutableOriginal.add(toPosition, originalSongToMove)
            originalPlaylist = mutableOriginal
            
            android.util.Log.d("MusicQueue", "📝 Updated original playlist:")
            originalPlaylist.forEachIndexed { index, song ->
                android.util.Log.d("MusicQueue", "  [$index] ${song.title}")
            }
        }
        
        // Update queue position if needed
        when {
            fromPosition == queuePosition -> queuePosition = toPosition
            fromPosition < queuePosition && toPosition >= queuePosition -> queuePosition--
            fromPosition > queuePosition && toPosition <= queuePosition -> queuePosition++
        }
        
        return true
    }
    
    /**
     * Jump to a specific position in the queue
     * @param position The queue position to jump to (0-based)
     * @return The song at that position, or null if position invalid
     */
    fun jumpToQueuePosition(position: Int): Song? {
        if (position < 0 || position >= currentQueue.size) return null
        
        queuePosition = position
        return getCurrentSong()
    }
    
    fun getQueueContents(): List<Song> = currentQueue.toList()
    
    fun getQueueFromPosition(position: Int = queuePosition): List<Song> {
        return if (position < currentQueue.size) {
            currentQueue.drop(position)
        } else {
            emptyList()
        }
    }
} 