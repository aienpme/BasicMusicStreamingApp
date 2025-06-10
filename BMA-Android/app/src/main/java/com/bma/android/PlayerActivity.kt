package com.bma.android

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ActivityPlayerBinding
import com.bma.android.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class PlayerActivity : AppCompatActivity(), MusicService.MusicServiceListener {

    private lateinit var binding: ActivityPlayerBinding
    
    // Music service
    private var musicService: MusicService? = null
    private var serviceBound = false
    
    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            musicService?.addListener(this@PlayerActivity)
            
            // Check if we need to load a new song from intent
            handlePlaybackIntent()
            
            // Update UI with current service state
            updateUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            musicService = null
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarAction = object : Runnable {
        override fun run() {
            musicService?.let { service ->
                if (service.isPlaying()) {
                    binding.seekBar.progress = service.getCurrentPosition()
                    binding.positionText.text = formatDuration(service.getCurrentPosition().toLong())
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        bindMusicService()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (serviceBound) {
            musicService?.removeListener(this@PlayerActivity)
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        startService(intent) // Ensure service is started
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun handlePlaybackIntent() {
        // Check if we have new playback data from intent
        val songIds = intent.getStringArrayExtra("playlist_song_ids")
        val songTitles = intent.getStringArrayExtra("playlist_song_titles")
        val songArtists = intent.getStringArrayExtra("playlist_song_artists")
        val currentSongId = intent.getStringExtra("song_id")
        val position = intent.getIntExtra("current_position", 0)
        val shuffleEnabled = intent.getBooleanExtra("shuffle_enabled", false)

        if (songIds != null && songTitles != null && songArtists != null && currentSongId != null) {
            // We have new playlist data, load it into the service
            val playlist = songIds.mapIndexed { index, id ->
                Song(
                    id = id,
                    filename = "",
                    title = songTitles.getOrElse(index) { "" },
                    artist = songArtists.getOrElse(index) { "" },
                    album = intent.getStringExtra("album_name") ?: "",
                    trackNumber = 0,
                    parentDirectory = "",
                    hasArtwork = false,
                    sortOrder = index
                )
            }
            
            val currentSong = playlist.find { it.id == currentSongId }
            if (currentSong != null) {
                musicService?.loadAndPlay(currentSong, playlist, position)
                
                // Enable shuffle if requested from the album page
                if (shuffleEnabled && musicService?.isShuffleEnabled() == false) {
                    musicService?.toggleShuffle()
                }
            }
        }
        // If no new intent data, just sync with current service state
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        binding.playPauseButton.setOnClickListener { togglePlayPause() }
        binding.nextButton.setOnClickListener { playNextSong() }
        binding.previousButton.setOnClickListener { playPreviousSong() }
        binding.shuffleButton.setOnClickListener { toggleShuffle() }
        binding.repeatButton.setOnClickListener { cycleRepeatMode() }
        binding.queueButton.setOnClickListener { showQueue() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateUI() {
        musicService?.let { service ->
            val currentSong = service.getCurrentSong()
            val isPlaying = service.isPlaying()
            val duration = service.getDuration()
            val position = service.getCurrentPosition()
            
            if (currentSong != null) {
                // Update song info
                binding.titleText.text = currentSong.title.replace(Regex("^\\d+\\.?\\s*"), "")
                binding.artistText.text = currentSong.artist.ifEmpty { "Unknown Artist" }
                
                // Update play/pause button
                binding.playPauseButton.setImageResource(
                    if (isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
                )
                
                // Update duration and seek bar
                if (duration > 0) {
                    binding.durationText.text = formatDuration(duration.toLong())
                    binding.seekBar.max = duration
                    binding.seekBar.progress = position
                }
                
                binding.positionText.text = formatDuration(position.toLong())
                
                // Load album artwork
                loadAlbumArtwork(currentSong)
                
                // Update shuffle and repeat button states
                updateShuffleButtonUI(service.isShuffleEnabled())
                updateRepeatButtonUI(service.getRepeatMode())
                
                // Start/stop seekbar updates
                if (isPlaying) {
                    handler.post(updateSeekBarAction)
                } else {
                    handler.removeCallbacks(updateSeekBarAction)
                }
            }
        }
    }
    
    private fun loadAlbumArtwork(song: Song) {
        val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
        val authHeader = ApiClient.getAuthHeader()
        
        if (authHeader != null) {
            val glideUrl = GlideUrl(
                artworkUrl, 
                LazyHeaders.Builder()
                    .addHeader("Authorization", authHeader)
                    .build()
            )
            
            Glide.with(this)
                .load(glideUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_folder)
                .error(R.drawable.ic_folder)
                .into(binding.albumArt)
        } else {
            binding.albumArt.setImageResource(R.drawable.ic_folder)
        }
    }
    
    private fun togglePlayPause() {
        musicService?.let { service ->
            if (service.isPlaying()) {
                service.pause()
            } else {
                service.play()
            }
        }
    }

    private fun playNextSong() {
        musicService?.skipToNext()
    }

    private fun playPreviousSong() {
        musicService?.let { service ->
            if (service.getCurrentPosition() > 3000) {
                service.seekTo(0)
            } else {
                service.skipToPrevious()
            }
        }
    }
    
    private fun toggleShuffle() {
        musicService?.let { service ->
            val isShuffleEnabled = service.toggleShuffle()
            updateShuffleButtonUI(isShuffleEnabled)
        }
    }

    private fun cycleRepeatMode() {
        musicService?.let { service ->
            val repeatMode = service.cycleRepeatMode()
            updateRepeatButtonUI(repeatMode)
        }
    }
    
    private fun updateShuffleButtonUI(isShuffleEnabled: Boolean) {
        binding.shuffleButton.setImageResource(
            if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
        )
    }
    
    private fun updateRepeatButtonUI(repeatMode: Int) {
        binding.repeatButton.setImageResource(
            when (repeatMode) {
                1 -> R.drawable.ic_repeat_all  // repeat all
                2 -> R.drawable.ic_repeat_one  // repeat one
                else -> R.drawable.ic_repeat_off // repeat off
            }
        )
    }
    
    private fun showQueue() {
        musicService?.let { service ->
            // NEW: Launch QueueActivity for Spotify-like queue management
            val intent = Intent(this, QueueActivity::class.java)
            startActivity(intent)
            
            /* OLD IMPLEMENTATION: Simple AlertDialog (preserved as fallback)
            val upcomingQueue = service.getUpcomingQueue()
            val currentSong = service.getCurrentSong()
            
            if (upcomingQueue.isEmpty()) {
                Toast.makeText(this, "Queue is empty", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Create a simple dialog showing the queue
            val queueText = StringBuilder()
            queueText.append("Now Playing:\n${currentSong?.title ?: "Unknown"}\n\n")
            queueText.append("Up Next:\n")
            
            upcomingQueue.take(10).forEachIndexed { index, song ->
                queueText.append("${index + 1}. ${song.title}\n")
            }
            
            if (upcomingQueue.size > 10) {
                queueText.append("... and ${upcomingQueue.size - 10} more songs")
            }
            
            android.app.AlertDialog.Builder(this)
                .setTitle("Current Queue")
                .setMessage(queueText.toString())
                .setPositiveButton("OK", null)
                .show()
            */
        }
    }
    
    private fun formatDuration(duration: Long): String {
        if (duration <= 0) return "0:00"
        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    
    // MusicService.MusicServiceListener implementation
    override fun onPlaybackStateChanged(state: Int) {
        updateUI()
    }
    
    override fun onSongChanged(song: Song?) {
        updateUI()
    }
    
    override fun onProgressChanged(progress: Int, duration: Int) {
        if (duration > 0) {
            binding.seekBar.progress = progress
            binding.positionText.text = formatDuration(progress.toLong())
        }
    }
}