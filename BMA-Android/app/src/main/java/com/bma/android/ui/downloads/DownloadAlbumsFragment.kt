package com.bma.android.ui.downloads

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bma.android.R
import com.bma.android.databinding.FragmentDownloadTabBinding
import com.bma.android.models.Album
import com.bma.android.storage.PlaylistManager
import kotlinx.coroutines.launch

/**
 * Fragment for selecting albums to download
 */
class DownloadAlbumsFragment : Fragment(R.layout.fragment_download_tab) {
    
    private var _binding: FragmentDownloadTabBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: DownloadItemAdapter<Album>
    private lateinit var playlistManager: PlaylistManager
    private val selectedAlbums = mutableSetOf<String>()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDownloadTabBinding.bind(view)
        
        playlistManager = PlaylistManager.getInstance(requireContext())
        
        setupUI()
        setupRecyclerView()
        loadAlbums()
    }
    
    private fun setupUI() {
        binding.tabIcon.text = "💿"
        binding.tabTitle.text = "Albums"
        binding.tabSubtitle.text = "Download complete albums with all their songs"
        
        binding.selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectAllAlbums()
            } else {
                clearAllSelections()
            }
        }
        
        binding.deleteAllButton.setOnClickListener {
            showDeleteAllConfirmation()
        }
        
        // TODO: Implement search functionality
        // binding.searchEditText.addTextChangedListener { adapter.filter(it.toString()) }
    }
    
    private fun setupRecyclerView() {
        adapter = DownloadItemAdapter(
            itemType = DownloadItemAdapter.ItemType.ALBUM,
            lifecycleOwner = this,
            onSelectionChanged = { albumId, isSelected ->
                if (isSelected) {
                    selectedAlbums.add(albumId)
                } else {
                    selectedAlbums.remove(albumId)
                }
                updateSelectionStatus()
            },
            onItemDeleted = {
                // Refresh all adapters when any item is deleted (affects cross-references)
                (activity as? DownloadSelectionActivity)?.refreshAllAdapters()
            }
        )
        
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsRecyclerView.adapter = adapter
    }
    
    fun loadAlbums() {
        lifecycleScope.launch {
            try {
                val albums = playlistManager.getAllAlbums()
                
                if (albums.isEmpty()) {
                    showEmptyState("No albums available for download. Check your connection and ensure you're connected to a BMA server.")
                } else {
                    adapter.updateItems(albums) {
                        // Called when download status refresh is complete
                        updateSelectionStatus()
                    }
                    binding.emptyState.visibility = View.GONE
                    binding.itemsRecyclerView.visibility = View.VISIBLE
                    android.util.Log.d("DownloadAlbumsFragment", "Loaded ${albums.size} albums")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("DownloadAlbumsFragment", "Error loading albums", e)
                showEmptyState("Error loading albums: ${e.message}")
            }
        }
    }
    
    private fun selectAllAlbums() {
        adapter.selectAll()
        selectedAlbums.addAll(adapter.getAllItemIds())
        updateSelectionStatus()
    }
    
    fun clearAllSelections() {
        adapter.clearAllSelections()
        selectedAlbums.clear()
        updateSelectionStatus()
        binding.selectAllCheckbox.setOnCheckedChangeListener(null)
        binding.selectAllCheckbox.isChecked = false
        binding.selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectAllAlbums()
            } else {
                clearAllSelections()
            }
        }
    }
    
    private fun updateSelectionStatus() {
        val selectedCount = selectedAlbums.size
        // Calculate estimated size based on selected albums
        val estimatedSize = selectedCount * 45.0 // Rough estimate: 45MB per album
        
        // Check if all albums are downloaded - if so, show already downloaded status and delete button
        val availableForDownload = adapter.getAvailableItemCount()
        val totalItems = adapter.getAllItemIds().size
        
        if (availableForDownload == 0 && totalItems > 0) {
            // All items are downloaded - show delete mode
            binding.selectAllCheckbox.visibility = View.GONE
            binding.deleteAllButton.visibility = View.VISIBLE
            binding.downloadStatusText.text = "✅ Already Downloaded!"
            binding.downloadSizeText.text = "All albums are on your device"
        } else {
            // Some items available for download - show selection mode
            binding.selectAllCheckbox.visibility = View.VISIBLE
            binding.deleteAllButton.visibility = View.GONE
            binding.downloadStatusText.text = "$selectedCount albums selected for download"
            binding.downloadSizeText.text = "~${String.format("%.1f", estimatedSize)} MB"
        }
        
        // Check if all available items are selected
        val allAvailableSelected = availableForDownload > 0 && selectedCount >= availableForDownload
        
        // Update parent activity with selection count and "all selected" state
        (activity as? DownloadSelectionActivity)?.updateSelectedCount(selectedCount, allAvailableSelected)
    }
    
    private fun showEmptyState(message: String) {
        binding.emptyState.visibility = View.VISIBLE
        binding.itemsRecyclerView.visibility = View.GONE
        binding.emptyStateText.text = message
    }
    
    fun getSelectedAlbumIds(): Set<String> = selectedAlbums.toSet()
    
    private fun showDeleteAllConfirmation() {
        val downloadedAlbums = adapter.getAllItemIds().filter { albumId ->
            adapter.downloadedItems.contains(albumId)
        }
        
        if (downloadedAlbums.isEmpty()) return
        
        lifecycleScope.launch {
            try {
                val allAlbums = playlistManager.getAllAlbums()
                val albumsToDelete = allAlbums.filter { it.id in downloadedAlbums }
                val totalSongs = albumsToDelete.sumOf { it.songs.size }
                
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete All Downloaded Albums")
                    .setMessage("Remove all ${downloadedAlbums.size} downloaded albums ($totalSongs songs) from device storage?\n\nThis will free up storage space but you'll need to re-download them for offline listening.")
                    .setPositiveButton("Delete All") { _, _ ->
                        deleteAllDownloadedAlbums(albumsToDelete)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                android.util.Log.e("DownloadAlbumsFragment", "Error showing delete confirmation", e)
            }
        }
    }
    
    private fun deleteAllDownloadedAlbums(albums: List<Album>) {
        lifecycleScope.launch {
            try {
                val downloadManager = com.bma.android.storage.DownloadManager.getInstance(requireContext())
                val playlistManager = PlaylistManager.getInstance(requireContext())
                
                var deletedCount = 0
                albums.forEach { album ->
                    album.songs.forEach { song ->
                        try {
                            downloadManager.deleteDownload(song.id)
                            playlistManager.markAsNotDownloaded(song.id)
                            deletedCount++
                        } catch (e: Exception) {
                            android.util.Log.e("DownloadAlbumsFragment", "Error deleting song: ${song.title}", e)
                        }
                    }
                }
                
                // Refresh all adapters
                (activity as? DownloadSelectionActivity)?.refreshAllAdapters()

            } catch (e: Exception) {
                Log.e("DownloadAlbumsFragment", "Error deleting all albums", e)
            }

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}