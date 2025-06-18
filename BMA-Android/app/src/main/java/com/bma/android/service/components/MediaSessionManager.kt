package com.bma.android.service.components

import android.content.Context
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.bma.android.models.Song

/**
 * Manages MediaSession for system media controls integration
 */
class MediaSessionManager(private val context: Context) {
    
    private var mediaSession: MediaSessionCompat? = null
    
    // Callback interface for media control events
    interface MediaControlCallback {
        fun onPlay()
        fun onPause()
        fun onSkipToNext()
        fun onSkipToPrevious()
        fun onStop()
    }
    
    fun initializeMediaSession(callback: MediaControlCallback) {
        mediaSession = MediaSessionCompat(context, "MusicService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { callback.onPlay() }
                override fun onPause() { callback.onPause() }
                override fun onSkipToNext() { callback.onSkipToNext() }
                override fun onSkipToPrevious() { callback.onSkipToPrevious() }
                override fun onStop() { callback.onStop() }
            })
            isActive = true
        }
    }
    
    fun updatePlaybackState(
        state: Int,
        position: Long,
        playbackSpeed: Float = 1f
    ) {
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, position, playbackSpeed)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .build()
            
        mediaSession?.setPlaybackState(playbackState)
    }
    
    fun updateMediaMetadata(
        song: Song,
        duration: Long,
        albumArt: Bitmap? = null
    ) {
        try {
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .apply {
                    albumArt?.let { bitmap ->
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    }
                }
                .build()
                
            mediaSession?.setMetadata(metadata)
            Log.d("MediaSessionManager", "MediaMetadata updated for: ${song.title}")
        } catch (e: Exception) {
            Log.e("MediaSessionManager", "Failed to update MediaMetadata: ${e.message}", e)
        }
    }
    
    fun getSessionToken(): MediaSessionCompat.Token? = mediaSession?.sessionToken
    
    fun release() {
        mediaSession?.release()
        mediaSession = null
    }
    
    fun isActive(): Boolean = mediaSession?.isActive ?: false
} 