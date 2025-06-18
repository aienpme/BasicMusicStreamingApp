package com.bma.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.PlaybackStateCompat
import com.bma.android.api.ApiClient
import com.bma.android.models.Song
import com.bma.android.storage.PlaybackStateManager
import com.bma.android.storage.PlaylistManager
import com.bma.android.storage.CacheManager
import com.bma.android.storage.OfflineModeManager
import com.bma.android.service.components.*
import com.google.android.exoplayer2.PlaybackException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Type alias for backward compatibility
typealias MusicServiceListener = ListenerManager.MusicServiceListener

class MusicService : Service() {
    
    companion object {
        // Playback states
        const val STATE_IDLE = 0
        const val STATE_PLAYING = 1
        const val STATE_PAUSED = 2
        const val STATE_STOPPED = 3
    }
    
    private lateinit var playbackManager: PlaybackManager
    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var notificationManager: MusicNotificationManager
    private var currentSong: Song? = null
    private var musicQueue = MusicQueue()
    private var playbackState = STATE_IDLE
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isForegroundStarted = false
    private lateinit var playbackStateManager: PlaybackStateManager
    private lateinit var playlistManager: PlaylistManager
    private lateinit var cacheManager: CacheManager
    
    // Streaming stats tracking
    private lateinit var playbackStatsTracker: PlaybackStatsTracker
    
    // Audio focus management
    private lateinit var audioFocusManager: AudioFocusManager
    
    // Binder for UI communication
    private val binder = MusicBinder()
    
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
    
    // Listener management
    private lateinit var listenerManager: ListenerManager
    
    // Progress update manager
    private lateinit var progressUpdateManager: ProgressUpdateManager
    
    // Queue navigator
    private lateinit var queueNavigator: QueueNavigator
    
    // State manager
    private lateinit var stateManager: StateManager
    
    // Notification coordinator
    private lateinit var notificationCoordinator: NotificationCoordinator
    
    // Shuffle repeat controller
    private lateinit var shuffleRepeatController: ShuffleRepeatController
    
    // Queue operations
    private lateinit var queueOperations: QueueOperations
    
    // Handler for error retries
    private val handler = Handler(Looper.getMainLooper())
    
    fun addListener(listener: MusicServiceListener) {
        listenerManager.addListener(listener)
        
        if (listenerManager.hasListeners() && isPlaying()) {
            android.util.Log.d("MusicService", "Starting progress updates for listeners")
            progressUpdateManager.startProgressUpdates()
        }
        
        // If we have a current song, immediately notify the new listener
        listenerManager.notifyNewListener(listener, currentSong, playbackState)
    }
    
    fun removeListener(listener: MusicServiceListener) {
        listenerManager.removeListener(listener)
        
        if (!listenerManager.hasListeners()) {
            android.util.Log.d("MusicService", "No more listeners, stopping progress updates")
            progressUpdateManager.stopProgressUpdates()
        }
    }
    
