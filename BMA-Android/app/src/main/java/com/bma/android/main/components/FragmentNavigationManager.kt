package com.bma.android.main.components

import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.bma.android.AlbumTransitionAnimator
import com.bma.android.NavigationTransitionAnimator
import com.bma.android.R
import com.bma.android.models.Album
import com.bma.android.models.Playlist
import com.bma.android.ui.album.AlbumDetailFragment
import com.bma.android.ui.playlist.PlaylistDetailFragment

/**
 * Manages fragment navigation and transitions.
 * Handles loading fragments, navigation animations, and detail view overlays.
 */
class FragmentNavigationManager(
    private val fragmentManager: FragmentManager,
    private val fragmentContainer: ViewGroup,
    private val callback: NavigationCallback
) {
    
    interface NavigationCallback {
        fun onAlbumDetailBackPressed()
        fun onPlaylistDetailBackPressed()
    }
    
    enum class DisplayMode {
        NORMAL,
        ALBUM_DETAIL,
        PLAYLIST_DETAIL
    }
    
    // Transition animators
    private var albumTransitionAnimator: AlbumTransitionAnimator? = null
    private var navigationTransitionAnimator: NavigationTransitionAnimator? = null
    
    // Current state
    var currentDisplayMode = DisplayMode.NORMAL
        private set
    
    var currentFragmentId = R.id.navigation_library
        private set
    
    var currentFragment: Fragment? = null
        private set
    
    // Detail fragments
    private var albumDetailFragment: AlbumDetailFragment? = null
    private var playlistDetailFragment: PlaylistDetailFragment? = null
    private var backgroundFragment: Fragment? = null
    
    init {
        // Initialize transition animators
        albumTransitionAnimator = AlbumTransitionAnimator(fragmentContainer)
        navigationTransitionAnimator = NavigationTransitionAnimator(fragmentContainer)
    }
    
    fun loadFragment(fragment: Fragment, fragmentId: Int? = null) {
        fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        
        currentFragment = fragment
        fragmentId?.let { currentFragmentId = it }
    }
    
    fun navigateToFragmentWithAnimation(targetFragment: Fragment, targetFragmentId: Int) {
        // Don't animate if we're already on this fragment or if animation is in progress
        if (currentFragmentId == targetFragmentId || navigationTransitionAnimator?.isCurrentlyAnimating() == true) {
            return
        }
        
        navigationTransitionAnimator?.transitionToFragment(
            fragmentContainer = fragmentContainer
        ) {
            // This callback executes during the transition (at the black screen)
            loadFragment(targetFragment, targetFragmentId)
        }
    }
    
    fun showAlbumDetail(album: Album) {
        if (albumTransitionAnimator?.isCurrentlyAnimating() == true) {
            return // Don't start new transitions while animating
        }
        
        // Store reference to current fragment and hide it
        backgroundFragment = fragmentManager.findFragmentById(R.id.fragment_container)
        backgroundFragment?.view?.visibility = android.view.View.INVISIBLE
        
        // Create album detail fragment
        albumDetailFragment = AlbumDetailFragment.newInstance(album)
        
        // Add fragment to container
        fragmentManager.beginTransaction()
            .add(R.id.fragment_container, albumDetailFragment!!, "album_detail")
            .commit()
        
        // Wait for fragment to be added then start animation
        fragmentManager.executePendingTransactions()
        
        // Start animation after ensuring view is ready
        albumDetailFragment?.view?.let { fragmentView ->
            fragmentView.post {
                albumTransitionAnimator?.fadeToBlackAndShowContent(fragmentView) {
                    currentDisplayMode = DisplayMode.ALBUM_DETAIL
                }
            }
        }
    }
    
    fun handleAlbumDetailBack(isFinishing: Boolean, isDestroyed: Boolean) {
        if (albumTransitionAnimator?.isCurrentlyAnimating() == true) {
            return // Don't start new transitions while animating
        }
        
        albumDetailFragment?.let { fragment ->
            // Start reverse animation
            albumTransitionAnimator?.fadeToBlackAndHideContent(fragment.requireView()) {
                // Remove fragment after animation completes
                try {
                    if (fragment.isAdded && !isFinishing && !isDestroyed) {
                        fragmentManager.beginTransaction()
                            .remove(fragment)
                            .commitNowAllowingStateLoss()
                    }
                } catch (e: Exception) {
                    // Ignore any fragment transaction exceptions during cleanup
                }
                
                albumDetailFragment = null
                currentDisplayMode = DisplayMode.NORMAL
                
                // Safely restore the background fragment visibility
                restoreBackgroundFragment()
                
                // Notify callback
                callback.onAlbumDetailBackPressed()
            }
        } ?: run {
            // Safety fallback: if albumDetailFragment is null but we're in ALBUM_DETAIL mode
            // This can happen if navigation gets interrupted
            if (currentDisplayMode == DisplayMode.ALBUM_DETAIL) {
                currentDisplayMode = DisplayMode.NORMAL
                restoreBackgroundFragment()
                callback.onAlbumDetailBackPressed()
            }
        }
    }
    
    fun showPlaylistDetail(playlist: Playlist) {
        if (albumTransitionAnimator?.isCurrentlyAnimating() == true) {
            return // Don't start new transitions while animating
        }
        
        // Store reference to current fragment and hide it
        backgroundFragment = fragmentManager.findFragmentById(R.id.fragment_container)
        backgroundFragment?.view?.visibility = android.view.View.INVISIBLE
        
        // Create playlist detail fragment
        playlistDetailFragment = PlaylistDetailFragment.newInstance(playlist)
        
        // Add fragment to container
        fragmentManager.beginTransaction()
            .add(R.id.fragment_container, playlistDetailFragment!!, "playlist_detail")
            .commit()
        
        // Wait for fragment to be added then start animation
        fragmentManager.executePendingTransactions()
        
        // Start animation after ensuring view is ready
        playlistDetailFragment?.view?.let { fragmentView ->
            fragmentView.post {
                albumTransitionAnimator?.fadeToBlackAndShowContent(fragmentView) {
                    currentDisplayMode = DisplayMode.PLAYLIST_DETAIL
                }
            }
        }
    }
    
    fun handlePlaylistDetailBack(isFinishing: Boolean, isDestroyed: Boolean) {
        if (albumTransitionAnimator?.isCurrentlyAnimating() == true) {
            return // Don't start new transitions while animating
        }
        
        playlistDetailFragment?.let { fragment ->
            // Start reverse animation
            albumTransitionAnimator?.fadeToBlackAndHideContent(fragment.requireView()) {
                // Remove fragment after animation completes
                try {
                    if (fragment.isAdded && !isFinishing && !isDestroyed) {
                        fragmentManager.beginTransaction()
                            .remove(fragment)
                            .commitNowAllowingStateLoss()
                    }
                } catch (e: Exception) {
                    // Ignore any fragment transaction exceptions during cleanup
                }
                
                playlistDetailFragment = null
                currentDisplayMode = DisplayMode.NORMAL
                
                // Safely restore the background fragment visibility
                restoreBackgroundFragment()
                
                // Notify callback
                callback.onPlaylistDetailBackPressed()
            }
        } ?: run {
            // Safety fallback: if playlistDetailFragment is null but we're in PLAYLIST_DETAIL mode
            // This can happen if navigation gets interrupted
            if (currentDisplayMode == DisplayMode.PLAYLIST_DETAIL) {
                currentDisplayMode = DisplayMode.NORMAL
                restoreBackgroundFragment()
                callback.onPlaylistDetailBackPressed()
            }
        }
    }
    
    fun isAnimating(): Boolean {
        return albumTransitionAnimator?.isCurrentlyAnimating() == true
    }
    
    /**
     * Safely restores the background fragment visibility.
     * Includes fallback logic if backgroundFragment reference is lost.
     */
    private fun restoreBackgroundFragment() {
        backgroundFragment?.view?.let { view ->
            if (view.visibility != android.view.View.VISIBLE) {
                view.visibility = android.view.View.VISIBLE
            }
        } ?: run {
            // Fallback: if we lost the backgroundFragment reference, 
            // find the current fragment and ensure it's visible
            val currentMainFragment = fragmentManager.findFragmentById(R.id.fragment_container)
            if (currentMainFragment != null && 
                currentMainFragment !is AlbumDetailFragment && 
                currentMainFragment !is PlaylistDetailFragment) {
                currentMainFragment.view?.visibility = android.view.View.VISIBLE
            }
        }
        
        // Clear the reference
        backgroundFragment = null
    }
    
    /**
     * Emergency recovery method to restore normal navigation state.
     * Call this if fragments get stuck in an invisible state.
     */
    fun forceRestoreNormalState() {
        currentDisplayMode = DisplayMode.NORMAL
        
        // Remove any detail fragments
        albumDetailFragment?.let { fragment ->
            if (fragment.isAdded) {
                try {
                    fragmentManager.beginTransaction()
                        .remove(fragment)
                        .commitNowAllowingStateLoss()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        albumDetailFragment = null
        
        playlistDetailFragment?.let { fragment ->
            if (fragment.isAdded) {
                try {
                    fragmentManager.beginTransaction()
                        .remove(fragment)
                        .commitNowAllowingStateLoss()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        playlistDetailFragment = null
        
        // Ensure main fragment is visible
        restoreBackgroundFragment()
    }
} 