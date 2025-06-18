package com.bma.android.setup.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.bma.android.MainActivity
import com.bma.android.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class LoadingFragment : BaseSetupFragment() {
    private lateinit var loadingCard: MaterialCardView
    private lateinit var errorCard: MaterialCardView
    private lateinit var loadingStatus: TextView
    private lateinit var loadingCount: TextView
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: MaterialButton
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_loading, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        loadingCard = view.findViewById(R.id.loading_card)
        errorCard = view.findViewById(R.id.error_card)
        loadingStatus = view.findViewById(R.id.loading_status)
        loadingCount = view.findViewById(R.id.loading_count)
        errorMessage = view.findViewById(R.id.error_message)
        retryButton = view.findViewById(R.id.retry_button)
        
        // Set up retry button
        retryButton.setOnClickListener {
            showLoading()
            loadLibrary()
        }
        
        // Observe loading status
        viewModel.loadingStatus.observe(viewLifecycleOwner) { status ->
            loadingStatus.text = status.message
            if (status.count > 0) {
                loadingCount.text = "${status.count} songs"
                loadingCount.visibility = View.VISIBLE
            }
        }
        
        // Observe errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { showError(it) }
        }
        
        // Start loading
        loadLibrary()
    }
    
    private fun loadLibrary() {
        lifecycleScope.launch {
            val success = viewModel.loadLibrary()
            if (success) {
                // Start main activity
                startMainActivity()
            }
        }
    }
    
    private fun showLoading() {
        loadingCard.visibility = View.VISIBLE
        errorCard.visibility = View.GONE
        loadingCount.visibility = View.GONE
        
        // Fade in animation
        loadingCard.startAnimation(AlphaAnimation(0f, 1f).apply {
            duration = 300
        })
    }
    
    override fun showError(message: String) {
        super.showError(message)
        loadingCard.visibility = View.GONE
        errorCard.visibility = View.VISIBLE
        errorMessage.text = message
        
        // Fade in animation
        errorCard.startAnimation(AlphaAnimation(0f, 1f).apply {
            duration = 300
        })
    }
    
    private fun startMainActivity() {
        // Start MainActivity first
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        
        // Then finish SetupActivity
        requireActivity().finish()
    }
} 