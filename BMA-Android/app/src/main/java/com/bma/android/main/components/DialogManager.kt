package com.bma.android.main.components

import android.content.Context
import androidx.appcompat.app.AlertDialog

/**
 * Manages dialog creation and display.
 * Centralizes all dialog logic for consistency and reusability.
 */
class DialogManager(
    private val context: Context,
    private val callback: DialogCallback
) {
    
    interface DialogCallback {
        fun onOfflineModeSelected()
        fun onRetryConnection()
        fun onDisconnectSelected()
        fun onBypassConnection()
    }
    
    fun showOfflineModeOption() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Server Unavailable")
        builder.setMessage("Cannot connect to the server. Would you like to use offline mode to access your downloaded music?")
        builder.setPositiveButton("Use Offline Mode") { _, _ ->
            callback.onOfflineModeSelected()
        }
        builder.setNegativeButton("Try Again") { _, _ ->
            callback.onRetryConnection()
        }
        builder.setNeutralButton("Cancel") { _, _ ->
            callback.onDisconnectSelected()
        }
        builder.setCancelable(false)
        builder.show()
    }
    
    fun showConnectionLostDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Connection Lost")
        builder.setMessage("Lost connection to the server. Would you like to switch to offline mode to continue using your downloaded music?")
        builder.setPositiveButton("Use Offline Mode") { _, _ ->
            callback.onOfflineModeSelected()
        }
        builder.setNegativeButton("Try Reconnect") { _, _ ->
            callback.onRetryConnection()
        }
        builder.setNeutralButton("Exit") { _, _ ->
            callback.onDisconnectSelected()
        }
        builder.setCancelable(false)
        builder.show()
    }
    
    fun showConnectionTimeoutDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Connection Timeout")
        builder.setMessage("Server connection is taking too long. Would you like to:\n\n• Try offline mode (if you have downloads)\n• Bypass connection and enter app anyway\n• Try connecting again")
        builder.setPositiveButton("Enter App Anyway") { _, _ ->
            callback.onBypassConnection()
        }
        builder.setNegativeButton("Try Offline Mode") { _, _ ->
            callback.onOfflineModeSelected()
        }
        builder.setNeutralButton("Try Again") { _, _ ->
            callback.onRetryConnection()
        }
        builder.setCancelable(false)
        builder.show()
    }
} 