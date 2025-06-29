package com.bma.android.main.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog

/**
 * Manages dialog creation and display.
 * Centralizes all dialog logic for consistency and reusability.
 */
class DialogManager(
    private val context: Context,
    private val callback: DialogCallback
) {
    
    // Track current dialog to prevent stacking
    private var currentDialog: AlertDialog? = null
    
    interface DialogCallback {
        fun onOfflineModeSelected()
        fun onRetryConnection()
        fun onDisconnectSelected()
        fun onBypassConnection()
        fun onOpenTailscale()
        fun onInstallTailscale()
    }
    
    /**
     * Dismisses any currently showing dialog to prevent stacking
     */
    private fun dismissCurrentDialog() {
        currentDialog?.dismiss()
        currentDialog = null
    }
    
    /**
     * Shows a dialog with proper stacking prevention
     */
    private fun showDialog(dialog: AlertDialog) {
        dismissCurrentDialog()
        currentDialog = dialog
        dialog.show()
    }
    
    /**
     * Main method to show diagnostic-based connection issues
     */
    fun showConnectionDiagnosticDialog(result: NetworkDiagnostics.DiagnosticResult) {
        when (result.issue) {
            NetworkDiagnostics.ConnectionIssue.NO_INTERNET -> showNoInternetDialog()
            NetworkDiagnostics.ConnectionIssue.TAILSCALE_NOT_INSTALLED -> showTailscaleNotInstalledDialog()
            NetworkDiagnostics.ConnectionIssue.TAILSCALE_NOT_CONNECTED -> showTailscaleNotConnectedDialog()
            NetworkDiagnostics.ConnectionIssue.INTERNET_DOWN_TAILSCALE_UP -> showInternetDownTailscaleUpDialog()
            NetworkDiagnostics.ConnectionIssue.SERVER_UNREACHABLE -> showServerUnreachableDialog()
            NetworkDiagnostics.ConnectionIssue.UNKNOWN_ERROR -> showUnknownErrorDialog()
            null -> {
                // No issue detected, this shouldn't happen but handle gracefully
                showConnectionTimeoutDialog()
            }
        }
    }
    
    private fun showNoInternetDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("No Internet Connection")
        builder.setMessage("Your device is not connected to the internet. Please check your WiFi or mobile data connection.")
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
        showDialog(builder.create())
    }
    
    private fun showTailscaleNotInstalledDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Tailscale Required")
        builder.setMessage("Tailscale is not installed on your device. You need Tailscale to connect to your BMA server.")
        builder.setPositiveButton("Install Tailscale") { _, _ ->
            callback.onInstallTailscale()
        }
        builder.setNegativeButton("Use Offline Mode") { _, _ ->
            callback.onOfflineModeSelected()
        }
        builder.setNeutralButton("Cancel") { _, _ ->
            callback.onDisconnectSelected()
        }
        builder.setCancelable(false)
        showDialog(builder.create())
    }
    
    private fun showTailscaleNotConnectedDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Tailscale Not Connected")
        builder.setMessage("Tailscale is installed but not connected to your network. Please open Tailscale and connect.")
        builder.setPositiveButton("Open Tailscale") { _, _ ->
            callback.onOpenTailscale()
        }
        builder.setNegativeButton("Use Offline Mode") { _, _ ->
            callback.onOfflineModeSelected()
        }
        builder.setNeutralButton("Try Again") { _, _ ->
            callback.onRetryConnection()
        }
        builder.setCancelable(false)
        showDialog(builder.create())
    }
    
    private fun showServerUnreachableDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Server Unavailable")
        builder.setMessage("Your BMA server is currently unreachable. The server may be down or having issues.")
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
        showDialog(builder.create())
    }
    
    private fun showUnknownErrorDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Connection Error")
        builder.setMessage("Unable to connect to your BMA server. Please check your connection and try again.")
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
        showDialog(builder.create())
    }
    
    private fun showInternetDownTailscaleUpDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Internet Connection Issue")
        builder.setMessage("Tailscale is connected but internet access is unavailable. Please check your WiFi or ISP connection.")
        builder.setPositiveButton("Use Offline Mode") { _, _ ->
            callback.onOfflineModeSelected()
        }
        builder.setNegativeButton("Check Connection") { _, _ ->
            callback.onRetryConnection()
        }
        builder.setNeutralButton("Cancel") { _, _ ->
            callback.onDisconnectSelected()
        }
        builder.setCancelable(false)
        showDialog(builder.create())
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
        showDialog(builder.create())
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
        showDialog(builder.create())
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
        showDialog(builder.create())
    }
} 