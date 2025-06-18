package com.bma.android.ui.downloads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.databinding.ItemDownloadSelectionBinding
import com.bma.android.models.Song
import com.bma.android.models.Album
import com.bma.android.models.Playlist
import com.bma.android.storage.PlaylistManager
import com.bma.android.storage.DownloadManager
import kotlinx.coroutines.launch

/**
 * Generic adapter for download selection items (Songs, Albums, Playlists)
 */
class DownloadItemAdapter<T>(
    private val itemType: ItemType,
    private val lifecycleOwner: LifecycleOwner,
    private val onSelectionChanged: (String, Boolean) -> Unit,
    private val onItemDeleted: (() -> Unit)? = null
) : RecyclerView.Adapter<DownloadItemAdapter.ViewHolder>() {
    
    enum class ItemType {
        SONG, ALBUM, PLAYLIST
    }
    
    enum class DownloadStatus {
        NOT_DOWNLOADED, DOWNLOADED
    }
    
    private var allItems = listOf<T>()
    private var filteredItems = listOf<T>()
    private val selectedItems = mutableSetOf<String>()
    val downloadedItems = mutableSetOf<String>()
    
    fun updateItems(items: List<T>, onStatusRefreshed: (() -> Unit)? = null) {
        allItems = items
        filteredItems = items
        refreshDownloadStatuses(onStatusRefreshed)
        notifyDataSetChanged()
    }
    
    private fun refreshDownloadStatuses(onStatusRefreshed: (() -> Unit)? = null) {
        lifecycleOwner.lifecycleScope.launch {
            try {
                val context = (lifecycleOwner as? androidx.fragment.app.Fragment)?.requireContext() 
                    ?: (lifecycleOwner as? androidx.appcompat.app.AppCompatActivity)?.applicationContext
                    ?: return@launch
                    
                val playlistManager = PlaylistManager.getInstance(context)
                val newDownloadedItems = mutableSetOf<String>()
                
                allItems.forEach { item ->
                    val isDownloaded = when (itemType) {
                        ItemType.SONG -> {
                            val song = item as Song
                            playlistManager.isSongDownloaded(song.id)
                        }
                        ItemType.ALBUM -> {
                            val album = item as Album
                            // Album is considered downloaded if ALL its songs are downloaded
                            album.songs.isNotEmpty() && album.songs.all { song -> 
                                playlistManager.isSongDownloaded(song.id) 
                            }
                        }
                        ItemType.PLAYLIST -> {
                            val playlist = item as Playlist
                            // Playlist is considered downloaded if ALL its songs are downloaded
                            playlist.songIds.isNotEmpty() && playlist.songIds.all { songId -> 
                                playlistManager.isSongDownloaded(songId) 
                            }
                        }
                    }
                    
                    if (isDownloaded) {
                        val itemId = when (itemType) {
                            ItemType.SONG -> (item as Song).id
                            ItemType.ALBUM -> (item as Album).id
                            ItemType.PLAYLIST -> (item as Playlist).id
                        }
                        newDownloadedItems.add(itemId)
                    }
                }
                
                downloadedItems.clear()
                downloadedItems.addAll(newDownloadedItems)
                notifyDataSetChanged()
                
                // Call the callback when refresh is complete
                onStatusRefreshed?.invoke()
                
            } catch (e: Exception) {
                android.util.Log.e("DownloadItemAdapter", "Error refreshing download statuses", e)
                onStatusRefreshed?.invoke() // Call even on error to prevent hanging
            }
        }
    }
    
    fun filter(query: String) {
        filteredItems = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { item ->
                when (itemType) {
                    ItemType.SONG -> {
                        val song = item as Song
                        song.title.contains(query, ignoreCase = true) ||
                        song.artist.contains(query, ignoreCase = true) ||
                        song.album.contains(query, ignoreCase = true)
                    }
                    ItemType.ALBUM -> {
                        val album = item as Album
                        album.name.contains(query, ignoreCase = true) ||
                        (album.artist ?: "").contains(query, ignoreCase = true)
                    }
                    ItemType.PLAYLIST -> {
                        val playlist = item as Playlist
                        playlist.name.contains(query, ignoreCase = true)
                    }
                }
            }
        }
        notifyDataSetChanged()
    }
    
    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(getAllItemIds())
        notifyDataSetChanged()
    }
    
    fun clearAllSelections() {
        selectedItems.clear()
        notifyDataSetChanged()
    }
    
    fun getAllItemIds(): List<String> {
        return allItems.map { item ->
            when (itemType) {
                ItemType.SONG -> (item as Song).id
                ItemType.ALBUM -> (item as Album).id
                ItemType.PLAYLIST -> (item as Playlist).id
            }
        }
    }
    
    fun getAvailableItemCount(): Int {
        return getAllItemIds().count { itemId ->
            !downloadedItems.contains(itemId)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadSelectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredItems[position]
        holder.bind(item, itemType, selectedItems, downloadedItems, lifecycleOwner, onSelectionChanged, onItemDeleted)
    }
    
    override fun getItemCount(): Int = filteredItems.size
    
    class ViewHolder(private val binding: ItemDownloadSelectionBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun <T> bind(
            item: T,
            itemType: ItemType,
            selectedItems: MutableSet<String>,
            downloadedItems: MutableSet<String>,
            lifecycleOwner: LifecycleOwner,
            onSelectionChanged: (String, Boolean) -> Unit,
            onItemDeleted: (() -> Unit)? = null
        ) {
            when (itemType) {
                ItemType.SONG -> bindSong(item as Song, selectedItems, downloadedItems, lifecycleOwner, onSelectionChanged, onItemDeleted)
                ItemType.ALBUM -> bindAlbum(item as Album, selectedItems, downloadedItems, lifecycleOwner, onSelectionChanged, onItemDeleted)
                ItemType.PLAYLIST -> bindPlaylist(item as Playlist, selectedItems, downloadedItems, lifecycleOwner, onSelectionChanged, onItemDeleted)
            }
        }
        
        private fun bindSong(
            song: Song,
            selectedItems: MutableSet<String>,
            downloadedItems: MutableSet<String>,
            lifecycleOwner: LifecycleOwner,
            onSelectionChanged: (String, Boolean) -> Unit,
            onItemDeleted: (() -> Unit)? = null
        ) {
            binding.itemTitle.text = song.title
            binding.itemSubtitle.text = "${song.artist} - ${song.album}"
            
            val estimatedSize = "~4.5 MB" // Rough estimate
            binding.itemDetails.text = "Track ${song.trackNumber} • $estimatedSize"
            
            val isDownloaded = downloadedItems.contains(song.id)
            val isSelected = selectedItems.contains(song.id)
            
            if (isDownloaded) {
                // Song is downloaded - show status and delete option
                binding.selectionCheckbox.visibility = View.GONE
                binding.downloadStatusIcon.visibility = View.VISIBLE
                binding.downloadStatusText.visibility = View.VISIBLE
                binding.downloadStatusIcon.setImageResource(android.R.drawable.ic_menu_delete)
                binding.downloadStatusText.text = "Downloaded"
                
                // Set up delete functionality
                binding.downloadStatusIcon.setOnClickListener {
                    showDeleteConfirmation(song, lifecycleOwner, onItemDeleted)
                }
                
                binding.root.setOnClickListener {
                    showDeleteConfirmation(song, lifecycleOwner, onItemDeleted)
                }
                
                // Visual styling for downloaded items
                binding.itemTitle.alpha = 0.8f
                binding.itemSubtitle.alpha = 0.7f
                binding.itemDetails.alpha = 0.7f
                
            } else {
                // Song is not downloaded - show selection checkbox
                binding.selectionCheckbox.visibility = View.VISIBLE
                binding.downloadStatusIcon.visibility = View.GONE
                binding.downloadStatusText.visibility = View.GONE
                
                // Clear listener before setting checked state to avoid triggering it
                binding.selectionCheckbox.setOnCheckedChangeListener(null)
                binding.selectionCheckbox.isChecked = isSelected
                
                // Now set the listener
                binding.selectionCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(song.id, isChecked)
                }
                
                binding.root.setOnClickListener {
                    binding.selectionCheckbox.isChecked = !binding.selectionCheckbox.isChecked
                }
                
                // Normal styling for available items
                binding.itemTitle.alpha = 1.0f
                binding.itemSubtitle.alpha = 1.0f
                binding.itemDetails.alpha = 1.0f
            }
        }
        
        private fun showDeleteConfirmation(
            song: Song, 
            lifecycleOwner: LifecycleOwner,
            onItemDeleted: (() -> Unit)? = null
        ) {
            val context = binding.root.context
            AlertDialog.Builder(context)
                .setTitle("Delete Downloaded Song")
                .setMessage("Remove '${song.title}' from device storage?\n\nThis will free up storage space but you'll need to re-download it for offline listening.")
                .setPositiveButton("Delete") { _, _ ->
                    deleteDownloadedSong(song, lifecycleOwner, context, onItemDeleted)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        private fun deleteDownloadedSong(
            song: Song,
            lifecycleOwner: LifecycleOwner, 
            context: android.content.Context,
            onItemDeleted: (() -> Unit)? = null
        ) {
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val downloadManager = DownloadManager.getInstance(context)
                    val playlistManager = PlaylistManager.getInstance(context)
                    
                    // Delete file and update metadata
                    downloadManager.deleteDownload(song.id)
                    playlistManager.markAsNotDownloaded(song.id)
                    
                    // Trigger refresh
                    onItemDeleted?.invoke()
                    
                } catch (e: Exception) {
                    android.util.Log.e("DownloadItemAdapter", "Error deleting song: ${song.title}", e)

                }
            }
        }
        
        private fun bindAlbum(
            album: Album,
            selectedItems: MutableSet<String>,
            downloadedItems: MutableSet<String>,
            lifecycleOwner: LifecycleOwner,
            onSelectionChanged: (String, Boolean) -> Unit,
            onItemDeleted: (() -> Unit)? = null
        ) {
            binding.itemTitle.text = album.name
            binding.itemSubtitle.text = album.artist ?: "Unknown Artist"
            
            val estimatedSize = album.trackCount * 4.5 // 4.5MB per song estimate
            val size = String.format("%.1f MB", estimatedSize)
            binding.itemDetails.text = "${album.trackCount} songs • ~$size"
            
            // For albums, check if all songs are downloaded
            val isDownloaded = isAlbumFullyDownloaded(album, downloadedItems)
            val isSelected = selectedItems.contains(album.id)
            
            if (isDownloaded) {
                // Album is fully downloaded - show status and delete option
                binding.selectionCheckbox.visibility = View.GONE
                binding.downloadStatusIcon.visibility = View.VISIBLE
                binding.downloadStatusText.visibility = View.VISIBLE
                binding.downloadStatusIcon.setImageResource(android.R.drawable.ic_menu_delete)
                binding.downloadStatusText.text = "Downloaded"
                
                // Set up delete functionality for album
                binding.downloadStatusIcon.setOnClickListener {
                    showAlbumDeleteConfirmation(album, lifecycleOwner, onItemDeleted)
                }
                
                binding.root.setOnClickListener {
                    showAlbumDeleteConfirmation(album, lifecycleOwner, onItemDeleted)
                }
                
                // Visual styling for downloaded albums
                binding.itemTitle.alpha = 0.8f
                binding.itemSubtitle.alpha = 0.7f
                binding.itemDetails.alpha = 0.7f
                
            } else {
                // Album is not fully downloaded - show selection checkbox
                binding.selectionCheckbox.visibility = View.VISIBLE
                binding.downloadStatusIcon.visibility = View.GONE
                binding.downloadStatusText.visibility = View.GONE
                
                // Clear listener before setting checked state to avoid triggering it
                binding.selectionCheckbox.setOnCheckedChangeListener(null)
                binding.selectionCheckbox.isChecked = isSelected
                
                // Now set the listener
                binding.selectionCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(album.id, isChecked)
                }
                
                binding.root.setOnClickListener {
                    binding.selectionCheckbox.isChecked = !binding.selectionCheckbox.isChecked
                }
                
                // Normal styling for available albums
                binding.itemTitle.alpha = 1.0f
                binding.itemSubtitle.alpha = 1.0f
                binding.itemDetails.alpha = 1.0f
            }
        }
        
        private fun isAlbumFullyDownloaded(album: Album, downloadedItems: MutableSet<String>): Boolean {
            // Check if the album ID is in the downloadedItems (which now contains properly detected album IDs)
            return downloadedItems.contains(album.id)
        }
        
        private fun showAlbumDeleteConfirmation(
            album: Album,
            lifecycleOwner: LifecycleOwner,
            onItemDeleted: (() -> Unit)? = null
        ) {
            val context = binding.root.context
            AlertDialog.Builder(context)
                .setTitle("Delete Downloaded Album")
                .setMessage("Remove all songs from '${album.name}' from device storage?\n\nThis will free up storage space but you'll need to re-download the album for offline listening.")
                .setPositiveButton("Delete") { _, _ ->
                    deleteDownloadedAlbum(album, lifecycleOwner, context, onItemDeleted)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        private fun deleteDownloadedAlbum(
            album: Album,
            lifecycleOwner: LifecycleOwner,
            context: android.content.Context,
            onItemDeleted: (() -> Unit)? = null
        ) {
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val downloadManager = DownloadManager.getInstance(context)
                    val playlistManager = PlaylistManager.getInstance(context)
                    
                    var deletedCount = 0
                    // Delete all songs in the album
                    album.songs.forEach { song ->
                        try {
                            downloadManager.deleteDownload(song.id)
                            playlistManager.markAsNotDownloaded(song.id)
                            deletedCount++
                        } catch (e: Exception) {
                            android.util.Log.e("DownloadItemAdapter", "Error deleting song: ${song.title}", e)
                        }
                    }

                    
                    // Trigger refresh of all adapters since album deletion affects songs and playlists
                    onItemDeleted?.invoke()
                    
                } catch (e: Exception) {
                    android.util.Log.e("DownloadItemAdapter", "Error deleting album: ${album.name}", e)
                }
            }
        }
        
        private fun bindPlaylist(
            playlist: Playlist,
            selectedItems: MutableSet<String>,
            downloadedItems: MutableSet<String>,
            lifecycleOwner: LifecycleOwner,
            onSelectionChanged: (String, Boolean) -> Unit,
            onItemDeleted: (() -> Unit)? = null
        ) {
            binding.itemTitle.text = playlist.name
            binding.itemSubtitle.text = "Playlist"
            
            val estimatedSize = playlist.songIds.size * 4.5 // 4.5MB per song estimate
            val size = String.format("%.1f MB", estimatedSize)
            binding.itemDetails.text = "${playlist.songIds.size} songs • ~$size"
            
            // For playlists, check if all songs are downloaded
            val isDownloaded = isPlaylistFullyDownloaded(playlist, downloadedItems)
            val isSelected = selectedItems.contains(playlist.id)
            
            if (isDownloaded) {
                // Playlist is fully downloaded - show status and delete option
                binding.selectionCheckbox.visibility = View.GONE
                binding.downloadStatusIcon.visibility = View.VISIBLE
                binding.downloadStatusText.visibility = View.VISIBLE
                binding.downloadStatusIcon.setImageResource(android.R.drawable.ic_menu_delete)
                binding.downloadStatusText.text = "Downloaded"
                
                // Set up delete functionality for playlist
                binding.downloadStatusIcon.setOnClickListener {
                    showPlaylistDeleteConfirmation(playlist, lifecycleOwner, onItemDeleted)
                }
                
                binding.root.setOnClickListener {
                    showPlaylistDeleteConfirmation(playlist, lifecycleOwner, onItemDeleted)
                }
                
                // Visual styling for downloaded playlists
                binding.itemTitle.alpha = 0.8f
                binding.itemSubtitle.alpha = 0.7f
                binding.itemDetails.alpha = 0.7f
                
            } else {
                // Playlist is not fully downloaded - show selection checkbox
                binding.selectionCheckbox.visibility = View.VISIBLE
                binding.downloadStatusIcon.visibility = View.GONE
                binding.downloadStatusText.visibility = View.GONE
                
                // Clear listener before setting checked state to avoid triggering it
                binding.selectionCheckbox.setOnCheckedChangeListener(null)
                binding.selectionCheckbox.isChecked = isSelected
                
                // Now set the listener
                binding.selectionCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(playlist.id, isChecked)
                }
                
                binding.root.setOnClickListener {
                    binding.selectionCheckbox.isChecked = !binding.selectionCheckbox.isChecked
                }
                
                // Normal styling for available playlists
                binding.itemTitle.alpha = 1.0f
                binding.itemSubtitle.alpha = 1.0f
                binding.itemDetails.alpha = 1.0f
            }
        }
        
        private fun isPlaylistFullyDownloaded(playlist: Playlist, downloadedItems: MutableSet<String>): Boolean {
            // Check if the playlist ID is in the downloadedItems (which now contains properly detected playlist IDs)
            return downloadedItems.contains(playlist.id)
        }
        
        private fun showPlaylistDeleteConfirmation(
            playlist: Playlist,
            lifecycleOwner: LifecycleOwner,
            onItemDeleted: (() -> Unit)? = null
        ) {
            val context = binding.root.context
            AlertDialog.Builder(context)
                .setTitle("Delete Downloaded Playlist")
                .setMessage("Remove all songs from '${playlist.name}' playlist from device storage?\n\nThis will free up storage space but you'll need to re-download the playlist for offline listening.")
                .setPositiveButton("Delete") { _, _ ->
                    deleteDownloadedPlaylist(playlist, lifecycleOwner, context, onItemDeleted)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        private fun deleteDownloadedPlaylist(
            playlist: Playlist,
            lifecycleOwner: LifecycleOwner,
            context: android.content.Context,
            onItemDeleted: (() -> Unit)? = null
        ) {
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val downloadManager = DownloadManager.getInstance(context)
                    val playlistManager = PlaylistManager.getInstance(context)
                    
                    var deletedCount = 0
                    // Delete all songs in the playlist
                    playlist.songIds.forEach { songId ->
                        try {
                            downloadManager.deleteDownload(songId)
                            playlistManager.markAsNotDownloaded(songId)
                            deletedCount++
                        } catch (e: Exception) {
                            android.util.Log.e("DownloadItemAdapter", "Error deleting song: $songId", e)
                        }
                    }

                    
                    // Trigger refresh of all adapters since playlist deletion affects songs and albums
                    onItemDeleted?.invoke()
                    
                } catch (e: Exception) {
                    android.util.Log.e("DownloadItemAdapter", "Error deleting playlist: ${playlist.name}", e)
                }
            }
        }
        
        private fun formatDuration(durationMs: Long): String {
            val minutes = (durationMs / 1000) / 60
            val seconds = (durationMs / 1000) % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }
}