package com.bma.android

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.bma.android.adapters.QueueAdapter
import com.bma.android.databinding.FragmentQueueBottomSheetBinding
import com.bma.android.models.Song
import com.bma.android.service.components.ListenerManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Clean, simplified queue management bottom sheet
 * 
 * Behavior:
 * - Click button → 50% visible
 * - Swipe up → 100% visible
 * - Swipe down from 100% → 50% visible  
 * - Swipe down from 50% → COMPLETELY HIDDEN (no remnants)
 * 
 * Features:
 * - All queue operations (drag, remove, reorder, navigate)
 * - Shuffle/Repeat controls with state persistence
 * - Queue state persistence across app restarts
 */
class QueueBottomSheetFragment : BottomSheetDialogFragment(), ListenerManager.MusicServiceListener {

    private var _binding: FragmentQueueBottomSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var queueAdapter: QueueAdapter
    
    // Music service connection
    private var musicService: MusicService? = null
    private var serviceBound = false
    
    // Bottom sheet behavior
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    
    // Service connection handler
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("QueueBottomSheet", "🔗 Service connected")
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            musicService?.addListener(this@QueueBottomSheetFragment)
            
            // Load queue data
            loadQueueData()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w("QueueBottomSheet", "🔗 Service disconnected")
            musicService?.removeListener(this@QueueBottomSheetFragment)
            serviceBound = false
            musicService = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d("QueueBottomSheet", "🚀 Setting up clean queue bottom sheet")
        
