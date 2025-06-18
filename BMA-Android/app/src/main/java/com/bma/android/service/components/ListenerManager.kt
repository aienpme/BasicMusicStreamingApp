package com.bma.android.service.components

import android.util.Log
import com.bma.android.models.Song

/**
 * Manages listeners for music service events
 */
class ListenerManager {
    
    // Listener interface for UI updates
    interface MusicServiceListener {
        fun onPlaybackStateChanged(state: Int)
        fun onSongChanged(song: Song?)
        fun onProgressChanged(progress: Int, duration: Int)
        fun onQueueChanged(queue: List<Song>) {}  // Optional method with default implementation
    }
    
    private val listeners = mutableListOf<MusicServiceListener>()
    
    fun addListener(listener: MusicServiceListener) {
        Log.d("ListenerManager", "=== ADD LISTENER ===")
        Log.d("ListenerManager", "Listener type: ${listener.javaClass.simpleName}")
        Log.d("ListenerManager", "Total listeners before: ${listeners.size}")
        
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            Log.d("ListenerManager", "Listener added successfully")
        } else {
            Log.d("ListenerManager", "Listener already exists, not adding duplicate")
        }
        
        Log.d("ListenerManager", "Total listeners after: ${listeners.size}")
    }
    
    fun removeListener(listener: MusicServiceListener) {
        Log.d("ListenerManager", "=== REMOVE LISTENER ===")
        Log.d("ListenerManager", "Listener type: ${listener.javaClass.simpleName}")
        Log.d("ListenerManager", "Total listeners before: ${listeners.size}")
        
        listeners.remove(listener)
        
        Log.d("ListenerManager", "Total listeners after: ${listeners.size}")
    }
    
    // Legacy method for backward compatibility
    fun setListener(listener: MusicServiceListener?) {
        Log.d("ListenerManager", "=== SET LISTENER (LEGACY) ===")
        Log.d("ListenerManager", "This method is deprecated, use addListener/removeListener instead")
        
        if (listener != null) {
            addListener(listener)
        } else {
            Log.d("ListenerManager", "Null listener passed to setListener - this may cause issues")
        }
    }
    
    fun notifyPlaybackStateChanged(state: Int) {
        Log.d("ListenerManager", "Notifying ${listeners.size} listeners of playback state change: $state")
        listeners.forEach { listener ->
            listener.onPlaybackStateChanged(state)
        }
    }
    
    fun notifySongChanged(song: Song?) {
        Log.d("ListenerManager", "Notifying ${listeners.size} listeners of song change: ${song?.title}")
        listeners.forEach { listener ->
            listener.onSongChanged(song)
        }
    }
    
    fun notifyProgressChanged(progress: Int, duration: Int) {
        listeners.forEach { listener ->
            listener.onProgressChanged(progress, duration)
        }
    }
    
    fun notifyQueueChanged(queue: List<Song>) {
        Log.d("ListenerManager", "🔔 === NOTIFYING QUEUE CHANGED ===")
        Log.d("ListenerManager", "Queue size: ${queue.size}")
        Log.d("ListenerManager", "Number of listeners: ${listeners.size}")
        
        listeners.forEach { listener ->
            Log.d("ListenerManager", "📢 Notifying listener: ${listener.javaClass.simpleName}")
            listener.onQueueChanged(queue)
        }
        
        Log.d("ListenerManager", "🏁 === QUEUE CHANGE NOTIFICATION COMPLETE ===")
    }
    
    fun hasListeners(): Boolean = listeners.isNotEmpty()
    
    fun getListenerCount(): Int = listeners.size
    
    /**
     * Notify new listener immediately if we have current state
     */
    fun notifyNewListener(listener: MusicServiceListener, currentSong: Song?, playbackState: Int) {
        if (currentSong != null) {
            Log.d("ListenerManager", "Immediately notifying new listener of current state")
            listener.onSongChanged(currentSong)
            listener.onPlaybackStateChanged(playbackState)
        }
    }
} 