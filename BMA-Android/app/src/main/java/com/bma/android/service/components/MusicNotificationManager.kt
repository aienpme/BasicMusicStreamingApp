package com.bma.android.service.components

import android.app.*
import com.bma.android.R
import com.bma.android.PlayerActivity
import com.bma.android.MusicService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.bma.android.api.ApiClient
import com.bma.android.models.Song
import com.bma.android.utils.ArtworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Manages music playback notifications
 */
class MusicNotificationManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    
    companion object {
        const val CHANNEL_ID = "MusicServiceChannel"
        const val NOTIFICATION_ID = 1
    }
    
    private var currentAlbumArt: Bitmap? = null
    
    // Callback interface for notification actions
    interface NotificationActionCallback {
        fun onPlayPause()
        fun onNext()
        fun onPrevious()
    }
    
    private var actionCallback: NotificationActionCallback? = null
    
    fun setActionCallback(callback: NotificationActionCallback) {
        this.actionCallback = callback
    }
    
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null) // No sound for updates
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun createNotification(
        song: Song?,
        isPlaying: Boolean,
        sessionToken: MediaSessionCompat.Token?
    ): Notification {
        Log.d("MusicNotificationManager", "Creating notification for song: ${song?.title}")
        
        val intent = Intent(context, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (song == null) {
            return createBasicNotification()
        }
        
        return try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setSubText(song.album)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying) // Only ongoing when actually playing
                .setShowWhen(false)
                .setOnlyAlertOnce(true) // Prevent notification sound/vibration on updates
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority to reduce interruptions
                .apply {
                    currentAlbumArt?.let { bitmap ->
                        setLargeIcon(bitmap)
                    }
                }
                .addAction(
                    R.drawable.ic_skip_previous, 
                    "Previous",
                    createActionPendingIntent("PREVIOUS")
                )
                .addAction(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    if (isPlaying) "Pause" else "Play",
                    createActionPendingIntent("PLAY_PAUSE")
                )
                .addAction(
                    R.drawable.ic_skip_next, 
                    "Next",
                    createActionPendingIntent("NEXT")
                )
                .setStyle(MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(false))
                .build()
                
            Log.d("MusicNotificationManager", "Notification created successfully")
            notification
        } catch (e: Exception) {
            Log.e("MusicNotificationManager", "Error creating notification: ${e.message}", e)
            createBasicNotification()
        }
    }
    
    fun createBasicNotification(): Notification {
        Log.d("MusicNotificationManager", "Creating basic notification")
        
        val intent = Intent(context, PlayerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Music Player")
                .setContentText("Ready to play")
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build()
                
            Log.d("MusicNotificationManager", "Basic notification created successfully")
            notification
        } catch (e: Exception) {
            Log.e("MusicNotificationManager", "Error creating basic notification: ${e.message}", e)
            // Fallback to absolute minimum notification
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Music")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        }
    }
    
    fun updateNotification(notification: Notification) {
        try {
            // Check notification permission on API 33+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
                } else {
                    Log.w("MusicNotificationManager", "Notification permission not granted")
                }
            } else {
                // Below API 33, no explicit permission needed
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            }
            Log.d("MusicNotificationManager", "Notification updated")
        } catch (e: Exception) {
            Log.e("MusicNotificationManager", "Failed to update notification: ${e.message}", e)
        }
    }
    
    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * Optimize bitmap for notification display
     * Android recommends 256x256dp for notification large icons
     * @param bitmap Original bitmap to optimize
     * @return Optimized bitmap for notification display
     */
    private fun optimizeBitmapForNotification(bitmap: Bitmap): Bitmap {
        val targetSize = 256 // Target size for notification large icon
        
        // If bitmap is already optimal size, return as-is
        if (bitmap.width == targetSize && bitmap.height == targetSize) {
            Log.d("MusicNotificationManager", "🎯 Bitmap already optimal size: ${targetSize}x${targetSize}")
            return bitmap
        }
        
        // Scale bitmap to target size while maintaining aspect ratio
        val scaledBitmap = if (bitmap.width != bitmap.height) {
            // Non-square image - scale to fit target size
            val scale = targetSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            
            Log.d("MusicNotificationManager", "📐 Scaling from ${bitmap.width}x${bitmap.height} to ${newWidth}x${newHeight} (scale: $scale)")
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            // Square image - direct scale to target size
            Log.d("MusicNotificationManager", "📐 Direct scaling from ${bitmap.width}x${bitmap.height} to ${targetSize}x${targetSize}")
            Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        }
        
        // Recycle original bitmap to free memory if it's different from the scaled one
        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }
        
        Log.d("MusicNotificationManager", "✨ Final notification bitmap: ${scaledBitmap.width}x${scaledBitmap.height}, config: ${scaledBitmap.config}")
        return scaledBitmap
    }
    
    fun loadAlbumArt(song: Song, onAlbumArtLoaded: () -> Unit) {
        Log.d("MusicNotificationManager", "🎨 === ALBUM ART LOADING DEBUG ===")
        Log.d("MusicNotificationManager", "Song: ${song.title}")
        Log.d("MusicNotificationManager", "Song ID: ${song.id}")
        Log.d("MusicNotificationManager", "Has artwork flag: ${song.hasArtwork}")
        
        // Clear previous artwork to ensure new artwork is loaded
        clearAlbumArt()
        Log.d("MusicNotificationManager", "🧹 Cleared previous album art")
        
        coroutineScope.launch {
            Log.d("MusicNotificationManager", "🔄 Inside coroutine - starting album art load")
            try {
                // Use ArtworkUtils to get the correct path (server URL or local file)
                val artworkPath = ArtworkUtils.getArtworkPath(context, song)
                Log.d("MusicNotificationManager", "🌐 Artwork path from ArtworkUtils: $artworkPath")
                
                if (artworkPath.isEmpty()) {
                    Log.w("MusicNotificationManager", "❌ Empty artwork path - no artwork available")
                    return@launch
                }
                
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        // Configure BitmapFactory options for high-quality loading
                        val options = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888  // Full color depth
                            inSampleSize = 1  // No downsampling
                            inScaled = false  // Preserve original resolution
                            inDither = false  // Better quality
                            inPreferQualityOverSpeed = true  // Prioritize quality
                        }
                        
                        val rawBitmap = when {
                            artworkPath.startsWith("file://") -> {
                                // Load from local file
                                Log.d("MusicNotificationManager", "📂 Loading from local file")
                                val filePath = artworkPath.removePrefix("file://")
                                val file = File(filePath)
                                if (file.exists()) {
                                    val result = BitmapFactory.decodeFile(filePath, options)
                                    Log.d("MusicNotificationManager", "🖼️ Local bitmap decoded: ${result != null}")
                                    if (result != null) {
                                        Log.d("MusicNotificationManager", "📏 Raw bitmap size: ${result.width}x${result.height}, config: ${result.config}")
                                    }
                                    result
                                } else {
                                    Log.w("MusicNotificationManager", "❌ Local file doesn't exist: $filePath")
                                    null
                                }
                            }
                            artworkPath.startsWith("http") -> {
                                // Load from server
                                Log.d("MusicNotificationManager", "📡 Loading from server")
                                val authHeader = ApiClient.getAuthHeader()
                                if (authHeader != null) {
                                    val connection = URL(artworkPath).openConnection()
                                    connection.setRequestProperty("Authorization", authHeader)
                                    connection.connectTimeout = 5000
                                    connection.readTimeout = 10000
                                    
                                    Log.d("MusicNotificationManager", "📊 Response code: ${(connection as java.net.HttpURLConnection).responseCode}")
                                    
                                    if (connection.responseCode == 200) {
                                        val inputStream = connection.getInputStream()
                                        val result = BitmapFactory.decodeStream(inputStream, null, options)
                                        inputStream.close()
                                        Log.d("MusicNotificationManager", "🖼️ Server bitmap decoded: ${result != null}")
                                        if (result != null) {
                                            Log.d("MusicNotificationManager", "📏 Raw bitmap size: ${result.width}x${result.height}, config: ${result.config}")
                                        }
                                        result
                                    } else {
                                        Log.w("MusicNotificationManager", "❌ HTTP ${connection.responseCode}: ${connection.responseMessage}")
                                        null
                                    }
                                } else {
                                    Log.e("MusicNotificationManager", "❌ No auth header available for server artwork")
                                    null
                                }
                            }
                            else -> {
                                Log.w("MusicNotificationManager", "❌ Unknown artwork path format: $artworkPath")
                                null
                            }
                        }
                        
                        // Optimize bitmap for notification display (256x256 target)
                        if (rawBitmap != null) {
                            optimizeBitmapForNotification(rawBitmap)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("MusicNotificationManager", "💥 Error loading artwork: ${e.message}", e)
                        null
                    }
                }
                
                if (bitmap != null) {
                    Log.d("MusicNotificationManager", "✅ Album art loaded successfully!")
                    currentAlbumArt = bitmap
                    onAlbumArtLoaded()
                } else {
                    Log.w("MusicNotificationManager", "❌ Album art bitmap is null")
                }
                
            } catch (e: Exception) {
                Log.e("MusicNotificationManager", "💥 Critical error loading album art: ${e.message}", e)
            }
        }
    }
    
    fun getCurrentAlbumArt(): Bitmap? = currentAlbumArt
    
    fun clearAlbumArt() {
        currentAlbumArt = null
    }
} 