    // Legacy method for backward compatibility
    fun setListener(listener: MusicServiceListener?) {
        listenerManager.setListener(listener)
    }
    

    
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MusicService", "=== SERVICE ONCREATE ===")
        listenerManager = ListenerManager()
        setupProgressUpdateManager()
        audioFocusManager = AudioFocusManager(this)
        setupAudioFocusCallback()
        playbackStateManager = PlaybackStateManager.getInstance(this)
        playlistManager = PlaylistManager.getInstance(this)
        cacheManager = CacheManager.getInstance(this)
        playbackStatsTracker = PlaybackStatsTracker(playlistManager, coroutineScope)
        mediaSessionManager = MediaSessionManager(this)
        notificationManager = MusicNotificationManager(this, coroutineScope)
        playbackManager = PlaybackManager(this, coroutineScope)
        setupPlaybackCallback()
        setupQueueNavigator()
        setupStateManager()
        setupShuffleRepeatController()
        setupQueueOperations()
        notificationManager.createNotificationChannel()
        initializeMediaSession()
        setupNotificationCoordinator()
        android.util.Log.d("MusicService", "Service created successfully")
    }
    
    private fun setupProgressUpdateManager() {
        progressUpdateManager = ProgressUpdateManager(
            object : ProgressUpdateManager.ProgressCallback {
                override fun getCurrentPosition(): Int = this@MusicService.getCurrentPosition()
                override fun getDuration(): Int = this@MusicService.getDuration()
                override fun isPlaying(): Boolean = this@MusicService.isPlaying()
            },
            object : ProgressUpdateManager.StateCallback {
                override fun onProgressUpdate(position: Int, duration: Int) {
                    if (listenerManager.hasListeners()) {
                        listenerManager.notifyProgressChanged(position, duration)
                    }
                }
                override fun onStateSaveRequired() {
                    stateManager.saveCurrentPlaybackState()
                }
            }
        )
    }
    
    private fun setupQueueNavigator() {
        queueNavigator = QueueNavigator(
            musicQueue,
            object : QueueNavigator.PlaybackCallback {
                override fun getCurrentSong(): Song? = currentSong
                override fun playNewSong(song: Song) {
                    currentSong = song
                    this@MusicService.playNewSong(song)
                }
                override fun seekTo(position: Int) = this@MusicService.seekTo(position)
                override fun stop() = this@MusicService.stop()
                override fun getRepeatMode(): Int = shuffleRepeatController.getRepeatMode()
            }
        )
    }
    
    private fun setupStateManager() {
        stateManager = StateManager(
            playbackStateManager,
            coroutineScope,
            object : StateManager.StateCallback {
                override fun getCurrentSong(): Song? = currentSong
                override fun getCurrentPosition(): Int = this@MusicService.getCurrentPosition()
                override fun getQueueContents(): List<Song> = musicQueue.getQueueContents()
                override fun getQueuePosition(): Int = musicQueue.getCurrentPosition()
                override fun isShuffled(): Boolean = musicQueue.getIsShuffled()
                override fun getRepeatMode(): Int = shuffleRepeatController.getRepeatMode()
                override fun setPlaylist(songs: List<Song>, position: Int) {
                    musicQueue.setPlaylist(songs, position)
                }
                override fun setShuffle(enabled: Boolean) {
                    if (enabled) shuffleRepeatController.enableShuffle()
                }
                override fun setRepeatMode(mode: Int) {
                    shuffleRepeatController.setRepeatMode(mode)
                }
                override fun setCurrentSong(song: Song) {
                    currentSong = song
                }
                override fun preparePlayer() {
                    playbackManager.release()
                    playbackManager.createPlayer()
                }
                override fun setupOfflinePlayback(songId: String, position: Int) {
                    coroutineScope.launch {
                        playbackManager.setupOfflineRestore(songId, position)
                    }
                }
                override fun setupOnlinePlayback(songId: String, position: Int) {
                    playbackManager.setupOnlineRestore(songId, position)
                }
            }
        )
    }
    
    private fun setupNotificationCoordinator() {
        notificationCoordinator = NotificationCoordinator(
            notificationManager,
            mediaSessionManager,
            object : NotificationCoordinator.CoordinatorCallback {
                override fun getCurrentSong(): Song? = currentSong
                override fun getCurrentPosition(): Int = this@MusicService.getCurrentPosition()
                override fun getDuration(): Int = this@MusicService.getDuration()
                override fun getPlaybackState(): Int = playbackState
                override fun isPlaying(): Boolean = this@MusicService.isPlaying()
            }
        )
    }
    
    private fun setupShuffleRepeatController() {
        shuffleRepeatController = ShuffleRepeatController(
            musicQueue,
            object : ShuffleRepeatController.ShuffleRepeatCallback {
                override fun onQueueChanged() {
                    notifyQueueChanged()
                }
            }
        )
    }
    
    private fun setupQueueOperations() {
        queueOperations = QueueOperations(
            musicQueue,
            object : QueueOperations.QueueOperationsCallback {
                override fun onQueueChanged() {
                    notifyQueueChanged()
                }
                override fun onSongChanged(song: Song?) {
                    currentSong = song
                    listenerManager.notifySongChanged(song)
                }
                override fun onPlayNewSong(song: Song) {
                    currentSong = song
                    playNewSong(song)
                }
            }
        )
    }
    
    // Setup audio focus callback
    private fun setupAudioFocusCallback() {
        audioFocusManager.setCallback(object : AudioFocusManager.AudioFocusCallback {
            override fun onAudioFocusGained() {
                // Resume playback or restore volume
                playbackManager.setVolume(1.0f)
                android.util.Log.d("MusicService", "Audio focus gained, resuming if paused")
                if (playbackState == STATE_PAUSED) {
                    play()
                }
            }
            
            override fun onAudioFocusLost() {
                // Stop playback and abandon focus
                android.util.Log.d("MusicService", "Audio focus lost permanently")
                pause()
                // Don't abandon focus here - keep it for potential regain
            }
            
            override fun onAudioFocusLostTransient() {
                // Pause playback temporarily
                android.util.Log.d("MusicService", "Audio focus lost temporarily")
                pause()
            }
            
            override fun onAudioFocusLostCanDuck() {
                // Lower volume instead of pausing
                android.util.Log.d("MusicService", "Audio focus lost, ducking volume")
                playbackManager.setVolume(0.3f)
            }
        })
    }
    
    // Setup playback callback
    private fun setupPlaybackCallback() {
        playbackManager.setCallback(object : PlaybackManager.PlaybackCallback {
            override fun onPlaybackStateChanged(isPlaying: Boolean) {
                android.util.Log.d("MusicService", "PlaybackManager isPlaying changed: $isPlaying")
                playbackState = if (isPlaying) STATE_PLAYING else STATE_PAUSED
                
                try {
                    // Always ensure we have a foreground service when there's music activity
                    if (!isForegroundStarted && currentSong != null) {
                        android.util.Log.d("MusicService", "=== STARTING FOREGROUND SERVICE (ONCE) ===")
                        val basicNotification = notificationManager.createBasicNotification()
                        startForeground(MusicNotificationManager.NOTIFICATION_ID, basicNotification)
                        isForegroundStarted = true
                        android.util.Log.d("MusicService", "✅ Foreground service started successfully")
                    }
                    
                                        // Always update notification content for any state change
                    if (isForegroundStarted) {
                        currentSong?.let { song ->
                            android.util.Log.d("MusicService", "Updating notification for: ${song.title} (playing: $isPlaying)")
                            try {
                                notificationCoordinator.updateAll()
                            } catch (e: Exception) {
                                android.util.Log.e("MusicService", "Error updating notification: ${e.message}", e)
                            }
                        }
                    }
                    
                    // Handle progress updates and streaming stats tracking
                    if (isPlaying) {
                        progressUpdateManager.startProgressUpdates()
                        playbackStatsTracker.startTracking()
                    } else {
                        progressUpdateManager.stopProgressUpdates()
                        playbackStatsTracker.stopTracking()
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("MusicService", "❌ CRITICAL ERROR in onPlaybackStateChanged: ${e.message}", e)
                }
                
                android.util.Log.d("MusicService", "Notifying listener of playback state change: $playbackState")
                listenerManager.notifyPlaybackStateChanged(playbackState)
                notificationCoordinator.updatePlaybackState()
            }
            
            override fun onSongReady() {
                android.util.Log.d("MusicService", "PlaybackManager ready, notifying listeners of song change: ${currentSong?.title}")
                listenerManager.notifySongChanged(currentSong)
            }
            
            override fun onSongEnded() {
                android.util.Log.d("MusicService", "PlaybackManager song ended")
                
                // Cache the song that just finished playing
                currentSong?.let { song ->
                    cacheCompletedSong(song)
                }
                
                skipToNext()
            }
            
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("MusicService", "PlaybackManager error: ${error.message}", error)
                
                // Check if this is an authentication error
                val isAuthError = error.message?.contains("401") == true || 
                                error.message?.contains("403") == true ||
                                error.message?.contains("Unauthorized") == true ||
                                error.message?.contains("Forbidden") == true
                
                if (isAuthError) {
                    android.util.Log.e("MusicService", "Authentication error detected, triggering auth failure callback")
                    ApiClient.onAuthFailure?.invoke()
                    playbackState = STATE_STOPPED
                    listenerManager.notifyPlaybackStateChanged(playbackState)
                    return
                }
                
                // Handle different types of errors
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        android.util.Log.e("MusicService", "Network error, retrying in 2 seconds...")
                        // Retry after a short delay
                        handler.postDelayed({
                            currentSong?.let { song ->
                                android.util.Log.d("MusicService", "Retrying playback for ${song.title}")
                                playNewSong(song)
                            }
                        }, 2000)
                    }
                    else -> {
                        // For other errors, skip to next song
                        android.util.Log.e("MusicService", "Unrecoverable error, skipping to next song")
                        handler.postDelayed({
                            skipToNext()
                        }, 1000)
                    }
                }
                
                // Notify UI of error state
                playbackState = STATE_STOPPED
                listenerManager.notifyPlaybackStateChanged(playbackState)
            }
            
            override fun onVolumeChanged(volume: Float) {
                android.util.Log.d("MusicService", "Volume changed: $volume")
            }
        })
    }
    

    

    
    override fun onBind(intent: Intent): IBinder {
        android.util.Log.d("MusicService", "=== SERVICE ONBIND CALLED ===")
        android.util.Log.d("MusicService", "Intent: ${intent.action}")
        android.util.Log.d("MusicService", "Current song: ${currentSong?.title}")
        android.util.Log.d("MusicService", "Current state: $playbackState")
        android.util.Log.d("MusicService", "Returning binder...")
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("MusicService", "=== SERVICE ONSTARTCOMMAND ===")
        android.util.Log.d("MusicService", "Intent: ${intent?.action}")
        android.util.Log.d("MusicService", "Flags: $flags, StartId: $startId")
        
        // Handle notification actions
        when (intent?.action) {
            "PLAY_PAUSE" -> {
                android.util.Log.d("MusicService", "Handling PLAY_PAUSE action")
                if (isPlaying()) pause() else play()
            }
            "NEXT" -> {
                android.util.Log.d("MusicService", "Handling NEXT action")
                skipToNext()
            }
            "PREVIOUS" -> {
                android.util.Log.d("MusicService", "Handling PREVIOUS action")
                skipToPrevious()
            }
            else -> {
                android.util.Log.d("MusicService", "No specific action, service started normally")
            }
        }
        
        // Return START_STICKY to ensure service is restarted if killed
        android.util.Log.d("MusicService", "Returning START_STICKY")
        return START_STICKY
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        android.util.Log.w("MusicService", "=== TASK REMOVED (APP SWIPED AWAY) ===")
        android.util.Log.w("MusicService", "Current song: ${currentSong?.title}")
        android.util.Log.w("MusicService", "Is playing: ${isPlaying()}")
        
        // Save current playback state before stopping
        stateManager.saveCurrentPlaybackState()
        
        // Stop music playback immediately
        playbackManager.stop()
        playbackState = STATE_STOPPED
        
        // Remove notification and stop foreground service
        stopForeground(true)
        isForegroundStarted = false
        
        // Stop the service completely
        stopSelf()
        
        android.util.Log.w("MusicService", "Music stopped and service stopping due to app being swiped away")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        android.util.Log.w("MusicService", "=== SERVICE ONDESTROY ===")
        android.util.Log.w("MusicService", "Current song: ${currentSong?.title}")
        android.util.Log.w("MusicService", "Playback state: $playbackState")
        android.util.Log.w("MusicService", "Is playing: ${isPlaying()}")
        
        // Save current playback state before destroying
        stateManager.saveCurrentPlaybackState()
        
        super.onDestroy()
        progressUpdateManager.stopProgressUpdates()
        playbackStatsTracker.stopTracking()  // Save any remaining tracking time
        audioFocusManager.abandonAudioFocus()
        playbackManager.release()
        mediaSessionManager.release()
        
        android.util.Log.w("MusicService", "Service destroyed - all resources released")
    }
    
    fun restorePlaybackState(): Boolean {
        return stateManager.restorePlaybackState()
    }
    

    
    private fun initializeMediaSession() {
        mediaSessionManager.initializeMediaSession(object : MediaSessionManager.MediaControlCallback {
            override fun onPlay() { play() }
            override fun onPause() { pause() }
            override fun onSkipToNext() { skipToNext() }
            override fun onSkipToPrevious() { skipToPrevious() }
            override fun onStop() { stop() }
        })
    }
    
    fun loadAndPlay(song: Song, songList: List<Song>, position: Int) {
        android.util.Log.d("MusicService", "=== LOAD AND PLAY ===")
        android.util.Log.d("MusicService", "Song: ${song.title} (${song.id})")
        android.util.Log.d("MusicService", "Playlist size: ${songList.size}, Position: $position")
        android.util.Log.d("MusicService", "Current listeners: ${listenerManager.getListenerCount()}")
        
        // Initialize queue with new playlist
        musicQueue.setPlaylist(songList, position)
        currentSong = song
        
        // Temporarily disable audio focus to prevent conflicts
        // audioFocusManager.requestAudioFocus()
        android.util.Log.d("MusicService", "Proceeding with playback...")
        
        playbackManager.prepareNewSong(song)
    }
    



    
        fun play() {
        android.util.Log.d("MusicService", "play() called...")
        // Temporarily disable audio focus to prevent conflicts
        // audioFocusManager.requestAudioFocus()
        
        playbackManager.play()
    }

    fun pause() {
        playbackManager.pause()
    }

    fun stop() {
        playbackManager.stop()
        playbackState = STATE_STOPPED
        stopForeground(true)
        isForegroundStarted = false
        listenerManager.notifyPlaybackStateChanged(playbackState)
        notificationCoordinator.updatePlaybackState()
    }
    
    fun skipToNext() {
        val nextSong = queueNavigator.skipToNext()
        if (nextSong != null) {
            currentSong = nextSong
        }
    }
    
    fun skipToPrevious() {
        val previousSong = queueNavigator.skipToPrevious()
        if (previousSong != null) {
            currentSong = previousSong
        }
    }
    
    private fun playNewSong(song: Song) {
        android.util.Log.d("MusicService", "=== PLAY NEW SONG ===")
        android.util.Log.d("MusicService", "Song: ${song.title} (${song.id})")
        
        playbackManager.prepareNewSong(song)
    }
    
    fun seekTo(position: Int) {
        playbackManager.seekTo(position)
    }
    
    fun getCurrentPosition(): Int = playbackManager.getCurrentPosition()
    fun getDuration(): Int = playbackManager.getDuration()
    fun isPlaying(): Boolean = playbackManager.isPlaying()
    fun getCurrentSong(): Song? = currentSong
    fun getPlaybackState(): Int = playbackState
    
    // Shuffle and repeat controls
    fun toggleShuffle(): Boolean = shuffleRepeatController.toggleShuffle()
    
    fun isShuffleEnabled(): Boolean = shuffleRepeatController.isShuffleEnabled()
    
    fun cycleRepeatMode(): Int = shuffleRepeatController.cycleRepeatMode()
    
    fun getRepeatMode(): Int = shuffleRepeatController.getRepeatMode()
    
    // Queue management methods
    fun addToQueue(song: Song) = queueOperations.addToQueue(song)
    
    fun addToQueue(songs: List<Song>) = queueOperations.addToQueue(songs)
    
    fun addNext(song: Song) = queueOperations.addNext(song)
    
    fun getCurrentQueue(): List<Song> = queueOperations.getCurrentQueue()
    
    fun getUpcomingQueue(): List<Song> = queueOperations.getUpcomingQueue()
    
    /**
     * Remove a song from the queue at the specified position
     * @param position The position in the queue to remove (0-based)
     * @return true if removal was successful
     */
    fun removeFromQueue(position: Int): Boolean = queueOperations.removeFromQueue(position)
    
    /**
     * Move a song in the queue from one position to another
     * @param fromPosition Source position (0-based)
     * @param toPosition Target position (0-based)
     * @return true if move was successful
     */
    fun moveQueueItem(fromPosition: Int, toPosition: Int): Boolean = queueOperations.moveQueueItem(fromPosition, toPosition)
    
    /**
     * Jump to a specific position in the queue and start playing
     * @param position The queue position to jump to (0-based)
     * @return true if jump was successful
     */
    fun jumpToQueuePosition(position: Int): Boolean = queueOperations.jumpToQueuePosition(position)
    
    private fun notifyQueueChanged() {
        val queue = getCurrentQueue()
        listenerManager.notifyQueueChanged(queue)
    }
    
    /**
     * Cache a song that just completed playing
     */
    private fun cacheCompletedSong(song: Song) {
        coroutineScope.launch {
            try {
                val streamUrl = "${ApiClient.getServerUrl()}stream/${song.id}"
                
                android.util.Log.d("MusicService", "=== AUTO-CACHING SONG ===")
                android.util.Log.d("MusicService", "Song: ${song.title}")
                android.util.Log.d("MusicService", "Stream URL: $streamUrl")
                
                // Check if already cached
                if (cacheManager.isCached(song.id)) {
                    android.util.Log.d("MusicService", "Song already cached, skipping")
                    return@launch
                }
                
                // Cache the song in background
                withContext(Dispatchers.IO) {
                    cacheManager.cacheAfterPlayback(song, streamUrl)
                }
                
                android.util.Log.d("MusicService", "Auto-caching completed for: ${song.title}")
                
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "Error auto-caching song ${song.title}: ${e.message}", e)
            }
        }
    }
}   