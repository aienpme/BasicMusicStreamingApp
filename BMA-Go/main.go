package main

import (
	"log"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/app"

	"bma-go/internal/models"
	"bma-go/internal/ui"
	"bma-go/internal/ui/theme"
)

// Window animation parameters
const (
	baseWindowHeight   = 580  // Added 30px more space for bottom status bar breathing room
	qrSectionHeight    = 80   // Reduced to 80px due to compact QR layout optimization
	animationDuration  = 300 * time.Millisecond
	animationSteps    = 30
)

func main() {
	log.Println("🚀 Starting BMA (Basic Music App) - Go+Fyne Edition")

	// Load configuration
	config, err := models.LoadConfig()
	if err != nil {
		log.Printf("⚠️ Error loading config: %v", err)
		config = &models.Config{SetupComplete: false}
	}

	// Create Fyne application
	fyneApp := app.New()
	
	// Apply custom theme
	fyneApp.Settings().SetTheme(theme.NewModernDarkTheme())

	// Check if setup is complete
	if !config.SetupComplete {
		log.Println("🔧 First run detected - starting setup wizard")
		showSetupWizard(fyneApp, config)
	} else {
		log.Println("✅ Setup complete - starting main application")
		showMainApplication(fyneApp, config)
	}
}

func showSetupWizard(fyneApp fyne.App, config *models.Config) {
	// Create setup window with modern size
	setupWindow := fyneApp.NewWindow("BMA Setup")
	setupWindow.Resize(fyne.NewSize(700, 600))
	setupWindow.SetFixedSize(true)
	
	// Create main window with modern size (but don't show it yet)
	mainWindow := fyneApp.NewWindow("BMA - Basic Music App")
	mainWindow.Resize(fyne.NewSize(600, float32(baseWindowHeight)))
	mainWindow.SetFixedSize(false) // Allow resizing for animation
	
	// Initialize the main UI with proper parameters
	mainUI := ui.NewMainUI(fyneApp, mainWindow, config)
	mainWindow.SetContent(mainUI.GetContent())
	
	// Setup window resize animation for QR section
	setupWindowAnimation(mainWindow, mainUI)
	
	// Handle app termination cleanup
	mainWindow.SetCloseIntercept(func() {
		log.Println("🛑 App terminating - ensuring server shutdown...")
		mainUI.Cleanup()
		log.Println("✅ App termination cleanup completed")
		fyneApp.Quit()
	})
	
	// Create setup wizard with transition callback
	wizard := ui.NewSetupWizard(config, func() {
		// On setup completion, hide setup window and show main app
		log.Println("✅ Setup completed - transitioning to main application")
		log.Println("🔧 [DEBUG] About to call LoadMusicLibrary()")
		
		// Load the music library now that setup is complete
		mainUI.LoadMusicLibrary()
		
		log.Println("🔧 [DEBUG] LoadMusicLibrary() call returned")
		log.Println("🔧 [DEBUG] About to hide setup window")
		
		setupWindow.Hide()
		mainWindow.Show()
		
		log.Println("🔧 [DEBUG] Windows switched - transition complete")
	})
	
	wizard.SetWindow(setupWindow)
	setupWindow.SetContent(wizard.GetContent())
	
	// Show setup window and run (this is the only ShowAndRun call)
	setupWindow.ShowAndRun()
}

func showMainApplication(fyneApp fyne.App, config *models.Config) {
	// Create and show main window with modern size
	mainWindow := fyneApp.NewWindow("BMA - Basic Music App")
	mainWindow.Resize(fyne.NewSize(600, float32(baseWindowHeight)))
	mainWindow.SetFixedSize(false) // Allow resizing for animation
	
	// Initialize the main UI with proper parameters
	mainUI := ui.NewMainUI(fyneApp, mainWindow, config)
	
	// Set the main content
	mainWindow.SetContent(mainUI.GetContent())
	
	// Setup window resize animation for QR section
	setupWindowAnimation(mainWindow, mainUI)
	
	// Handle app termination cleanup
	mainWindow.SetCloseIntercept(func() {
		log.Println("🛑 App terminating - ensuring server shutdown...")
		mainUI.Cleanup()
		log.Println("✅ App termination cleanup completed")
		fyneApp.Quit()
	})

	// Load the music library AFTER UI is shown and initialized
	// This ensures UI callbacks are ready to handle library changes
	go func() {
		time.Sleep(100 * time.Millisecond) // Brief delay to ensure UI is ready
		log.Println("🎵 Loading music library after UI initialization...")
		mainUI.LoadMusicLibrary()
	}()

	// Show window and run
	mainWindow.ShowAndRun()
}

// setupWindowAnimation configures smooth window resizing when QR section expands/collapses
func setupWindowAnimation(window fyne.Window, mainUI *ui.MainUI) {
	// Get the animation coordinator and set up window resize callback
	animationCoord := mainUI.GetAnimationCoordinator()
	if animationCoord == nil {
		log.Println("❌ No animation coordinator found")
		return
	}
	
	// Set up window resize callback for animation coordinator
	animationCoord.SetWindowResizeCallback(func(expanded bool, onComplete func()) {
		currentHeight := window.Canvas().Size().Height
		var targetHeight float32
		
		if expanded {
			// QR section expanding - animate to expanded height
			targetHeight = float32(baseWindowHeight + qrSectionHeight)
			log.Printf("🪟 [ANIMATION] Window expanding: %.0f → %.0f", currentHeight, targetHeight)
		} else {
			// QR section collapsing - animate back to base height
			targetHeight = float32(baseWindowHeight)
			log.Printf("🪟 [ANIMATION] Window collapsing: %.0f → %.0f", currentHeight, targetHeight)
		}
		
		// Start window resize animation
		animateWindowResize(window, currentHeight, targetHeight, onComplete)
	})
}

// animateWindowResize smoothly resizes the window height
func animateWindowResize(window fyne.Window, fromHeight, toHeight float32, onComplete func()) {
	if fromHeight == toHeight {
		if onComplete != nil {
			onComplete()
		}
		return
	}
	
	log.Printf("🪟 Animating window resize: %.0f → %.0f", fromHeight, toHeight)
	
	go func() {
		stepDuration := animationDuration / animationSteps
		heightDiff := toHeight - fromHeight
		
		for i := 1; i <= animationSteps; i++ {
			progress := float32(i) / float32(animationSteps)
			// Apply ease-in-out curve
			easedProgress := easeInOut(progress)
			
			newHeight := fromHeight + (heightDiff * easedProgress)
			currentWidth := window.Canvas().Size().Width
			
			window.Resize(fyne.NewSize(currentWidth, newHeight))
			
			time.Sleep(stepDuration)
		}
		
		// Ensure final size is exact
		window.Resize(fyne.NewSize(window.Canvas().Size().Width, toHeight))
		log.Printf("✅ Window resize animation complete")
		
		// Call completion callback
		if onComplete != nil {
			onComplete()
		}
	}()
}

// easeInOut provides smooth acceleration and deceleration
func easeInOut(t float32) float32 {
	if t < 0.5 {
		return 2 * t * t
	}
	return -1 + (4-2*t)*t
} 