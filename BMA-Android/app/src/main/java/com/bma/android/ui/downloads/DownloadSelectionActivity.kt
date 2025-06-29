package com.bma.android.ui.downloads

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.lifecycle.lifecycleScope
import com.bma.android.R
import com.bma.android.databinding.ActivityDownloadSelectionBinding
import com.bma.android.storage.PlaylistManager
import com.bma.android.storage.DownloadManager
import com.bma.android.api.ApiClient
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.view.View
import android.widget.LinearLayout

/**
 * Activity for selecting songs, albums, and playlists to download
 * Features tabbed interface with download management capabilities
 */
class DownloadSelectionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDownloadSelectionBinding
    private lateinit var playlistManager: PlaylistManager
    private lateinit var downloadManager: DownloadManager
    private var isAnimating = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make the activity full screen to match app theme
        window.statusBarColor = getColor(android.R.color.black)
        
        binding = ActivityDownloadSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize managers
        playlistManager = PlaylistManager.getInstance(this)
        downloadManager = DownloadManager.getInstance(this)
        
        setupToolbar()
        setupViewPager()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Download Music"
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupViewPager() {
        val adapter = DownloadPagerAdapter(this)
        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Songs"
                1 -> "Albums" 
                2 -> "Playlists"
                else -> "Tab $position"
            }
        }.attach()
        
        setupActionButtons()
    }
    
    private fun setupActionButtons() {
        binding.downloadAllButton.setOnClickListener {
            downloadEntireLibrary()
        }
        
        binding.downloadSelectedButton.setOnClickListener {
            downloadSelectedItems()
        }
    }
    
    private fun downloadEntireLibrary() {
        lifecycleScope.launch {
            try {
                binding.downloadAllButton.isEnabled = false
                binding.downloadAllButton.text = "Starting Downloads..."
                
                // Hide Download Selected button and make Download All take full width during download
                setDownloadInProgress(true, isDownloadAll = true)
                
                val allSongs = playlistManager.getAllSongs()
                android.util.Log.d("DownloadSelectionActivity", "Starting download of entire library: ${allSongs.size} songs")
                
                if (allSongs.isEmpty()) {
                    return@launch
                }
                
                // Check authentication
                val authHeader = ApiClient.getAuthHeader()
                if (authHeader == null || ApiClient.isTokenExpired(this@DownloadSelectionActivity)) {
                    return@launch
                }
                
                // Start downloading songs
                var successCount = 0
                var failCount = 0
                
                allSongs.forEachIndexed { index, song ->
                    binding.downloadAllButton.text = "Downloading ${index + 1}/${allSongs.size}"
                    
                    if (downloadManager.isDownloaded(song.id)) {
                        successCount++
                        android.util.Log.d("DownloadSelectionActivity", "Song already downloaded: ${song.title}")
                    } else {
                        val success = downloadSong(song, authHeader)
                        if (success) {
                            successCount++
                            val downloadFile = downloadManager.getDownloadFile(song)
                            downloadManager.markAsDownloaded(song, downloadFile)
                        } else {
                            failCount++
                        }
                    }
                }


                
            } catch (e: Exception) {
                android.util.Log.e("DownloadSelectionActivity", "Error starting library download: ${e.message}", e)
            } finally {
                binding.downloadAllButton.isEnabled = true
                binding.downloadAllButton.text = "Download All Library"
                
                // Restore button layout after download
                setDownloadInProgress(false)
                
                // Refresh all adapters to show new download statuses
                refreshAllAdapters()
            }
        }
    }
    
    private fun downloadSelectedItems() {
        // Get selected items from all fragments
        lifecycleScope.launch {
            try {
                val selectedSongs = getSelectedSongsFromFragments()
                
                if (selectedSongs.isEmpty()) {
                    return@launch
                }
                
                binding.downloadSelectedButton.isEnabled = false
                binding.downloadSelectedButton.text = "Starting Downloads..."
                
                // Hide Download All button and make Download Selected take full width during download
                setDownloadInProgress(true, isDownloadAll = false)
                
                android.util.Log.d("DownloadSelectionActivity", "Starting download of selected items: ${selectedSongs.size} songs")
                
                // Check authentication
                val authHeader = ApiClient.getAuthHeader()
                if (authHeader == null || ApiClient.isTokenExpired(this@DownloadSelectionActivity)) {
                    return@launch
                }
                
                // Start downloading selected songs
                var successCount = 0
                var failCount = 0
                
                selectedSongs.forEachIndexed { index, song ->
                    binding.downloadSelectedButton.text = "Downloading ${index + 1}/${selectedSongs.size}"
                    
                    if (downloadManager.isDownloaded(song.id)) {
                        successCount++
                        android.util.Log.d("DownloadSelectionActivity", "Song already downloaded: ${song.title}")
                    } else {
                        val success = downloadSong(song, authHeader)
                        if (success) {
                            successCount++
                            val downloadFile = downloadManager.getDownloadFile(song)
                            downloadManager.markAsDownloaded(song, downloadFile)
                        } else {
                            failCount++
                        }
                    }
                }

                
            } catch (e: Exception) {
                android.util.Log.e("DownloadSelectionActivity", "Error starting selected downloads: ${e.message}", e)
            } finally {
                binding.downloadSelectedButton.isEnabled = true
                
                // Restore button layout after download
                setDownloadInProgress(false)
                
                // Reset all selections after successful download
                resetSelectionState()
            }
        }
    }
    
    private suspend fun getSelectedSongsFromFragments(): List<com.bma.android.models.Song> {
        val allSelectedSongs = mutableListOf<com.bma.android.models.Song>()
        
        try {
            val adapter = binding.viewPager.adapter as? DownloadPagerAdapter
            if (adapter != null) {
                // Get songs from Songs fragment
                val songsFragment = supportFragmentManager.findFragmentByTag("f0") as? DownloadSongsFragment
                songsFragment?.let { fragment ->
                    val selectedSongIds = fragment.getSelectedSongIds()
                    if (selectedSongIds.isNotEmpty()) {
                        val allSongs = playlistManager.getAllSongs()
                        val selectedSongs = allSongs.filter { it.id in selectedSongIds }
                        allSelectedSongs.addAll(selectedSongs)
                    }
                }
                
                // Get songs from Albums fragment
                val albumsFragment = supportFragmentManager.findFragmentByTag("f1") as? DownloadAlbumsFragment
                albumsFragment?.let { fragment ->
                    val selectedAlbumIds = fragment.getSelectedAlbumIds()
                    if (selectedAlbumIds.isNotEmpty()) {
                        val allAlbums = playlistManager.getAllAlbums()
                        val selectedAlbums = allAlbums.filter { it.id in selectedAlbumIds }
                        selectedAlbums.forEach { album ->
                            allSelectedSongs.addAll(album.songs)
                        }
                    }
                }
                
                // Get songs from Playlists fragment
                val playlistsFragment = supportFragmentManager.findFragmentByTag("f2") as? DownloadPlaylistsFragment
                playlistsFragment?.let { fragment ->
                    val selectedPlaylistIds = fragment.getSelectedPlaylistIds()
                    if (selectedPlaylistIds.isNotEmpty()) {
                        selectedPlaylistIds.forEach { playlistId ->
                            val playlistSongs = playlistManager.getSongsForPlaylist(playlistId)
                            allSelectedSongs.addAll(playlistSongs)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadSelectionActivity", "Error getting selected songs from fragments", e)
        }
        
        // Remove duplicates by song ID
        val uniqueSongs = allSelectedSongs.distinctBy { it.id }
        android.util.Log.d("DownloadSelectionActivity", "Found ${uniqueSongs.size} unique selected songs")
        return uniqueSongs
    }
    
    /**
     * Download a single song
     */
    private suspend fun downloadSong(song: com.bma.android.models.Song, authHeader: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val streamUrl = "${ApiClient.getServerUrl()}stream/${song.id}"
            val outputFile = downloadManager.getDownloadFile(song)
            
            // Create parent directories
            outputFile.parentFile?.mkdirs()
            
            android.util.Log.d("DownloadSelectionActivity", "Downloading: ${song.title} to ${outputFile.absolutePath}")
            
            // Download the audio file
            val audioSuccess = downloadFile(streamUrl, outputFile, authHeader)
            if (!audioSuccess) {
                return@withContext false
            }

            // Download artwork if available
            if (song.hasArtwork) {
                val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
                val artworkFile = downloadManager.getArtworkFile(song)
                downloadFile(artworkUrl, artworkFile, authHeader) // Don't fail if artwork fails
            }

            android.util.Log.d("DownloadSelectionActivity", "Successfully downloaded: ${song.title}")
            true
        } catch (e: Exception) {
            android.util.Log.e("DownloadSelectionActivity", "Error downloading song: ${song.title}", e)
            false
        }
    }
    
    /**
     * Download a file from URL to local file
     */
    private suspend fun downloadFile(url: String, outputFile: File, authHeader: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", authHeader)
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                android.util.Log.e("DownloadSelectionActivity", "HTTP error: $responseCode for URL: $url")
                return@withContext false
            }

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            android.util.Log.d("DownloadSelectionActivity", "Downloaded file: ${outputFile.name} (${outputFile.length()} bytes)")
            true
        } catch (e: Exception) {
            android.util.Log.e("DownloadSelectionActivity", "Error downloading file: $url", e)
            false
        }
    }
    
    /**
     * Called by fragments to update the selected count
     */
    fun updateSelectedCount(count: Int, allAvailableSelected: Boolean = false) {
        // Check if entire library across all tabs is downloaded (asynchronously)
        lifecycleScope.launch {
            val entireLibraryDownloaded = checkIfEntireLibraryDownloaded()
            
            // Determine if button will be hidden to avoid text flicker
            val willHideSelectedButton = (entireLibraryDownloaded) || (allAvailableSelected && count > 0)
            
            if (!willHideSelectedButton && !isAnimating) {
                // Safe to update text - button will remain visible
                binding.downloadSelectedButton.text = if (count > 0) {
                    "Download Selected ($count)"
                } else {
                    "Select to Download"
                }
            }
            
            binding.downloadSelectedButton.isEnabled = count > 0
            updateButtonVisibility(count, allAvailableSelected, entireLibraryDownloaded)
        }
    }
    
    /**
     * Update button visibility based on selection and download state with smooth animations
     */
    private fun updateButtonVisibility(count: Int, allAvailableSelected: Boolean, entireLibraryDownloaded: Boolean) {
        // Handle button visibility based on library download state
        if (entireLibraryDownloaded) {
            // Entire library is downloaded - hide entire button container
            animateContainerVisibility(binding.buttonContainer, false)
        } else {
            // Library not fully downloaded - show container and manage individual buttons
            animateContainerVisibility(binding.buttonContainer, true)
            
            if (allAvailableSelected && count > 0) {
                // All available items are selected - hide Download Selected button
                animateButtonVisibility(binding.downloadSelectedButton, false, count, allAvailableSelected)
                animateButtonVisibility(binding.downloadAllButton, true, count, allAvailableSelected)
            } else {
                // Not all selected - show both buttons
                animateButtonVisibility(binding.downloadAllButton, true, count, allAvailableSelected)
                animateButtonVisibility(binding.downloadSelectedButton, true, count, allAvailableSelected)
            }
        }
    }
    
    /**
     * Smoothly animate button container visibility
     */
    private fun animateContainerVisibility(container: LinearLayout, show: Boolean) {
        if (show && container.visibility == View.VISIBLE && container.alpha == 1f) {
            // Already visible, no animation needed
            return
        }
        if (!show && container.visibility == View.GONE) {
            // Already hidden, no animation needed
            return
        }
        
        if (show) {
            // Show container with fade in
            container.visibility = View.VISIBLE
            container.alpha = 0f
            container.animate()
                .alpha(1f)
                .setDuration(200)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } else {
            // Hide container with immediate fade out (no flash)
            container.animate()
                .alpha(0f)
                .setDuration(100)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    container.visibility = View.GONE
                }
                .start()
        }
    }
    
    /**
     * Smoothly animate button visibility changes with ultra-fast transitions
     */
    private fun animateButtonVisibility(button: com.google.android.material.button.MaterialButton, show: Boolean, count: Int, allAvailableSelected: Boolean) {
        if (show && button.visibility == View.VISIBLE && button.alpha == 1f) {
            // Already visible, just update text if it's the selected button
            if (button == binding.downloadSelectedButton) {
                button.text = if (count > 0) "Download Selected ($count)" else "Select to Download"
            }
            return
        }
        if (!show && button.visibility == View.GONE) {
            // Already hidden, no animation needed
            return
        }
        
        if (show) {
            // Show button with ultra-fast fade in
            isAnimating = true
            button.visibility = View.VISIBLE
            button.alpha = 0f
            
            // Update text immediately when showing
            if (button == binding.downloadSelectedButton) {
                button.text = if (count > 0) "Download Selected ($count)" else "Select to Download"
            }
            
            button.animate()
                .alpha(1f)
                .setDuration(30)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    isAnimating = false
                }
                .start()
        } else {
            // Hide button with ultra-fast fade out
            isAnimating = true
            
            // Reset text to default before hiding to prevent flicker on next show
            if (button == binding.downloadSelectedButton) {
                button.text = "Select to Download"
            }
            
            button.animate()
                .alpha(0f)
                .setDuration(30)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    button.visibility = View.GONE
                    isAnimating = false
                }
                .start()
        }
    }
    
    /**
     * Convert dp to pixels
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    /**
     * Manage button layout during download operations with smooth animations
     */
    private fun setDownloadInProgress(inProgress: Boolean, isDownloadAll: Boolean = true) {
        if (inProgress) {
            // During download - hide the inactive button
            if (isDownloadAll) {
                // Download All Library is active - hide Download Selected
                animateButtonVisibility(binding.downloadSelectedButton, false, 0, false)
                animateButtonVisibility(binding.downloadAllButton, true, 0, false)
            } else {
                // Download Selected is active - hide Download All Library  
                animateButtonVisibility(binding.downloadAllButton, false, 0, false)
                animateButtonVisibility(binding.downloadSelectedButton, true, 0, false)
            }
        } else {
            // Download finished - restore both buttons to normal state
            animateButtonVisibility(binding.downloadAllButton, true, 0, false)
            animateButtonVisibility(binding.downloadSelectedButton, true, 0, false)
            
            // After restoring, let the normal selection logic take over
            // This will be handled by the next updateSelectedCount call from fragments
        }
    }
    
    /**
     * Check if the entire library is downloaded
     * Since albums and playlists are collections of songs, we just need to check if all songs are downloaded
     */
    private suspend fun checkIfEntireLibraryDownloaded(): Boolean {
        return try {
            val allSongs = playlistManager.getAllSongs()
            val downloadedSongs = allSongs.filter { downloadManager.isDownloaded(it.id) }
            // Library is fully downloaded if there are songs and ALL songs are downloaded
            allSongs.isNotEmpty() && downloadedSongs.size == allSongs.size
        } catch (e: Exception) {
            android.util.Log.e("DownloadSelectionActivity", "Error checking library download status", e)
            false
        }
    }
    
    /**
     * Reset all selections after successful download
     */
    private fun resetSelectionState() {
        try {
            // Clear selections in all fragments
            val adapter = binding.viewPager.adapter as? DownloadPagerAdapter
            if (adapter != null) {
                // Get fragments using tags
                val songsFragment = supportFragmentManager.findFragmentByTag("f0") as? DownloadSongsFragment
                val albumsFragment = supportFragmentManager.findFragmentByTag("f1") as? DownloadAlbumsFragment
                val playlistsFragment = supportFragmentManager.findFragmentByTag("f2") as? DownloadPlaylistsFragment
                
                songsFragment?.clearAllSelections()
                albumsFragment?.clearAllSelections()
                playlistsFragment?.clearAllSelections()
            }
            
            // Reset button state
            updateSelectedCount(0)
            
            // Refresh all adapters to show new download status
            refreshAllAdapters()
            
        } catch (e: Exception) {
            android.util.Log.e("DownloadSelectionActivity", "Error resetting selection state", e)
        }
    }
    
    /**
     * Refresh all adapters to show updated download statuses
     */
    fun refreshAllAdapters() {
        try {
            val adapter = binding.viewPager.adapter as? DownloadPagerAdapter
            if (adapter != null) {
                val songsFragment = supportFragmentManager.findFragmentByTag("f0") as? DownloadSongsFragment
                val albumsFragment = supportFragmentManager.findFragmentByTag("f1") as? DownloadAlbumsFragment
                val playlistsFragment = supportFragmentManager.findFragmentByTag("f2") as? DownloadPlaylistsFragment
                
                // Trigger refresh by reloading data
                songsFragment?.let { lifecycleScope.launch { it.loadSongs() } }
                albumsFragment?.let { lifecycleScope.launch { it.loadAlbums() } }
                playlistsFragment?.let { lifecycleScope.launch { it.loadPlaylists() } }
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadSelectionActivity", "Error refreshing adapters", e)
        }
    }
    
    /**
     * ViewPager adapter for download selection tabs
     */
    private class DownloadPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        
        override fun getItemCount(): Int = 3
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> DownloadSongsFragment()
                1 -> DownloadAlbumsFragment()
                2 -> DownloadPlaylistsFragment()
                else -> DownloadSongsFragment()
            }
        }
    }
}