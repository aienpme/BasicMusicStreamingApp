package com.bma.android.main.components

import android.content.*
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.bma.android.MusicService
import com.bma.android.service.components.ListenerManager
import com.bma.android.storage.PlaybackStateManager

/**
 * Manages the music service connection and lifecycle.
 * Handles binding/unbinding and provides access to the service instance.
 */
class MusicServiceManager(
    private val context: Context,
    private val callback: MusicServiceCallback
) {
    private var musicService: MusicService? = null
    private var serviceBound = false
    
    interface MusicServiceCallback {
        fun onServiceConnected(service: MusicService)
        fun onServiceDisconnected()
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("MusicServiceManager", "=== SERVICE CONNECTED ===")
            Log.d("MusicServiceManager", "Service component: ${name?.className}")
            
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            
            Log.d("MusicServiceManager", "Service bound successfully")
            
            // Try to restore playback state if no current song is playing
            val currentSong = musicService?.getCurrentSong()
            val isPlaying = musicService?.isPlaying() ?: false
            Log.d("MusicServiceManager", "Current service state - Song: ${currentSong?.title}, Playing: $isPlaying")
            
            // If no song is currently playing, try to restore from saved state
            if (currentSong == null) {
                val playbackStateManager = PlaybackStateManager.getInstance(context)
                if (playbackStateManager.hasValidPlaybackState()) {
                    Log.d("MusicServiceManager", "Attempting to restore playback state...")
                    musicService?.restorePlaybackState()
                }
            }
            
            // Notify callback
            callback.onServiceConnected(musicService!!)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w("MusicServiceManager", "=== SERVICE DISCONNECTED ===")
            Log.w("MusicServiceManager", "Service component: ${name?.className}")
            serviceBound = false
            musicService = null
            
            // Notify callback
            callback.onServiceDisconnected()
            
            // Try to rebind after a short delay
            Log.d("MusicServiceManager", "Attempting to rebind to service...")
            Handler(Looper.getMainLooper()).postDelayed({
                if (!serviceBound) {
                    bindMusicService()
                }
            }, 1000)
        }
    }
    
    fun bindMusicService() {
        Log.d("MusicServiceManager", "=== BINDING TO MUSIC SERVICE ===")
        val intent = Intent(context, MusicService::class.java)
        Log.d("MusicServiceManager", "Starting service...")
        val serviceStartResult = context.startService(intent)
        Log.d("MusicServiceManager", "Service start result: $serviceStartResult")
        Log.d("MusicServiceManager", "Attempting to bind service...")
        val bindResult = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d("MusicServiceManager", "Bind service result: $bindResult")
    }
    
    fun unbindMusicService(listener: ListenerManager.MusicServiceListener? = null) {
        Log.d("MusicServiceManager", "=== UNBINDING FROM SERVICE ===")
        Log.d("MusicServiceManager", "Service bound: $serviceBound")
        
        if (serviceBound) {
            Log.d("MusicServiceManager", "Unbinding from service...")
            listener?.let { musicService?.removeListener(it) }
            context.unbindService(serviceConnection)
            serviceBound = false
            Log.d("MusicServiceManager", "Service unbound")
        } else {
            Log.d("MusicServiceManager", "Service was not bound, no unbinding needed")
        }
    }
    
    fun getMusicService(): MusicService? = musicService
    
    fun isServiceBound(): Boolean = serviceBound
    
    fun addListener(listener: ListenerManager.MusicServiceListener) {
        musicService?.addListener(listener)
    }
    
    fun removeListener(listener: ListenerManager.MusicServiceListener) {
        musicService?.removeListener(listener)
    }
} 