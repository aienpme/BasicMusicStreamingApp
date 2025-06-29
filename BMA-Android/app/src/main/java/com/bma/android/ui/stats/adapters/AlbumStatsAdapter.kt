package com.bma.android.ui.stats.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.models.Album
import com.bma.android.models.AlbumStats
import com.bma.android.storage.PlaylistManager
import com.bma.android.storage.OfflineModeManager
import com.bma.android.utils.ArtworkUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter for displaying album streaming statistics
 */
class AlbumStatsAdapter(private val context: Context) : RecyclerView.Adapter<AlbumStatsAdapter.AlbumStatsViewHolder>() {
    
    private var albumStats: List<AlbumStats> = emptyList()
    private var albumMetadata: Map<String, Album> = emptyMap()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun updateStats(newStats: List<AlbumStats>) {
        albumStats = newStats
        loadAlbumMetadataForStats()
        notifyDataSetChanged()
    }
    
    private fun loadAlbumMetadataForStats() {
        // Get album names for the albums we have stats for (match by name only)
        val albumNames = albumStats.map { it.albumName }.toSet()
        
        // Load metadata asynchronously
        scope.launch {
            try {
                val allAlbums = withContext(Dispatchers.IO) {
                    val playlistManager = PlaylistManager.getInstance(context)
                    
                    // FIXED: Load from BOTH online and offline sources to ensure comprehensive resolution
                    android.util.Log.d("AlbumStatsAdapter", "Loading album metadata from both online and offline sources")
                    
                    val onlineAlbums = try {
                        playlistManager.getAllAlbums()
                    } catch (e: Exception) {
                        android.util.Log.w("AlbumStatsAdapter", "Failed to load online albums: ${e.message}")
                        emptyList()
                    }
                    
                    val offlineAlbums = try {
                        playlistManager.getAllAlbumsOffline()
                    } catch (e: Exception) {
                        android.util.Log.w("AlbumStatsAdapter", "Failed to load offline albums: ${e.message}")
                        emptyList()
                    }
                    
                    // Combine both sources and remove duplicates by name
                    val combinedAlbums = (onlineAlbums + offlineAlbums).distinctBy { it.name }
                    android.util.Log.d("AlbumStatsAdapter", "Combined metadata: ${onlineAlbums.size} online + ${offlineAlbums.size} offline = ${combinedAlbums.size} total unique albums")
                    
                    combinedAlbums
                }
                
                // Match albums by name only (ignore artist variations)
                val albumsByName = mutableMapOf<String, Album>()
                allAlbums.forEach { album ->
                    if (albumNames.contains(album.name) && !albumsByName.containsKey(album.name)) {
                        albumsByName[album.name] = album
                        android.util.Log.d("AlbumStatsAdapter", "Matched album '${album.name}' by '${album.artist}' for stats")
                    }
                }
                
                albumMetadata = albumsByName
                android.util.Log.d("AlbumStatsAdapter", "Loaded metadata for ${albumMetadata.size} albums out of ${albumNames.size} stats (matching by name only)")
                notifyDataSetChanged() // Refresh UI with loaded metadata
            } catch (e: Exception) {
                android.util.Log.e("AlbumStatsAdapter", "Error loading album metadata: ${e.message}", e)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumStatsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album_stats, parent, false)
        return AlbumStatsViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: AlbumStatsViewHolder, position: Int) {
        val stats = albumStats[position]
        val album = albumMetadata[stats.albumName] // Match by album name only
        holder.bind(stats, album)
    }
    
    override fun getItemCount(): Int = albumStats.size
    
    class AlbumStatsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val albumArtwork: ImageView = itemView.findViewById(R.id.album_artwork)
        private val albumTitle: TextView = itemView.findViewById(R.id.album_title)
        private val albumArtist: TextView = itemView.findViewById(R.id.album_artist)
        private val listeningTime: TextView = itemView.findViewById(R.id.listening_time)
        private val playCount: TextView = itemView.findViewById(R.id.play_count)
        
        fun bind(stats: AlbumStats, album: Album?) {
            albumTitle.text = stats.albumName
            albumArtist.text = stats.artistName
            listeningTime.text = formatMinutes(stats.totalMinutes)
            playCount.text = formatPlays(stats.playCount)
            
            // Load album artwork
            if (album != null && album.songs.isNotEmpty()) {
                loadAlbumArtwork(album)
            } else {
                albumArtwork.setImageResource(R.drawable.ic_folder)
            }
        }
        
        private fun loadAlbumArtwork(album: Album) {
            // Use the first song from the album for artwork
            val firstSong = album.songs.first()
            
            // Use ArtworkUtils for offline-aware artwork loading
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val artworkPath = ArtworkUtils.getArtworkPath(itemView.context, firstSong)
                    
                    if (artworkPath.isNotEmpty()) {
                        if (artworkPath.startsWith("file://")) {
                            // Local file - load directly
                            Glide.with(itemView.context)
                                .load(artworkPath)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .placeholder(R.drawable.ic_folder)
                                .error(R.drawable.ic_folder)
                                .into(albumArtwork)
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
                                
                                Glide.with(itemView.context)
                                    .load(glideUrl)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .placeholder(R.drawable.ic_folder)
                                    .error(R.drawable.ic_folder)
                                    .into(albumArtwork)
                            } else {
                                albumArtwork.setImageResource(R.drawable.ic_folder)
                            }
                        }
                    } else {
                        // Empty path - use placeholder
                        albumArtwork.setImageResource(R.drawable.ic_folder)
                    }
                } catch (e: Exception) {
                    // Error loading artwork - use placeholder
                    albumArtwork.setImageResource(R.drawable.ic_folder)
                }
            }
        }
        
        private fun formatMinutes(minutes: Long): String {
            return when {
                minutes == 0L -> "0min"
                minutes < 60 -> "${minutes}min"
                else -> {
                    val hours = minutes / 60
                    val remainingMinutes = minutes % 60
                    if (remainingMinutes == 0L) {
                        "${hours}h"
                    } else {
                        "${hours}h ${remainingMinutes}min"
                    }
                }
            }
        }
        
        private fun formatPlays(plays: Int): String {
            return when (plays) {
                0 -> "0 plays"
                1 -> "1 play"
                else -> "$plays plays"
            }
        }
    }
}