package com.bma.android

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ActivityMainBinding
import com.bma.android.main.components.*
import com.bma.android.models.Album
import com.bma.android.models.Playlist
import com.bma.android.models.Song
import com.bma.android.service.components.ListenerManager
import com.bma.android.storage.OfflineModeManager
import com.bma.android.ui.disconnection.DisconnectionFragment
import com.bma.android.ui.library.LibraryFragment
import com.bma.android.ui.search.SearchFragment
import com.bma.android.ui.settings.SettingsFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), 
    ListenerManager.MusicServiceListener,
    MusicServiceManager.MusicServiceCallback,
    MiniPlayerController.MiniPlayerCallback,
    ConnectionManager.ConnectionCallback,
    FragmentNavigationManager.NavigationCallback,
    DialogManager.DialogCallback {

    private lateinit var binding: ActivityMainBinding
    
    // Component managers
    private lateinit var musicServiceManager: MusicServiceManager
    private lateinit var miniPlayerController: MiniPlayerController
    private lateinit var connectionManager: ConnectionManager
    private lateinit var fragmentNavigationManager: FragmentNavigationManager
    private lateinit var dialogManager: DialogManager
    private lateinit var permissionManager: PermissionManager
    
    // Fragments
    private val libraryFragment = LibraryFragment()
    private val searchFragment = SearchFragment()
    private val settingsFragment = SettingsFragment()
    private val disconnectionFragment = DisconnectionFragment()
    
    // State flags
    private var isHandlingAuthFailure = false
    private var preventMiniPlayerUpdates = false
    private var isInNormalMode = false
    private var hasPerformedStartupConnectivityCheck = false
    
    // Setup activity result launcher for handling QR scanning results
    private val setupActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "Setup activity result: ${result.resultCode}")
        
        if (result.resultCode == RESULT_OK) {
            Log.d("MainActivity", "Setup completed successfully, reloading connection details...")
            isHandlingAuthFailure = false
            loadConnectionDetails()
        } else {
            Log.d("MainActivity", "Setup was cancelled or failed")
            isHandlingAuthFailure = false
            showDisconnectionScreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // EASY FIX: Clear any stale ApiClient state immediately on app start
        // This happens before ANY components can initialize and use old tokens
        ApiClient.clearAll()
        Log.d("MainActivity", "Cleared ApiClient on app start to prevent stale token usage")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("MainActivity", "=== ONCREATE STARTED ===")
        
        // Reset mini player updates flag in case it was stuck from previous state
        preventMiniPlayerUpdates = false
        
        // Reset startup connectivity check flag for new app session
        hasPerformedStartupConnectivityCheck = false
        
        // Initialize components
        initializeComponents()
        
        // Initialize offline mode manager
        OfflineModeManager.initialize(this)
        
        // Request notification permission
        permissionManager.requestNotificationPermission()
        
        // Setup UI components
        setupBottomNavigation()
        
        // Load saved credentials and check connection
        loadConnectionDetails()
        
        // Bind to music service
        Log.d("MainActivity", "About to bind music service...")
        musicServiceManager.bindMusicService()
        
        Log.d("MainActivity", "=== ONCREATE COMPLETED ===")
    }
    
    private fun initializeComponents() {
        // Initialize component managers
        musicServiceManager = MusicServiceManager(this, this)
        miniPlayerController = MiniPlayerController(this, binding.miniPlayer, lifecycleScope, this)
        connectionManager = ConnectionManager(this, lifecycleScope, this)
        fragmentNavigationManager = FragmentNavigationManager(supportFragmentManager, binding.fragmentContainer, this)
        dialogManager = DialogManager(this, this)
        permissionManager = PermissionManager(this)
        
        // Setup mini player
        miniPlayerController.setup()
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavView.setOnItemSelectedListener { item ->
            // Skip animation if we're in a detail mode (album/playlist)
            if (fragmentNavigationManager.currentDisplayMode != FragmentNavigationManager.DisplayMode.NORMAL) {
                // Reset mini player updates when navigating back to normal mode
                preventMiniPlayerUpdates = false
                // Force restore normal state to ensure fragments are visible
                fragmentNavigationManager.forceRestoreNormalState()
                when (item.itemId) {
                    R.id.navigation_library -> {
                        fragmentNavigationManager.loadFragment(libraryFragment, item.itemId)
                        true
                    }   
                    R.id.navigation_search -> {
                        fragmentNavigationManager.loadFragment(searchFragment, item.itemId)
                        true
                    }
                    R.id.navigation_settings -> {
                        fragmentNavigationManager.loadFragment(settingsFragment, item.itemId)
                        true
                    }
                    else -> false
                }
            } else {
                // Use animated transitions for normal navigation
                when (item.itemId) {
                    R.id.navigation_library -> {
                        fragmentNavigationManager.navigateToFragmentWithAnimation(libraryFragment, item.itemId)
                        true
                    }   
                    R.id.navigation_search -> {
                        fragmentNavigationManager.navigateToFragmentWithAnimation(searchFragment, item.itemId)
                        true
                    }
                    R.id.navigation_settings -> {
                        fragmentNavigationManager.navigateToFragmentWithAnimation(settingsFragment, item.itemId)
                        true
                    }
                    else -> false
                }
            }
        }
    }
    
    override fun onDestroy() {
        Log.d("MainActivity", "=== ONDESTROY CALLED ===")
        super.onDestroy()
        
        // Stop health check timer
        connectionManager.stopHealthCheckTimer()
        
        // Unbind from music service
        musicServiceManager.unbindMusicService(this)
        
        Log.d("MainActivity", "=== ONDESTROY COMPLETED ===")
    }
    
    override fun onPause() {
        super.onPause()
        connectionManager.pauseHealthCheck()
    }
    
    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "=== ONSTART CALLED ===")
        
        // Immediately check if service is connected and music is playing
        musicServiceManager.getMusicService()?.let { service ->
            Log.d("MainActivity", "Service connected in onStart")
            if (service.getCurrentSong() != null) {
                Log.d("MainActivity", "Music is playing, forcing immediate mini player update")
                preventMiniPlayerUpdates = false
                miniPlayerController.update()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        connectionManager.resumeHealthCheck()
        
        // Force immediate mini player update when resuming
        preventMiniPlayerUpdates = false
        
        // Check if service is already connected and update immediately
        musicServiceManager.getMusicService()?.let { service ->
            Log.d("MainActivity", "Service already connected in onResume, updating mini player immediately")
            service.getCurrentSong()?.let { song ->
                Log.d("MainActivity", "Current song found: ${song.title}")
                miniPlayerController.update()
            }
        }
        
        // Also call updateMiniPlayer for any other state updates
        updateMiniPlayer()
    }

    private fun loadConnectionDetails() {
        val prefs = getSharedPreferences("BMA", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", null)
        val savedToken = prefs.getString("auth_token", null)
        
        Log.d("MainActivity", "Loading connection details - URL: ${savedUrl?.take(30)}, Token: ${savedToken?.take(8)}...")
        
        // Note: ApiClient.clearAll() is now called at the start of onCreate()
        // to prevent any race conditions with background threads
        
        // Load the current values from SharedPreferences
        if (!savedUrl.isNullOrEmpty()) {
            ApiClient.setServerUrl(savedUrl)
            Log.d("MainActivity", "Set server URL from SharedPreferences")
        }
        if (!savedToken.isNullOrEmpty()) {
            ApiClient.setAuthToken(savedToken)
            Log.d("MainActivity", "Set auth token from SharedPreferences")
        }
        
        // Set up authentication failure callback
        ApiClient.onAuthFailure = {
            runOnUiThread {
                handleAuthenticationFailure()
            }
        }
        
        // Check connection status
        connectionManager.checkConnection()
    }
    
    private fun handleAuthenticationFailure() {
        if (isHandlingAuthFailure) {
            Log.d("MainActivity", "Already handling auth failure, ignoring duplicate call")
            return
        }
        
        isHandlingAuthFailure = true
        Log.e("MainActivity", "Authentication failure detected, clearing stored credentials")
        
        // Clear stored credentials
        val prefs = getSharedPreferences("BMA", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("auth_token")
            .remove("token_expires_at")
            .apply()
        
        // Clear from ApiClient
        ApiClient.setAuthToken(null)
        
        // Redirect to setup
        Log.d("MainActivity", "Launching setup for authentication failure")
        val intent = Intent(this, com.bma.android.setup.SetupActivity::class.java)
        setupActivityLauncher.launch(intent)
    }
    
    private fun setupNormalMode() {
        // Show normal UI
        binding.bottomNavView.isVisible = true
        binding.fragmentContainer.isVisible = true
        
        // Check if there's a specific tab to select from intent
        val selectedTab = intent.getStringExtra("selected_tab")
        val (fragment, fragmentId) = when (selectedTab) {
            "search" -> searchFragment to R.id.navigation_search
            "settings" -> settingsFragment to R.id.navigation_settings
            else -> libraryFragment to R.id.navigation_library
        }
        
        binding.bottomNavView.selectedItemId = fragmentId
        fragmentNavigationManager.loadFragment(fragment, fragmentId)
        
        // Start health check timer
        isInNormalMode = true
        connectionManager.startHealthCheckTimer()
        
        // Notify fragments about online mode (important for offline → online transitions)
        binding.root.post {
            notifyFragmentsOnlineMode()
        }
    }
    
    private fun setupOfflineMode() {
        Log.d("MainActivity", "Setting up offline mode UI")
        
        // Show normal UI with offline mode enabled
        binding.bottomNavView.isVisible = true
        binding.fragmentContainer.isVisible = true
        
        // Don't start health check timer in offline mode
        isInNormalMode = false
        
        // Check if there's a specific tab to select from intent
        val selectedTab = intent.getStringExtra("selected_tab")
        val (fragment, fragmentId) = when (selectedTab) {
            "search" -> searchFragment to R.id.navigation_search
            "settings" -> settingsFragment to R.id.navigation_settings
            else -> libraryFragment to R.id.navigation_library
        }
        
        binding.bottomNavView.selectedItemId = fragmentId
        fragmentNavigationManager.loadFragment(fragment, fragmentId)
        
        // Wait for fragment to be loaded, then notify about offline mode
        binding.root.post {
            notifyFragmentsOfflineMode()
            
            // Add a short delay to check if music is already playing
            // This handles the case where music was started before MainActivity was ready
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d("MainActivity", "Checking for existing playback in offline mode...")
                updateMiniPlayer()
            }, 500)
        }
    }
    
    private fun notifyFragmentsOfflineMode() {
        Log.d("MainActivity", "Notifying fragments about offline mode")
        
        try {
            // Notify all fragments that we're now in offline mode
            if (libraryFragment.isAdded && libraryFragment.view != null) {
                (libraryFragment as? OfflineModeAware)?.onOfflineModeChanged(true)
                Log.d("MainActivity", "Notified LibraryFragment about offline mode")
            }
            
            if (searchFragment.isAdded && searchFragment.view != null) {
                (searchFragment as? OfflineModeAware)?.onOfflineModeChanged(true)
                Log.d("MainActivity", "Notified SearchFragment about offline mode")
            }
            
            if (settingsFragment.isAdded && settingsFragment.view != null) {
                (settingsFragment as? OfflineModeAware)?.onOfflineModeChanged(true)
                Log.d("MainActivity", "Notified SettingsFragment about offline mode")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error notifying fragments about offline mode", e)
        }
    }
    
    private fun notifyFragmentsOnlineMode() {
        Log.d("MainActivity", "Notifying fragments about online mode")
        
        try {
            // Notify all fragments that we're now in online mode
            if (libraryFragment.isAdded && libraryFragment.view != null) {
                (libraryFragment as? OfflineModeAware)?.onOfflineModeChanged(false)
                Log.d("MainActivity", "Notified LibraryFragment about online mode")
            }
            
            if (searchFragment.isAdded && searchFragment.view != null) {
                (searchFragment as? OfflineModeAware)?.onOfflineModeChanged(false)
                Log.d("MainActivity", "Notified SearchFragment about online mode")
            }
            
            if (settingsFragment.isAdded && settingsFragment.view != null) {
                (settingsFragment as? OfflineModeAware)?.onOfflineModeChanged(false)
                Log.d("MainActivity", "Notified SettingsFragment about online mode")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error notifying fragments about online mode", e)
        }
    }
    
    private fun showDisconnectionScreen() {
        // Hide bottom navigation but keep fragment container
        binding.bottomNavView.isVisible = false
        binding.fragmentContainer.isVisible = true
        
        // Stop health check timer
        isInNormalMode = false
        connectionManager.stopHealthCheckTimer()
        
        // Show disconnection fragment
        fragmentNavigationManager.loadFragment(disconnectionFragment)
    }
    
    private fun redirectToSetup() {
        Log.d("MainActivity", "Redirecting to setup activity")
        val intent = Intent(this, com.bma.android.setup.SetupActivity::class.java)
        setupActivityLauncher.launch(intent)
    }
    
    private fun handleConnectionLost() {
        Log.d("MainActivity", "Handling connection lost - checking if offline mode should be suggested")
        
        lifecycleScope.launch {
            if (OfflineModeManager.shouldSuggestOfflineMode(this@MainActivity)) {
                dialogManager.showConnectionLostDialog()
            } else {
                showDisconnectionScreen()
            }
        }
    }
    
    // MusicServiceManager.MusicServiceCallback
    override fun onServiceConnected(service: MusicService) {
        musicServiceManager.addListener(this)
        
        // Reset flag immediately when service connects
        preventMiniPlayerUpdates = false
        
        // Force immediate update
        updateMiniPlayer()
        
        // Explicitly check for existing playback
        service.getCurrentSong()?.let { song ->
            Log.d("MainActivity", "Found current song when service connected: ${song.title}")
            // Force immediate mini player update
            miniPlayerController.update()
        }
    }
    
    override fun onServiceDisconnected() {
        miniPlayerController.hide()
    }
    
    // MiniPlayerController.MiniPlayerCallback
    override fun getMusicService(): MusicService? = musicServiceManager.getMusicService()
    
    // ConnectionManager.ConnectionCallback
    override fun onConnected() {
        runOnUiThread {
            if (OfflineModeManager.isOfflineMode()) {
                setupOfflineMode()
            } else {
                setupNormalMode()
            }
        }
    }
    
    override fun onDisconnected() {
        runOnUiThread {
            lifecycleScope.launch {
                if (OfflineModeManager.shouldSuggestOfflineMode(this@MainActivity)) {
                    dialogManager.showOfflineModeOption()
                } else {
                    showDisconnectionScreen()
                }
            }
        }
    }
    
    override fun onTokenExpired() {
        runOnUiThread {
            showDisconnectionScreen()
        }
    }
    
    override fun onNoCredentials() {
        runOnUiThread {
            // Don't redirect to setup if offline mode is active
            if (!OfflineModeManager.isOfflineMode()) {
                redirectToSetup()
            } else {
                Log.d("MainActivity", "onNoCredentials: Offline mode active, skipping setup redirect")
            }
        }
    }
    
    override fun onConnectionTimeout() {
        runOnUiThread {
            dialogManager.showConnectionTimeoutDialog()
        }
    }
    
    override fun onConnectionLost() {
        runOnUiThread {
            handleConnectionLost()
        }
    }
    
    override fun onConnectionDiagnostics(result: NetworkDiagnostics.DiagnosticResult) {
        runOnUiThread {
            Log.d("MainActivity", "Connection diagnostics: ${result.issue} - ${result.message}")
            dialogManager.showConnectionDiagnosticDialog(result)
        }
    }
    
    // FragmentNavigationManager.NavigationCallback
    override fun onAlbumDetailBackPressed() {
        preventMiniPlayerUpdates = false
    }
    
    override fun onPlaylistDetailBackPressed() {
        preventMiniPlayerUpdates = false
    }
    
    // DialogManager.DialogCallback
    override fun onOfflineModeSelected() {
        OfflineModeManager.enableOfflineMode()
        setupOfflineMode()
    }
    
    override fun onRetryConnection() {
        loadConnectionDetails()
    }
    
    override fun onDisconnectSelected() {
        showDisconnectionScreen()
    }
    
    override fun onBypassConnection() {
        Log.d("MainActivity", "User chose to bypass connection check")
        setupNormalMode()
    }
    
    override fun onOpenTailscale() {
        Log.d("MainActivity", "User chose to open Tailscale")
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
            if (intent != null) {
                startActivity(intent)
            } else {
                Log.e("MainActivity", "Could not find Tailscale app to open")
                // Fallback to retry connection
                loadConnectionDetails()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open Tailscale", e)
            // Fallback to retry connection
            loadConnectionDetails()
        }
    }
    
    override fun onInstallTailscale() {
        Log.d("MainActivity", "User chose to install Tailscale")
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=com.tailscale.ipn")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // Fallback to browser if Play Store app is not installed
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn")
                }
                startActivity(intent)
            } catch (ex: Exception) {
                Log.e("MainActivity", "Failed to open Play Store for Tailscale", ex)
                // Fallback to offline mode suggestion
                onOfflineModeSelected()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to install Tailscale", e)
            // Fallback to offline mode suggestion
            onOfflineModeSelected()
        }
    }
    
    /**
     * Performs startup connectivity check similar to Settings.exitOfflineMode()
     * Only runs once per app session and only when not in offline mode
     */
    fun performStartupConnectivityCheck() {
        // Skip if already performed this session
        if (hasPerformedStartupConnectivityCheck) {
            Log.d("MainActivity", "Startup connectivity check already performed this session")
            return
        }
        
        // Skip if already in offline mode
        if (OfflineModeManager.isOfflineMode()) {
            Log.d("MainActivity", "Skipping startup connectivity check - already in offline mode")
            hasPerformedStartupConnectivityCheck = true
            return
        }
        
        // Mark as performed to prevent duplicate checks
        hasPerformedStartupConnectivityCheck = true
        
        Log.d("MainActivity", "Performing startup connectivity check...")
        
        lifecycleScope.launch {
            try {
                // Run comprehensive network diagnostics (same as Settings.exitOfflineMode)
                val networkDiagnostics = NetworkDiagnostics(this@MainActivity)
                val diagnosticResult = networkDiagnostics.diagnoseConnectionFailure()
                
                if (diagnosticResult.hasIssue) {
                    // Connectivity/Tailscale issues detected - show specific error dialog
                    Log.d("MainActivity", "Startup diagnostics failed: ${diagnosticResult.issue}")
                    runOnUiThread {
                        dialogManager.showConnectionDiagnosticDialog(diagnosticResult)
                    }
                } else {
                    // All connectivity checks passed - no action needed
                    Log.d("MainActivity", "Startup diagnostics passed - connectivity is good")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during startup connectivity check", e)
                // Don't show error dialogs for startup check failures - fail silently
                // User can still use the app normally or manually check from Settings
            }
        }
    }
    
    // Music service listener methods
    override fun onPlaybackStateChanged(state: Int) {
        Log.d("MainActivity", "=== PLAYBACK STATE CHANGED ===")
        Log.d("MainActivity", "New state: $state")
        updateMiniPlayer()
    }
    
    override fun onSongChanged(song: Song?) {
        Log.d("MainActivity", "=== SONG CHANGED ===")
        Log.d("MainActivity", "New song: ${song?.title} by ${song?.artist}")
        // Reset the flag when song changes to ensure mini player updates
        preventMiniPlayerUpdates = false
        updateMiniPlayer()
    }
    
    override fun onProgressChanged(progress: Int, duration: Int) {
        Log.v("MainActivity", "Progress changed: $progress/$duration")
        miniPlayerController.updateProgress(progress, duration)
    }
    
    private fun updateMiniPlayer() {
        if (!preventMiniPlayerUpdates) {
            Log.d("MainActivity", "updateMiniPlayer called - preventUpdates: false")
            val service = musicServiceManager.getMusicService()
            Log.d("MainActivity", "MusicService available: ${service != null}")
            service?.let {
                val currentSong = it.getCurrentSong()
                Log.d("MainActivity", "Current song: ${currentSong?.title}")
            }
            miniPlayerController.update()
        } else {
            Log.d("MainActivity", "updateMiniPlayer called - preventUpdates: true")
        }
    }
    
    override fun onBackPressed() {
        // Handle album detail back navigation first
        if (fragmentNavigationManager.currentDisplayMode == FragmentNavigationManager.DisplayMode.ALBUM_DETAIL) {
            fragmentNavigationManager.handleAlbumDetailBack(isFinishing, isDestroyed)
            return
        }
        
        // If user is on Search or Settings tab, navigate back to Library
        when (binding.bottomNavView.selectedItemId) {
            R.id.navigation_search, R.id.navigation_settings -> {
                binding.bottomNavView.selectedItemId = R.id.navigation_library
            }
            else -> {
                // If already on Library or any other state, use default back behavior
                super.onBackPressed()
            }
        }
    }
    
    // Public methods for fragments to use
    fun showAlbumDetail(album: Album) {
        if (!fragmentNavigationManager.isAnimating()) {
            preventMiniPlayerUpdates = true
            fragmentNavigationManager.showAlbumDetail(album)
        }
    }
    
    fun handleAlbumDetailBackPressed() {
        fragmentNavigationManager.handleAlbumDetailBack(isFinishing, isDestroyed)
    }
    
    fun showPlaylistDetail(playlist: Playlist) {
        if (!fragmentNavigationManager.isAnimating()) {
            preventMiniPlayerUpdates = true
            fragmentNavigationManager.showPlaylistDetail(playlist)
        }
    }
    
    fun handlePlaylistDetailBackPressed() {
        fragmentNavigationManager.handlePlaylistDetailBack(isFinishing, isDestroyed)
    }
    
    fun getAllSongs(): List<Song> {
        return libraryFragment.getAllSongs()
    }
    
    // Interface for fragments that need to know about offline mode changes
    interface OfflineModeAware {
        fun onOfflineModeChanged(isOffline: Boolean)
    }
}