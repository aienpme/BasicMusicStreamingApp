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
import com.bma.android.ui.stats.StreamingStatsActivity
import com.bma.android.main.components.NetworkDiagnostics
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class SettingsFragment : Fragment(R.layout.fragment_settings), MainActivity.OfflineModeAware {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    // Offline mode state
    private var isOfflineMode = false
    
    // Track if QR scanner was launched from offline mode exit context
    private var isExitingOfflineMode = false

    private lateinit var qrScannerLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var createBackupFileLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var selectRestoreFileLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register ActivityResultLaunchers in onCreate to ensure they're available
        qrScannerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // After returning from QR scanner, update the status
            updateConnectionStatus()
            
            // If we were in the process of exiting offline mode, check if we can now complete it
            if (isExitingOfflineMode) {
                isExitingOfflineMode = false // Reset the flag
                
                // Re-check connection status after QR authentication
                lifecycleScope.launch {
                    try {
                        when (OfflineModeManager.getExitOfflineModeStatus(requireContext())) {
                            com.bma.android.api.ApiClient.ConnectionStatus.CONNECTED -> {
                                // Successfully authenticated - exit offline mode automatically
                                OfflineModeManager.disableOfflineMode()
                                
                                // Restart MainActivity to switch back to online mode
                                val intent = Intent(requireContext(), com.bma.android.MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }
                            else -> {
                                // Authentication failed or other issue - user can try again
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SettingsFragment", "Error checking connection after QR auth", e)
                    }
                }
            }
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

        // CRITICAL: Set correct UI state immediately to prevent flash
        updateConnectionStatus()

        setupClickListeners()
        setupComingSoonFeatures()
        setupOfflineModeControls()
    }
    
    override fun onOfflineModeChanged(isOffline: Boolean) {
        val previousMode = isOfflineMode
        isOfflineMode = isOffline
        
        // Only update UI if view is available AND mode actually changed
        if (_binding != null) {
            if (previousMode != isOfflineMode) {
                android.util.Log.d("SettingsFragment", "Offline mode changed: $previousMode → $isOfflineMode, updating UI")
                updateConnectionStatus()
            } else {
                android.util.Log.d("SettingsFragment", "Offline mode notification received but no actual change ($isOfflineMode)")
            }
        } else {
            android.util.Log.d("SettingsFragment", "Offline mode changed but view not available yet")
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Force save any accumulated streaming stats for immediate display
        val mainActivity = activity as? MainActivity
        mainActivity?.let { main ->
            val musicService = main.getMusicService()
            musicService?.let { service ->
                // Force save any accumulated streaming time
                service.forceSaveStreamingStats()
                android.util.Log.d("SettingsFragment", "Force saved streaming stats for immediate display")
            }
        }
        
        // Update status immediately (already called in onViewCreated, but refresh for any state changes)
        updateConnectionStatus()
        
        // Small delay only for stats to ensure save completes
        lifecycleScope.launch {
            delay(100) // 100ms delay to ensure stats save completes
            updateStreamingStats()
        }
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
        
        // Streaming Stats feature
        binding.seeStreamingStatsButton.setOnClickListener {
            openStreamingStats()
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
    
    private fun openStreamingStats() {
        val intent = Intent(requireContext(), StreamingStatsActivity::class.java)
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
            // Offline mode is active - use modern styling
            binding.connectionStatusText.text = "Offline Mode Active\nShowing downloaded content only"
            binding.connectionStatusText.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
            // Add offline icon to the text using compound drawable
            val offlineIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_offline)
            offlineIcon?.setTint(requireContext().getColor(android.R.color.holo_orange_dark))
            binding.connectionStatusText.setCompoundDrawablesWithIntrinsicBounds(offlineIcon, null, null, null)
            binding.connectionStatusText.compoundDrawablePadding = 12
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
            // Clear any compound drawables for connected state
            binding.connectionStatusText.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            binding.disconnectButton.isVisible = true
            binding.reconnectButton.text = "Reconnect"
            binding.reconnectButton.isVisible = false
            binding.reconnectButton.setOnClickListener {
                qrScannerLauncher.launch(Intent(requireContext(), QRScannerActivity::class.java))
            }
        } else {
            binding.connectionStatusText.text = "❌ Not connected"
            binding.connectionStatusText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            // Clear any compound drawables for disconnected state  
            binding.connectionStatusText.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
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
                // FIRST: Run comprehensive network diagnostics before attempting any authentication
                android.util.Log.d("SettingsFragment", "Running pre-authentication diagnostics...")
                val networkDiagnostics = NetworkDiagnostics(requireContext())
                val diagnosticResult = networkDiagnostics.diagnoseConnectionFailure()
                
                if (diagnosticResult.hasIssue) {
                    // Connectivity/Tailscale issues detected - show specific error and don't proceed
                    android.util.Log.d("SettingsFragment", "Diagnostics failed: ${diagnosticResult.issue}")
                    showConnectionDiagnosticDialog(diagnosticResult)
                    return@launch
                }
                
                // SECOND: Only if diagnostics pass, proceed with authentication check
                android.util.Log.d("SettingsFragment", "Diagnostics passed - checking authentication...")
                when (OfflineModeManager.getExitOfflineModeStatus(requireContext())) {
                    com.bma.android.api.ApiClient.ConnectionStatus.CONNECTED -> {
                        // Server is reachable and authenticated - exit offline mode
                        OfflineModeManager.disableOfflineMode()
                        
                        // Restart MainActivity to switch back to online mode
                        val intent = Intent(requireContext(), com.bma.android.MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    com.bma.android.api.ApiClient.ConnectionStatus.TOKEN_EXPIRED -> {
                        // Server is reachable but token expired - launch QR scanner for re-authentication
                        AlertDialog.Builder(requireContext())
                            .setTitle("Re-authentication Required")
                            .setMessage("Your session has expired. Please scan the QR code to reconnect to the server.")
                            .setPositiveButton("Scan QR Code") { _, _ ->
                                isExitingOfflineMode = true // Set flag to auto-complete exit after QR auth
                                qrScannerLauncher.launch(Intent(requireContext(), QRScannerActivity::class.java))
                            }
                            .setNegativeButton("Stay Offline", null)
                            .show()
                    }
                    com.bma.android.api.ApiClient.ConnectionStatus.NO_CREDENTIALS -> {
                        // No credentials stored - launch QR scanner for initial authentication
                        AlertDialog.Builder(requireContext())
                            .setTitle("Authentication Required") 
                            .setMessage("Please scan the QR code to connect to the server.")
                            .setPositiveButton("Scan QR Code") { _, _ ->
                                isExitingOfflineMode = true // Set flag to auto-complete exit after QR auth
                                qrScannerLauncher.launch(Intent(requireContext(), QRScannerActivity::class.java))
                            }
                            .setNegativeButton("Stay Offline", null)
                            .show()
                    }
                    com.bma.android.api.ApiClient.ConnectionStatus.DISCONNECTED -> {
                        // This should rarely happen if diagnostics passed, but handle gracefully
                        AlertDialog.Builder(requireContext())
                            .setTitle("Connection Issue")
                            .setMessage("Unable to connect to the server despite passing initial checks. Please try again.")
                            .setPositiveButton("Try Again") { _, _ ->
                                exitOfflineMode() // Retry the entire process
                            }
                            .setNegativeButton("Stay Offline", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error during offline mode exit", e)
                AlertDialog.Builder(requireContext())
                    .setTitle("Error")
                    .setMessage("An unexpected error occurred. Please try again.")
                    .setPositiveButton("Try Again") { _, _ ->
                        exitOfflineMode() // Retry the entire process
                    }
                    .setNegativeButton("Stay Offline", null)
                    .show()
            }
        }
    }

    private fun showConnectionDiagnosticDialog(result: NetworkDiagnostics.DiagnosticResult) {
        when (result.issue) {
            NetworkDiagnostics.ConnectionIssue.NO_INTERNET -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("No Internet Connection")
                    .setMessage("Your device is not connected to the internet. Please check your WiFi or mobile data connection.")
                    .setPositiveButton("Try Again") { _, _ ->
                        exitOfflineMode() // Retry the exit process
                    }
                    .setNegativeButton("Stay Offline", null)
                    .show()
            }
            NetworkDiagnostics.ConnectionIssue.TAILSCALE_NOT_INSTALLED -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Tailscale Required")
                    .setMessage("Tailscale is not installed. Please install Tailscale to connect to your BMA server.")
                    .setPositiveButton("Install Tailscale") { _, _ ->
                        // Open Play Store to install Tailscale
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Stay Offline", null)
                    .show()
            }
            NetworkDiagnostics.ConnectionIssue.TAILSCALE_NOT_CONNECTED -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Tailscale Not Connected")
                    .setMessage("Tailscale is installed but not running. Please open Tailscale and connect to your network.")
                    .setPositiveButton("Open Tailscale") { _, _ ->
                        // Open Tailscale app using proper launch intent
                        try {
                            val intent = requireContext().packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                                android.util.Log.d("SettingsFragment", "Successfully launched Tailscale")
                            } else {
                                android.util.Log.e("SettingsFragment", "Tailscale launch intent not found")
                                Toast.makeText(requireContext(), "Unable to open Tailscale - please open it manually", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsFragment", "Failed to open Tailscale", e)
                            Toast.makeText(requireContext(), "Please open Tailscale manually", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNeutralButton("Try Again") { _, _ ->
                        exitOfflineMode() // Retry the exit process
                    }
                    .setNegativeButton("Stay Offline", null)
                    .show()
            }
            NetworkDiagnostics.ConnectionIssue.INTERNET_DOWN_TAILSCALE_UP -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Internet Connection Issue")
                    .setMessage("Tailscale is connected but internet access is unavailable. Please check your WiFi or ISP connection.")
                    .setPositiveButton("Try Again") { _, _ ->
                        exitOfflineMode() // Retry the exit process
                    }
                    .setNegativeButton("Stay Offline", null)
                    .show()
            }
            NetworkDiagnostics.ConnectionIssue.SERVER_UNREACHABLE -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("BMA Server Unavailable")
                    .setMessage("Your BMA server is not reachable. The server may be down or there may be network issues.")
                    .setPositiveButton("Try Again") { _, _ ->
                        exitOfflineMode() // Retry the exit process
                    }
                    .setNegativeButton("Stay Offline", null)
                    .show()
            }
            NetworkDiagnostics.ConnectionIssue.UNKNOWN_ERROR, null -> {
                // Fallback to original generic message
                AlertDialog.Builder(requireContext())
                    .setTitle("Cannot Exit Offline Mode")
                    .setMessage("Unable to connect to the server. Please check your connection and try again.")
                    .setPositiveButton("Try Again") { _, _ ->
                        exitOfflineMode() // Retry the exit process
                    }
                    .setNegativeButton("Stay Offline", null)
                    .show()
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
            
            // Automatically enable offline mode to prevent redirect to setup wizard
            OfflineModeManager.enableOfflineMode()
            
            // Update local offline mode state to sync with OfflineModeManager
            isOfflineMode = true
            
            val message = if (serverNotified) {
                "Disconnected from server - offline mode enabled"
            } else {
                "Disconnected locally - offline mode enabled"
            }
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