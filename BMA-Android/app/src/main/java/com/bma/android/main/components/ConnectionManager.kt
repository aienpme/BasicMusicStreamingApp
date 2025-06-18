package com.bma.android.main.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.bma.android.api.ApiClient
import com.bma.android.storage.OfflineModeManager
import kotlinx.coroutines.launch

/**
 * Manages server connectivity and health checks.
 * Monitors connection status and triggers appropriate actions on connection changes.
 */
class ConnectionManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val callback: ConnectionCallback
) {
    
    interface ConnectionCallback {
        fun onConnected()
        fun onDisconnected()
        fun onTokenExpired()
        fun onNoCredentials()
        fun onConnectionTimeout()
        fun onConnectionLost()
    }
    
    private var healthCheckHandler: Handler? = null
    private var healthCheckRunnable: Runnable? = null
    private var isInNormalMode = false
    
    fun checkConnection() {
        lifecycleScope.launch {
            // If already in offline mode, skip connection check
            if (OfflineModeManager.isOfflineMode()) {
                Log.d("ConnectionManager", "Already in offline mode, skipping connection check")
                callback.onConnected() // Treat offline mode as "connected"
                return@launch
            }
            
            try {
                Log.d("ConnectionManager", "Starting connection check with timeout...")
                
                // Add timeout to prevent infinite loading
                val connectionJob = launch {
                    when (ApiClient.checkConnection(context)) {
                        ApiClient.ConnectionStatus.CONNECTED -> {
                            Log.d("ConnectionManager", "Connected to server")
                            callback.onConnected()
                        }
                        ApiClient.ConnectionStatus.DISCONNECTED -> {
                            Log.d("ConnectionManager", "Server disconnected")
                            callback.onDisconnected()
                        }
                        ApiClient.ConnectionStatus.TOKEN_EXPIRED -> {
                            Log.d("ConnectionManager", "Token expired")
                            callback.onTokenExpired()
                        }
                        ApiClient.ConnectionStatus.NO_CREDENTIALS -> {
                            Log.d("ConnectionManager", "No credentials")
                            callback.onNoCredentials()
                        }
                    }
                }
                
                // Wait for connection check or timeout after 10 seconds
                launch {
                    kotlinx.coroutines.delay(10000) // 10 second timeout
                    if (connectionJob.isActive) {
                        Log.w("ConnectionManager", "Connection check timed out after 10 seconds")
                        connectionJob.cancel()
                        callback.onConnectionTimeout()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("ConnectionManager", "Connection check failed with exception", e)
                callback.onConnectionTimeout()
            }
        }
    }
    
    fun startHealthCheckTimer() {
        stopHealthCheckTimer() // Stop any existing timer
        
        healthCheckHandler = Handler(Looper.getMainLooper())
        healthCheckRunnable = object : Runnable {
            override fun run() {
                performHealthCheck()
                // Schedule next check in 30 seconds if still in normal mode
                if (isInNormalMode && healthCheckHandler != null) {
                    healthCheckHandler?.postDelayed(this, 30000) // 30 seconds
                }
            }
        }
        
        // Start first check after 30 seconds
        healthCheckHandler?.postDelayed(healthCheckRunnable!!, 30000)
        isInNormalMode = true
        Log.d("ConnectionManager", "Health check timer started")
    }
    
    fun stopHealthCheckTimer() {
        healthCheckHandler?.removeCallbacks(healthCheckRunnable ?: return)
        healthCheckHandler = null
        healthCheckRunnable = null
        isInNormalMode = false
        Log.d("ConnectionManager", "Health check timer stopped")
    }
    
    fun pauseHealthCheck() {
        if (isInNormalMode) {
            stopHealthCheckTimer()
            Log.d("ConnectionManager", "Health checks paused")
        }
    }
    
    fun resumeHealthCheck() {
        if (isInNormalMode) {
            startHealthCheckTimer()
            Log.d("ConnectionManager", "Health checks resumed")
        }
    }
    
    private fun performHealthCheck() {
        Log.d("ConnectionManager", "Performing health check...")
        
        lifecycleScope.launch {
            try {
                when (ApiClient.checkConnection(context)) {
                    ApiClient.ConnectionStatus.CONNECTED -> {
                        Log.d("ConnectionManager", "Health check: Still connected")
                        // All good, continue
                    }
                    ApiClient.ConnectionStatus.DISCONNECTED -> {
                        Log.w("ConnectionManager", "Health check: Server disconnected")
                        callback.onConnectionLost()
                    }
                    ApiClient.ConnectionStatus.TOKEN_EXPIRED -> {
                        Log.w("ConnectionManager", "Health check: Token expired")
                        callback.onTokenExpired()
                    }
                    ApiClient.ConnectionStatus.NO_CREDENTIALS -> {
                        Log.w("ConnectionManager", "Health check: No credentials")
                        callback.onNoCredentials()
                    }
                }
            } catch (e: Exception) {
                Log.e("ConnectionManager", "Health check failed", e)
                // On exception, treat as connection lost
                callback.onConnectionLost()
            }
        }
    }
} 