package com.bma.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ItemSongInAlbumBinding
import com.bma.android.databinding.ActivityAlbumDetailBinding
import com.bma.android.models.Album
import com.bma.android.models.Song
import com.bma.android.storage.PlaylistManager
import com.bma.android.ui.playlist.PlaylistSelectionDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import androidx.core.view.isVisible
import com.bma.android.ui.library.LibraryFragment
import com.bma.android.ui.search.SearchFragment
import com.bma.android.ui.settings.SettingsFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bma.android.service.components.ListenerManager
import kotlinx.coroutines.launch

class AlbumDetailActivity : AppCompatActivity(), ListenerManager.MusicServiceListener {

    private lateinit var binding: ActivityAlbumDetailBinding
    private lateinit var album: Album
    private lateinit var songAdapter: AlbumSongAdapter
    
    // Music service connection
    private var musicService: MusicService? = null
    private var serviceBound = false
    
    // Pending playback request for when service connects
    private var pendingPlayback: PlaybackRequest? = null
    
    private data class PlaybackRequest(
        val song: Song,
        val songs: List<Song>,
        val currentPosition: Int,
        val shuffled: Boolean
    )
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            
            // Add this activity as a listener
            musicService?.addListener(this@AlbumDetailActivity)
            
            // Update mini player with current state
            updateMiniPlayer()
            
            // Handle pending playback request
            pendingPlayback?.let { request ->
                musicService!!.loadAndPlay(request.song, request.songs, request.currentPosition)
                
                // Apply shuffle if requested
                if (request.shuffled && !musicService!!.isShuffleEnabled()) {
                    musicService!!.toggleShuffle()
                }
                
                pendingPlayback = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            musicService?.removeListener(this@AlbumDetailActivity)
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get album data from intent
        album = intent.getSerializableExtra("album") as? Album
            ?: run {
                finish()
                return
            }

        setupToolbar()
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        setupMiniPlayer()
        setupBottomNavigation()
        bindMusicService()
    }

    private fun setupUI() {
        // Set album information
        binding.albumTitle.text = album.name
        binding.artistName.text = album.artist ?: "Unknown Artist"
        binding.trackCount.text = "• ${album.trackCount} tracks"

        // Load album artwork
        loadAlbumArtwork()
    }

