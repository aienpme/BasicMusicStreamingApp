package com.bma.android.main.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.bma.android.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Utility class for diagnosing network connectivity issues.
 * Provides specific failure reasons instead of generic "connection failed" messages.
 */
class NetworkDiagnostics(private val context: Context) {
    
    private val TAG = "NetworkDiagnostics"
    
    enum class ConnectionIssue {
        NO_INTERNET,           // No WiFi/mobile data
        TAILSCALE_NOT_INSTALLED, // Tailscale app not found
        TAILSCALE_NOT_CONNECTED, // Tailscale installed but not running
        INTERNET_DOWN_TAILSCALE_UP, // Tailscale connected but internet access down
        SERVER_UNREACHABLE,    // Internet works, Tailscale works, but BMA server down
        UNKNOWN_ERROR          // Something else went wrong
    }
    
    data class DiagnosticResult(
        val hasIssue: Boolean,
        val issue: ConnectionIssue? = null,
        val canUseOfflineMode: Boolean = false,
        val message: String = ""
    )
    
    /**
     * Performs comprehensive network diagnostics to determine why connection failed
     */
    suspend fun diagnoseConnectionFailure(): DiagnosticResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting network diagnostics...")
        
        // Step 1: Check if network interfaces are available
        if (!hasNetworkInterface()) {
            Log.d(TAG, "No network interfaces available")
            return@withContext DiagnosticResult(
                hasIssue = true,
                issue = ConnectionIssue.NO_INTERNET,
                canUseOfflineMode = true,
                message = "No network connection detected. Please check your WiFi or mobile data."
            )
        }
        
        Log.d(TAG, "Network interfaces are available")
        
        // Step 2: Check if Tailscale is installed
        if (!isTailscaleInstalled()) {
            Log.d(TAG, "Tailscale is not installed")
            return@withContext DiagnosticResult(
                hasIssue = true,
                issue = ConnectionIssue.TAILSCALE_NOT_INSTALLED,
                canUseOfflineMode = true,
                message = "Tailscale is not installed. Please install Tailscale to connect to your BMA server."
            )
        }
        
        Log.d(TAG, "Tailscale is installed")
        
        // Step 3: Check if Tailscale is connected
        if (!isTailscaleConnected()) {
            Log.d(TAG, "Tailscale is not connected")
            return@withContext DiagnosticResult(
                hasIssue = true,
                issue = ConnectionIssue.TAILSCALE_NOT_CONNECTED,
                canUseOfflineMode = true,
                message = "Tailscale is not running. Please open Tailscale and connect to your network."
            )
        }
        
        Log.d(TAG, "Tailscale is connected")
        
        // Step 4: Test actual internet connectivity (critical for Tailscale + Internet down case)
        if (!canReachInternet()) {
            Log.d(TAG, "Tailscale connected but internet access is down")
            return@withContext DiagnosticResult(
                hasIssue = true,
                issue = ConnectionIssue.INTERNET_DOWN_TAILSCALE_UP,
                canUseOfflineMode = true,
                message = "Tailscale is connected but internet access is unavailable. Please check your WiFi or ISP connection."
            )
        }
        
        Log.d(TAG, "Internet connectivity confirmed")
        
        // Step 5: Check if BMA server is reachable
        if (!isBmaServerReachable()) {
            Log.d(TAG, "BMA server is not reachable")
            return@withContext DiagnosticResult(
                hasIssue = true,
                issue = ConnectionIssue.SERVER_UNREACHABLE,
                canUseOfflineMode = true,
                message = "BMA server is unavailable. The server may be down or unreachable."
            )
        }
        
        Log.d(TAG, "All diagnostics passed - no issues detected")
        
