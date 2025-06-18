package com.bma.android.service.components

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Manages progress update notifications for music playback
 */
class ProgressUpdateManager(
    private val progressCallback: ProgressCallback,
    private val stateCallback: StateCallback
) {
    
    interface ProgressCallback {
        fun getCurrentPosition(): Int
        fun getDuration(): Int
        fun isPlaying(): Boolean
    }
    
    interface StateCallback {
        fun onProgressUpdate(position: Int, duration: Int)
        fun onStateSaveRequired()
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var lastStateSaveTime = 0L
    
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (progressCallback.isPlaying()) {
                val position = progressCallback.getCurrentPosition()
                val duration = progressCallback.getDuration()
                stateCallback.onProgressUpdate(position, duration)
                
                // Save playback state every 10 seconds during playback
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastStateSaveTime > 10000) { // 10 seconds
                    stateCallback.onStateSaveRequired()
                    lastStateSaveTime = currentTime
                }
                
                handler.postDelayed(this, 1000) // Update every second
            }
        }
    }
    
    fun startProgressUpdates() {
        stopProgressUpdates()
        handler.post(progressUpdateRunnable)
        Log.d("ProgressUpdateManager", "Progress updates started")
    }
    
    fun stopProgressUpdates() {
        handler.removeCallbacks(progressUpdateRunnable)
        Log.d("ProgressUpdateManager", "Progress updates stopped")
    }
} 