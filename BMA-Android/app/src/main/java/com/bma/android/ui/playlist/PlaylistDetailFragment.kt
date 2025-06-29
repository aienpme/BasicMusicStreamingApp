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
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.bma.android.utils.ArtworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            // Empty playlist - use placeholder
            binding.playlistArtwork.setImageResource(R.drawable.ic_queue_music)
            return
        }
        
        if (songs.size == 1) {
            // Single song - use its artwork
            loadSingleArtwork(songs[0])
            return
        }
        
        // Multiple songs - create 2x2 composite
        createCompositeArtwork(songs.take(4))
    }
    
    private fun loadSingleArtwork(song: Song) {
        // Use ArtworkUtils for offline-aware artwork loading
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val artworkPath = ArtworkUtils.getArtworkPath(requireContext(), song)
                
                if (artworkPath.isNotEmpty()) {
                    if (artworkPath.startsWith("file://")) {
                        // Local file - load directly
                        Glide.with(requireContext())
                            .load(artworkPath)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.ic_queue_music)
                            .error(R.drawable.ic_queue_music)
                            .into(binding.playlistArtwork)
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
                } else {
                    // Empty path - use placeholder
                    binding.playlistArtwork.setImageResource(R.drawable.ic_queue_music)
                }
            } catch (e: Exception) {
                // Error loading artwork - use placeholder
                binding.playlistArtwork.setImageResource(R.drawable.ic_queue_music)
            }
        }
    }
    
    private fun createCompositeArtwork(songs: List<Song>) {
        // Get unique songs for the composite (avoid duplicates)
        val uniqueSongs = songs.distinctBy { it.id }.take(4)
        
        if (uniqueSongs.size == 1) {
            loadSingleArtwork(uniqueSongs[0])
            return
        }
        
        // Load artworks and create composite
        val loadedBitmaps = arrayOfNulls<Bitmap>(4)
        var loadCount = 0
        val targetSize = 128 // Size for each quadrant
        
        fun checkAndCreateComposite() {
            if (loadCount >= uniqueSongs.size) {
                createCompositeBitmap(loadedBitmaps.toList())
            }
        }
        
        // Load artworks for each position using offline-aware artwork loading
        uniqueSongs.forEachIndexed { index, song ->
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val artworkPath = ArtworkUtils.getArtworkPath(requireContext(), song)
                    
                    if (artworkPath.isNotEmpty()) {
                        if (artworkPath.startsWith("file://")) {
                            // Local file - load directly
                            Glide.with(requireContext())
                                .asBitmap()
                                .load(artworkPath)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .override(targetSize, targetSize)
                                .into(object : CustomTarget<Bitmap>() {
                                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                        loadedBitmaps[index] = resource
                                        loadCount++
                                        checkAndCreateComposite()
                                    }
                                    
                                    override fun onLoadCleared(placeholder: Drawable?) {
                                        loadCount++
                                        checkAndCreateComposite()
                                    }
                                })
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
                                
                                Glide.with(requireContext())
                                    .asBitmap()
                                    .load(glideUrl)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .override(targetSize, targetSize)
                                    .into(object : CustomTarget<Bitmap>() {
                                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                            loadedBitmaps[index] = resource
                                            loadCount++
                                            checkAndCreateComposite()
                                        }
                                        
                                        override fun onLoadCleared(placeholder: Drawable?) {
                                            loadCount++
                                            checkAndCreateComposite()
                                        }
                                    })
                            } else {
                                // No auth header - skip this artwork
                                loadCount++
                                checkAndCreateComposite()
                            }
                        }
                    } else {
                        // Empty path - skip this artwork
                        loadCount++
                        checkAndCreateComposite()
                    }
                } catch (e: Exception) {
                    // Error loading artwork - skip this artwork
                    loadCount++
                    checkAndCreateComposite()
                }
            }
        }
    }
    
    private fun createCompositeBitmap(bitmaps: List<Bitmap?>) {
        val compositeSize = 256 // Final composite size
        val quadrantSize = compositeSize / 2
        
        val compositeBitmap = Bitmap.createBitmap(compositeSize, compositeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(compositeBitmap)
        
        // Fill background with dark color
        canvas.drawColor(0xFF333333.toInt())
        
        // Draw up to 4 quadrants
        val positions = listOf(
            Pair(0, 0),           // Top-left
            Pair(quadrantSize, 0), // Top-right
            Pair(0, quadrantSize), // Bottom-left
            Pair(quadrantSize, quadrantSize) // Bottom-right
        )
        
        for (i in 0 until minOf(4, bitmaps.size)) {
            val bitmap = bitmaps[i]
            if (bitmap != null) {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, quadrantSize, quadrantSize, true)
                val (x, y) = positions[i]
                canvas.drawBitmap(scaledBitmap, x.toFloat(), y.toFloat(), null)
                scaledBitmap.recycle()
            }
        }
        
        // Set the composite bitmap to the ImageView
        binding.playlistArtwork.setImageBitmap(compositeBitmap)
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