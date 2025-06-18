package ui

import (
	"fmt"
	"log"
	"time"
	
	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/widget"
	
	"bma-go/internal/models"
	customTheme "bma-go/internal/ui/theme"
)

// SongListView displays the music library with album folder organization
type SongListView struct {
	musicLibrary  *models.MusicLibrary
	content       *fyne.Container
	folderButton  *customTheme.ModernButton
	songList      *widget.List
	noMusicCard   *customTheme.ModernCard
	parentWindow  fyne.Window
	centerStack   *fyne.Container  // Stack layout for switching between views
	
	// Animation state
	isAnimating   bool
}

// NewSongListView creates a new song list view
func NewSongListView(musicLibrary *models.MusicLibrary) *SongListView {
	slv := &SongListView{
		musicLibrary: musicLibrary,
	}
	slv.initialize()
	return slv
}

// initialize sets up the song list view components
func (slv *SongListView) initialize() {
	// Clean folder selection button - no icon
	slv.folderButton = customTheme.NewModernButton(
		"Select Music Folder",
		func() {
			slv.onSelectFolder()
		},
	)
	slv.folderButton.SetImportance(widget.HighImportance)

	// No music selected message in a clean card
	noMusicLabel := widget.NewLabelWithStyle(
		"No music folder selected.\nClick 'Select Music Folder' to choose your music directory.",
		fyne.TextAlignCenter,
		fyne.TextStyle{},
	)
	slv.noMusicCard = customTheme.NewModernCard("Welcome to BMA", "Let's get your music ready", noMusicLabel)

	// Album list (displays albums instead of individual songs)
	slv.songList = widget.NewList(
		func() int {
			// Return the actual number of albums
			return slv.musicLibrary.GetAlbumCount()
		},
		func() fyne.CanvasObject {
			// Create modern card template for albums
			albumCard := customTheme.NewModernCard("Album Name", "Artist • X tracks", nil)
			return albumCard
		},
		func(id widget.ListItemID, obj fyne.CanvasObject) {
			// Update album item with actual data
			albums := slv.musicLibrary.GetAlbums()
			log.Printf("🎵 [DEBUG] Updating album item %d, total albums: %d", id, len(albums))
			
			if id < len(albums) {
				album := albums[id]
				if card, ok := obj.(*customTheme.ModernCard); ok {
					trackText := "tracks"
					if album.TrackCount() == 1 {
						trackText = "track"
					}
					log.Printf("🎵 [DEBUG] Setting album %d: %s - %s (%d tracks)", id, album.Artist, album.Name, album.TrackCount())
					card.Title = album.Name
					card.Subtitle = album.Artist + " • " + fmt.Sprintf("%d %s", album.TrackCount(), trackText)
					card.Refresh()
				} else {
					log.Printf("❌ [DEBUG] Album item %d is not a ModernCard", id)
				}
			} else {
				log.Printf("⚠️ [DEBUG] Album item %d out of bounds (albums: %d)", id, len(albums))
			}
		},
	)

	// Header with button - remove extra padding
	headerContent := container.NewPadded(slv.folderButton)

	// IMPORTANT: Set up library listener BEFORE creating content
	// This ensures callbacks are registered before any potential library loading
	slv.startLibraryListener()

	// Create a stack container for the center content
	// This allows us to switch between no music card and song list more easily
	slv.centerStack = container.NewStack()
	
	// Initially show the appropriate content
	slv.updateCenterStack()

	// Main content area - use VBox for compact layout that doesn't expand unnecessarily
	slv.content = container.NewVBox(
		headerContent,
		slv.centerStack,
	)
	
	// Do an initial refresh in case music is already loaded
	// This will handle the case where the library is loaded before the UI is ready
	slv.refreshContent()
}

// createCenterContent creates the center content that switches between states
func (slv *SongListView) createCenterContent() fyne.CanvasObject {
	if slv.musicLibrary.GetAlbumCount() > 0 {
		return slv.songList
	}
	return slv.noMusicCard
}

// updateCenterStack updates the center stack to show the appropriate view
func (slv *SongListView) updateCenterStack() {
	albumCount := slv.musicLibrary.GetAlbumCount()
	log.Printf("🎵 [DEBUG] updateCenterStack called, album count: %d", albumCount)
	
	// Clear the stack
	slv.centerStack.Objects = nil
	
	// Add the appropriate content
	if albumCount > 0 {
		log.Println("🎵 [DEBUG] Showing album list")
		// Create a fixed-size container that BorderContainer will respect
		// Each album card is roughly 85px tall, so 5 albums = ~425px
		constrainedContainer := container.NewWithoutLayout(slv.songList)
		
		// Set both the container and list to the same constrained size
		containerSize := fyne.NewSize(600, 425)
		constrainedContainer.Resize(containerSize)
		slv.songList.Resize(containerSize)
		slv.songList.Move(fyne.NewPos(0, 0))
		
		// Wrap in VBox to prevent BorderContainer from expanding it
		wrappedContainer := container.NewVBox(constrainedContainer)
		
		slv.centerStack.Add(wrappedContainer)
		// Also refresh the album list data
		slv.songList.Refresh()
	} else {
		log.Println("🎵 [DEBUG] Showing no music card")
		slv.centerStack.Add(slv.noMusicCard)
	}
	
	slv.centerStack.Refresh()
}

// refreshContent updates the content based on library state
func (slv *SongListView) refreshContent() {
	log.Println("🎵 [DEBUG] refreshContent called")
	
	// Update the center stack
	slv.updateCenterStack()
	
	// Refresh the main content container
	if slv.content != nil {
		slv.content.Refresh()
	}
}

