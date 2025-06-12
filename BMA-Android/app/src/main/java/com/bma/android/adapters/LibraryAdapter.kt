package com.bma.android.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ItemAlbumHeaderBinding
import com.bma.android.databinding.ItemSectionHeaderBinding
import com.bma.android.databinding.ItemSongBinding
import com.bma.android.databinding.ItemPlaylistBinding
import com.bma.android.databinding.ItemCreatePlaylistBinding
import com.bma.android.models.Album
import com.bma.android.models.LibraryContent
import com.bma.android.models.Song
import com.bma.android.models.Playlist
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class LibraryAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onAlbumClick: (Album) -> Unit,
    private val onSongLongClick: (Song) -> Unit,
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onPlaylistMenu: (Playlist) -> Unit,
    private val onCreatePlaylistClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION_HEADER = 0
        private const val TYPE_PLAYLIST = 1
        private const val TYPE_CREATE_PLAYLIST = 2
        private const val TYPE_ALBUM = 3
        private const val TYPE_SONG = 4
    }

    private var items = listOf<LibraryItem>()
    private var playlistsExpanded = true
    private var albumsExpanded = true
    private var songsExpanded = false
    private var currentLibraryContent: LibraryContent? = null

    sealed class LibraryItem {
        data class SectionHeader(val title: String, val isExpanded: Boolean) : LibraryItem()
        data class PlaylistItem(val playlist: Playlist) : LibraryItem()
        object CreatePlaylistItem : LibraryItem()
        data class AlbumItem(val album: Album) : LibraryItem()
        data class SongItem(val song: Song) : LibraryItem()
    }

    fun updateContent(libraryContent: LibraryContent) {
        currentLibraryContent = libraryContent
        val newItems = mutableListOf<LibraryItem>()
        
        // Add Playlists section (always show, even if empty, to allow creating new playlists)
        newItems.add(LibraryItem.SectionHeader("Playlists", playlistsExpanded))
        if (playlistsExpanded) {
            // Always show "Create new playlist" option first
            newItems.add(LibraryItem.CreatePlaylistItem)
            
            // Add existing playlists
            libraryContent.playlists.forEach { playlist ->
                newItems.add(LibraryItem.PlaylistItem(playlist))
            }
        }
        
        // Add Albums section
        if (libraryContent.hasAlbums) {
            newItems.add(LibraryItem.SectionHeader("Albums", albumsExpanded))
            if (albumsExpanded) {
                libraryContent.albums.forEach { album ->
                    newItems.add(LibraryItem.AlbumItem(album))
                }
            }
        }
        
        // Add Songs section
        if (libraryContent.hasStandaloneSongs) {
            newItems.add(LibraryItem.SectionHeader("Songs", songsExpanded))
            if (songsExpanded) {
                libraryContent.standaloneSongs.forEach { song ->
                    newItems.add(LibraryItem.SongItem(song))
                }
            }
        }
        
        items = newItems
        notifyDataSetChanged()
    }
    
    private fun getCurrentLibraryContent(): LibraryContent {
        return currentLibraryContent ?: LibraryContent(emptyList(), emptyList(), emptyList())
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is LibraryItem.SectionHeader -> TYPE_SECTION_HEADER
            is LibraryItem.PlaylistItem -> TYPE_PLAYLIST
            LibraryItem.CreatePlaylistItem -> TYPE_CREATE_PLAYLIST
            is LibraryItem.AlbumItem -> TYPE_ALBUM
            is LibraryItem.SongItem -> TYPE_SONG
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SECTION_HEADER -> {
                val binding = ItemSectionHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SectionHeaderViewHolder(binding)
            }
            TYPE_PLAYLIST -> {
                val binding = ItemPlaylistBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                PlaylistViewHolder(binding)
            }
            TYPE_CREATE_PLAYLIST -> {
                val binding = ItemCreatePlaylistBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                CreatePlaylistViewHolder(binding)
            }
            TYPE_ALBUM -> {
                val binding = ItemAlbumHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AlbumViewHolder(binding)
            }
            TYPE_SONG -> {
                val binding = ItemSongBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SongViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LibraryItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item.title, item.isExpanded)
            is LibraryItem.PlaylistItem -> (holder as PlaylistViewHolder).bind(item.playlist)
            LibraryItem.CreatePlaylistItem -> (holder as CreatePlaylistViewHolder).bind()
            is LibraryItem.AlbumItem -> (holder as AlbumViewHolder).bind(item.album)
            is LibraryItem.SongItem -> (holder as SongViewHolder).bind(item.song)
        }
    }

    override fun getItemCount() = items.size

    inner class SectionHeaderViewHolder(
        private val binding: ItemSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(title: String, isExpanded: Boolean) {
            binding.sectionTitle.text = title
            
            // Update expand/collapse icon
            val iconRes = if (isExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right
            binding.expandCollapseIcon.setImageResource(iconRes)
            
            // Handle click to toggle section
            binding.root.setOnClickListener {
                when (title) {
                    "Playlists" -> {
                        playlistsExpanded = !playlistsExpanded
                        updateContent(getCurrentLibraryContent())
                    }
                    "Albums" -> {
                        albumsExpanded = !albumsExpanded
                        updateContent(getCurrentLibraryContent())
                    }
                    "Songs" -> {
                        songsExpanded = !songsExpanded
                        updateContent(getCurrentLibraryContent())
                    }
                }
            }
        }
    }

    inner class AlbumViewHolder(
        private val binding: ItemAlbumHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(album: Album) {
            binding.albumNameText.text = album.name
            binding.artistText.text = album.artist ?: "Unknown Artist"
            binding.trackCountText.text = "• ${album.trackCount} tracks"
            
            // Show right chevron to indicate navigation
            binding.expandIcon.setImageResource(R.drawable.ic_chevron_right)
            
            // Load album artwork from the first song in the album
            loadAlbumArtwork(album)
            
            binding.root.setOnClickListener {
                onAlbumClick(album)
            }
        }
        
        private fun loadAlbumArtwork(album: Album) {
            if (album.songs.isNotEmpty()) {
                val firstSong = album.songs.first()
                val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${firstSong.id}"
                
                val authHeader = ApiClient.getAuthHeader()
                
                if (authHeader != null) {
                    val glideUrl = GlideUrl(
                        artworkUrl, 
                        LazyHeaders.Builder()
                            .addHeader("Authorization", authHeader)
                            .build()
                    )
                    
                    Glide.with(binding.root.context)
                        .load(glideUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_folder)
                        .error(R.drawable.ic_folder)
                        .into(binding.albumArtwork)
                } else {
                    binding.albumArtwork.setImageResource(R.drawable.ic_folder)
                }
            } else {
                binding.albumArtwork.setImageResource(R.drawable.ic_folder)
            }
        }
    }

    inner class PlaylistViewHolder(
        private val binding: ItemPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.playlistNameText.text = playlist.name
            binding.songCountText.text = "${playlist.songCount} songs"
            
            // Load playlist artwork (composite or placeholder)
            loadPlaylistArtwork(playlist)
            
            // Handle playlist click (navigate to playlist detail)
            binding.root.setOnClickListener {
                onPlaylistClick(playlist)
            }
            
            // Play button removed from layout
            
            // Handle menu button click (future: delete, rename, etc.)
            binding.playlistMenuButton.setOnClickListener {
                onPlaylistMenu(playlist)
            }
        }
        
        private fun loadPlaylistArtwork(playlist: Playlist) {
            val allSongs = currentLibraryContent?.let { content ->
                content.albums.flatMap { it.songs } + content.standaloneSongs
            } ?: emptyList()
            val songs = playlist.getSongs(allSongs)
            
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
            val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
            val authHeader = ApiClient.getAuthHeader()
            
            if (authHeader != null) {
                val glideUrl = GlideUrl(
                    artworkUrl, 
                    LazyHeaders.Builder()
                        .addHeader("Authorization", authHeader)
                        .build()
                )
                
                Glide.with(binding.root.context)
                    .load(glideUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_queue_music)
                    .error(R.drawable.ic_queue_music)
                    .into(binding.playlistArtwork)
            } else {
                binding.playlistArtwork.setImageResource(R.drawable.ic_queue_music)
            }
        }
        
        private fun createCompositeArtwork(songs: List<Song>) {
            // For now, use the first song's artwork as a placeholder
            // TODO: Implement proper 2x2 composite artwork generation
            if (songs.isNotEmpty()) {
                loadSingleArtwork(songs[0])
            } else {
                binding.playlistArtwork.setImageResource(R.drawable.ic_queue_music)
            }
        }
    }

    inner class CreatePlaylistViewHolder(
        private val binding: ItemCreatePlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.root.setOnClickListener {
                onCreatePlaylistClick()
            }
        }
    }

    inner class SongViewHolder(
        private val binding: ItemSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.titleText.text = song.title
            binding.artistText.text = song.artist.ifEmpty { "Unknown Artist" }
            
            // Load song artwork
            loadSongArtwork(song)
            
            binding.root.setOnClickListener {
                onSongClick(song)
            }
            
            binding.root.setOnLongClickListener {
                onSongLongClick(song)
                true
            }
        }
        
        private fun loadSongArtwork(song: Song) {
            val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
            val authHeader = ApiClient.getAuthHeader()
            
            if (authHeader != null) {
                val glideUrl = GlideUrl(
                    artworkUrl, 
                    LazyHeaders.Builder()
                        .addHeader("Authorization", authHeader)
                        .build()
                )
                
                Glide.with(binding.root.context)
                    .load(glideUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .into(binding.albumArtwork)
            } else {
                binding.albumArtwork.setImageResource(R.drawable.ic_music_note)
            }
        }
    }
}