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
	ui.songList = NewSongListView(ui.musicLibrary)
	ui.libraryStatus = NewLibraryStatusBar(ui.musicLibrary, ui.serverManager)

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
	ui.content = container.NewBorder(
		// Top: Server status bar (stays at top)
		ui.serverStatus.GetContent(),
		// Bottom: Library status bar (stays at window bottom)
		ui.libraryStatus.GetContent(),
		// Left & Right: nil
		nil, nil,
		// Center: Song list (size-constrained in SongListView)
		ui.songList.GetContent(),
	)
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
	
	// Automatically generate and show QR code
	ui.serverStatus.AutoGenerateQR()
}

// GetContent returns the main UI content for display
func (ui *MainUI) GetContent() fyne.CanvasObject {
	return ui.content
}

// Cleanup handles application termination cleanup
func (ui *MainUI) Cleanup() {
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