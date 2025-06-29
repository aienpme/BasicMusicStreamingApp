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
import com.bma.android.models.Song
import com.bma.android.models.SongStats
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
 * Adapter for displaying individual song streaming statistics
 */
class SongStatsAdapter(private val context: Context) : RecyclerView.Adapter<SongStatsAdapter.SongStatsViewHolder>() {
    
    private var songStats: List<SongStats> = emptyList()
    private var songMetadata: Map<String, Song> = emptyMap()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun updateStats(newStats: List<SongStats>) {
        songStats = newStats
        loadSongMetadataForStats()
        notifyDataSetChanged()
    }
    
    private fun loadSongMetadataForStats() {
        // Get song metadata for the songs we have stats for
        val songIds = songStats.map { it.songId }.toSet()
        
        // Load metadata asynchronously
        scope.launch {
            try {
                val allSongs = withContext(Dispatchers.IO) {
                    val playlistManager = PlaylistManager.getInstance(context)
                    
                    // FIXED: Load from BOTH online and offline sources to ensure comprehensive resolution
                    android.util.Log.d("SongStatsAdapter", "Loading song metadata from both online and offline sources")
                    
                    val onlineSongs = try {
                        playlistManager.getAllSongs()
                    } catch (e: Exception) {
                        android.util.Log.w("SongStatsAdapter", "Failed to load online songs: ${e.message}")
                        emptyList()
                    }
                    
                    val offlineSongs = try {
                        playlistManager.getAllSongsOffline()
                    } catch (e: Exception) {
                        android.util.Log.w("SongStatsAdapter", "Failed to load offline songs: ${e.message}")
                        emptyList()
                    }
                    
                    // Combine both sources and remove duplicates by ID
                    val combinedSongs = (onlineSongs + offlineSongs).distinctBy { it.id }
                    android.util.Log.d("SongStatsAdapter", "Combined metadata: ${onlineSongs.size} online + ${offlineSongs.size} offline = ${combinedSongs.size} total unique songs")
                    
                    combinedSongs
                }
                
                songMetadata = allSongs.filter { songIds.contains(it.id) }.associateBy { it.id }
                android.util.Log.d("SongStatsAdapter", "Loaded metadata for ${songMetadata.size} songs out of ${songIds.size} stats")
                notifyDataSetChanged() // Refresh UI with loaded metadata
            } catch (e: Exception) {
                android.util.Log.e("SongStatsAdapter", "Error loading song metadata: ${e.message}", e)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongStatsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song_stats, parent, false)
        return SongStatsViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: SongStatsViewHolder, position: Int) {
        val stats = songStats[position]
        val song = songMetadata[stats.songId]
        
        holder.bind(stats, song)
    }
    
    override fun getItemCount(): Int = songStats.size
    
    class SongStatsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val albumArtwork: ImageView = itemView.findViewById(R.id.album_artwork)
        private val songTitle: TextView = itemView.findViewById(R.id.song_title)
        private val songArtist: TextView = itemView.findViewById(R.id.song_artist)
        private val listeningTime: TextView = itemView.findViewById(R.id.listening_time)
        private val playCount: TextView = itemView.findViewById(R.id.play_count)
        
        fun bind(stats: SongStats, song: Song?) {
            // Set song info or fallback to ID if metadata not available
            if (song != null) {
                songTitle.text = song.title
                songArtist.text = song.artist
                loadSongArtwork(song)
            } else {
                songTitle.text = "Unknown Song"
                songArtist.text = "ID: ${stats.songId}"
                albumArtwork.setImageResource(R.drawable.ic_music_note)
            }
            
            // Format and display stats
            listeningTime.text = formatMinutes(stats.totalMinutes)
            playCount.text = formatPlays(stats.playCount)
        }
        
        private fun loadSongArtwork(song: Song) {
            // Use ArtworkUtils for offline-aware artwork loading
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val artworkPath = ArtworkUtils.getArtworkPath(itemView.context, song)
                    
                    if (artworkPath.isNotEmpty()) {
                        if (artworkPath.startsWith("file://")) {
                            // Local file - load directly
                            Glide.with(itemView.context)
                                .load(artworkPath)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .placeholder(R.drawable.ic_music_note)
                                .error(R.drawable.ic_music_note)
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
                                    .placeholder(R.drawable.ic_music_note)
                                    .error(R.drawable.ic_music_note)
                                    .into(albumArtwork)
                            } else {
                                albumArtwork.setImageResource(R.drawable.ic_music_note)
                            }
                        }
                    } else {
                        // Empty path - use placeholder
                        albumArtwork.setImageResource(R.drawable.ic_music_note)
                    }
                } catch (e: Exception) {
                    // Error loading artwork - use placeholder
                    albumArtwork.setImageResource(R.drawable.ic_music_note)
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