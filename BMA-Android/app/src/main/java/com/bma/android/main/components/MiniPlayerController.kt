package com.bma.android.main.components

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.bma.android.MusicService
import com.bma.android.PlayerActivity
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.MiniPlayerBinding
import com.bma.android.models.Song
import com.bma.android.utils.ArtworkUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import kotlinx.coroutines.launch

/**
 * Controls the mini player UI component.
 * Handles updating song info, playback controls, and artwork loading.
 */
class MiniPlayerController(
    private val context: Context,
    private val binding: MiniPlayerBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val callback: MiniPlayerCallback
) {
    
    interface MiniPlayerCallback {
        fun getMusicService(): MusicService?
    }
    
    fun setup() {
        // Click mini-player to open PlayerActivity
        binding.root.setOnClickListener {
            callback.getMusicService()?.getCurrentSong()?.let {
                val intent = Intent(context, PlayerActivity::class.java)
                context.startActivity(intent)
            }
        }
        
        // Mini-player controls
        binding.miniPlayerPlayPause.setOnClickListener {
            callback.getMusicService()?.let { service ->
                if (service.isPlaying()) {
                    service.pause()
                } else {
                    service.play()
                }
            }
        }
        
        binding.miniPlayerNext.setOnClickListener {
            callback.getMusicService()?.skipToNext()
        }
        
        binding.miniPlayerPrevious.setOnClickListener {
            callback.getMusicService()?.skipToPrevious()
        }
    }
    
    fun update() {
        Log.d("MiniPlayerController", "update() called")
        callback.getMusicService()?.let { service ->
            val currentSong = service.getCurrentSong()
            val isPlaying = service.isPlaying()
            
            Log.d("MiniPlayerController", "Current song: ${currentSong?.title}, isPlaying: $isPlaying")
            
            if (currentSong != null) {
                // Show mini-player
                Log.d("MiniPlayerController", "Showing mini player")
                binding.root.isVisible = true
                
                // Update song info
                binding.miniPlayerTitle.text = currentSong.title
                binding.miniPlayerArtist.text = currentSong.artist.ifEmpty { "Unknown Artist" }
                
                // Update play/pause button
                val playPauseIcon = if (isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
                binding.miniPlayerPlayPause.setImageResource(playPauseIcon)
                
                // Load album artwork
                loadArtwork(currentSong)
                
                // Update progress
                val currentPos = service.getCurrentPosition()
                val duration = service.getDuration()
                val progress = if (duration > 0) {
                    (currentPos * 100) / duration
                } else 0
                binding.miniPlayerProgress.progress = progress
                
            } else {
                // Hide mini-player
                Log.d("MiniPlayerController", "Hiding mini player - no current song")
                binding.root.isVisible = false
            }
        } ?: run {
            // Hide mini-player when no service
            Log.d("MiniPlayerController", "Hiding mini player - no service")
            binding.root.isVisible = false
        }
    }
    
    fun updateProgress(progress: Int, duration: Int) {
        if (duration > 0) {
            val progressPercent = (progress * 100) / duration
            binding.miniPlayerProgress.progress = progressPercent
        }
    }
    
    fun hide() {
        binding.root.isVisible = false
    }
    
    private fun loadArtwork(song: Song) {
        lifecycleScope.launch {
            try {
                val artworkPath = ArtworkUtils.getArtworkPath(context, song)
                
                // Handle different path types
                when {
                    artworkPath.startsWith("file://") -> {
                        // Local file - load directly
                        Glide.with(context)
                            .load(artworkPath)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.ic_music_note)
                            .error(R.drawable.ic_music_note)
                            .into(binding.miniPlayerArtwork)
                    }
                    artworkPath.isBlank() -> {
                        // No artwork available - use fallback
                        binding.miniPlayerArtwork.setImageResource(R.drawable.ic_music_note)
                    }
                    else -> {
                        // Server URL - load with auth headers
                        val authHeader = ApiClient.getAuthHeader()
                        if (authHeader != null) {
                            val glideUrl = GlideUrl(
                                artworkPath, 
                                LazyHeaders.Builder()
                                    .addHeader("Authorization", authHeader)
                                    .build()
                            )
                            
                            Glide.with(context)
                                .load(glideUrl)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .placeholder(R.drawable.ic_music_note)
                                .error(R.drawable.ic_music_note)
                                .into(binding.miniPlayerArtwork)
                        } else {
                            binding.miniPlayerArtwork.setImageResource(R.drawable.ic_music_note)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MiniPlayerController", "Error loading mini player artwork: ${e.message}", e)
                binding.miniPlayerArtwork.setImageResource(R.drawable.ic_music_note)
            }
        }
    }
} 