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
import com.bma.android.models.ArtistStats
import com.bma.android.models.Song
import com.bma.android.storage.IndividualStatsManager
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
 * Adapter for displaying artist streaming statistics
 */
class ArtistStatsAdapter(private val context: Context) : RecyclerView.Adapter<ArtistStatsAdapter.ArtistStatsViewHolder>() {
    
    private var artistStats: List<ArtistStats> = emptyList()
    private var artistRepresentativeSongs: Map<String, Song> = emptyMap()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun updateStats(newStats: List<ArtistStats>) {
        artistStats = newStats
        loadRepresentativeSongsForArtists()
        notifyDataSetChanged()
    }
    
    /**
     * Check if an artist string contains an individual artist name
     * Uses same separators as IndividualStatsManager for consistency
     */
    private fun artistStringContainsArtist(artistString: String, targetArtist: String): Boolean {
        val separators = listOf(
            " and ", " & ", ", ", " feat. ", " feat ", " ft. ", " ft ", 
            " featuring ", " with ", " vs. ", " vs ", " x ", " X "
        )
        
        var artists = listOf(artistString.trim())
        
        // Apply each separator to split the string
        separators.forEach { separator ->
            artists = artists.flatMap { artist ->
                artist.split(separator, ignoreCase = true)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
        }
        
        // Check if any of the split artists matches our target (case-insensitive)
        return artists.any { it.equals(targetArtist, ignoreCase = true) }
    }

    private fun loadRepresentativeSongsForArtists() {
        // Find the most-played song for each artist to use for artwork
        scope.launch {
            try {
                val allSongs = withContext(Dispatchers.IO) {
                    val playlistManager = PlaylistManager.getInstance(context)
                    
                    // FIXED: Load from BOTH online and offline sources to ensure comprehensive resolution
                    android.util.Log.d("ArtistStatsAdapter", "Loading song metadata from both online and offline sources")
                    
                    val onlineSongs = try {
                        playlistManager.getAllSongs()
                    } catch (e: Exception) {
                        android.util.Log.w("ArtistStatsAdapter", "Failed to load online songs: ${e.message}")
                        emptyList()
                    }
                    
                    val offlineSongs = try {
                        playlistManager.getAllSongsOffline()
                    } catch (e: Exception) {
                        android.util.Log.w("ArtistStatsAdapter", "Failed to load offline songs: ${e.message}")
                        emptyList()
                    }
                    
                    // Combine both sources and remove duplicates by ID
                    val combinedSongs = (onlineSongs + offlineSongs).distinctBy { it.id }
                    android.util.Log.d("ArtistStatsAdapter", "Combined metadata: ${onlineSongs.size} online + ${offlineSongs.size} offline = ${combinedSongs.size} total unique songs")
                    
                    combinedSongs
                }
                
                val statsManager = IndividualStatsManager.getInstance(context)
                val songStats = statsManager.getAllSongStats()
                
                val representativeSongs = mutableMapOf<String, Song>()
                
                for (artistStat in artistStats) {
                    // Find all songs that include this artist (handles multi-artist tracks)
                    val artistSongs = allSongs.filter { song ->
                        artistStringContainsArtist(song.artist, artistStat.artistName)
                    }
                    
                    android.util.Log.d("ArtistStatsAdapter", "Found ${artistSongs.size} songs for artist '${artistStat.artistName}'")
                    
                    if (artistSongs.isNotEmpty()) {
                        // Find the most-played song by this artist
                        val mostPlayedSong = artistSongs.maxByOrNull { song ->
                            val songStat = songStats.find { it.songId == song.id }
                            songStat?.totalMinutes ?: 0L
                        }
                        
                        if (mostPlayedSong != null) {
                            representativeSongs[artistStat.artistName] = mostPlayedSong
                            android.util.Log.d("ArtistStatsAdapter", "Representative song for '${artistStat.artistName}': '${mostPlayedSong.title}' by '${mostPlayedSong.artist}'")
                        } else {
                            // Fallback to first song if no stats found
                            representativeSongs[artistStat.artistName] = artistSongs.first()
                            android.util.Log.d("ArtistStatsAdapter", "Fallback song for '${artistStat.artistName}': '${artistSongs.first().title}' by '${artistSongs.first().artist}'")
                        }
                    } else {
                        android.util.Log.w("ArtistStatsAdapter", "No songs found for artist '${artistStat.artistName}'")
                    }
                }
                
                artistRepresentativeSongs = representativeSongs
                android.util.Log.d("ArtistStatsAdapter", "Loaded representative songs for ${representativeSongs.size} artists out of ${artistStats.size} stats")
                notifyDataSetChanged() // Refresh UI with loaded metadata
            } catch (e: Exception) {
                android.util.Log.e("ArtistStatsAdapter", "Error loading representative songs: ${e.message}", e)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistStatsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_artist_stats, parent, false)
        return ArtistStatsViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ArtistStatsViewHolder, position: Int) {
        val stats = artistStats[position]
        val representativeSong = artistRepresentativeSongs[stats.artistName]
        holder.bind(stats, representativeSong)
    }
    
    override fun getItemCount(): Int = artistStats.size
    
    class ArtistStatsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val artistArtwork: ImageView = itemView.findViewById(R.id.artist_artwork)
        private val artistName: TextView = itemView.findViewById(R.id.artist_name)
        private val listeningTime: TextView = itemView.findViewById(R.id.listening_time)
        private val playCount: TextView = itemView.findViewById(R.id.play_count)
        
        fun bind(stats: ArtistStats, representativeSong: Song?) {
            artistName.text = stats.artistName
            listeningTime.text = formatMinutes(stats.totalMinutes)
            playCount.text = formatPlays(stats.playCount)
            
            // Load artist artwork from representative song
            if (representativeSong != null) {
                loadArtistArtwork(representativeSong)
            } else {
                artistArtwork.setImageResource(R.drawable.ic_music_note)
            }
        }
        
        private fun loadArtistArtwork(song: Song) {
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
                                .into(artistArtwork)
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
                                    .into(artistArtwork)
                            } else {
                                artistArtwork.setImageResource(R.drawable.ic_music_note)
                            }
                        }
                    } else {
                        // Empty path - use placeholder
                        artistArtwork.setImageResource(R.drawable.ic_music_note)
                    }
                } catch (e: Exception) {
                    // Error loading artwork - use placeholder
                    artistArtwork.setImageResource(R.drawable.ic_music_note)
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