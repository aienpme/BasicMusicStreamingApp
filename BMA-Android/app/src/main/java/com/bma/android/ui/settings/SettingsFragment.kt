package com.bma.android.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bma.android.QRScannerActivity
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.FragmentSettingsBinding
import com.bma.android.storage.PlaylistManager
import com.bma.android.storage.OfflineModeManager
import com.bma.android.MainActivity
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings), MainActivity.OfflineModeAware {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    // Offline mode state
    private var isOfflineMode = false

    private lateinit var qrScannerLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var createBackupFileLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var selectRestoreFileLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register ActivityResultLaunchers in onCreate to ensure they're available
        qrScannerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // After returning from QR scanner, update the status
            updateConnectionStatus()
        }

        createBackupFileLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let { 
                performBackup(it)
            }
        }

        selectRestoreFileLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { 
                performRestore(it)
            }
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        // Check current offline mode state
        isOfflineMode = OfflineModeManager.isOfflineMode()

        setupClickListeners()
        setupComingSoonFeatures()
        setupOfflineModeControls()
    }
    
    override fun onOfflineModeChanged(isOffline: Boolean) {
        isOfflineMode = isOffline
        
        // Only update UI if view is available
        if (_binding != null) {
            // Update UI to reflect new offline mode state
            updateConnectionStatus()
        } else {
            android.util.Log.d("SettingsFragment", "Offline mode changed but view not available yet")
        }
    }

    override fun onResume() {
        super.onResume()
        // Update status every time the fragment is shown
        updateConnectionStatus()
        updateStreamingStats()
    }

    private fun setupClickListeners() {
        // Connection Settings
        binding.disconnectButton.setOnClickListener {
            disconnectFromServer()
        }
        binding.reconnectButton.setOnClickListener {
            qrScannerLauncher.launch(Intent(requireContext(), QRScannerActivity::class.java))
        }
        
        // DEBUG: Add temporary debug feature (long click on clear cache button)
        binding.clearCacheButton.setOnLongClickListener {
            showDownloadStatsDebug()
            true
        }
    }

    private fun setupComingSoonFeatures() {
        // Backup & Restore features
        binding.backupButton.setOnClickListener {
            createBackup()
        }
        binding.restoreButton.setOnClickListener {
            restoreBackup()
        }

        // Download Settings features - now enabled
        binding.downloadQualityButton.setOnClickListener {
            openDownloadSelection()
        }
        binding.clearCacheButton.setOnClickListener {
            clearCache()
        }
    }
    
    private fun setupOfflineModeControls() {
        // For now, we'll add offline mode controls programmatically
        // In a real implementation, you'd add these to the layout XML
        
        // Since we don't have specific offline mode UI elements in the layout,
        // we'll handle offline mode through the connection status display
        // and potentially add a toggle button dynamically if needed
    }
    
    private fun openDownloadSelection() {
        val intent = android.content.Intent(requireContext(), com.bma.android.ui.downloads.DownloadSelectionActivity::class.java)
        startActivity(intent)
    }
    
    private fun clearCache() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear Cache")
            .setMessage("This will clear all cached music files. Downloaded files will not be affected.\n\nAre you sure?")
            .setPositiveButton("Clear Cache") { _, _ ->
                executeClearCache()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun executeClearCache() {
        lifecycleScope.launch {
            try {
                val cacheManager = com.bma.android.storage.CacheManager.getInstance(requireContext())
                cacheManager.clearCache()
                
                android.util.Log.d("SettingsFragment", "Cache cleared successfully")
                Toast.makeText(requireContext(), "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error clearing cache: ${e.message}", e)
                Toast.makeText(requireContext(), "Error clearing cache", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateConnectionStatus() {
        // Check if binding is available before updating UI
        if (_binding == null) return
        
        val isAuthenticated = ApiClient.isAuthenticated()
        val serverUrl = ApiClient.getServerUrl()

        if (isOfflineMode) {
            // Offline mode is active
            binding.connectionStatusText.text = "🔸 Offline Mode Active\nShowing downloaded content only"
            binding.connectionStatusText.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
            binding.disconnectButton.isVisible = false
            binding.reconnectButton.text = "Exit Offline Mode"
            binding.reconnectButton.isVisible = true
            binding.reconnectButton.setOnClickListener {
                exitOfflineMode()
            }
        } else if (isAuthenticated && serverUrl.isNotEmpty()) {
            val protocol = when {
                serverUrl.contains(".ts.net") -> "Tailscale"
                serverUrl.startsWith("https://") -> "HTTPS"
                else -> "HTTP"
            }
            binding.connectionStatusText.text = "✅ Connected via $protocol to:\n$serverUrl"
            binding.connectionStatusText.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            binding.disconnectButton.isVisible = true
            binding.reconnectButton.text = "Reconnect"
            binding.reconnectButton.isVisible = false
            binding.reconnectButton.setOnClickListener {
                qrScannerLauncher.launch(Intent(requireContext(), QRScannerActivity::class.java))
            }
        } else {
            binding.connectionStatusText.text = "❌ Not connected"
            binding.connectionStatusText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            binding.disconnectButton.isVisible = false
            binding.reconnectButton.text = "Reconnect"
            binding.reconnectButton.isVisible = true
            binding.reconnectButton.setOnClickListener {
                qrScannerLauncher.launch(Intent(requireContext(), QRScannerActivity::class.java))
            }
        }
    }
    
    private fun exitOfflineMode() {
        lifecycleScope.launch {
            try {
                // Check if server is now reachable
                if (OfflineModeManager.canExitOfflineMode(requireContext())) {
                    OfflineModeManager.disableOfflineMode()
                    Toast.makeText(requireContext(), "Exited offline mode", Toast.LENGTH_SHORT).show()
                    
                    // Restart MainActivity to switch back to online mode
                    val intent = Intent(requireContext(), com.bma.android.MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } else {
                    // Server still not reachable
                    AlertDialog.Builder(requireContext())
                        .setTitle("Cannot Exit Offline Mode")
                        .setMessage("Server is still unreachable. Please check your connection and try again.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error checking server connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStreamingStats() {
        // Check if binding is available before updating UI
        if (_binding == null) return
        
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                val streamingStats = playlistManager.getStreamingStats()
                val totalMinutes = streamingStats.totalMinutesListened
                
                // Format the minutes display
                val formattedTime = when {
                    totalMinutes == 0L -> "0 minutes"
                    totalMinutes < 60L -> "$totalMinutes minutes"
                    totalMinutes < 1440L -> { // Less than 24 hours
                        val hours = totalMinutes / 60L
                        val remainingMinutes = totalMinutes % 60L
                        when {
                            remainingMinutes == 0L -> "$hours hours"
                            hours == 1L -> "1 hour, $remainingMinutes minutes"
                            else -> "$hours hours, $remainingMinutes minutes"
                        }
                    }
                    else -> { // 24 hours or more
                        val days = totalMinutes / 1440L
                        val remainingHours = (totalMinutes % 1440L) / 60L
                        val remainingMinutes = totalMinutes % 60L
                        
                        buildString {
                            if (days == 1L) append("1 day") else append("$days days")
                            if (remainingHours > 0L) {
                                append(", ")
                                if (remainingHours == 1L) append("1 hour") else append("$remainingHours hours")
                            }
                            if (remainingMinutes > 0L) {
                                append(", $remainingMinutes minutes")
                            }
                        }
                    }
                }
                
                binding.minutesListenedValue.text = formattedTime
                
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error loading streaming stats: ${e.message}", e)
                binding.minutesListenedValue.text = "Error loading stats"
            }
        }
    }
    

    private fun disconnectFromServer() {
        lifecycleScope.launch {
            var serverNotified = false
            if (ApiClient.isAuthenticated()) {
                try {
                    ApiClient.getAuthHeader()?.let {
                        ApiClient.api.disconnect(it)
                        serverNotified = true
                    }
                } catch (e: Exception) {
                    // Ignore, we are disconnecting anyway
                }
            }

            // Clear local credentials
            ApiClient.setAuthToken(null)
            requireActivity().getSharedPreferences("BMA", Context.MODE_PRIVATE).edit()
                .remove("auth_token")
                .apply()
            
            val message = if (serverNotified) "Disconnected from server" else "Disconnected locally"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            updateConnectionStatus()
        }
    }


    private fun showStorageInformation() {
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                val downloadManager = com.bma.android.storage.DownloadManager.getInstance(requireContext())
                val downloadStats = playlistManager.getDownloadStats()
                val downloadManagerStats = downloadManager.getDownloadStats()
                
                // Get storage paths
                val downloadDir = requireContext().getExternalFilesDir(null)
                val downloadPath = downloadDir?.let { "${it.absolutePath}/BMA_Downloads" } ?: "External storage unavailable"
                
                // Calculate storage usage
                val totalDownloadedMB = downloadStats.totalDownloadSize / 1024 / 1024
                val availableSpaceGB = downloadDir?.freeSpace?.let { it / 1024 / 1024 / 1024 } ?: 0
                
                val storageInfo = """
                    📁 STORAGE INFORMATION
                    
                    📦 Downloads:
                    • Downloaded songs: ${downloadStats.downloadedSongs.size}
                    • Total files: ${downloadStats.totalDownloadedFiles}
                    • Storage used: ${totalDownloadedMB} MB
                    
                    💾 Storage Location:
                    • Path: ${downloadPath}
                    • Available space: ${availableSpaceGB} GB
                    
                    📊 Download Manager:
                    • Total downloads: ${downloadManagerStats.totalDownloads}
                    • Storage used: ${downloadManagerStats.storageUsed}
                    • Available space: ${downloadManagerStats.availableSpace}
                """.trimIndent()
                
                AlertDialog.Builder(requireContext())
                    .setTitle("Storage Information")
                    .setMessage(storageInfo)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Clear Downloads") { _, _ ->
                        showClearDownloadsConfirmation()
                    }
                    .show()
                    
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error getting storage info: ${e.message}", e)
                Toast.makeText(requireContext(), "Error loading storage information", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showClearDownloadsConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear All Downloads")
            .setMessage("This will delete all downloaded music files from your device. This action cannot be undone.\n\nAre you sure?")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllDownloads()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearAllDownloads() {
        lifecycleScope.launch {
            try {
                val downloadManager = com.bma.android.storage.DownloadManager.getInstance(requireContext())
                val playlistManager = PlaylistManager.getInstance(requireContext())
                
                // Clear all downloads
                downloadManager.clearAllDownloads()
                playlistManager.clearAllDownloadStatus()
                
                Toast.makeText(requireContext(), "All downloads cleared successfully", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error clearing downloads: ${e.message}", e)
                Toast.makeText(requireContext(), "Error clearing downloads", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDownloadStatsDebug() {
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                val downloadStats = playlistManager.getDownloadStats()
                val offlineSongs = playlistManager.getAllSongsOffline()
                val offlineAlbums = playlistManager.getAllAlbumsOffline()
                
                val debugInfo = """
                    📊 DOWNLOAD DEBUG INFO 📊
                    
                    📦 Download Stats:
                    • Downloaded songs: ${downloadStats.downloadedSongs.size}
                    • Song IDs: ${downloadStats.downloadedSongs.joinToString(", ")
                        .take(100)}${if (downloadStats.downloadedSongs.size > 5) "..." else ""}
                    • Total files: ${downloadStats.totalDownloadedFiles}
                    • Total size: ${downloadStats.totalDownloadSize / 1024 / 1024} MB
                    
                    🎵 Offline Songs Found: ${offlineSongs.size}
                    ${offlineSongs.take(3).joinToString("\n") { "• ${it.title} (${it.id})" }}
                    
                    💿 Offline Albums Found: ${offlineAlbums.size}
                    ${offlineAlbums.take(3).joinToString("\n") { "• ${it.name} (${it.songs.size} songs)" }}
                    
                    🔸 Offline Mode: ${if (isOfflineMode) "ENABLED" else "DISABLED"}
                """.trimIndent()
                
                AlertDialog.Builder(requireContext())
                    .setTitle("Debug: Download Stats")
                    .setMessage(debugInfo)
                    .setPositiveButton("OK", null)
                    .show()
                    
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Debug error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun createBackup() {
        lifecycleScope.launch {
            val playlistManager = PlaylistManager.getInstance(requireContext())
            val filename = playlistManager.generateBackupFilename()
            createBackupFileLauncher.launch(filename)
        }
    }

    private fun restoreBackup() {
        selectRestoreFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    private fun performBackup(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                playlistManager.exportBackup(uri)
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error creating backup: ${e.message}", e)
            }
        }
    }

    private fun performRestore(uri: android.net.Uri) {
        // Show dialog to ask if user wants to merge or replace
        AlertDialog.Builder(requireContext())
            .setTitle("Restore Playlists")
            .setMessage("How would you like to restore your playlists?")
            .setPositiveButton("Merge with existing") { _, _ ->
                executeRestore(uri, mergeWithExisting = true)
            }
            .setNegativeButton("Replace all") { _, _ ->
                showReplaceConfirmation(uri)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showReplaceConfirmation(uri: android.net.Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle("Replace All Playlists")
            .setMessage("This will delete all your current playlists and replace them with the backup. This action cannot be undone.\n\nAre you sure you want to continue?")
            .setPositiveButton("Yes, Replace All") { _, _ ->
                executeRestore(uri, mergeWithExisting = false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeRestore(uri: android.net.Uri, mergeWithExisting: Boolean) {
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                val result = playlistManager.importBackup(uri, mergeWithExisting)
                
                when (result) {
                    is PlaylistManager.ImportResult.Success -> {
                        android.util.Log.d("SettingsFragment", "Restore successful: ${result.message}")
                    }
                    is PlaylistManager.ImportResult.Error -> {
                        android.util.Log.e("SettingsFragment", "Restore failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error restoring backup: ${e.message}", e)
            }
        }
    }
}