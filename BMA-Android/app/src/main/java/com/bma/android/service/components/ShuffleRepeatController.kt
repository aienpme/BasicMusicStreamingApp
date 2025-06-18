package com.bma.android.service.components

import android.util.Log

/**
 * Controls shuffle and repeat modes for music playback
 */
class ShuffleRepeatController(
    private val queue: MusicQueue,
    private val callback: ShuffleRepeatCallback
) {
    
    interface ShuffleRepeatCallback {
        fun onQueueChanged()
    }
    
    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ALL = 1
        const val REPEAT_MODE_ONE = 2
    }
    
    private var repeatMode = REPEAT_MODE_OFF
    
    /**
     * Toggle shuffle mode on/off
     * @return true if shuffle is now enabled, false if disabled
     */
    fun toggleShuffle(): Boolean {
        if (queue.getIsShuffled()) {
            // Currently shuffled, turn off shuffle
            queue.unshuffle()
            Log.d("ShuffleRepeatController", "Shuffle disabled - restored original order")
        } else {
            // Currently not shuffled, turn on shuffle
            queue.shuffle()
            Log.d("ShuffleRepeatController", "Shuffle enabled - created shuffled order")
        }
        
        val isShuffleEnabled = queue.getIsShuffled()
        Log.d("ShuffleRepeatController", "Shuffle toggled: $isShuffleEnabled")
        
        // Notify that the queue has changed
        callback.onQueueChanged()
        
        return isShuffleEnabled
    }
    
    /**
     * Check if shuffle mode is currently enabled
     * @return true if shuffle is enabled
     */
    fun isShuffleEnabled(): Boolean = queue.getIsShuffled()
    
    /**
     * Cycle through repeat modes: off -> all -> one -> off
     * @return the new repeat mode
     */
    fun cycleRepeatMode(): Int {
        repeatMode = when (repeatMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ALL  // off -> repeat all
            REPEAT_MODE_ALL -> REPEAT_MODE_ONE  // repeat all -> repeat one
            REPEAT_MODE_ONE -> REPEAT_MODE_OFF  // repeat one -> off
            else -> REPEAT_MODE_OFF
        }
        Log.d("ShuffleRepeatController", "Repeat mode changed: $repeatMode")
        return repeatMode
    }
    
    /**
     * Get the current repeat mode
     * @return current repeat mode (0=off, 1=all, 2=one)
     */
    fun getRepeatMode(): Int = repeatMode
    
    /**
     * Set the repeat mode directly
     * @param mode The repeat mode to set
     */
    fun setRepeatMode(mode: Int) {
        repeatMode = mode
        Log.d("ShuffleRepeatController", "Repeat mode set to: $repeatMode")
    }
    
    /**
     * Enable shuffle mode
     */
    fun enableShuffle() {
        if (!queue.getIsShuffled()) {
            queue.shuffle()
            Log.d("ShuffleRepeatController", "Shuffle enabled")
            callback.onQueueChanged()
        }
    }
    
    /**
     * Disable shuffle mode  
     */
    fun disableShuffle() {
        if (queue.getIsShuffled()) {
            queue.unshuffle()
            Log.d("ShuffleRepeatController", "Shuffle disabled")
            callback.onQueueChanged()
        }
    }
} 