package com.bma.android

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("MainActivity", "=== ONCREATE STARTED ===")
        
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
    
    override fun onResume() {
        super.onResume()
        connectionManager.resumeHealthCheck()
    }

    private fun loadConnectionDetails() {
        val prefs = getSharedPreferences("BMA", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", null)
        val savedToken = prefs.getString("auth_token", null)

        if (!savedUrl.isNullOrEmpty()) {
            ApiClient.setServerUrl(savedUrl)
        }
        if (!savedToken.isNullOrEmpty()) {
            ApiClient.setAuthToken(savedToken)
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
        updateMiniPlayer()
        
        // Explicitly check for existing playback in both online and offline modes
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
            redirectToSetup()
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
    
    // Music service listener methods
    override fun onPlaybackStateChanged(state: Int) {
        Log.d("MainActivity", "=== PLAYBACK STATE CHANGED ===")
        Log.d("MainActivity", "New state: $state")
        updateMiniPlayer()
    }
    
    override fun onSongChanged(song: Song?) {
        Log.d("MainActivity", "=== SONG CHANGED ===")
        Log.d("MainActivity", "New song: ${song?.title} by ${song?.artist}")
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