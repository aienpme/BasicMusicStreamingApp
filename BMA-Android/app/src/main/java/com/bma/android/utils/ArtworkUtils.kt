package com.bma.android.utils

import android.content.Context
import com.bma.android.api.ApiClient
import com.bma.android.models.Song
import com.bma.android.storage.DownloadManager
import com.bma.android.storage.OfflineModeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utility class for handling artwork URLs in both online and offline modes.
 * Provides the correct artwork path/URL based on current app state.
 */
object ArtworkUtils {
    
    /**
     * Get the appropriate artwork URL or file path for a song from both local and server sources
     * @param context Application context
     * @param song Song object
     * @return String containing either local file path or server URL
     */
    suspend fun getArtworkPath(context: Context, song: Song): String {
        return withContext(Dispatchers.IO) {
            // FIXED: Always check for local artwork first, regardless of current mode
            // This ensures songs tracked in offline mode can display artwork when back online
            
            val downloadManager = DownloadManager.getInstance(context)
            val artworkFile = downloadManager.getArtworkFile(song)
            
            // Check if artwork file exists locally
            if (artworkFile.exists() && artworkFile.length() > 0) {
                android.util.Log.d("ArtworkUtils", "Using local artwork for song: ${song.title}")
                return@withContext "file://${artworkFile.absolutePath}"
            }
            
            // If no local artwork available, check if we should use server URL
            if (!OfflineModeManager.isOfflineMode()) {
                // Online mode: use server URL as fallback
                android.util.Log.d("ArtworkUtils", "Using server artwork for song: ${song.title}")
                return@withContext "${ApiClient.getServerUrl()}/artwork/${song.id}"
            } else {
                // Offline mode: no server access allowed, use empty string for fallback drawable
                android.util.Log.d("ArtworkUtils", "No local artwork available in offline mode for song: ${song.title}")
                return@withContext ""
            }
        }
    }
    
    /**
     * Check if local artwork exists for a song
     * @param context Application context
     * @param song Song object
     * @return Boolean indicating if local artwork file exists
     */
    suspend fun hasLocalArtwork(context: Context, song: Song): Boolean {
        return withContext(Dispatchers.IO) {
            val downloadManager = DownloadManager.getInstance(context)
            val artworkFile = downloadManager.getArtworkFile(song)
            artworkFile.exists() && artworkFile.length() > 0
        }
    }
}