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
     * Get the appropriate artwork URL or file path for a song based on current mode
     * @param context Application context
     * @param song Song object
     * @return String containing either server URL or local file path
     */
    suspend fun getArtworkPath(context: Context, song: Song): String {
        return withContext(Dispatchers.IO) {
            // Check if we're in offline mode
            if (OfflineModeManager.isOfflineMode()) {
                // Try to get local artwork file
                val downloadManager = DownloadManager.getInstance(context)
                val artworkFile = downloadManager.getArtworkFile(song)
                
                // Check if artwork file exists locally
                if (artworkFile.exists() && artworkFile.length() > 0) {
                    return@withContext "file://${artworkFile.absolutePath}"
                }
                
                // Fallback: if no local artwork but song is downloaded, 
                // still try to avoid server calls in offline mode
                if (downloadManager.isDownloaded(song.id)) {
                    // Return empty string to trigger fallback drawable in Glide
                    return@withContext ""
                }
            }
            
            // Default: return server URL for online mode
            return@withContext "${ApiClient.getServerUrl()}/artwork/${song.id}"
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