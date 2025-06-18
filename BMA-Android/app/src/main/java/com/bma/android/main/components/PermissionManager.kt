package com.bma.android.main.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Manages runtime permission requests.
 * Handles notification permissions and other permissions as needed.
 */
class PermissionManager(
    private val activity: AppCompatActivity
) {
    
    private var notificationPermissionLauncher: ActivityResultLauncher<String>? = null
    
    init {
        setupPermissionLaunchers()
    }
    
    private fun setupPermissionLaunchers() {
        notificationPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d("PermissionManager", "Notification permission granted")
            } else {
                Log.w("PermissionManager", "Notification permission denied")
            }
        }
    }
    
    fun requestNotificationPermission() {
        // Only request permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    Log.d("PermissionManager", "Notification permission already granted")
                }
                else -> {
                    // Request permission
                    Log.d("PermissionManager", "Requesting notification permission")
                    notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // On Android 12 and below, notification permission is granted by default
            Log.d("PermissionManager", "Notification permission not required on Android < 13")
        }
    }
    
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission is granted by default on older versions
            true
        }
    }
} 