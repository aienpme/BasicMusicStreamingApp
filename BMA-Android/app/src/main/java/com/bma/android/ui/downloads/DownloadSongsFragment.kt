package com.bma.android.ui.downloads

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bma.android.R
import com.bma.android.databinding.FragmentDownloadTabBinding
import com.bma.android.models.Song
import com.bma.android.storage.PlaylistManager
import kotlinx.coroutines.launch
import android.text.TextWatcher
import android.text.Editable

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
        binding.tabIcon.setImageResource(R.drawable.ic_music_note)
        binding.tabTitle.text = "Songs"
        binding.tabSubtitle.text = "Select All"
        
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
        
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                android.util.Log.d("DownloadSongsFragment", "Search query changed: '$query'")
                adapter.filter(query)
            }
        })
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
                adapter.updateSelection(songId, isSelected)
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
        selectedSongs.addAll(adapter.getAllItemIds())
        adapter.selectAll()
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
            binding.downloadStatusText.text = "All songs downloaded"
            binding.downloadSizeText.text = ""
            // Show check icon and set green tint for downloaded state
            binding.downloadStatusText.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_check_circle, 0, 0, 0
            )
            binding.downloadStatusText.compoundDrawables[0]?.setTint(
                android.graphics.Color.parseColor("#4CAF50")
            )
            // Add small spacing between status and songs, remove root bottom padding
            setStatusCardBottomMargin(8)
            setRootBottomPadding(0)
        } else {
            // Some items available for download - show selection mode
            binding.selectAllCheckbox.visibility = View.VISIBLE
            binding.deleteAllButton.visibility = View.GONE
            binding.downloadStatusText.text = "$selectedCount selected for download"
            binding.downloadSizeText.text = "~${String.format("%.1f", estimatedSize)} MB"
            // Hide check icon for selection mode
            binding.downloadStatusText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            // Add bottom spacing when download actions are needed, restore root bottom padding
            setStatusCardBottomMargin(20)
            setRootBottomPadding(16)
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
    
    /**
     * Dynamically adjust bottom margin of status card based on download state
     * @param marginDp Bottom margin in dp (8 for spacing when all downloaded, 20 for selection mode)
     */
    private fun setStatusCardBottomMargin(marginDp: Int) {
        val statusCard = binding.root.findViewById<LinearLayout>(R.id.download_status_card)
        statusCard?.let { card ->
            val layoutParams = card.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            layoutParams?.let { params ->
                val marginPx = (marginDp * resources.displayMetrics.density).toInt()
                params.bottomMargin = marginPx
                card.layoutParams = params
            }
        }
    }
    
    /**
     * Dynamically adjust root container bottom padding
     * @param paddingDp Bottom padding in dp (0 when all downloaded, 16 for selection mode)
     */
    private fun setRootBottomPadding(paddingDp: Int) {
        val paddingPx = (paddingDp * resources.displayMetrics.density).toInt()
        binding.root.setPadding(
            binding.root.paddingLeft,
            binding.root.paddingTop, 
            binding.root.paddingRight,
            paddingPx
        )
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