        setupBottomSheetBehavior()
        setupRecyclerView()
        setupControls()
        bindMusicService()
    }
    
    /**
     * Clean, simple bottom sheet behavior setup
     */
    private fun setupBottomSheetBehavior() {
        val dialog = dialog as BottomSheetDialog
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        
        bottomSheet?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            this.bottomSheetBehavior = behavior
            
            // Configure bottom sheet behavior
            behavior.apply {
                // Enable content-based expansion
                isDraggable = true
                isFitToContents = true  // Limits expansion to actual content size
                isHideable = true
                
                // Size configuration
                peekHeight = 0  // 0 = no remnants when collapsed
                skipCollapsed = true
            }
            
            // Start at expanded (fits content size)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
    
    /**
     * Setup RecyclerView with clean, simple configuration
     */
    private fun setupRecyclerView() {
        Log.d("QueueBottomSheet", "🔄 Setting up RecyclerView")
        
        queueAdapter = QueueAdapter(
            onSongClick = { song, position -> 
                // Passive click - just log the interaction, no queue changes
                Log.d("QueueBottomSheet", "🎵 Song clicked: ${song.title} at position $position (passive mode)")
            },
            onRemoveClick = { song, position ->
                musicService?.let { service ->
                    // Convert upcoming queue position to full queue position
                    val currentQueue = service.getCurrentQueue()
                    val upcomingQueue = service.getUpcomingQueue()
                    val currentPos = currentQueue.size - upcomingQueue.size - 1
                    val fullQueuePosition = currentPos + 1 + position
                    
                    service.removeFromQueue(fullQueuePosition)
                    Log.d("QueueBottomSheet", "🗑️ Removed ${song.title} from position $fullQueuePosition")
                }
            },
            onReorder = { fromPosition, toPosition ->
                musicService?.let { service ->
                    // Convert adapter positions to full queue positions
                    val currentQueue = service.getCurrentQueue()
                    val upcomingQueue = service.getUpcomingQueue()
                    val currentPos = currentQueue.size - upcomingQueue.size - 1
                    val fullQueueFromPosition = currentPos + 1 + fromPosition
                    val fullQueueToPosition = currentPos + 1 + toPosition
                    
                    service.moveQueueItem(fullQueueFromPosition, fullQueueToPosition)
                    Log.d("QueueBottomSheet", "🔄 Reordered: $fullQueueFromPosition → $fullQueueToPosition")
                }
            }
        )
        
        binding.queueRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = queueAdapter
        }
        
        // Setup drag-and-drop
        queueAdapter.setupDragAndDrop(binding.queueRecyclerView)
        
        Log.d("QueueBottomSheet", "✅ RecyclerView setup complete")
    }
    
    /**
     * Setup shuffle/repeat controls with state persistence
     */
    private fun setupControls() {
        Log.d("QueueBottomSheet", "🎛️ Setting up controls")
        
        // Shuffle button
        binding.shuffleButton.setOnClickListener { 
            musicService?.let { service ->
                val isShuffleEnabled = service.toggleShuffle()
                updateShuffleButton(isShuffleEnabled)
                
                // Persist shuffle state
                persistShuffleState(isShuffleEnabled)
                
                Log.d("QueueBottomSheet", "🔀 Shuffle toggled: $isShuffleEnabled")
            }
        }
        
        // Repeat button
        binding.repeatButton.setOnClickListener { 
            musicService?.let { service ->
                val repeatMode = service.cycleRepeatMode()
                updateRepeatButton(repeatMode)
                
                // Persist repeat state
                persistRepeatState(repeatMode)
                
                Log.d("QueueBottomSheet", "🔁 Repeat mode: $repeatMode")
            }
        }
        
        Log.d("QueueBottomSheet", "✅ Controls setup complete")
    }
    
    /**
     * Load queue data from service
     */
    private fun loadQueueData() {
        musicService?.let { service ->
            Log.d("QueueBottomSheet", "📊 Loading queue data")
            
            val currentSong = service.getCurrentSong()
            val upcomingQueue = service.getUpcomingQueue()
            val isPlaying = service.isPlaying()
            
            Log.d("QueueBottomSheet", "   - Current song: ${currentSong?.title ?: "none"}")
            Log.d("QueueBottomSheet", "   - Upcoming songs: ${upcomingQueue.size}")
            Log.d("QueueBottomSheet", "   - Is playing: $isPlaying")
            
            // Update adapter - simple, always update
            queueAdapter.updateQueue(upcomingQueue)
            
            // Update controls
            updateShuffleButton(service.isShuffleEnabled())
            updateRepeatButton(service.getRepeatMode())
            
            // Show/hide content
            if (upcomingQueue.isNotEmpty()) {
                binding.queueRecyclerView.isVisible = true
                binding.emptyStateLayout.isVisible = false
            } else {
                binding.queueRecyclerView.isVisible = false
                binding.emptyStateLayout.isVisible = true
            }
            
            Log.d("QueueBottomSheet", "✅ Queue data loaded")
        }
    }
    
    /**
     * Update shuffle button state
     */
    private fun updateShuffleButton(isShuffleEnabled: Boolean) {
        binding.shuffleButton.setImageResource(
            if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
        )
    }
    
    /**
     * Update repeat button state
     */
    private fun updateRepeatButton(repeatMode: Int) {
        binding.repeatButton.setImageResource(
            when (repeatMode) {
                1 -> R.drawable.ic_repeat_all  // repeat all
                2 -> R.drawable.ic_repeat_one  // repeat one
                else -> R.drawable.ic_repeat_off // repeat off
            }
        )
    }
    
    /**
     * Persist shuffle state for app restart
     */
    private fun persistShuffleState(isShuffleEnabled: Boolean) {
        requireContext().getSharedPreferences("queue_state", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("shuffle_enabled", isShuffleEnabled)
            .apply()
    }
    
    /**
     * Persist repeat state for app restart
     */
    private fun persistRepeatState(repeatMode: Int) {
        requireContext().getSharedPreferences("queue_state", Context.MODE_PRIVATE)
            .edit()
            .putInt("repeat_mode", repeatMode)
            .apply()
    }
    
    /**
     * Bind to music service
     */
    private fun bindMusicService() {
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    // MusicService.MusicServiceListener implementation
    override fun onPlaybackStateChanged(state: Int) {
        Log.d("QueueBottomSheet", "🎵 Playback state changed: $state")
        loadQueueData()
    }
    
        override fun onSongChanged(song: Song?) {
        Log.d("QueueBottomSheet", "🎵 Song changed: ${song?.title}")
        loadQueueData()
    }

    override fun onProgressChanged(progress: Int, duration: Int) {
        // No progress display needed since we removed "Now Playing" section
        // Queue only shows upcoming songs, not current song progress
    }

    override fun onQueueChanged(queue: List<Song>) {
        Log.d("QueueBottomSheet", "🎵 Queue changed - size: ${queue.size}")
        loadQueueData()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (serviceBound) {
            musicService?.removeListener(this)
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        _binding = null
        Log.d("QueueBottomSheet", "🧹 Bottom sheet destroyed")
    }



    override fun onStart() {
        super.onStart()
        
        // Ensure bottom sheet opens at content size
        val dialog = dialog as? BottomSheetDialog
        val behavior = dialog?.behavior
        
        behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        behavior?.skipCollapsed = true
        behavior?.peekHeight = 0
        
        Log.d("QueueBottomSheet", "🚀 Bottom sheet started")
    }
}