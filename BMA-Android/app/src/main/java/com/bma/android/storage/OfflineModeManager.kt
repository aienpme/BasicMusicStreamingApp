package com.bma.android.storage

import android.content.Context
import android.content.SharedPreferences
import com.bma.android.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages offline mode state throughout the application.
 * Simple singleton that tracks whether the app is currently in offline mode.
 */
object OfflineModeManager {
    private const val PREFS_NAME = "offline_mode_prefs"
    private const val KEY_OFFLINE_MODE = "is_offline_mode"
    
    private var isOfflineMode = false
    private var sharedPrefs: SharedPreferences? = null
    
    // Listeners for offline mode state changes
    private val stateChangeListeners = mutableSetOf<(Boolean) -> Unit>()
    
    /**
     * Initialize the offline mode manager with application context
     */
    fun initialize(context: Context) {
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Load saved offline mode state
        isOfflineMode = sharedPrefs?.getBoolean(KEY_OFFLINE_MODE, false) ?: false
    }
    
    /**
     * Check if app is currently in offline mode
     */
    fun isOfflineMode(): Boolean = isOfflineMode
    
    /**
     * Enable offline mode
     */
    fun enableOfflineMode() {
        if (!isOfflineMode) {
            isOfflineMode = true
            persistState()
            notifyStateChange()
        }
    }
    
    /**
     * Disable offline mode
     */
    fun disableOfflineMode() {
        if (isOfflineMode) {
            isOfflineMode = false
            persistState()
            notifyStateChange()
        }
    }
    
    /**
     * Toggle offline mode state
     */
    fun toggleOfflineMode() {
        if (isOfflineMode) {
            disableOfflineMode()
        } else {
            enableOfflineMode()
        }
    }
    
    /**
     * Check server connection and determine if offline mode should be offered
     * @return true if server is unreachable and offline mode should be suggested
     */
    suspend fun shouldSuggestOfflineMode(context: Context): Boolean {
        return when (ApiClient.checkConnection(context)) {
            ApiClient.ConnectionStatus.DISCONNECTED -> true
            ApiClient.ConnectionStatus.NO_CREDENTIALS -> false // User needs to login first
            ApiClient.ConnectionStatus.TOKEN_EXPIRED -> false // User needs to re-authenticate
            ApiClient.ConnectionStatus.CONNECTED -> false // All good
        }
    }
    
    /**
     * Check if server is reachable for exiting offline mode
     */
    suspend fun canExitOfflineMode(context: Context): Boolean {
        return when (ApiClient.checkConnection(context)) {
            ApiClient.ConnectionStatus.CONNECTED -> true
            else -> false
        }
    }
    
    /**
     * Add listener for offline mode state changes
     */
    fun addStateChangeListener(listener: (Boolean) -> Unit) {
        stateChangeListeners.add(listener)
    }
    
    /**
     * Remove listener for offline mode state changes
     */
    fun removeStateChangeListener(listener: (Boolean) -> Unit) {
        stateChangeListeners.remove(listener)
    }
    
    /**
     * Force check server connection and suggest offline mode if needed
     * This is useful for app startup or when network issues are detected
     */
    fun checkConnectionAndSuggestOffline(
        context: Context,
        onSuggestOffline: () -> Unit,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    ) {
        scope.launch {
            if (shouldSuggestOfflineMode(context)) {
                onSuggestOffline()
            }
        }
    }
    
    private fun persistState() {
        sharedPrefs?.edit()?.putBoolean(KEY_OFFLINE_MODE, isOfflineMode)?.apply()
    }
    
    private fun notifyStateChange() {
        stateChangeListeners.forEach { listener ->
            try {
                listener(isOfflineMode)
            } catch (e: Exception) {
                // Ignore listener errors to prevent crashes
            }
        }
    }
}