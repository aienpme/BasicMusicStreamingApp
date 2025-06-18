package com.bma.android.ui.downloads

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bma.android.R
import com.bma.android.databinding.FragmentDownloadTabBinding
import com.bma.android.models.Song
import com.bma.android.storage.PlaylistManager
import kotlinx.coroutines.launch

/**
 * Fragment for selecting individual songs to download
 */
class DownloadSongsFragment : Fragment(R.layout.fragment_download_tab) {
    
    private var _binding: FragmentDownloadTabBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: DownloadItemAdapter<Song>
    private lateinit var playlistManager: PlaylistManager
    private val selectedSongs = mutableSetOf<String>()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDownloadTabBinding.bind(view)
        
        playlistManager = PlaylistManager.getInstance(requireContext())
        
        setupUI()
        setupRecyclerView()
        loadSongs()
    }
    
    private fun setupUI() {
        binding.tabIcon.text = "🎵"
        binding.tabTitle.text = "Songs"
        binding.tabSubtitle.text = "Select individual songs to download for offline listening"
        
        binding.selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectAllSongs()
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
            itemType = DownloadItemAdapter.ItemType.SONG,
            lifecycleOwner = this,
            onSelectionChanged = { songId, isSelected ->
                if (isSelected) {
                    selectedSongs.add(songId)
                } else {
                    selectedSongs.remove(songId)
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
    
    fun loadSongs() {
        lifecycleScope.launch {
            try {
                val songs = playlistManager.getAllSongs()
                
                if (songs.isEmpty()) {
                    showEmptyState("No songs available for download. Check your connection and ensure you're connected to a BMA server.")
                } else {
                    adapter.updateItems(songs) {
                        // Called when download status refresh is complete
                        updateSelectionStatus()
                    }
                    binding.emptyState.visibility = View.GONE
                    binding.itemsRecyclerView.visibility = View.VISIBLE
                    android.util.Log.d("DownloadSongsFragment", "Loaded ${songs.size} songs")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("DownloadSongsFragment", "Error loading songs", e)
                showEmptyState("Error loading songs: ${e.message}")
            }
        }
    }
    
    private fun selectAllSongs() {
        adapter.selectAll()
        selectedSongs.addAll(adapter.getAllItemIds())
        updateSelectionStatus()
    }
    
    
    private fun updateSelectionStatus() {
        val selectedCount = selectedSongs.size
        val estimatedSize = selectedCount * 4.5 // Rough estimate: 4.5MB per song
        
        // Check if all songs are downloaded - if so, show already downloaded status and delete button
        val availableForDownload = adapter.getAvailableItemCount()
        val totalItems = adapter.getAllItemIds().size
        
        if (availableForDownload == 0 && totalItems > 0) {
            // All items are downloaded - show delete mode
            binding.selectAllCheckbox.visibility = View.GONE
            binding.deleteAllButton.visibility = View.VISIBLE
            binding.downloadStatusText.text = "✅ Already Downloaded!"
            binding.downloadSizeText.text = "All songs are on your device"
        } else {
            // Some items available for download - show selection mode
            binding.selectAllCheckbox.visibility = View.VISIBLE
            binding.deleteAllButton.visibility = View.GONE
            binding.downloadStatusText.text = "$selectedCount selected for download"
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
    
    fun getSelectedSongIds(): Set<String> = selectedSongs.toSet()
    
    private fun showDeleteAllConfirmation() {
        val downloadedSongs = adapter.getAllItemIds().filter { songId ->
            adapter.downloadedItems.contains(songId)
        }
        
        if (downloadedSongs.isEmpty()) return
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete All Downloaded Songs")
            .setMessage("Remove all ${downloadedSongs.size} downloaded songs from device storage?\n\nThis will free up storage space but you'll need to re-download them for offline listening.")
            .setPositiveButton("Delete All") { _, _ ->
                deleteAllDownloadedSongs(downloadedSongs)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteAllDownloadedSongs(songIds: List<String>) {
        lifecycleScope.launch {
            try {
                val downloadManager = com.bma.android.storage.DownloadManager.getInstance(requireContext())
                val playlistManager = PlaylistManager.getInstance(requireContext())
                
                var deletedCount = 0
                songIds.forEach { songId ->
                    try {
                        downloadManager.deleteDownload(songId)
                        playlistManager.markAsNotDownloaded(songId)
                        deletedCount++
                    } catch (e: Exception) {
                        android.util.Log.e("DownloadSongsFragment", "Error deleting song: $songId", e)
                    }
                }
                
                // Songs deleted successfully
                
                // Refresh all adapters
                (activity as? DownloadSelectionActivity)?.refreshAllAdapters()
                
            } catch (e: Exception) {
                android.util.Log.e("DownloadSongsFragment", "Error deleting all songs", e)
            }
        }
    }
    
    fun clearAllSelections() {
        selectedSongs.clear()
        adapter.clearAllSelections()
        updateSelectionStatus()
        binding.selectAllCheckbox.setOnCheckedChangeListener(null)
        binding.selectAllCheckbox.isChecked = false
        binding.selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectAllSongs()
            } else {
                clearAllSelections()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}