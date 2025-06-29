package com.bma.android.ui.stats

import androidx.recyclerview.widget.RecyclerView
import com.bma.android.R
import com.bma.android.models.ArtistStats
import com.bma.android.ui.stats.adapters.ArtistStatsAdapter

/**
 * Fragment displaying artist streaming statistics  
 * Shows artists sorted by total listening time with aggregated play counts
 */
class StatsArtistsFragment : StatsFragment() {
    
    private lateinit var adapter: ArtistStatsAdapter
    
    override fun getLayoutResId(): Int = R.layout.fragment_stats_list
    
    override fun createAdapter(): RecyclerView.Adapter<*> {
        adapter = ArtistStatsAdapter(requireContext())
        return adapter
    }
    
    override suspend fun fetchData(): List<Any> {
        return statsManager.getArtistStats()
    }
    
    override fun updateAdapter(data: List<Any>) {
        @Suppress("UNCHECKED_CAST")
        adapter.updateStats(data as List<ArtistStats>)
    }
    
    override fun getEmptyStateMessage(): String {
        return "No artist statistics available\n\nStart listening to music to see your stats!"
    }
}