package ui

import (
	"bytes"
	"image"
	"image/color"
	"log"
	"sync"
	"time"
	
	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/widget"
	
	"bma-go/internal/models"
	customTheme "bma-go/internal/ui/theme"
)

// SongListView displays the music library with album folder organization
type SongListView struct {
	musicLibrary    *models.MusicLibrary
	content         *fyne.Container
	folderButton    *customTheme.ModernButton
	songList        *widget.List
	noMusicCard     *customTheme.ModernCard
	parentWindow    fyne.Window
	centerStack     *fyne.Container  // Stack layout for switching between views
	
	// Animation state
	isAnimating     bool
	
	// Artwork cache
	artworkCache    map[string]*canvas.Image
	artworkMutex    sync.RWMutex
	placeholderImg  *canvas.Image
}

// NewSongListView creates a new song list view
func NewSongListView(musicLibrary *models.MusicLibrary) *SongListView {
	slv := &SongListView{
		musicLibrary: musicLibrary,
		artworkCache: make(map[string]*canvas.Image),
	}
	slv.createPlaceholderImage()
	slv.initialize()
	return slv
}

// createPlaceholderImage creates a simple placeholder image for albums without artwork
func (slv *SongListView) createPlaceholderImage() {
	// Create a more visible placeholder image for debugging
	img := image.NewRGBA(image.Rect(0, 0, 60, 60))
	
	// Create a bright red placeholder so we can easily see it
	redColor := color.RGBA{R: 255, G: 0, B: 0, A: 255}
	whiteColor := color.RGBA{R: 255, G: 255, B: 255, A: 255}
	
	for y := 0; y < 60; y++ {
		for x := 0; x < 60; x++ {
			// Create a simple pattern - red background with white border
			if x < 2 || x >= 58 || y < 2 || y >= 58 {
				img.Set(x, y, whiteColor)
			} else {
				img.Set(x, y, redColor)
			}
		}
	}
	
	slv.placeholderImg = canvas.NewImageFromImage(img)
	slv.placeholderImg.FillMode = canvas.ImageFillContain
}

// getDisplayItemArtwork retrieves or creates artwork for a display item (album or folder)
func (slv *SongListView) getDisplayItemArtwork(item models.DisplayItem) *canvas.Image {
	// Use item ID as cache key
	itemID := item.GetID()
	
	log.Printf("🎨 [ARTWORK] Getting artwork for item: %s (ID: %s, folder: %t)", item.GetName(), itemID, item.IsFolder())
	
	// Check cache first
	slv.artworkMutex.RLock()
	if cachedImg, exists := slv.artworkCache[itemID]; exists {
		slv.artworkMutex.RUnlock()
		// Check if cached image is placeholder or real artwork
		if cachedImg == slv.placeholderImg {
			log.Printf("🎨 [ARTWORK] Cache hit for item: %s (PLACEHOLDER)", item.GetName())
		} else {
			log.Printf("🎨 [ARTWORK] Cache hit for item: %s (REAL ARTWORK)", item.GetName())
		}
		return cachedImg
	}
	slv.artworkMutex.RUnlock()
	
	log.Printf("🎨 [ARTWORK] Cache miss for item: %s, checking %d songs for artwork", item.GetName(), len(item.GetSongs()))
	
	// Try to get artwork from item
	artworkData := item.GetArtwork()
	if len(artworkData) == 0 {
		log.Printf("🎨 [ARTWORK] No artwork data found for item: %s, using placeholder", item.GetName())
		return slv.placeholderImg
	}
	
	log.Printf("🎨 [ARTWORK] Found artwork data for item: %s (%d bytes)", item.GetName(), len(artworkData))
	
	// Create image from artwork data
	reader := bytes.NewReader(artworkData)
	img, format, err := image.Decode(reader)
	if err != nil {
		log.Printf("⚠️ [ARTWORK] Failed to decode artwork for item %s: %v", item.GetName(), err)
		return slv.placeholderImg
	}
	
	log.Printf("🎨 [ARTWORK] Successfully decoded %s artwork for item: %s", format, item.GetName())
	
	// Create Fyne canvas image
	canvasImg := canvas.NewImageFromImage(img)
	canvasImg.FillMode = canvas.ImageFillContain
	
	// Cache the image
	slv.artworkMutex.Lock()
	slv.artworkCache[itemID] = canvasImg
	slv.artworkMutex.Unlock()
	
	log.Printf("🎨 [ARTWORK] Cached artwork for item: %s", item.GetName())
	
	return canvasImg
}

