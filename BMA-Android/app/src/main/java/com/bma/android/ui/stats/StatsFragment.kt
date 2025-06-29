package com.bma.android.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.R
import com.bma.android.storage.IndividualStatsManager
import kotlinx.coroutines.launch

/**
 * Base fragment for streaming statistics display
 * Provides common functionality for Songs, Albums, and Artists tabs
 */
abstract class StatsFragment : Fragment() {
    
    protected lateinit var recyclerView: RecyclerView
    protected lateinit var emptyStateView: TextView
    protected lateinit var statsManager: IndividualStatsManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutResId(), container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        statsManager = IndividualStatsManager.getInstance(requireContext())
        
        setupViews(view)
        setupRecyclerView()
        loadData()
    }
    
    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.recycler_view)
        emptyStateView = view.findViewById(R.id.empty_state_text)
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = createAdapter()
    }
    
    protected fun showEmptyState(message: String) {
        recyclerView.visibility = View.GONE
        emptyStateView.visibility = View.VISIBLE
        emptyStateView.text = message
    }
    
    protected fun showData() {
        recyclerView.visibility = View.VISIBLE
        emptyStateView.visibility = View.GONE
    }
    
    protected fun loadData() {
        lifecycleScope.launch {
            try {
                val data = fetchData()
                if (data.isEmpty()) {
                    showEmptyState(getEmptyStateMessage())
                } else {
                    updateAdapter(data)
                    showData()
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsFragment", "Error loading stats data: ${e.message}", e)
                showEmptyState("Error loading statistics")
            }
        }
    }
    
    /**
     * Format minutes into human-readable string
     * @param minutes Total minutes
     * @return Formatted string like "2h 30min" or "45min"
     */
    protected fun formatMinutes(minutes: Long): String {
        return when {
            minutes == 0L -> "0min"
            minutes < 60 -> "${minutes}min"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes == 0L) {
                    "${hours}h"
                } else {
                    "${hours}h ${remainingMinutes}min"
                }
            }
        }
    }
    
    /**
     * Format play count into human-readable string
     * @param plays Number of plays
     * @return Formatted string like "1 play" or "5 plays"
     */
    protected fun formatPlays(plays: Int): String {
        return when (plays) {
            0 -> "0 plays"
            1 -> "1 play"
            else -> "$plays plays"
        }
    }
    
    // Abstract methods to be implemented by subclasses
    abstract fun getLayoutResId(): Int
    abstract fun createAdapter(): RecyclerView.Adapter<*>
    abstract suspend fun fetchData(): List<Any>
    abstract fun updateAdapter(data: List<Any>)
    abstract fun getEmptyStateMessage(): String
}