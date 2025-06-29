package com.bma.android.storage

import android.content.Context
import android.util.Log
import com.bma.android.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages library version tracking for automatic refresh detection.
 * Uses SharedPreferences to store the last known server library version.
 * Compares server version with cached version to detect when new content is available.
 */
object LibraryVersionManager {
    private const val TAG = "LibraryVersionManager"
    private const val PREFS_NAME = "library_version_prefs"
    private const val KEY_LAST_LIBRARY_VERSION = "last_library_version"
    
    // Listeners for version change events
    private val versionChangeListeners = mutableSetOf<() -> Unit>()
    
    /**
     * Get the last known library version from local storage
     */
    fun getLastKnownVersion(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_LIBRARY_VERSION, 0L)
    }
    
    /**
     * Save the library version to local storage
     */
    fun saveLibraryVersion(context: Context, version: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_LIBRARY_VERSION, version)
            .apply()
        
        Log.d(TAG, "Saved library version: $version")
    }
    
    /**
     * Check if the server has a newer library version than what we know about.
     * Returns true if new content is detected, false otherwise.
     * Automatically saves the new version if detected.
     */
    suspend fun checkForLibraryUpdate(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // Skip checking in offline mode for battery optimization
            if (OfflineModeManager.isOfflineMode()) {
                Log.d(TAG, "Skipping version check - offline mode enabled")
                return@withContext false
            }
            
            // Get server info including library version
            val serverInfo = ApiClient.api.getServerInfo()
            val library = serverInfo["library"] as? Map<String, Any>
            
            if (library == null) {
                Log.w(TAG, "No library section in server info")
                return@withContext false
            }
            
            // Extract library version (handle both Double and Long types from JSON)
            val serverVersion = when (val version = library["libraryVersion"]) {
                is Double -> version.toLong()
                is Long -> version
                is Int -> version.toLong()
                else -> {
                    Log.w(TAG, "Invalid or missing libraryVersion in server response: $version")
                    return@withContext false
                }
            }
            
            // Get last known version
            val lastKnownVersion = getLastKnownVersion(context)
            
            Log.d(TAG, "Version check - Server: $serverVersion, Local: $lastKnownVersion")
            
            // Check if server has newer content
            if (serverVersion > lastKnownVersion) {
                Log.i(TAG, "New library content detected! Server version $serverVersion > local $lastKnownVersion")
                
                // Save the new version
                saveLibraryVersion(context, serverVersion)
                
                // Notify listeners
                notifyVersionChangeListeners()
                
                return@withContext true // New content detected!
            }
            
            Log.d(TAG, "No library updates detected")
            return@withContext false
            
        } catch (e: Exception) {
            Log.d(TAG, "Version check failed (this is normal if server is unreachable): ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Force refresh the library version from server without comparison.
     * Useful when exiting offline mode or after manual refresh.
     */
    suspend fun refreshLibraryVersion(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val serverInfo = ApiClient.api.getServerInfo()
            val library = serverInfo["library"] as? Map<String, Any>
            
            if (library != null) {
                val serverVersion = when (val version = library["libraryVersion"]) {
                    is Double -> version.toLong()
                    is Long -> version
                    is Int -> version.toLong()
                    else -> 0L
                }
                
                if (serverVersion > 0) {
                    saveLibraryVersion(context, serverVersion)
                    Log.d(TAG, "Refreshed library version to: $serverVersion")
                    return@withContext true
                }
            }
            
            return@withContext false
            
        } catch (e: Exception) {
            Log.d(TAG, "Version refresh failed: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Add a listener for version change events
     */
    fun addVersionChangeListener(listener: () -> Unit) {
        versionChangeListeners.add(listener)
    }
    
    /**
     * Remove a listener for version change events
     */
    fun removeVersionChangeListener(listener: () -> Unit) {
        versionChangeListeners.remove(listener)
    }
    
    /**
     * Notify all listeners that the library version has changed
     */
    private fun notifyVersionChangeListeners() {
        versionChangeListeners.forEach { listener ->
            try {
                listener()
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying version change listener", e)
            }
        }
    }
    
    /**
     * Clear all stored version data (useful for logout/reset scenarios)
     */
    fun clearVersionData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all version data")
    }
} 