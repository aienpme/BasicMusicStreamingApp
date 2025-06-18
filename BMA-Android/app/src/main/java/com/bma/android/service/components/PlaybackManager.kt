package com.bma.android.service.components

import android.content.Context
import android.util.Log
import com.bma.android.api.ApiClient
import com.bma.android.models.Song
import com.bma.android.storage.DownloadManager
import com.bma.android.storage.OfflineModeManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages ExoPlayer and playback functionality
 */
class PlaybackManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    
    private var exoPlayer: ExoPlayer? = null
    
    // Callback interface for playback events
    interface PlaybackCallback {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onSongReady()
        fun onSongEnded()
        fun onPlayerError(error: PlaybackException)
        fun onVolumeChanged(volume: Float)
    }
    
    private var callback: PlaybackCallback? = null
    
    fun setCallback(callback: PlaybackCallback) {
        this.callback = callback
    }
    
    /**
     * Initialize ExoPlayer based on online/offline mode
     */
    fun createPlayer() {
        // CRITICAL FIX: Different ExoPlayer setup for offline vs online mode
        val isOffline = OfflineModeManager.isOfflineMode()
        
        if (isOffline) {
            Log.d("PlaybackManager", "Creating ExoPlayer for offline mode (no auth required)")
            // For offline mode, use default data source factory (supports file:// URIs)
            exoPlayer = ExoPlayer.Builder(context)
                .build()
        } else {
            // For online mode, use HTTP data source with auth headers
            val authHeader = ApiClient.getAuthHeader()
            if (authHeader == null) {
                Log.e("PlaybackManager", "No auth header for ExoPlayer setup")
                return
            }

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("Authorization" to authHeader))

            val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

            exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
        }
            
        // Set audio attributes for music playback
        val exoAudioAttributes = com.google.android.exoplayer2.audio.AudioAttributes.Builder()
            .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
            .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        exoPlayer?.setAudioAttributes(exoAudioAttributes, true)
            
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                Log.d("PlaybackManager", "ExoPlayer state changed: $state")
                when (state) {
                    Player.STATE_READY -> {
                        Log.d("PlaybackManager", "ExoPlayer ready, playWhenReady: ${exoPlayer?.playWhenReady}")
                        callback?.onSongReady()
                    }
                    Player.STATE_BUFFERING -> {
                        Log.d("PlaybackManager", "ExoPlayer buffering")
                    }
                    Player.STATE_ENDED -> {
                        Log.d("PlaybackManager", "ExoPlayer ended")
                        callback?.onSongEnded()
                    }
                    Player.STATE_IDLE -> {
                        Log.d("PlaybackManager", "ExoPlayer idle")
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("PlaybackManager", "ExoPlayer isPlaying changed: $isPlaying")
                callback?.onPlaybackStateChanged(isPlaying)
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackManager", "ExoPlayer error: ${error.message}", error)
                Log.e("PlaybackManager", "Error code: ${error.errorCode}")
                callback?.onPlayerError(error)
            }
        })
    }
    
    /**
     * Setup playback for online streaming
     */
    fun setupOnlinePlayback(song: Song) {
        val streamUrl = "${ApiClient.getServerUrl()}/stream/${song.id}"
        val authHeader = ApiClient.getAuthHeader()
        
        Log.d("PlaybackManager", "Stream URL: $streamUrl")
        Log.d("PlaybackManager", "Auth header present: ${authHeader != null}")
        
        if (authHeader != null) {
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(song.id)
                .build()
                
            Log.d("PlaybackManager", "Setting media item and preparing...")
            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                Log.d("PlaybackManager", "ExoPlayer prepared, playWhenReady set to true")
            }
        } else {
            // Handle auth error
            Log.e("PlaybackManager", "No auth header available")
        }
    }
    
    /**
     * Setup playback for offline/downloaded files
     */
    suspend fun setupOfflinePlayback(song: Song) {
        try {
            Log.d("PlaybackManager", "=== OFFLINE PLAYBACK DIAGNOSTIC ===")
            Log.d("PlaybackManager", "Song: ${song.title} (ID: ${song.id})")
            
            val downloadManager = DownloadManager.getInstance(context)
            
            // Check download status
            val isDownloaded = downloadManager.isDownloaded(song.id)
            Log.d("PlaybackManager", "DownloadManager.isDownloaded(): $isDownloaded")
            
            // Check expected file path
            val expectedFile = downloadManager.getDownloadFile(song)
            Log.d("PlaybackManager", "Expected file path: ${expectedFile.absolutePath}")
            Log.d("PlaybackManager", "Expected file exists: ${expectedFile.exists()}")
            
            val downloadedFile = downloadManager.getDownloadedFile(song.id)
            Log.d("PlaybackManager", "DownloadManager.getDownloadedFile() returned: ${downloadedFile?.absolutePath ?: "null"}")
            
            if (downloadedFile != null && downloadedFile.exists()) {
                Log.d("PlaybackManager", "Found downloaded file: ${downloadedFile.absolutePath} (${downloadedFile.length()} bytes)")
                
                val mediaItem = MediaItem.Builder()
                    .setUri("file://${downloadedFile.absolutePath}")
                    .setMediaId(song.id)
                    .build()
                    
                Log.d("PlaybackManager", "Setting up offline playback...")
                exoPlayer?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                    Log.d("PlaybackManager", "Offline playback prepared")
                }
            } else {
                Log.e("PlaybackManager", "Downloaded file not found for song: ${song.title}")
                Log.e("PlaybackManager", "File null: ${downloadedFile == null}, File exists: ${downloadedFile?.exists() ?: false}")
                
                // Additional diagnostic: Check if file exists at expected location
                if (expectedFile.exists()) {
                    Log.w("PlaybackManager", "WARNING: File exists at expected location but DownloadManager didn't find it!")
                    Log.w("PlaybackManager", "This suggests a metadata inconsistency")
                }
                
                android.widget.Toast.makeText(context, "Downloaded file not found for: ${song.title}", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Error setting up offline playback", e)
            android.widget.Toast.makeText(context, "Error playing offline song: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Setup playback restoration for online mode
     */
    fun setupOnlineRestore(songId: String, seekPosition: Int) {
        val streamUrl = "${ApiClient.getServerUrl()}/stream/$songId"
        val authHeader = ApiClient.getAuthHeader()
        
        if (authHeader != null) {
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(songId)
                .build()
            
            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = false
                
                // Seek to saved position once ready
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            seekTo(seekPosition)
                            removeListener(this)
                            callback?.onSongReady()
                        }
                    }
                })
            }
        } else {
            Log.e("PlaybackManager", "No auth header available for restoration")
        }
    }
    
    /**
     * Setup playback restoration for offline mode
     */
    suspend fun setupOfflineRestore(songId: String, seekPosition: Int) {
        try {
            val downloadManager = DownloadManager.getInstance(context)
            val downloadedFile = downloadManager.getDownloadedFile(songId)
            
            if (downloadedFile != null && downloadedFile.exists()) {
                val mediaItem = MediaItem.Builder()
                    .setUri("file://${downloadedFile.absolutePath}")
                    .setMediaId(songId)
                    .build()
                    
                exoPlayer?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = false
                    
                    // Seek to saved position once ready
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                seekTo(seekPosition)
                                removeListener(this)
                                callback?.onSongReady()
                            }
                        }
                    })
                }
            } else {
                Log.e("PlaybackManager", "Downloaded file not found for restore: $songId")
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Error setting up offline restore", e)
        }
    }
    
    /**
     * Release and recreate player for a new song
     */
    fun prepareNewSong(song: Song) {
        Log.d("PlaybackManager", "=== PREPARE NEW SONG ===")
        Log.d("PlaybackManager", "Song: ${song.title} (${song.id})")
        
        release()
        createPlayer()
        
        // CRITICAL FIX: Check if we're in offline mode and use local files
        val isOffline = OfflineModeManager.isOfflineMode()
        
        if (isOffline) {
            Log.d("PlaybackManager", "Offline mode: Using local downloaded file")
            // MINIPLAYER FIX: Immediately notify song change for offline mode
            // This ensures the miniplayer updates immediately, just like online mode
            callback?.onSongReady()
            coroutineScope.launch {
                setupOfflinePlayback(song)
            }
        } else {
            Log.d("PlaybackManager", "Online mode: Using streaming URL")
            setupOnlinePlayback(song)
        }
    }
    
    // Playback control methods
    fun play() {
        Log.d("PlaybackManager", "play() called...")
        exoPlayer?.let {
            if (!it.isPlaying) {
                Log.d("PlaybackManager", "Starting ExoPlayer playback")
                it.play()
            } else {
                Log.d("PlaybackManager", "ExoPlayer already playing")
            }
        }
    }
    
    fun pause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
    }
    
    fun stop() {
        exoPlayer?.stop()
    }
    
    fun seekTo(position: Int) {
        exoPlayer?.seekTo(position.toLong())
    }
    
    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
        callback?.onVolumeChanged(volume)
    }
    
    // State query methods
    fun getCurrentPosition(): Int = exoPlayer?.currentPosition?.toInt() ?: 0
    
    fun getDuration(): Int = exoPlayer?.duration?.toInt() ?: 0
    
    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false
    
    fun release() {
        exoPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        exoPlayer = null
    }
} 