        // If we get here, everything should be working
        return@withContext DiagnosticResult(
            hasIssue = false,
            canUseOfflineMode = false,
            message = "All connectivity checks passed"
        )
    }
    
    /**
     * Checks if device has network interface connections (WiFi/Cellular/VPN)
     */
    private fun hasNetworkInterface(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            capabilities?.let {
                when {
                    it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        Log.d(TAG, "Network interface: WiFi")
                        true
                    }
                    it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        Log.d(TAG, "Network interface: Cellular")
                        true
                    }
                    it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                        Log.d(TAG, "Network interface: VPN")
                        true
                    }
                    else -> {
                        Log.d(TAG, "Unknown network transport type")
                        false
                    }
                }
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network interface", e)
            false
        }
    }
    
    /**
     * Tests actual internet connectivity by attempting to reach a well-known DNS server
     */
    private suspend fun canReachInternet(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Testing actual internet connectivity...")
            
            // Try to connect to Google DNS (8.8.8.8) on port 53 with short timeout
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53), 3000) // 3 second timeout
                Log.d(TAG, "Successfully reached internet (Google DNS)")
                true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cannot reach internet: ${e.message}")
            false
        }
    }
    
    /**
     * Comprehensive internet connectivity check:
     * 1. Check network interfaces first (fast)
     * 2. If interfaces available, test actual internet access
     */
    private suspend fun hasInternetConnection(): Boolean {
        // Step 1: Quick check for network interfaces
        if (!hasNetworkInterface()) {
            Log.d(TAG, "No network interfaces available")
            return false
        }
        
        // Step 2: Test actual internet connectivity
        return canReachInternet()
    }
    
    /**
     * Checks if Tailscale app is installed
     */
    private fun isTailscaleInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.tailscale.ipn", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Checks if Tailscale is currently connected
     */
    private fun isTailscaleConnected(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            // Check if VPN is active
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                // Try to resolve Tailscale's service to confirm it's their VPN
                val intent = Intent().apply {
                    component = ComponentName(
                        "com.tailscale.ipn",
                        "com.tailscale.ipn.IPNService"
                    )
                }
                
                val resolveInfo = context.packageManager.resolveService(intent, 0)
                return resolveInfo != null
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Tailscale connection", e)
            false
        }
    }
    
    /**
     * Checks if BMA server is reachable
     */
    private suspend fun isBmaServerReachable(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val serverUrl = ApiClient.getServerUrl()
            if (serverUrl.isEmpty()) {
                Log.d(TAG, "No server URL configured")
                return@withContext false
            }
            
            // Extract host and port from server URL
            val url = java.net.URL(serverUrl)
            val host = url.host
            val port = if (url.port == -1) {
                if (url.protocol == "https") 443 else 80
            } else {
                url.port
            }
            
            Log.d(TAG, "Testing connection to $host:$port")
            
            // Try to connect with a short timeout
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 5000) // 5 second timeout
                Log.d(TAG, "Successfully connected to $host:$port")
                true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to connect to BMA server: ${e.message}")
            false
        }
    }
    
    /**
     * Quick check to see if we should suggest offline mode
     */
    fun canUseOfflineMode(): Boolean {
        // For now, always suggest offline mode if available
        // Could be enhanced to check if user has downloaded content
        return true
    }
    
    /**
     * Gets user-friendly action suggestions based on the diagnosed issue
     */
    fun getActionSuggestions(issue: ConnectionIssue): List<String> {
        return when (issue) {
            ConnectionIssue.NO_INTERNET -> listOf(
                "Check your WiFi connection",
                "Check your mobile data", 
                "Use offline mode to access downloaded music"
            )
            ConnectionIssue.TAILSCALE_NOT_INSTALLED -> listOf(
                "Install Tailscale from the Play Store",
                "Use offline mode to access downloaded music"
            )
            ConnectionIssue.TAILSCALE_NOT_CONNECTED -> listOf(
                "Open Tailscale app and connect",
                "Check your Tailscale network settings",
                "Use offline mode to access downloaded music"
            )
            ConnectionIssue.INTERNET_DOWN_TAILSCALE_UP -> listOf(
                "Check your WiFi connection",
                "Check your ISP connection",
                "Restart your router",
                "Use offline mode to access downloaded music"
            )
            ConnectionIssue.SERVER_UNREACHABLE -> listOf(
                "Try again in a few minutes",
                "Check if your BMA server is running",
                "Use offline mode to access downloaded music"
            )
            ConnectionIssue.UNKNOWN_ERROR -> listOf(
                "Try again",
                "Use offline mode to access downloaded music"
            )
        }
    }
}