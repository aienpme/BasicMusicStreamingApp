package com.bma.android.utils

import android.content.Context
import android.util.Log
import com.bma.android.models.Song
import com.bma.android.storage.DownloadManager
import com.bma.android.storage.PlaylistManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Diagnostic utility for troubleshooting download and offline playback issues
 */
object DownloadDiagnostics {
    
    /**
     * Comprehensive diagnostic check for a song's download status
     */
    suspend fun diagnoseSong(context: Context, song: Song): String {
        return withContext(Dispatchers.IO) {
            val report = StringBuilder()
            report.appendLine("=== DOWNLOAD DIAGNOSTIC FOR: ${song.title} ===")
            report.appendLine("Song ID: ${song.id}")
            report.appendLine("Artist: ${song.artist}")
            report.appendLine("Album: ${song.album}")
            report.appendLine()
            
            try {
                val downloadManager = DownloadManager.getInstance(context)
                val playlistManager = PlaylistManager.getInstance(context)
                
                // Check DownloadManager status
                val isDownloadedDM = downloadManager.isDownloaded(song.id)
                report.appendLine("DownloadManager.isDownloaded(): $isDownloadedDM")
                
                // Check PlaylistManager status  
                val isDownloadedPM = playlistManager.isSongDownloaded(song.id)
                report.appendLine("PlaylistManager.isSongDownloaded(): $isDownloadedPM")
                
                // Check expected file paths
                val expectedAudioFile = downloadManager.getDownloadFile(song)
                val expectedArtworkFile = downloadManager.getArtworkFile(song)
                
                report.appendLine()
                report.appendLine("Expected audio file: ${expectedAudioFile.absolutePath}")
                report.appendLine("Audio file exists: ${expectedAudioFile.exists()}")
                if (expectedAudioFile.exists()) {
                    report.appendLine("Audio file size: ${expectedAudioFile.length()} bytes")
                }
                
                report.appendLine()
                report.appendLine("Expected artwork file: ${expectedArtworkFile.absolutePath}")
                report.appendLine("Artwork file exists: ${expectedArtworkFile.exists()}")
                if (expectedArtworkFile.exists()) {
                    report.appendLine("Artwork file size: ${expectedArtworkFile.length()} bytes")
                }
                
                // Check actual downloaded file from DownloadManager
                val downloadedFile = downloadManager.getDownloadedFile(song.id)
                report.appendLine()
                if (downloadedFile != null) {
                    report.appendLine("DownloadManager.getDownloadedFile() path: ${downloadedFile.absolutePath}")
                    report.appendLine("Downloaded file exists: ${downloadedFile.exists()}")
                    if (downloadedFile.exists()) {
                        report.appendLine("Downloaded file size: ${downloadedFile.length()} bytes")
                    }
                    
                    // Check if paths match
                    val pathsMatch = downloadedFile.absolutePath == expectedAudioFile.absolutePath
                    report.appendLine("Paths match: $pathsMatch")
                } else {
                    report.appendLine("DownloadManager.getDownloadedFile() returned: null")
                }
                
                // Check download directory structure
                report.appendLine()
                report.appendLine("=== DIRECTORY STRUCTURE ===")
                val downloadDir = expectedAudioFile.parentFile
                if (downloadDir?.exists() == true) {
                    report.appendLine("Download directory exists: ${downloadDir.absolutePath}")
                    val files = downloadDir.listFiles()
                    if (files != null && files.isNotEmpty()) {
                        report.appendLine("Files in directory:")
                        files.forEach { file ->
                            report.appendLine("  - ${file.name} (${file.length()} bytes)")
                        }
                    } else {
                        report.appendLine("Directory is empty")
                    }
                } else {
                    report.appendLine("Download directory does not exist: ${downloadDir?.absolutePath}")
                }
                
            } catch (e: Exception) {
                report.appendLine("ERROR during diagnosis: ${e.message}")
                Log.e("DownloadDiagnostics", "Error diagnosing song: ${song.title}", e)
            }
            
            report.appendLine("=== END DIAGNOSTIC ===")
            return@withContext report.toString()
        }
    }
    
    /**
     * List all downloaded songs with their file status
     */
    suspend fun listAllDownloads(context: Context): String {
        return withContext(Dispatchers.IO) {
            val report = StringBuilder()
            report.appendLine("=== ALL DOWNLOADS STATUS ===")
            
            try {
                val playlistManager = PlaylistManager.getInstance(context)
                val downloadManager = DownloadManager.getInstance(context)
                
                // Get all songs and check download status
                val allSongs = playlistManager.getAllSongs()
                report.appendLine("Total songs in library: ${allSongs.size}")
                
                val downloadedSongs = allSongs.filter { song ->
                    playlistManager.isSongDownloaded(song.id)
                }
                report.appendLine("Songs marked as downloaded: ${downloadedSongs.size}")
                
                if (downloadedSongs.isNotEmpty()) {
                    report.appendLine()
                    downloadedSongs.forEach { song ->
                        val file = downloadManager.getDownloadedFile(song.id)
                        val exists = file?.exists() ?: false
                        val size = if (exists) file?.length() ?: 0 else 0
                        report.appendLine("- ${song.title} | File exists: $exists | Size: $size bytes")
                    }
                } else {
                    report.appendLine("No songs marked as downloaded")
                }
                
            } catch (e: Exception) {
                report.appendLine("ERROR: ${e.message}")
                Log.e("DownloadDiagnostics", "Error listing downloads", e)
            }
            
            return@withContext report.toString()
        }
    }
}