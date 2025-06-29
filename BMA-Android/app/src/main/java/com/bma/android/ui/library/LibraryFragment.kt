package com.bma.android.ui.library

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bma.android.MainActivity
import com.bma.android.MusicService
import com.bma.android.PlayerActivity
import com.bma.android.R
import com.bma.android.adapters.LibraryAdapter
import com.bma.android.api.ApiClient
import com.bma.android.databinding.FragmentLibraryBinding
import com.bma.android.models.Album
import com.bma.android.models.LibraryContent
import com.bma.android.models.Song
import com.bma.android.models.Playlist
import com.bma.android.storage.PlaylistManager
import com.bma.android.storage.OfflineModeManager
import com.bma.android.storage.LibraryVersionManager
import com.bma.android.ui.playlist.CreatePlaylistDialog
import com.bma.android.ui.playlist.PlaylistSelectionDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class LibraryFragment : Fragment(R.layout.fragment_library), MainActivity.OfflineModeAware {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var libraryAdapter: LibraryAdapter
    private var libraryContent: LibraryContent = LibraryContent(emptyList(), emptyList(), emptyList())
    private var allSongs: List<Song> = emptyList()
    
    // Offline mode state
    private var isOfflineMode = false
    
    // Music service connection
    private var musicService: MusicService? = null
    private var serviceBound = false
    
    // Pending playback request for when service connects
    private var pendingPlayback: PlaybackRequest? = null
    
    private data class PlaybackRequest(
        val song: Song,
        val album: Album
    )
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            
            // Handle pending playback request
            pendingPlayback?.let { request ->
                val currentPosition = request.album.songs.indexOf(request.song)
                musicService!!.loadAndPlay(request.song, request.album.songs, currentPosition)
                pendingPlayback = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            musicService = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLibraryBinding.bind(view)

        // Check current offline mode state
        isOfflineMode = OfflineModeManager.isOfflineMode()

        setupRecyclerView()
        loadAlbums()
        bindMusicService()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when fragment becomes visible after navigation
        if (_binding != null && isVisible) {
            // Check for library updates before loading albums (non-blocking)
            lifecycleScope.launch {
                val hasNewContent = LibraryVersionManager.checkForLibraryUpdate(requireContext())
                if (hasNewContent) {
                    // Show user-friendly notification
                    showNewContentNotification()
                }
                // Always refresh data to keep consistent
                loadAlbums()
            }
            
            // Trigger startup connectivity check (same as Settings offline mode exit)
            (activity as? MainActivity)?.performStartupConnectivityCheck()
        }
    }
    
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Fragment became visible, refresh if needed
        if (!hidden && _binding != null) {
            loadAlbums()
        }
    }
    
    override fun onOfflineModeChanged(isOffline: Boolean) {
        val wasOfflineMode = isOfflineMode
        isOfflineMode = isOffline
        
        android.util.Log.d("LibraryFragment", "Offline mode changed: $wasOfflineMode → $isOfflineMode")
        
        // Only reload if view is available
        if (_binding != null) {
            if (wasOfflineMode != isOfflineMode) {
                // Mode actually changed - need to sync data
                lifecycleScope.launch {
                    try {
                        val playlistManager = PlaylistManager.getInstance(requireContext())
                        
                        if (!isOfflineMode && wasOfflineMode) {
                            // Transitioning from offline → online
                            android.util.Log.d("LibraryFragment", "Syncing playlists from offline to online mode")
                            playlistManager.syncPlaylistsOfflineToOnline()
                            
                            // Refresh library version when going back online
                            LibraryVersionManager.refreshLibraryVersion(requireContext())
                            
                            // Check for updates after refreshing version
                            val hasNewContent = LibraryVersionManager.checkForLibraryUpdate(requireContext())
                            if (hasNewContent) {
                                showNewContentNotification()
                            }
                        }
                        
                        // Reload data with new mode
                        loadAlbums()
                        
                    } catch (e: Exception) {
                        android.util.Log.e("LibraryFragment", "Error during mode transition sync", e)
                        // Still reload even if sync fails
                        loadAlbums()
                    }
                }
            } else {
                // No actual mode change, just refresh
                loadAlbums()
            }
        } else {
            android.util.Log.d("LibraryFragment", "Offline mode changed but view not available yet")
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        _binding = null
    }
    
    private fun bindMusicService() {
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupRecyclerView() {
        libraryAdapter = LibraryAdapter(
            onSongClick = { song ->
                // Check if song is in an album or standalone
                val album = libraryContent.albums.find { it.songs.contains(song) }
                
                // Start music service
                val serviceIntent = Intent(requireContext(), MusicService::class.java)
                requireContext().startService(serviceIntent)
                
                if (serviceBound && musicService != null) {
                    if (album != null) {
                        // Song is part of an album - play album queue
                        val currentPosition = album.songs.indexOf(song)
                        musicService!!.loadAndPlay(song, album.songs, currentPosition)
                    } else {
                        // Standalone song - play just this song
                        musicService!!.loadAndPlay(song, listOf(song), 0)
                    }
                } else {
                    // Fallback: bind service and play when connected
                    bindMusicService()
                    pendingPlayback = PlaybackRequest(song, album ?: Album("", null, listOf(song)))
                }
            },
            onAlbumClick = { album ->
                // Use new animation system instead of AlbumDetailActivity
                (requireActivity() as? MainActivity)?.showAlbumDetail(album)
            },
            onSongLongClick = { song ->
                showQueueOptionsDialog(song)
            },
            onPlaylistClick = { playlist ->
                // Navigate to playlist detail (similar to album detail)
                (requireActivity() as? MainActivity)?.showPlaylistDetail(playlist)
            },
            onPlaylistMenu = { playlist ->
                showPlaylistOptionsDialog(playlist)
            },
            onCreatePlaylistClick = {
                showCreatePlaylistDialog()
            }
        )

        binding.albumsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = libraryAdapter
        }
    }

    private fun loadAlbums() {
        lifecycleScope.launch {
            // Check if view is still available before proceeding
            if (_binding == null) return@launch
            
            binding.progressBar.isVisible = true
            binding.errorText.isVisible = false
            
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                
                if (isOfflineMode) {
                    android.util.Log.d("LibraryFragment", "Loading library in offline mode")
                    
                    // Load offline data
                    val songList = playlistManager.getAllSongsOffline()
                    val playlists = playlistManager.getPlaylistsOffline()
                    
                    allSongs = songList // Store for playlist creation
                    
                    if (songList.isEmpty()) {
                        showOfflineEmptyState()
                        return@launch
                    }
                    
                    // Use same organization logic as online mode for consistent album/song separation
                    libraryContent = organizeLibraryContent(songList, playlists)
                    
                    // Check if view is still available before updating UI
                    if (_binding == null) return@launch
                    libraryAdapter.updateContent(libraryContent)
                    binding.albumsRecyclerView.isVisible = !libraryContent.isEmpty
                    
                } else {
                    android.util.Log.d("LibraryFragment", "Loading library in online mode")
                    
                    // Online mode - original logic
                    val authHeader = ApiClient.getAuthHeader()
                    if (authHeader == null || ApiClient.isTokenExpired(requireContext())) {
                        // Check if view is still available before showing error
                        if (_binding != null) {
                            showError("Not authenticated. Please connect in settings.")
                        }
                        return@launch
                    }

                    val songList = playlistManager.getAllSongs() // Use PlaylistManager for consistency
                    allSongs = songList // Store for playlist creation
                    
                    // Load playlists from local storage
                    val playlists = playlistManager.loadPlaylists()
                    
                    libraryContent = organizeLibraryContent(songList, playlists)
                    
                    // Check if view is still available before updating UI
                    if (_binding == null) return@launch
                    libraryAdapter.updateContent(libraryContent)
                    binding.albumsRecyclerView.isVisible = !libraryContent.isEmpty
                }

            } catch (e: Exception) {
                val errorMessage = if (isOfflineMode) {
                    "Failed to load offline library: ${e.message}"
                } else {
                    "Failed to load albums: ${e.message}"
                }
                // Check if view is still available before showing error
                if (_binding != null) {
                    showError(errorMessage)
                }
            } finally {
                // Check if view is still available before hiding progress bar
                if (_binding != null) {
                    binding.progressBar.isVisible = false
                }
            }
        }
    }

    private fun showError(message: String) {
        // Check if view is still available
        if (_binding == null) return
        
        binding.errorText.text = message
        binding.errorText.isVisible = true
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
    
    private fun showOfflineEmptyState() {
        // Check if view is still available
        if (_binding == null) return
        
        val message = "🔸 Offline Mode - No Downloaded Music\n\n" +
                     "You don't have any downloaded music yet.\n" +
                     "To use offline mode:\n" +
                     "1. Go back online\n" +
                     "2. Use Settings → Manage Downloads\n" +
                     "3. Download your favorite music\n\n" +
                     "Then you can enjoy your music offline!"
        
        binding.errorText.text = message
        binding.errorText.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
        binding.errorText.isVisible = true
        binding.albumsRecyclerView.isVisible = false
    }

    private fun organizeLibraryContent(songList: List<Song>, playlists: List<Playlist>): LibraryContent {
        val sortedSongs = songList.sortedBy { it.sortOrder }
        
        // Group songs by album name (excluding empty/unknown)
        val albumGroups = sortedSongs
            .filter { it.album.isNotEmpty() && it.album != "Unknown Album" }
            .groupBy { it.album }
        
        // Separate real albums (2+ songs) from standalone songs
        val albums = mutableListOf<Album>()
        val standaloneSongs = mutableListOf<Song>()
        
        // Process album groups
        albumGroups.forEach { (albumName, albumSongs) ->
            if (albumSongs.size >= 2) {
                // Create album for multiple songs
                albums.add(
                    Album(
                        name = albumName,
                        artist = albumSongs.firstOrNull()?.artist?.takeIf { it.isNotEmpty() },
                        songs = albumSongs
                    )
                )
            } else {
                // Single song with album name becomes standalone
                standaloneSongs.addAll(albumSongs)
            }
        }
        
        // Add songs without album info as standalone
        standaloneSongs.addAll(
            sortedSongs.filter { it.album.isEmpty() || it.album == "Unknown Album" }
        )
        
        return LibraryContent(
            playlists = playlists.sortedBy { it.name },
            albums = albums.sortedBy { it.name },
            standaloneSongs = standaloneSongs.sortedBy { it.title }
        )
    }
    
    private fun showQueueOptionsDialog(song: Song) {
        val options = arrayOf("Add to Queue", "Add Next", "Add to playlist")
        
        AlertDialog.Builder(requireContext())
            .setTitle(song.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> addSongToQueue(song) // Add to Queue
                    1 -> addSongNext(song)    // Add Next
                    2 -> showPlaylistSelectionDialog(song) // Add to playlist
                }
            }
            .show()
    }
    
    private fun addSongToQueue(song: Song) {
        if (serviceBound && musicService != null) {
            musicService!!.addToQueue(song)
            Toast.makeText(requireContext(), "Added '${song.title}' to queue", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Music service not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addSongNext(song: Song) {
        if (serviceBound && musicService != null) {
            musicService!!.addNext(song)
            Toast.makeText(requireContext(), "Added '${song.title}' to play next", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Music service not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showCreatePlaylistDialog() {
        if (allSongs.isEmpty()) {
            Toast.makeText(requireContext(), "No songs available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialog = CreatePlaylistDialog.newInstance(allSongs)
        dialog.setOnPlaylistCreatedListener { playlistId ->
            // Reload library content to show the new playlist
            loadAlbums()
        }
        dialog.show(parentFragmentManager, "CreatePlaylistDialog")
    }
    
    private fun playPlaylist(playlist: Playlist, shuffled: Boolean) {
        val playlistSongs = playlist.getSongs(allSongs)
        
        if (playlistSongs.isEmpty()) {
            Toast.makeText(requireContext(), "Playlist is empty", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Start music service
        val serviceIntent = Intent(requireContext(), MusicService::class.java)
        requireContext().startService(serviceIntent)
        
        if (serviceBound && musicService != null) {
            if (shuffled) {
                musicService!!.loadAndPlay(playlistSongs.first(), playlistSongs, 0)
                musicService!!.toggleShuffle() // Enable shuffle after loading
            } else {
                musicService!!.loadAndPlay(playlistSongs.first(), playlistSongs, 0)
            }
            
            val shuffleText = if (shuffled) " (shuffled)" else ""
            Toast.makeText(
                requireContext(), 
                "Playing '${playlist.name}'$shuffleText", 
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(requireContext(), "Music service not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showPlaylistSelectionDialog(song: Song) {
        if (allSongs.isEmpty()) {
            Toast.makeText(requireContext(), "No songs available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialog = PlaylistSelectionDialog.newInstance(song, allSongs)
        dialog.setOnPlaylistsUpdatedListener {
            // Reload library content to reflect playlist changes
            loadAlbums()
        }
        dialog.show(parentFragmentManager, "PlaylistSelectionDialog")
    }
    
    private fun showPlaylistOptionsDialog(playlist: Playlist) {
        val options = arrayOf("Delete Playlist", "Rename Playlist")
        
        AlertDialog.Builder(requireContext())
            .setTitle(playlist.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDeletePlaylistConfirmation(playlist)
                    1 -> showRenamePlaylistDialog(playlist)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDeletePlaylistConfirmation(playlist: Playlist) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete '${playlist.name}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deletePlaylist(playlist)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deletePlaylist(playlist: Playlist) {
        lifecycleScope.launch {
            val playlistManager = PlaylistManager.getInstance(requireContext())
            playlistManager.deletePlaylist(playlist.id)
            
            // Reload library to update UI
            loadAlbums()
        }
    }
    
    private fun showRenamePlaylistDialog(playlist: Playlist) {
        val input = EditText(requireContext())
        input.setText(playlist.name)
        input.selectAll()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Rename Playlist")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != playlist.name) {
                    renamePlaylist(playlist, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun renamePlaylist(playlist: Playlist, newName: String) {
        lifecycleScope.launch {
            val playlistManager = PlaylistManager.getInstance(requireContext())
            val updatedPlaylist = playlist.copy(
                name = newName,
                updatedAt = System.currentTimeMillis()
            )
            playlistManager.updatePlaylist(updatedPlaylist)
            
            // Reload library to update UI
            loadAlbums()
        }
    }
    
    // Public method to get all songs for MainActivity
    fun getAllSongs(): List<Song> {
        return allSongs
    }
    
    /**
     * Show a subtle, user-friendly notification when new library content is detected
     */
    private fun showNewContentNotification() {
        binding.root.let { view ->
            Snackbar.make(view, "🎵 New music detected! Library refreshed automatically", Snackbar.LENGTH_LONG)
                .setAction("OK") { /* User acknowledged */ }
                .setAnchorView(binding.albumsRecyclerView) // Position above RecyclerView
                .show()
        }
    }

}