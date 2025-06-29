package com.bma.android.ui.disconnection

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bma.android.MainActivity
import com.bma.android.api.ApiClient
import com.bma.android.databinding.FragmentDisconnectionBinding
import com.bma.android.setup.SetupActivity
import kotlinx.coroutines.launch

class DisconnectionFragment : Fragment() {

    private var _binding: FragmentDisconnectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisconnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            checkConnectionAndRefresh()
        }

        binding.generateQrButton.setOnClickListener {
            startSetupFlow()
        }
    }

    private fun checkConnectionAndRefresh() {
        binding.refreshButton.isEnabled = false
        binding.statusText.text = "Checking connection..."
        
        lifecycleScope.launch {
            when (ApiClient.checkConnection(requireContext())) {
                ApiClient.ConnectionStatus.CONNECTED -> {
                    // Connection restored, restart MainActivity
                    val intent = Intent(requireContext(), MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                ApiClient.ConnectionStatus.DISCONNECTED -> {
                    binding.statusText.text = "Seems we are disconnected"
                    binding.refreshButton.isEnabled = true
                }
                ApiClient.ConnectionStatus.TOKEN_EXPIRED -> {
                    binding.statusText.text = "Server is back but needs new pairing"
                    binding.refreshButton.isEnabled = true
                }
                ApiClient.ConnectionStatus.NO_CREDENTIALS -> {
                    startSetupFlow()
                }
            }
        }
    }

    private fun startSetupFlow() {
        // Clear saved credentials so SetupActivity doesn't think setup is complete
        val prefs = requireContext().getSharedPreferences("BMA", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .remove("server_url")
            .remove("auth_token")
            .remove("token_expires_at")
            .apply()
        
        // Complete ApiClient state reset (prevents re-pairing crashes)
        ApiClient.clearAll()
        
        val intent = Intent(requireContext(), SetupActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("skip_to_qr", true)  // Skip directly to QR scanner
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}