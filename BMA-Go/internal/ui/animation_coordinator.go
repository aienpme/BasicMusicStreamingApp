package ui

import (
	"log"
	"time"

	"fyne.io/fyne/v2"
)

// UIState represents the current UI state for content visibility
type UIState int

const (
	ALBUMS_VISIBLE UIState = iota
	QR_VISIBLE
)

// Animation timing constants
const (
	fadeOutDuration    = 200 * time.Millisecond
	windowResizeDuration = 200 * time.Millisecond  
	fadeInDuration     = 200 * time.Millisecond
	totalTransitionDuration = 600 * time.Millisecond
)

// AnimationCoordinator orchestrates smooth transitions between QR code and album list
type AnimationCoordinator struct {
	// Component references
	qrSection   *QRCodeSection
	songList    *SongListView
	window      fyne.Window
	
	// State management
	currentState UIState
	isAnimating  bool
	
	// Window animation callback (will be set by main.go)
	onWindowResize func(expanded bool, onComplete func())
}

// NewAnimationCoordinator creates a new animation coordinator
func NewAnimationCoordinator(qrSection *QRCodeSection, songList *SongListView, window fyne.Window) *AnimationCoordinator {
	return &AnimationCoordinator{
		qrSection:   qrSection,
		songList:    songList,
		window:      window,
		currentState: ALBUMS_VISIBLE, // Start with albums visible
		isAnimating: false,
	}
}

// SetWindowResizeCallback sets the callback for window resizing (called from main.go)
func (ac *AnimationCoordinator) SetWindowResizeCallback(callback func(expanded bool, onComplete func())) {
	ac.onWindowResize = callback
}

// ShowQRCode triggers the coordinated transition to show QR code and hide albums
func (ac *AnimationCoordinator) ShowQRCode(onComplete func()) {
	if ac.isAnimating || ac.currentState == QR_VISIBLE {
		log.Println("🎬 [DEBUG] ShowQRCode: Already animating or QR already visible")
		if onComplete != nil {
			onComplete()
		}
		return
	}
	
	log.Println("🎬 [COORDINATOR] Starting QR show transition")
	ac.isAnimating = true
	
	// Phase 1: Fade out albums (200ms)
	ac.songList.AnimateOut(func() {
		log.Println("🎬 [COORDINATOR] Albums faded out, starting window resize")
		
		// Phase 2: Resize window to expanded state (200ms)
		if ac.onWindowResize != nil {
			ac.onWindowResize(true, func() {
				log.Println("🎬 [COORDINATOR] Window resized, fading in QR")
				
				// Phase 3: Fade in QR code (200ms)
				ac.qrSection.AnimateIn(func() {
					log.Println("🎬 [COORDINATOR] QR fade in complete")
					ac.currentState = QR_VISIBLE
					ac.isAnimating = false
					if onComplete != nil {
						onComplete()
					}
				})
			})
		} else {
			// Fallback if no window resize callback
			log.Println("🎬 [COORDINATOR] No window resize callback, proceeding to QR fade in")
			ac.qrSection.AnimateIn(func() {
				ac.currentState = QR_VISIBLE
				ac.isAnimating = false
				if onComplete != nil {
					onComplete()
				}
			})
		}
	})
}

// ShowAlbums triggers the coordinated transition to hide QR code and show albums
func (ac *AnimationCoordinator) ShowAlbums(onComplete func()) {
	if ac.isAnimating || ac.currentState == ALBUMS_VISIBLE {
		log.Println("🎬 [DEBUG] ShowAlbums: Already animating or albums already visible")
		if onComplete != nil {
			onComplete()
		}
		return
	}
	
	log.Println("🎬 [COORDINATOR] Starting albums show transition")
	ac.isAnimating = true
	
	// Phase 1: Fade out QR code (200ms)
	ac.qrSection.AnimateOut(func() {
		log.Println("🎬 [COORDINATOR] QR faded out, starting window resize")
		
		// Phase 2: Resize window to normal state (200ms)
		if ac.onWindowResize != nil {
			ac.onWindowResize(false, func() {
				log.Println("🎬 [COORDINATOR] Window resized, fading in albums")
				
				// Phase 3: Fade in albums (200ms)
				ac.songList.AnimateIn(func() {
					log.Println("🎬 [COORDINATOR] Albums fade in complete")
					ac.currentState = ALBUMS_VISIBLE
					ac.isAnimating = false
					if onComplete != nil {
						onComplete()
					}
				})
			})
		} else {
			// Fallback if no window resize callback
			log.Println("🎬 [COORDINATOR] No window resize callback, proceeding to albums fade in")
			ac.songList.AnimateIn(func() {
				ac.currentState = ALBUMS_VISIBLE
				ac.isAnimating = false
				if onComplete != nil {
					onComplete()
				}
			})
		}
	})
}

// ToggleQRCode toggles between QR and albums based on current state
func (ac *AnimationCoordinator) ToggleQRCode(onComplete func()) {
	if ac.isAnimating {
		log.Println("🎬 [DEBUG] ToggleQRCode: Animation already in progress")
		if onComplete != nil {
			onComplete()
		}
		return
	}
	
	if ac.currentState == ALBUMS_VISIBLE {
		ac.ShowQRCode(onComplete)
	} else {
		ac.ShowAlbums(onComplete)
	}
}

// IsAnimating returns whether an animation is currently in progress
func (ac *AnimationCoordinator) IsAnimating() bool {
	return ac.isAnimating
}

// GetCurrentState returns the current UI state
func (ac *AnimationCoordinator) GetCurrentState() UIState {
	return ac.currentState
}

// IsQRVisible returns true if QR code is currently visible
func (ac *AnimationCoordinator) IsQRVisible() bool {
	return ac.currentState == QR_VISIBLE
}

// IsAlbumsVisible returns true if albums are currently visible  
func (ac *AnimationCoordinator) IsAlbumsVisible() bool {
	return ac.currentState == ALBUMS_VISIBLE
}

// ForceState sets the state without animation (for initialization)
func (ac *AnimationCoordinator) ForceState(state UIState) {
	if ac.isAnimating {
		log.Println("🎬 [WARNING] ForceState called during animation")
		return
	}
	
	// Check if components are properly initialized
	if ac.qrSection == nil || ac.songList == nil {
		log.Println("🎬 [WARNING] ForceState called before components are initialized")
		ac.currentState = state // Set state but don't manipulate components yet
		return
	}
	
	log.Printf("🎬 [COORDINATOR] Force setting state to: %v", state)
	ac.currentState = state
	
	// Immediately set component visibility to match state
	if state == QR_VISIBLE {
		ac.songList.ForceHide()
		ac.qrSection.ForceShow()
	} else {
		ac.qrSection.ForceHide() 
		ac.songList.ForceShow()
	}
}