// getAlbumArtwork retrieves or creates artwork for an album (with caching) - DEPRECATED, use getDisplayItemArtwork
func (slv *SongListView) getAlbumArtwork(album *models.Album) *canvas.Image {
	// Convert album to AlbumItem and use new method
	albumItem := &models.AlbumItem{Album: album}
	return slv.getDisplayItemArtwork(albumItem)
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

	// Display list (shows both albums and folders)
	slv.songList = widget.NewList(
		func() int {
			// Return the actual number of display items (albums + folders)
			return slv.musicLibrary.GetDisplayItemCount()
		},
		func() fyne.CanvasObject {
			// Create modern card template for display items (artwork will be set in update function)
			displayCard := customTheme.NewModernCardWithArtwork("Item Name", "Type • X tracks", nil, slv.placeholderImg)
			return displayCard
		},
		func(id widget.ListItemID, obj fyne.CanvasObject) {
			// Update display item with actual data
			displayItems := slv.musicLibrary.GetDisplayItems()
			log.Printf("🎵 [DEBUG] Updating display item %d, total items: %d", id, len(displayItems))
			
			if id < len(displayItems) {
				item := displayItems[id]
				if card, ok := obj.(*customTheme.ModernCard); ok {
					log.Printf("🎵 [DEBUG] Setting display item %d: %s (folder: %t)", id, item.GetName(), item.IsFolder())
					
					// Update card content
					card.Title = item.GetName()
					card.Subtitle = item.GetSubtitle()
					
					// Update artwork
					artwork := slv.getDisplayItemArtwork(item)
					card.Artwork = artwork
					log.Printf("🎨 [ARTWORK] Set artwork for item %s (artwork is placeholder: %t)", item.GetName(), artwork == slv.placeholderImg)
					
					card.Refresh()
				} else {
					log.Printf("❌ [DEBUG] Display item %d is not a ModernCard", id)
				}
			} else {
				log.Printf("⚠️ [DEBUG] Display item %d out of bounds (items: %d)", id, len(displayItems))
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

	// Main content area - use Border layout to give list maximum vertical space
	slv.content = container.NewBorder(
		headerContent,    // top - header gets only the space it needs
		nil,             // bottom
		nil,             // left  
		nil,             // right
		slv.centerStack, // center - list gets all remaining space
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
	displayItemCount := slv.musicLibrary.GetDisplayItemCount()
	log.Printf("🎵 [DEBUG] updateCenterStack called, display item count: %d", displayItemCount)
	
	// Clear the stack
	slv.centerStack.Objects = nil
	
	// Add the appropriate content
	if displayItemCount > 0 {
		log.Println("🎵 [DEBUG] Showing display list (albums + folders)")
		// Let the song list use available space directly
		slv.centerStack.Add(slv.songList)
		// Also refresh the display list data
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
		
		// Debug: Log current display item count
		displayItemCount := slv.musicLibrary.GetDisplayItemCount()
		albumCount := slv.musicLibrary.GetAlbumCount()
		log.Printf("🎵 [DEBUG] Display item count in callback: %d (%d albums)", displayItemCount, albumCount)
		
		// Debug: Log first few display items if available
		if displayItemCount > 0 {
			displayItems := slv.musicLibrary.GetDisplayItems()
			firstItem := displayItems[0]
			log.Printf("🎵 [DEBUG] First display item: %s (folder: %t)", firstItem.GetName(), firstItem.IsFolder())
		}
		
		slv.refreshContent()
		if slv.songList != nil {
			log.Println("🎵 [DEBUG] Refreshing display list")
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