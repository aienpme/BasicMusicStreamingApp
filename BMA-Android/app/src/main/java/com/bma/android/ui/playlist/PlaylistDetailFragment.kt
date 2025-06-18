package com.bma.android.ui.playlist

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.bma.android.databinding.FragmentPlaylistDetailBinding
import com.bma.android.models.Playlist
import com.bma.android.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class PlaylistDetailFragment : Fragment(), ListenerManager.MusicServiceListener {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var playlist: Playlist
    private lateinit var songAdapter: PlaylistSongAdapter
    
    // Music service connection
    private var musicService: MusicService? = null
    private var serviceBound = false
    
    // Pending playback request for when service connects
    private var pendingPlayback: PlaybackRequest? = null
    
    // Gesture detector for swipe back functionality
    private lateinit var gestureDetector: GestureDetector
    
    // Back press handler for custom transition
    private lateinit var backPressedCallback: OnBackPressedCallback
    
    private data class PlaybackRequest(
        val song: Song,
        val songs: List<Song>,
        val currentPosition: Int
    )
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            musicService?.addListener(this@PlaylistDetailFragment)
            serviceBound = true
            
            // Handle any pending playback request
            pendingPlayback?.let { request ->
                musicService?.loadAndPlay(request.song, request.songs, request.currentPosition)
                pendingPlayback = null
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService?.removeListener(this@PlaylistDetailFragment)
            musicService = null
            serviceBound = false
        }
    }

    companion object {
        fun newInstance(playlist: Playlist): PlaylistDetailFragment {
            val fragment = PlaylistDetailFragment()
            val args = Bundle()
            args.putString("playlist_id", playlist.id)
            args.putString("playlist_name", playlist.name)
            args.putStringArrayList("playlist_song_ids", ArrayList(playlist.songIds))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get playlist data from arguments
        val playlistId = arguments?.getString("playlist_id") ?: ""
        val playlistName = arguments?.getString("playlist_name") ?: ""
        val songIds = arguments?.getStringArrayList("playlist_song_ids") ?: arrayListOf()
        
        playlist = Playlist(
            id = playlistId,
            name = playlistName,
            songIds = songIds
        )
        
        setupRecyclerView()
        setupUI()
        setupSwipeGesture()
        setupBackPressHandler()
        loadPlaylistSongs()
        loadPlaylistArtwork()
        bindMusicService()
    }
    
    private fun setupUI() {
        binding.playlistTitle.text = playlist.name
        
        // Set up toolbar
        binding.toolbar.setNavigationOnClickListener {
            (requireActivity() as? MainActivity)?.handlePlaylistDetailBackPressed()
        }
        
        // Action buttons
        binding.playButton.setOnClickListener {
            val songs = songAdapter.getSongs()
            if (songs.isNotEmpty()) {
                playPlaylist(songs)
            } else {
                Toast.makeText(requireContext(), "Playlist is empty", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.shuffleButton.setOnClickListener {
            val songs = songAdapter.getSongs()
            if (songs.isNotEmpty()) {
                // Shuffle the songs and play
                val shuffledSongs = songs.shuffled()
                playSong(shuffledSongs[0], shuffledSongs, 0)
            } else {
                Toast.makeText(requireContext(), "Playlist is empty", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val deltaX = e2.x - (e1?.x ?: 0f)
                val deltaY = e2.y - (e1?.y ?: 0f)
                
                // Check if it's a horizontal right swipe (swipe from left to right)
                if (deltaX > 100 && Math.abs(deltaY) < Math.abs(deltaX)) {
                    // Trigger back navigation with the existing transition
                    (requireActivity() as? MainActivity)?.handlePlaylistDetailBackPressed()
                    return true
                }
                return false
            }
        })
        
        // Apply gesture detector to the root view
        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }
    
    private fun setupBackPressHandler() {
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Use custom transition instead of default Android back behavior
                (requireActivity() as? MainActivity)?.handlePlaylistDetailBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }
    
    private fun setupRecyclerView() {
        songAdapter = PlaylistSongAdapter { song, position ->
            // Play song in context of playlist
            val songs = songAdapter.getSongs()
            playSong(song, songs, position)
        }
        
        binding.songsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }
    }
    
    private fun loadPlaylistSongs() {
        // Get all songs from the app and filter for this playlist
        val allSongs = (requireActivity() as? MainActivity)?.getAllSongs() ?: emptyList()
        val playlistSongs = playlist.getSongs(allSongs)
        
        songAdapter.updateSongs(playlistSongs)
        binding.songCount.text = "• ${playlistSongs.size} songs"
    }
    
    private fun loadPlaylistArtwork() {
        val songs = songAdapter.getSongs()
        
        if (songs.isEmpty()) {
            binding.playlistArtwork.setImageResource(R.drawable.ic_queue_music)
            return
        }
        
        // For now, use the first song's artwork
        // TODO: Implement 2x2 composite artwork
        loadSingleArtwork(songs[0])
    }
    
    private fun loadSingleArtwork(song: Song) {
        val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
        val authHeader = ApiClient.getAuthHeader()
        
        if (authHeader != null) {
            val glideUrl = GlideUrl(
                artworkUrl, 
                LazyHeaders.Builder()
                    .addHeader("Authorization", authHeader)
                    .build()
            )
            
            Glide.with(requireContext())
                .load(glideUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_queue_music)
                .error(R.drawable.ic_queue_music)
                .into(binding.playlistArtwork)
        } else {
            binding.playlistArtwork.setImageResource(R.drawable.ic_queue_music)
        }
    }
    
    private fun bindMusicService() {
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun playSong(song: Song, songs: List<Song>, position: Int) {
        // Start music service first
        val serviceIntent = Intent(requireContext(), MusicService::class.java)
        requireContext().startService(serviceIntent)
        
        if (serviceBound && musicService != null) {
            musicService!!.loadAndPlay(song, songs, position)
        } else {
            // Store request for when service connects
            pendingPlayback = PlaybackRequest(song, songs, position)
        }
    }
    
    private fun playPlaylist(songs: List<Song>) {
        if (songs.isEmpty()) return
        
        val serviceIntent = Intent(requireContext(), MusicService::class.java)
        requireContext().startService(serviceIntent)
        
        if (serviceBound && musicService != null) {
            musicService!!.loadAndPlay(songs[0], songs, 0)
        } else {
            pendingPlayback = PlaybackRequest(songs[0], songs, 0)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            musicService?.removeListener(this)
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        _binding = null
    }
    
    // MusicService.MusicServiceListener implementation
    override fun onSongChanged(song: Song?) {
        // Update UI if needed
    }
    
    override fun onPlaybackStateChanged(state: Int) {
        // Update UI if needed
    }
    
    override fun onProgressChanged(progress: Int, duration: Int) {
        // Update UI if needed
    }
}

// Adapter for songs in playlist detail
class PlaylistSongAdapter(
    private val onSongClick: (Song, Int) -> Unit
) : RecyclerView.Adapter<PlaylistSongAdapter.PlaylistSongViewHolder>() {
    
    private var songs = listOf<Song>()
    
    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }
    
    fun getSongs(): List<Song> = songs
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistSongViewHolder {
        val binding = ItemSongInAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlaylistSongViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: PlaylistSongViewHolder, position: Int) {
        holder.bind(songs[position])
    }
    
    override fun getItemCount() = songs.size
    
    inner class PlaylistSongViewHolder(
        private val binding: ItemSongInAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(song: Song) {
            // Simple title display like album view (no track numbers)
            binding.titleText.text = song.title
            
            binding.root.setOnClickListener {
                onSongClick(song, adapterPosition)
            }
        }
    }
}