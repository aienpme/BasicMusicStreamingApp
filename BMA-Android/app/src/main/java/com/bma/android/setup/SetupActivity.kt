package com.bma.android.setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bma.android.MainActivity
import com.bma.android.R
import com.bma.android.setup.fragments.LoadingFragment
import com.bma.android.setup.fragments.QRScannerFragment
import com.bma.android.setup.fragments.TailscaleCheckFragment
import com.bma.android.setup.fragments.WelcomeFragment
import com.bma.android.storage.OfflineModeManager

class SetupActivity : AppCompatActivity() {
    private lateinit var viewModel: SetupViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OfflineModeManager to load saved offline mode state
        OfflineModeManager.initialize(this)

        // Check if setup is already complete or if offline mode is active
        if (isSetupComplete()) {
            // If so, go straight to the main app
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return // Important to prevent the rest of onCreate from running
        }

        setContentView(R.layout.activity_setup)
        
        viewModel = ViewModelProvider(this)[SetupViewModel::class.java]
        
        // Start with welcome fragment if no saved state
        if (savedInstanceState == null) {
            val skipToQr = intent.getBooleanExtra("skip_to_qr", false)
            val startFragment = if (skipToQr) {
                QRScannerFragment()
            } else {
                WelcomeFragment()
            }
            
            supportFragmentManager.beginTransaction()
                .replace(R.id.setup_container, startFragment)
                .commit()
        }
    }

    private fun isSetupComplete(): Boolean {
        // If offline mode is active, bypass normal setup requirements
        if (OfflineModeManager.isOfflineMode()) {
            return true
        }
        
        // Otherwise, check for normal authentication credentials
        val prefs = getSharedPreferences("BMA", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", null)
        val authToken = prefs.getString("auth_token", null)
        return !serverUrl.isNullOrEmpty() && !authToken.isNullOrEmpty()
    }
    
    fun navigateToNext(currentFragment: Fragment) {
        val nextFragment = when (currentFragment) {
            is WelcomeFragment -> TailscaleCheckFragment()
            is TailscaleCheckFragment -> QRScannerFragment()
            is QRScannerFragment -> LoadingFragment()
            else -> null
        }
        
        nextFragment?.let {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .replace(R.id.setup_container, it)
                .addToBackStack(null)
                .commit()
        }
    }
    
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}