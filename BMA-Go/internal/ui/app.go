package ui

import (
	"log"
	
	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"

	"bma-go/internal/models"
	"bma-go/internal/server"
)

// MainUI represents the main application UI, equivalent to ContentView in Swift
type MainUI struct {
	app               fyne.App
	window            fyne.Window
	config            *models.Config
	serverManager     *server.ServerManager
	musicLibrary      *models.MusicLibrary
	serverStatus      *ServerStatusBar
	deviceStatus      *DeviceStatusView
	songList          *SongListView
	libraryStatus     *LibraryStatusBar
	content           *fyne.Container
	animationCoord    *AnimationCoordinator  // New animation coordinator
}

// NewMainUI creates a new main UI instance
func NewMainUI(app fyne.App, window fyne.Window, config *models.Config) *MainUI {
	ui := &MainUI{
		app:    app,
		window: window,
		config: config,
	}
	ui.initialize()
	return ui
}

// initialize sets up the UI components
func (ui *MainUI) initialize() {
	// Create a real ServerManager instance
	ui.serverManager = server.NewServerManager()
	
	// Create a MusicLibrary instance
	ui.musicLibrary = models.NewMusicLibrary()
	
	// Connect the MusicLibrary to the ServerManager
	ui.serverManager.SetMusicLibrary(ui.musicLibrary)
	
	// Create UI components connected to the real server manager and music library
	ui.serverStatus = NewServerStatusBar(ui.serverManager)
	ui.deviceStatus = NewDeviceStatusView(ui.serverManager)
	ui.songList = NewSongListView(ui.musicLibrary)
	ui.libraryStatus = NewLibraryStatusBar(ui.musicLibrary, ui.serverManager)
	
	// Connect library updates to server status bar
	ui.setupLibraryStatusConnection()

	// Set the parent window for dialogs
	ui.songList.SetParentWindow(ui.window)

	// Create animation coordinator
	qrSection := ui.serverStatus.GetQRSection()
	ui.animationCoord = NewAnimationCoordinator(qrSection, ui.songList, ui.window)
	
	// Connect animation coordinator to server status bar
	ui.serverStatus.SetAnimationCoordinator(ui.animationCoord)
	
	// Note: Don't call ForceState here - let components initialize naturally
	// The QR section starts hidden by default, albums start visible

	// Create the main layout matching ContentView.swift structure:
	// VStack(spacing: 0) {
	//   ServerStatusBar().padding().background(Color.gray.opacity(0.1))
	//   HStack { SongListView().frame(minWidth: 400) }.frame(maxHeight: .infinity)
	//   LibraryStatusBar().padding().background(Color.gray.opacity(0.1))
	// }

	// Use BorderContainer to properly position top/bottom bars, but constrain center
	topSection := container.NewVBox(
		ui.serverStatus.GetContent(),
		ui.deviceStatus.GetContent(),
	)
	
	ui.content = container.NewBorder(
		// Top: Server status + device status
		topSection,
		// Bottom: nil (removed library status bar)
		nil,
		// Left & Right: nil
		nil, nil,
		// Center: Song list (size-constrained in SongListView)
		ui.songList.GetContent(),
	)
}

// setupLibraryStatusConnection connects the music library to update the device status bar
func (ui *MainUI) setupLibraryStatusConnection() {
	// Set up library change callback to update device status bar
	ui.musicLibrary.SetLibraryChangedCallback(func() {
		albumCount := ui.musicLibrary.GetAlbumCount()
		songCount := ui.musicLibrary.GetSongCount()
		ui.deviceStatus.UpdateLibraryStats(albumCount, songCount)
	})
	
	// Initial update
	albumCount := ui.musicLibrary.GetAlbumCount()
	songCount := ui.musicLibrary.GetSongCount()
	ui.deviceStatus.UpdateLibraryStats(albumCount, songCount)
}

// LoadMusicLibrary loads the music library from the configured folder
func (ui *MainUI) LoadMusicLibrary() {
	// Check if music folder is configured in the passed config
	if ui.config.MusicFolder == "" {
		log.Println("⚠️ No music folder configured")
		return
	}
	
	log.Printf("🎵 Loading music library from: %s", ui.config.MusicFolder)
	
	// Use SelectFolder which sets the path and scans the library
	go ui.musicLibrary.SelectFolder(ui.config.MusicFolder)
	
	// Automatically start the server after music library loading
	go ui.AutoStartServer()
}

// AutoStartServer automatically starts the server and generates QR code for seamless UX
func (ui *MainUI) AutoStartServer() {
	// Wait a moment for music library to start loading
	log.Println("🚀 Auto-starting server for seamless experience...")
	
	// Start the server automatically
	err := ui.serverManager.StartServer()
	if err != nil {
		log.Printf("❌ Auto-start server failed: %v", err)
		return
	}
	
	log.Println("✅ Server auto-started successfully!")
	
	// Automatically generate and show QR code (async to prevent UI lag)
	go ui.serverStatus.AutoGenerateQR()
}

// GetContent returns the main UI content for display
func (ui *MainUI) GetContent() fyne.CanvasObject {
	return ui.content
}

// Cleanup handles application termination cleanup
func (ui *MainUI) Cleanup() {
	// Stop file system watcher before app terminates
	if ui.musicLibrary != nil {
		ui.musicLibrary.StopWatching()
	}
	
	// Ensure server is stopped before app terminates (like SwiftUI onReceive)
	if ui.serverManager != nil {
		ui.serverManager.Cleanup()
	}
}

// GetServerStatus returns the server status bar for external access
func (ui *MainUI) GetServerStatus() *ServerStatusBar {
	return ui.serverStatus
}

// GetAnimationCoordinator returns the animation coordinator for external access
func (ui *MainUI) GetAnimationCoordinator() *AnimationCoordinator {
	return ui.animationCoord
} 