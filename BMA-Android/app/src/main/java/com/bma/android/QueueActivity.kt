package com.bma.android

import android.content.*
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.bma.android.adapters.QueueAdapter
import com.bma.android.databinding.ActivityQueueBinding
import com.bma.android.models.Song
import com.bma.android.service.components.ListenerManager

class QueueActivity : AppCompatActivity(), ListenerManager.MusicServiceListener {

    private lateinit var binding: ActivityQueueBinding
    private lateinit var queueAdapter: QueueAdapter
    
    // Music service
    private var musicService: MusicService? = null
    private var serviceBound = false
    
    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            android.util.Log.d("QueueActivity", "Service connected")
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            musicService?.addListener(this@QueueActivity)
            
            // Update UI with current queue state
            updateQueueDisplay()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            android.util.Log.w("QueueActivity", "Service disconnected")
            serviceBound = false
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        bindMusicService()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            musicService?.removeListener(this@QueueActivity)
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun setupUI() {
        binding.backButton.setOnClickListener { 
            finish() 
        }
    }
    
    private fun setupRecyclerView() {
        queueAdapter = QueueAdapter(
            onSongClick = { song, position -> 
                // Jump to queue position and start playing
                musicService?.let { service ->
                    val success = service.jumpToQueuePosition(position)
                    android.util.Log.d("QueueActivity", "Jump to position $position: $success")
                }
            },
            onRemoveClick = { song, position ->
                // Remove song from queue
                musicService?.let { service ->
                    // Convert adapter position to full queue position
                    // Same logic as reorder but for single position
                    val currentQueue = service.getCurrentQueue()
                    val upcomingQueue = service.getUpcomingQueue()
                    val currentPos = currentQueue.size - upcomingQueue.size - 1 // Calculate current position
                    val fullQueuePosition = currentPos + 1 + position  // +1 to skip current song
                    
                    android.util.Log.d("QueueActivity", "🗑️ === REMOVE REQUESTED ===")
                    android.util.Log.d("QueueActivity", "Adapter position: $position")
                    android.util.Log.d("QueueActivity", "Current position in full queue: $currentPos")
                    android.util.Log.d("QueueActivity", "Adapter position $position → Full queue $fullQueuePosition")
                    
                    val success = service.removeFromQueue(fullQueuePosition)
                    android.util.Log.d("QueueActivity", "Remove from full queue position $fullQueuePosition: $success")
                }
            },
            onPlayPauseClick = {
                // Toggle play/pause for current song
                musicService?.let { service ->
                    if (service.isPlaying()) {
                        service.pause()
                    } else {
                        service.play()
                    }
                }
            },
            onReorder = { fromPosition, toPosition ->
                // Reorder queue items
                android.util.Log.d("QueueActivity", "🎯 === REORDER REQUESTED ===")
                android.util.Log.d("QueueActivity", "Adapter positions: fromPosition=$fromPosition, toPosition=$toPosition")
                
                musicService?.let { service ->
                    // CRITICAL FIX: Convert upcoming queue positions to full queue positions
                    // The adapter works with upcoming queue, but service needs full queue positions
                    val currentQueue = service.getCurrentQueue()
                    val upcomingQueue = service.getUpcomingQueue()
                    val currentPos = currentQueue.size - upcomingQueue.size - 1 // Calculate current position
                    val fullQueueFromPosition = currentPos + 1 + fromPosition  // +1 to skip current song
                    val fullQueueToPosition = currentPos + 1 + toPosition
                    
                    android.util.Log.d("QueueActivity", "🔧 Position conversion:")
                    android.util.Log.d("QueueActivity", "  Current position in full queue: $currentPos")
                    android.util.Log.d("QueueActivity", "  Adapter fromPosition $fromPosition → Full queue $fullQueueFromPosition")
                    android.util.Log.d("QueueActivity", "  Adapter toPosition $toPosition → Full queue $fullQueueToPosition")
                    
                    android.util.Log.d("QueueActivity", "📞 Calling musicService.moveQueueItem($fullQueueFromPosition, $fullQueueToPosition)")
                    val success = service.moveQueueItem(fullQueueFromPosition, fullQueueToPosition)
                    android.util.Log.d("QueueActivity", "🔄 MusicService.moveQueueItem result: $success")
                    
                    if (!success) {
                        android.util.Log.e("QueueActivity", "❌ MusicService.moveQueueItem FAILED!")
                    }
                } ?: run {
                    android.util.Log.e("QueueActivity", "❌ MusicService is null!")
                }
                
                android.util.Log.d("QueueActivity", "🏁 === REORDER COMPLETE ===")
            }
        )
        
        binding.queueRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@QueueActivity)
            adapter = queueAdapter
        }
        
        // Set up drag-and-drop functionality
        queueAdapter.setupDragAndDrop(binding.queueRecyclerView)
    }
    
    private fun updateQueueDisplay() {
        musicService?.let { service ->
            val currentSong = service.getCurrentSong()
            val upcomingQueue = service.getUpcomingQueue()
            val isPlaying = service.isPlaying()
            
            android.util.Log.d("QueueActivity", "Updating queue display - Current: ${currentSong?.title}, Queue size: ${upcomingQueue.size}")
            
            if (currentSong != null || upcomingQueue.isNotEmpty()) {
                // Show queue
                binding.queueRecyclerView.isVisible = true
                binding.emptyStateLayout.isVisible = false
                
                // Show UP NEXT header only if there are upcoming songs
                binding.upNextHeader.isVisible = upcomingQueue.isNotEmpty()
                
                // Update adapter with current queue state
                queueAdapter.updateQueue(currentSong, upcomingQueue, isPlaying)
            } else {
                // Show empty state
                binding.queueRecyclerView.isVisible = false
                binding.emptyStateLayout.isVisible = true
                binding.upNextHeader.isVisible = false
            }
        } ?: run {
            // No service, show empty state
            binding.queueRecyclerView.isVisible = false
            binding.emptyStateLayout.isVisible = true
            binding.upNextHeader.isVisible = false
        }
    }
    
    // MusicService.MusicServiceListener implementation
    override fun onPlaybackStateChanged(state: Int) {
        android.util.Log.d("QueueActivity", "Playback state changed: $state")
        updateQueueDisplay()
    }
    
    override fun onSongChanged(song: Song?) {
        android.util.Log.d("QueueActivity", "Song changed: ${song?.title}")
        updateQueueDisplay()
    }
    
    override fun onProgressChanged(progress: Int, duration: Int) {
        // Update progress for current song in adapter
        queueAdapter.updateProgress(progress, duration)
    }
    
    override fun onQueueChanged(queue: List<Song>) {
        android.util.Log.d("QueueActivity", "🔔 === QUEUE CHANGED NOTIFICATION ===")
        android.util.Log.d("QueueActivity", "Queue size: ${queue.size}")
        android.util.Log.d("QueueActivity", "📝 Received queue:")
        queue.forEachIndexed { index, song ->
            android.util.Log.d("QueueActivity", "  [$index] ${song.title}")
        }
        
        // Delay queue updates slightly to prevent conflicts with dragging
        binding.queueRecyclerView.post {
            android.util.Log.d("QueueActivity", "📱 Updating queue display on UI thread")
            updateQueueDisplay()
        }
        
        android.util.Log.d("QueueActivity", "🏁 === QUEUE CHANGED COMPLETE ===")
    }
}