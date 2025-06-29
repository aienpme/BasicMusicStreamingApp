package com.bma.android.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ItemQueueSongBinding
import com.bma.android.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class QueueAdapter(
    private val onSongClick: (Song, Int) -> Unit,
    private val onRemoveClick: (Song, Int) -> Unit,
    val onReorder: (Int, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_QUEUE_ITEM = 0
    }
    
    private var queueItems: MutableList<Song> = mutableListOf()
    
    fun updateQueue(queueItems: List<Song>) {
        android.util.Log.d("QueueAdapter", "🔄 Updating queue: items=${queueItems.size}")
        
        this.queueItems.clear()
        this.queueItems.addAll(queueItems)
        
        notifyDataSetChanged()
        android.util.Log.d("QueueAdapter", "✅ Queue updated - total items: $itemCount")
    }
    

    
    fun setupDragAndDrop(recyclerView: RecyclerView) {
        val itemTouchHelper = ItemTouchHelper(QueueItemTouchHelper(this))
        itemTouchHelper.attachToRecyclerView(recyclerView)
        this.itemTouchHelper = itemTouchHelper
    }
    
    private var itemTouchHelper: ItemTouchHelper? = null
    
    /**
     * Move item visually during drag without committing to service
     */
    fun moveItemVisually(fromPosition: Int, toPosition: Int) {
        if (fromPosition >= 0 && fromPosition < queueItems.size &&
            toPosition >= 0 && toPosition < queueItems.size) {
            
            val item = queueItems.removeAt(fromPosition)
            queueItems.add(toPosition, item)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return TYPE_QUEUE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        android.util.Log.d("QueueAdapter", "🏗️ onCreateViewHolder called - viewType: $viewType")
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemQueueSongBinding.inflate(inflater, parent, false)
        val viewHolder = QueueItemViewHolder(binding)
        viewHolder.setItemTouchHelper(itemTouchHelper)
        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        android.util.Log.d("QueueAdapter", "📋 onBindViewHolder called - position: $position")
        if (holder is QueueItemViewHolder && position < queueItems.size) {
            val song = queueItems[position]
            holder.bind(song, position + 1)
        }
    }
    


    override fun getItemCount(): Int {
        return queueItems.size
    }



    inner class QueueItemViewHolder(
        private val binding: ItemQueueSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var itemTouchHelper: ItemTouchHelper? = null
        
        fun setItemTouchHelper(helper: ItemTouchHelper?) {
            itemTouchHelper = helper
        }

        fun bind(song: Song, position: Int) {
            binding.songTitle.text = song.title.replace(Regex("^\\d+\\.?\\s*"), "")
            binding.songArtist.text = song.artist.ifEmpty { "Unknown Artist" }
            binding.queuePosition.text = position.toString()
            
            loadArtwork(song, binding.albumArtwork)
            
            binding.draggableContainer.setOnClickListener {
                val currentPosition = adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION && currentPosition < queueItems.size) {
                    android.util.Log.d("QueueAdapter", "🎵 Song clicked: ${song.title} at position $currentPosition")
                    onSongClick(song, currentPosition)
                } else {
                    android.util.Log.w("QueueAdapter", "⚠️ Invalid song click position: $currentPosition, queue size: ${queueItems.size}")
                }
            }
            
            binding.removeButton.setOnClickListener {
                val currentPosition = adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION && currentPosition < queueItems.size) {
                    android.util.Log.d("QueueAdapter", "🗑️ Remove clicked: ${song.title} at position $currentPosition")
                    onRemoveClick(song, currentPosition)
                } else {
                    android.util.Log.w("QueueAdapter", "⚠️ Invalid remove click position: $currentPosition, queue size: ${queueItems.size}")
                }
            }
            
            // Drag handle functionality
            binding.dragHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        itemTouchHelper?.startDrag(this)
                        true
                    }
                    else -> false
                }
            }
        }
    }
    
    private fun loadArtwork(song: Song, imageView: android.widget.ImageView) {
        val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
        val authHeader = ApiClient.getAuthHeader()
        
        if (authHeader != null) {
            val glideUrl = GlideUrl(
                artworkUrl, 
                LazyHeaders.Builder()
                    .addHeader("Authorization", authHeader)
                    .build()
            )
            
            Glide.with(imageView.context)
                .load(glideUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(com.bma.android.R.drawable.ic_music_note)
                .error(com.bma.android.R.drawable.ic_music_note)
                .into(imageView)
        } else {
            imageView.setImageResource(com.bma.android.R.drawable.ic_music_note)
        }
    }
}

/**
 * Simple, clean ItemTouchHelper for drag-and-drop
 */
class QueueItemTouchHelper(
    private val adapter: QueueAdapter
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    0
) {
    
    private var dragStartPosition = -1
    private var dragEndPosition = -1
    
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return super.getMovementFlags(recyclerView, viewHolder)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition
        
        // Perform visual move only
        adapter.moveItemVisually(fromPosition, toPosition)
        
        // Track the end position for when drag completes
        dragEndPosition = toPosition
        
        return true
    }
    
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        
        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                // Remember start position
                dragStartPosition = viewHolder?.adapterPosition ?: -1
                dragEndPosition = dragStartPosition
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                // Drag ended - commit to service
                if (dragStartPosition != -1 && dragEndPosition != -1 && dragStartPosition != dragEndPosition) {
                    android.util.Log.d("QueueDrag", "Committing drag: $dragStartPosition -> $dragEndPosition")
                    
                    // Commit to service
                    adapter.onReorder(dragStartPosition, dragEndPosition)
                }
                dragStartPosition = -1
                dragEndPosition = -1
            }
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No swipe functionality
    }
    
    override fun isLongPressDragEnabled(): Boolean = false
    
    override fun isItemViewSwipeEnabled(): Boolean = false
}