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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    
    fun loadAlbumArt(song: Song, onAlbumArtLoaded: () -> Unit) {
        Log.d("MusicNotificationManager", "🎨 === ALBUM ART LOADING DEBUG ===")
        Log.d("MusicNotificationManager", "Song: ${song.title}")
        Log.d("MusicNotificationManager", "Song ID: ${song.id}")
        Log.d("MusicNotificationManager", "Has artwork flag: ${song.hasArtwork}")
        Log.d("MusicNotificationManager", "Server URL: ${ApiClient.getServerUrl()}")
        Log.d("MusicNotificationManager", "Auth header present: ${ApiClient.getAuthHeader() != null}")
        
        // Always try loading album art for debugging (ignore hasArtwork flag)
        Log.d("MusicNotificationManager", "🚀 Starting coroutine...")
        
        coroutineScope.launch {
            Log.d("MusicNotificationManager", "🔄 Inside coroutine - starting album art load")
            try {
                val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
                val authHeader = ApiClient.getAuthHeader()
                
                Log.d("MusicNotificationManager", "🌐 Attempting to load from: $artworkUrl")
                Log.d("MusicNotificationManager", "🔐 Auth header: ${authHeader?.take(20)}...")
                
                if (authHeader != null) {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            Log.d("MusicNotificationManager", "📡 Making HTTP request...")
                            val connection = URL(artworkUrl).openConnection()
                            connection.setRequestProperty("Authorization", authHeader)
                            connection.connectTimeout = 5000
                            connection.readTimeout = 10000
                            
                            Log.d("MusicNotificationManager", "📊 Response code: ${(connection as java.net.HttpURLConnection).responseCode}")
                            
                            if (connection.responseCode == 200) {
                                val inputStream = connection.getInputStream()
                                val result = BitmapFactory.decodeStream(inputStream)
                                inputStream.close()
                                Log.d("MusicNotificationManager", "🖼️ Bitmap decoded: ${result != null}")
                                if (result != null) {
                                    Log.d("MusicNotificationManager", "📏 Bitmap size: ${result.width}x${result.height}")
                                }
                                result
                            } else {
                                Log.w("MusicNotificationManager", "❌ HTTP ${connection.responseCode}: ${connection.responseMessage}")
                                null
                            }
                        } catch (e: Exception) {
                            Log.e("MusicNotificationManager", "💥 Network error: ${e.message}", e)
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
                } else {
                    Log.e("MusicNotificationManager", "❌ No auth header available for album art")
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