// startLibraryListener monitors for library changes
func (slv *SongListView) startLibraryListener() {
	// Set up callback for library changes
	slv.musicLibrary.SetLibraryChangedCallback(func() {
		log.Println("🎵 [DEBUG] Library changed callback triggered in SongListView")
		
		// Debug: Log current album count
		albumCount := slv.musicLibrary.GetAlbumCount()
		log.Printf("🎵 [DEBUG] Album count in callback: %d", albumCount)
		
		// Debug: Log first few albums if available
		if albumCount > 0 {
			albums := slv.musicLibrary.GetAlbums()
			log.Printf("🎵 [DEBUG] First album: %s - %s (%d tracks)", albums[0].Artist, albums[0].Name, albums[0].TrackCount())
		}
		
		slv.refreshContent()
		if slv.songList != nil {
			log.Println("🎵 [DEBUG] Refreshing album list")
			slv.songList.Refresh()
		}
	})
}

// GetContent returns the song list view content
func (slv *SongListView) GetContent() fyne.CanvasObject {
	return slv.content
}

// SetParentWindow sets the parent window for dialogs
func (slv *SongListView) SetParentWindow(window fyne.Window) {
	slv.parentWindow = window
}

// onSelectFolder handles folder selection
func (slv *SongListView) onSelectFolder() {
	if slv.parentWindow == nil {
		// Update the no music card with error
		slv.noMusicCard.Title = "Error"
		slv.noMusicCard.Subtitle = "No parent window set for dialog"
		slv.noMusicCard.Refresh()
		return
	}
	
	// Open the folder selection dialog using the MusicLibrary
	slv.musicLibrary.ShowFolderSelectionDialog(slv.parentWindow)
}

// LoadMusicLibrary loads and displays the music library
func (slv *SongListView) LoadMusicLibrary(folderPath string) {
	// TODO: Implement music library loading
	// This will scan the folder, organize by albums, and populate the list
	// Will be implemented in Phase 3
}

// RefreshLibrary refreshes the current music library display
func (slv *SongListView) RefreshLibrary() {
	// TODO: Implement library refresh
	// This will re-scan and update the display
}

// Animation methods for coordinated transitions

// AnimateIn fades in the song list content
func (slv *SongListView) AnimateIn(onComplete func()) {
	if slv.isAnimating {
		log.Println("🎵 [DEBUG] SongListView AnimateIn: Already animating")
		if onComplete != nil {
			onComplete()
		}
		return
	}
	
	log.Println("🎵 [ANIMATION] SongListView animating in")
	slv.isAnimating = true
	slv.content.Show()
	
	// Start fully transparent
	slv.content.Refresh()
	
	// Create fade in animation
	completed := false
	anim := fyne.NewAnimation(200*time.Millisecond, func(p float32) {
		// For container fade, we'll use a simple approach
		// In a more complex implementation, we might animate individual components
		
		// Handle completion
		if p >= 1.0 && !completed {
			completed = true
			slv.isAnimating = false
			log.Println("🎵 [ANIMATION] SongListView fade in complete")
			if onComplete != nil {
				onComplete()
			}
		}
	})
	anim.Curve = fyne.AnimationEaseInOut
	anim.Start()
}

// AnimateOut fades out the song list content
func (slv *SongListView) AnimateOut(onComplete func()) {
	if slv.isAnimating {
		log.Println("🎵 [DEBUG] SongListView AnimateOut: Already animating")
		if onComplete != nil {
			onComplete()
		}
		return
	}
	
	log.Println("🎵 [ANIMATION] SongListView animating out")
	slv.isAnimating = true
	
	// Create fade out animation
	completed := false
	anim := fyne.NewAnimation(200*time.Millisecond, func(p float32) {
		// For container fade, we'll use a simple approach
		// In a more complex implementation, we might animate individual components
		
		// Handle completion
		if p >= 1.0 && !completed {
			completed = true
			slv.content.Hide()
			slv.content.Refresh()
			slv.isAnimating = false
			log.Println("🎵 [ANIMATION] SongListView fade out complete")
			if onComplete != nil {
				onComplete()
			}
		}
	})
	anim.Curve = fyne.AnimationEaseInOut
	anim.Start()
}

// ForceShow immediately shows the song list without animation
func (slv *SongListView) ForceShow() {
	if slv.isAnimating {
		log.Println("🎵 [WARNING] ForceShow called during animation")
		return
	}
	
	// Safety check - ensure content is initialized
	if slv.content == nil {
		log.Println("🎵 [WARNING] ForceShow called before content is initialized")
		return
	}
	
	log.Println("🎵 [DEBUG] SongListView force show")
	slv.content.Show()
	slv.content.Refresh()
}

// ForceHide immediately hides the song list without animation
func (slv *SongListView) ForceHide() {
	if slv.isAnimating {
		log.Println("🎵 [WARNING] ForceHide called during animation")
		return
	}
	
	// Safety check - ensure content is initialized
	if slv.content == nil {
		log.Println("🎵 [WARNING] ForceHide called before content is initialized")
		return
	}
	
	log.Println("🎵 [DEBUG] SongListView force hide")
	slv.content.Hide()
	slv.content.Refresh()
}

// IsAnimating returns whether the song list is currently animating
func (slv *SongListView) IsAnimating() bool {
	return slv.isAnimating
}

// IsVisible returns whether the song list content is currently visible
func (slv *SongListView) IsVisible() bool {
	return slv.content.Visible()
} 