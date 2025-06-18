package ui

import (
	"fmt"
	"log"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/layout"
	"fyne.io/fyne/v2/widget"
	
	"bma-go/internal/models"
	"bma-go/internal/server"
)

// LibraryStatusBar displays library statistics and connected devices
type LibraryStatusBar struct {
	musicLibrary    *models.MusicLibrary
	serverManager   *server.ServerManager
	content         *fyne.Container
	libraryLabel    *widget.Label
	devicesLabel    *widget.Label
	scanProgress    *widget.ProgressBar
}

// NewLibraryStatusBar creates a new library status bar
func NewLibraryStatusBar(musicLibrary *models.MusicLibrary, serverManager *server.ServerManager) *LibraryStatusBar {
	lsb := &LibraryStatusBar{
		musicLibrary:  musicLibrary,
		serverManager: serverManager,
	}
	lsb.initialize()
	lsb.setupCallbacks()
	lsb.startDeviceMonitoring()
	return lsb
}

// initialize sets up the library status bar components
func (lsb *LibraryStatusBar) initialize() {
	// Library statistics label
	lsb.libraryLabel = widget.NewLabel("No library")
	lsb.libraryLabel.TextStyle = fyne.TextStyle{Bold: true}

	// Connected devices label
	lsb.devicesLabel = widget.NewLabel("No devices")

	// Scanning progress bar with modern styling
	lsb.scanProgress = widget.NewProgressBar()
	lsb.scanProgress.Hide()

	// Main status content - simple layout
	statusContent := container.NewHBox(
		lsb.libraryLabel,
		layout.NewSpacer(),
		lsb.devicesLabel,
	)
	
	// Simple padded container
	lsb.content = container.NewPadded(statusContent)
}

// GetContent returns the library status bar content
func (lsb *LibraryStatusBar) GetContent() fyne.CanvasObject {
	return lsb.content
}

// UpdateLibraryStats updates the library statistics display
func (lsb *LibraryStatusBar) UpdateLibraryStats(albumCount, songCount int) {
	if albumCount == 0 && songCount == 0 {
		lsb.libraryLabel.SetText("No library")
	} else {
		lsb.libraryLabel.SetText(fmt.Sprintf("%d albums • %d songs", albumCount, songCount))
	}
}

// UpdateDeviceCount updates the connected devices count
func (lsb *LibraryStatusBar) UpdateDeviceCount(count int) {
	if count == 0 {
		lsb.devicesLabel.SetText("No devices")
	} else if count == 1 {
		lsb.devicesLabel.SetText("1 device")
	} else {
		lsb.devicesLabel.SetText(fmt.Sprintf("%d devices", count))
	}
}

// ShowScanProgress shows the scanning progress bar
func (lsb *LibraryStatusBar) ShowScanProgress() {
	lsb.scanProgress.Show()
	lsb.scanProgress.SetValue(0)
}

// UpdateScanProgress updates the scanning progress
func (lsb *LibraryStatusBar) UpdateScanProgress(progress float64) {
	lsb.scanProgress.SetValue(progress)
}

// HideScanProgress hides the scanning progress bar
func (lsb *LibraryStatusBar) HideScanProgress() {
	lsb.scanProgress.Hide()
}

// setupCallbacks sets up the MusicLibrary callbacks to update the UI
func (lsb *LibraryStatusBar) setupCallbacks() {
	log.Println("📊 [DEBUG] Setting up LibraryStatusBar callbacks")
	
	// Set up scanning state change callback - keep it simple to avoid deadlocks
	lsb.musicLibrary.SetScanningChangedCallback(func(isScanning bool) {
		log.Printf("📊 [DEBUG] LibraryStatusBar: scanning changed to %v", isScanning)
		
		defer func() {
			if r := recover(); r != nil {
				log.Printf("🔥 [CRASH] Panic in scanning callback: %v", r)
			}
		}()
		
		if isScanning {
			log.Println("📊 [DEBUG] LibraryStatusBar: showing scan progress")
			lsb.ShowScanProgress()
		} else {
			log.Println("📊 [DEBUG] LibraryStatusBar: hiding scan progress")
			lsb.HideScanProgress()
			// Don't call refreshLibraryStats here - let the library changed callback handle it
		}
	})
	
	// Set up library change callback - this will be called after scanning completes
	lsb.musicLibrary.SetLibraryChangedCallback(func() {
		log.Println("📊 [DEBUG] LibraryStatusBar: library changed callback")
		
		defer func() {
			if r := recover(); r != nil {
				log.Printf("🔥 [CRASH] Panic in library changed callback: %v", r)
			}
		}()
		
		lsb.refreshLibraryStats()
	})
	
	log.Println("📊 [DEBUG] LibraryStatusBar: calling initial refresh")
	
	// Initial refresh
	lsb.refreshLibraryStats()
	
	log.Println("📊 [DEBUG] LibraryStatusBar callbacks setup complete")
}

// refreshLibraryStats updates the library statistics from the actual MusicLibrary
func (lsb *LibraryStatusBar) refreshLibraryStats() {
	log.Println("📊 [DEBUG] refreshLibraryStats called")
	
	defer func() {
		if r := recover(); r != nil {
			log.Printf("🔥 [CRASH] Panic in refreshLibraryStats: %v", r)
		}
	}()
	
	if lsb.musicLibrary == nil {
		log.Println("❌ [DEBUG] musicLibrary is nil in refreshLibraryStats")
		return
	}
	
	albumCount := lsb.musicLibrary.GetAlbumCount()
	songCount := lsb.musicLibrary.GetSongCount()
	
	log.Printf("📊 [DEBUG] Retrieved counts: %d albums, %d songs", albumCount, songCount)
	
	lsb.UpdateLibraryStats(albumCount, songCount)
	
	log.Println("📊 [DEBUG] refreshLibraryStats completed")
}

// startDeviceMonitoring starts monitoring connected devices
func (lsb *LibraryStatusBar) startDeviceMonitoring() {
	log.Println("📊 [DEBUG] Starting device monitoring")
	
	go func() {
		for {
			time.Sleep(5 * time.Second) // Wait between checks
			
			defer func() {
				if r := recover(); r != nil {
					log.Printf("🔥 [CRASH] Panic in device monitoring: %v", r)
				}
			}()
			
			connectedDevices := lsb.serverManager.GetConnectedDevices()
			lsb.UpdateDeviceCount(len(connectedDevices))
		}
	}()
} 