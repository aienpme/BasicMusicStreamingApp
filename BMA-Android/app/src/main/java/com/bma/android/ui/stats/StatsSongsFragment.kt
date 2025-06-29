package com.bma.android.ui.stats

import androidx.recyclerview.widget.RecyclerView
import com.bma.android.R
import com.bma.android.models.SongStats
import com.bma.android.ui.stats.adapters.SongStatsAdapter

/**
 * Fragment displaying individual song streaming statistics
 * Shows songs sorted by total listening time with play counts
 */
class StatsSongsFragment : StatsFragment() {
    
    private lateinit var adapter: SongStatsAdapter
    
    override fun getLayoutResId(): Int = R.layout.fragment_stats_list
    
    override fun createAdapter(): RecyclerView.Adapter<*> {
        adapter = SongStatsAdapter(requireContext())
        return adapter
    }
    
    override suspend fun fetchData(): List<Any> {
        return statsManager.getAllSongStats()
    }
    
    override fun updateAdapter(data: List<Any>) {
        @Suppress("UNCHECKED_CAST")
        adapter.updateStats(data as List<SongStats>)
    }
    
    override fun getEmptyStateMessage(): String {
        return "No song statistics available\n\nStart listening to music to see your stats!"
    }
}