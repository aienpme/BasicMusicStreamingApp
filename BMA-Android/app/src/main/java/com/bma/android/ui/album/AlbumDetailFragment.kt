package com.bma.android.ui.album

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.service.components.ListenerManager
import com.bma.android.MainActivity
import com.bma.android.MusicService
import com.bma.android.PlayerActivity
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ItemSongInAlbumBinding
import com.bma.android.databinding.FragmentAlbumDetailBinding
import com.bma.android.models.Album
import com.bma.android.models.Song
import com.bma.android.storage.PlaylistManager
import com.bma.android.ui.playlist.PlaylistSelectionDialog
import com.bma.android.utils.ArtworkUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlbumDetailFragment : Fragment(), ListenerManager.MusicServiceListener {

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!
    
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
            
            // Add this fragment as a listener
            musicService?.addListener(this@AlbumDetailFragment)
            
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
            musicService?.removeListener(this@AlbumDetailFragment)
            musicService = null
        }
    }

    companion object {
        fun newInstance(album: Album): AlbumDetailFragment {
            return AlbumDetailFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("album", album)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get album data from arguments
        album = arguments?.getSerializable("album") as? Album ?: return

        setupToolbar()
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        bindMusicService()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (serviceBound) {
            musicService?.removeListener(this)
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        _binding = null
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
            
            // Use ArtworkUtils for offline-aware artwork loading
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val artworkPath = ArtworkUtils.getArtworkPath(requireContext(), firstSong)
                    
                    if (artworkPath.isNotEmpty()) {
                        if (artworkPath.startsWith("file://")) {
                            // Local file - load directly
                            Glide.with(this@AlbumDetailFragment)
                                .load(artworkPath)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .placeholder(R.drawable.ic_folder)
                                .error(R.drawable.ic_folder)
                                .into(binding.albumArtwork)
                        } else {
                            // Server URL - load with auth header
                            val authHeader = ApiClient.getAuthHeader()
                            if (authHeader != null) {
                                val glideUrl = GlideUrl(
                                    artworkPath, 
                                    LazyHeaders.Builder()
                                        .addHeader("Authorization", authHeader)
                                        .build()
                                )
                                
                                Glide.with(this@AlbumDetailFragment)
                                    .load(glideUrl)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .placeholder(R.drawable.ic_folder)
                                    .error(R.drawable.ic_folder)
                                    .into(binding.albumArtwork)
                            } else {
                                binding.albumArtwork.setImageResource(R.drawable.ic_folder)
                            }
                        }
                    } else {
                        // Empty path - use placeholder
                        binding.albumArtwork.setImageResource(R.drawable.ic_folder)
                    }
                } catch (e: Exception) {
                    binding.albumArtwork.setImageResource(R.drawable.ic_folder)
                }
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
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }
        
        songAdapter.updateSongs(album.songs)
    }
    
    private fun bindMusicService() {
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().startService(intent)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun showSongOptions(song: Song) {
        android.app.AlertDialog.Builder(requireContext())
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
        Toast.makeText(requireContext(), "Added to queue: ${song.title}", Toast.LENGTH_SHORT).show()
    }
    
    private fun addNext(song: Song) {
        musicService?.addNext(song)
        Toast.makeText(requireContext(), "Added next: ${song.title}", Toast.LENGTH_SHORT).show()
    }
    
    private fun showPlaylistSelectionDialog(song: Song) {
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                val allSongs = playlistManager.getAllSongs()
                
                val dialog = PlaylistSelectionDialog.newInstance(song, allSongs)
                dialog.show(parentFragmentManager, "PlaylistSelectionDialog")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "No songs available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            // Trigger back navigation with animation
            (requireActivity() as? MainActivity)?.handleAlbumDetailBackPressed()
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
        val serviceIntent = Intent(requireContext(), MusicService::class.java)
        requireContext().startService(serviceIntent)
        
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
        // MainActivity handles mini-player updates
    }
    
    override fun onSongChanged(song: Song?) {
        // MainActivity handles mini-player updates
    }
    
    override fun onProgressChanged(progress: Int, duration: Int) {
        // MainActivity handles mini-player updates
    }
}