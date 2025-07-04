package com.bma.android.service.components

import android.support.v4.media.session.MediaSessionCompat
import com.bma.android.MusicService
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.bma.android.models.Song

/**
 * Coordinates updates between notification, media session, and metadata
 */
class NotificationCoordinator(
    private val notificationManager: MusicNotificationManager,
    private val mediaSessionManager: MediaSessionManager,
    private val coordinatorCallback: CoordinatorCallback
) {
    
    interface CoordinatorCallback {
        fun getCurrentSong(): Song?
        fun getCurrentPosition(): Int
        fun getDuration(): Int
        fun getPlaybackState(): Int
        fun isPlaying(): Boolean
    }
    
    /**
     * Update the notification with current playback state
     */
    fun updateNotification() {
        try {
            val currentSong = coordinatorCallback.getCurrentSong()
            val playbackState = coordinatorCallback.getPlaybackState()
            
            if (playbackState != MusicService.STATE_STOPPED && currentSong != null) {
                val notification = notificationManager.createNotification(
                    currentSong,
                    coordinatorCallback.isPlaying(),
                    mediaSessionManager.getSessionToken()
                )
                notificationManager.updateNotification(notification)
            }
        } catch (e: Exception) {
            Log.e("NotificationCoordinator", "Failed to update notification: ${e.message}", e)
        }
    }
    
    /**
     * Update the media session playback state
     */
    fun updatePlaybackState() {
        val state = when (coordinatorCallback.getPlaybackState()) {
            MusicService.STATE_PLAYING -> PlaybackStateCompat.STATE_PLAYING
            MusicService.STATE_PAUSED -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        
        mediaSessionManager.updatePlaybackState(
            state, 
            coordinatorCallback.getCurrentPosition().toLong(), 
            1f
        )
    }
    
    /**
     * Update media metadata for the current song
     * @param song The current song
     */
    fun updateMediaMetadata(song: Song) {
        mediaSessionManager.updateMediaMetadata(
            song, 
            coordinatorCallback.getDuration().toLong(), 
            notificationManager.getCurrentAlbumArt()
        )
    }
    
    /**
     * Update everything - notification, playback state, and metadata
     * Useful when song changes or playback state changes significantly
     */
    fun updateAll() {
        val currentSong = coordinatorCallback.getCurrentSong()
        if (currentSong != null) {
            Log.d("NotificationCoordinator", "Updating all: notification, metadata, and playback state")
            
            // Clear any cached artwork first to ensure fresh load
            notificationManager.clearAlbumArt()
            
            // Update playback state first
            updatePlaybackState()
            
            // Load album art and update both notification and MediaSession metadata
            notificationManager.loadAlbumArt(currentSong) {
                // Album art loaded, update MediaSession metadata with high-quality artwork
                updateMediaMetadata(currentSong)
                // Then update notification
                updateNotification()
            }
            
            // Also update MediaSession metadata immediately with no artwork
            // This ensures the MediaSession shows new song info right away
            updateMediaMetadata(currentSong)
            
            // Also update notification immediately with no artwork
            // This ensures the notification shows new song info right away
            updateNotification()
        }
    }
    
    /**
     * Get the current session token
     */
    fun getSessionToken(): MediaSessionCompat.Token? = mediaSessionManager.getSessionToken()
} 