    private fun loadAlbumArtwork() {
        if (album.songs.isNotEmpty()) {
            val firstSong = album.songs.first()
            val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${firstSong.id}"
            
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
                    .into(binding.albumArtwork)
            } else {
                binding.albumArtwork.setImageResource(R.drawable.ic_folder)
            }
        } else {
            binding.albumArtwork.setImageResource(R.drawable.ic_folder)
        }
    }

    private fun setupRecyclerView() {
        songAdapter = AlbumSongAdapter(
            onSongClick = { song -> playSong(song) },
            onSongLongClick = { song -> showSongOptions(song) }
        )
        
        binding.songsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AlbumDetailActivity)
            adapter = songAdapter
        }
        
        songAdapter.updateSongs(album.songs)
    }
    
    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            musicService?.removeListener(this)
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    private fun showSongOptions(song: Song) {
        android.app.AlertDialog.Builder(this)
            .setTitle(song.title)
            .setItems(arrayOf("Add to Queue", "Add Next", "Add to playlist")) { _, which ->
                when (which) {
                    0 -> addToQueue(song)
                    1 -> addNext(song)
                    2 -> showPlaylistSelectionDialog(song)
                }
            }
            .show()
    }
    
    private fun addToQueue(song: Song) {
        musicService?.addToQueue(song)
        Toast.makeText(this, "Added to queue: ${song.title}", Toast.LENGTH_SHORT).show()
    }
    
    private fun addNext(song: Song) {
        musicService?.addNext(song)
        Toast.makeText(this, "Added next: ${song.title}", Toast.LENGTH_SHORT).show()
    }
    
    private fun showPlaylistSelectionDialog(song: Song) {
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(this@AlbumDetailActivity)
                val allSongs = playlistManager.getAllSongs()
                
                val dialog = PlaylistSelectionDialog.newInstance(song, allSongs)
                dialog.show(supportFragmentManager, "PlaylistSelectionDialog")
            } catch (e: Exception) {
                Toast.makeText(this@AlbumDetailActivity, "No songs available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.playButton.setOnClickListener {
            if (album.songs.isNotEmpty()) {
                playSong(album.songs.first(), startFromBeginning = true)
            }
        }

        binding.shuffleButton.setOnClickListener {
            if (album.songs.isNotEmpty()) {
                // Pick a random song to start shuffle mode
                val randomSong = album.songs.random()
                playSong(randomSong, startFromBeginning = true, shuffled = true)
            }
        }
    }

        private fun playSong(song: Song, startFromBeginning: Boolean = false, shuffled: Boolean = false) {
        // Always pass the original album order - let service handle shuffling
        val songs = album.songs
        val currentPosition = songs.indexOf(song)
        
        // Start music service
        val serviceIntent = Intent(this, MusicService::class.java)
        startService(serviceIntent)
        
        // If service is bound, directly start playback without opening PlayerActivity
        if (serviceBound && musicService != null) {
            musicService!!.loadAndPlay(song, songs, currentPosition)
            
            // Apply shuffle if requested
            if (shuffled && !musicService!!.isShuffleEnabled()) {
                musicService!!.toggleShuffle()
            }
        } else {
            // Fallback: bind service and play when connected
            bindMusicService()
            // Store playback request for when service connects
            pendingPlayback = PlaybackRequest(song, songs, currentPosition, shuffled)
        }
    }

    // Simple adapter for songs in album detail
    private class AlbumSongAdapter(
        private val onSongClick: (Song) -> Unit,
        private val onSongLongClick: (Song) -> Unit
    ) : RecyclerView.Adapter<AlbumSongAdapter.SongViewHolder>() {

        private var songs = listOf<Song>()

        fun updateSongs(newSongs: List<Song>) {
            songs = newSongs
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
            val binding = ItemSongInAlbumBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return SongViewHolder(binding)
        }

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
            holder.bind(songs[position])
        }

        override fun getItemCount() = songs.size

        inner class SongViewHolder(
            private val binding: ItemSongInAlbumBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(song: Song) {
                // Remove track numbers and clean up title for display
                val cleanTitle = song.title.replace(Regex("^\\d+\\.?\\s*"), "")
                binding.titleText.text = cleanTitle.ifEmpty { song.title }
                
                binding.root.setOnClickListener {
                    onSongClick(song)
                }
                
                binding.root.setOnLongClickListener {
                    onSongLongClick(song)
                    true
                }
            }
        }
    }
    
    // MusicServiceListener implementation
    override fun onPlaybackStateChanged(state: Int) {
        updateMiniPlayer()
    }
    
    override fun onSongChanged(song: Song?) {
        updateMiniPlayer()
    }
    
    override fun onProgressChanged(progress: Int, duration: Int) {
        // Update progress in mini player
        if (duration > 0) {
            val progressPercent = (progress * 100) / duration
            binding.miniPlayer.miniPlayerProgress.progress = progressPercent
        }
    }
    
    private fun setupMiniPlayer() {
        // Click mini-player to open PlayerActivity
        binding.miniPlayer.root.setOnClickListener {
            musicService?.getCurrentSong()?.let {
                val intent = Intent(this, PlayerActivity::class.java)
                startActivity(intent)
            }
        }
        
        // Mini-player controls
        binding.miniPlayer.miniPlayerPlayPause.setOnClickListener {
            musicService?.let { service ->
                if (service.isPlaying()) {
                    service.pause()
                } else {
                    service.play()
                }
            }
        }
        
        binding.miniPlayer.miniPlayerNext.setOnClickListener {
            musicService?.skipToNext()
        }
        
        binding.miniPlayer.miniPlayerPrevious.setOnClickListener {
            musicService?.skipToPrevious()
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_library -> {
                    // Navigate back to MainActivity with Library selected
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("selected_tab", "library")
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                    true
                }
                R.id.navigation_search -> {
                    // Navigate back to MainActivity with Search selected
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("selected_tab", "search")
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                    true
                }
                R.id.navigation_settings -> {
                    // Navigate back to MainActivity with Settings selected
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("selected_tab", "settings")
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun updateMiniPlayer() {
        musicService?.let { service ->
            val currentSong = service.getCurrentSong()
            val isPlaying = service.isPlaying()
            
            if (currentSong != null) {
                // Show mini-player
                binding.miniPlayer.root.isVisible = true
                
                // Update song info
                binding.miniPlayer.miniPlayerTitle.text = currentSong.title
                binding.miniPlayer.miniPlayerArtist.text = currentSong.artist.ifEmpty { "Unknown Artist" }
                
                // Update play/pause button
                val playPauseIcon = if (isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
                binding.miniPlayer.miniPlayerPlayPause.setImageResource(playPauseIcon)
                
                // Load album artwork
                loadMiniPlayerArtwork(currentSong)
                
                // Update progress
                val currentPos = service.getCurrentPosition()
                val duration = service.getDuration()
                val progress = if (duration > 0) {
                    (currentPos * 100) / duration
                } else 0
                binding.miniPlayer.miniPlayerProgress.progress = progress
                
            } else {
                // Hide mini-player
                binding.miniPlayer.root.isVisible = false
            }
        } ?: run {
            // Hide mini-player when no service
            binding.miniPlayer.root.isVisible = false
        }
    }
    
    private fun loadMiniPlayerArtwork(song: Song) {
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
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .into(binding.miniPlayer.miniPlayerArtwork)
        } else {
            // Fallback to default icon
            binding.miniPlayer.miniPlayerArtwork.setImageResource(R.drawable.ic_music_note)
        }
    }
} 