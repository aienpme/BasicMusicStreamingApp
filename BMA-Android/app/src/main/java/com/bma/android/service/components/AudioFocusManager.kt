package com.bma.android.service.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages audio focus for music playback
 */
class AudioFocusManager(private val context: Context) {
    
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    
    // Callback interface for audio focus changes
    interface AudioFocusCallback {
        fun onAudioFocusGained()
        fun onAudioFocusLost()
        fun onAudioFocusLostTransient()
        fun onAudioFocusLostCanDuck()
    }
    
    private var callback: AudioFocusCallback? = null
    
    fun setCallback(callback: AudioFocusCallback) {
        this.callback = callback
    }
    
    // Audio focus change listener
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d("AudioFocusManager", "Audio focus changed: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Resume playback or restore volume
                hasAudioFocus = true
                Log.d("AudioFocusManager", "Audio focus gained, notifying callback")
                callback?.onAudioFocusGained()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Stop playback and abandon focus
                Log.d("AudioFocusManager", "Audio focus lost permanently")
                hasAudioFocus = false
                callback?.onAudioFocusLost()
                // Don't abandon focus here - keep it for potential regain
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pause playback temporarily
                Log.d("AudioFocusManager", "Audio focus lost temporarily")
                hasAudioFocus = false
                callback?.onAudioFocusLostTransient()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lower volume instead of pausing
                Log.d("AudioFocusManager", "Audio focus lost, ducking volume")
                hasAudioFocus = false
                callback?.onAudioFocusLostCanDuck()
            }
        }
    }
    
    fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
                
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
                
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d("AudioFocusManager", "Audio focus request result: $result, hasAudioFocus: $hasAudioFocus")
            hasAudioFocus
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d("AudioFocusManager", "Audio focus request result (legacy): $result, hasAudioFocus: $hasAudioFocus")
            hasAudioFocus
        }
    }
    
    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
        Log.d("AudioFocusManager", "Audio focus abandoned")
    }
    
    fun hasAudioFocus(): Boolean = hasAudioFocus
} 