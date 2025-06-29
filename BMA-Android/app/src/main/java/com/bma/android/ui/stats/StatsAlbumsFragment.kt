package com.bma.android.ui.stats

import androidx.recyclerview.widget.RecyclerView
import com.bma.android.R
import com.bma.android.models.AlbumStats
import com.bma.android.ui.stats.adapters.AlbumStatsAdapter

/**
 * Fragment displaying album streaming statistics
 * Shows albums sorted by total listening time with aggregated play counts
 */
class StatsAlbumsFragment : StatsFragment() {
    
    private lateinit var adapter: AlbumStatsAdapter
    
    override fun getLayoutResId(): Int = R.layout.fragment_stats_list
    
    override fun createAdapter(): RecyclerView.Adapter<*> {
        adapter = AlbumStatsAdapter(requireContext())
        return adapter
    }
    
    override suspend fun fetchData(): List<Any> {
        return statsManager.getAlbumStats()
    }
    
    override fun updateAdapter(data: List<Any>) {
        @Suppress("UNCHECKED_CAST")
        adapter.updateStats(data as List<AlbumStats>)
    }
    
    override fun getEmptyStateMessage(): String {
        return "No album statistics available\n\nStart listening to music to see your stats!"
    }
}