package ui

import (
	"log"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/layout"
	"fyne.io/fyne/v2/widget"

	"bma-go/internal/server"
	customTheme "bma-go/internal/ui/theme"
)

// ServerStatusBar represents the server status and controls, equivalent to ServerStatusBar.swift
type ServerStatusBar struct {
	serverManager     *server.ServerManager
	serverButton      *customTheme.ModernButton
	serverStatusLabel *widget.Label
	qrButton          *customTheme.ModernButton
	tailscaleLabel    *widget.Label
	refreshButton     *customTheme.ModernButton
	content           *fyne.Container
	qrSection         *QRCodeSection
	mainContainer     *fyne.Container
	qrVisible         bool // Track visibility state
	onResizeRequest   func() // Callback to request a window resize to fit content
	animationCoord    *AnimationCoordinator // Animation coordinator for QR ↔ Albums transitions
}

// NewServerStatusBar creates a new server status bar
func NewServerStatusBar(serverManager *server.ServerManager) *ServerStatusBar {
	bar := &ServerStatusBar{
		serverManager: serverManager,
	}
	bar.initialize()
	bar.startPeriodicUpdates()
	return bar
}

// initialize sets up the UI components
func (bar *ServerStatusBar) initialize() {
	// Server control button - clean style
	bar.serverButton = customTheme.NewModernButton("Server", bar.toggleServer)
	bar.serverButton.SetImportance(widget.MediumImportance)

	// Server status label - subtle text
	bar.serverStatusLabel = widget.NewLabel("Starting")
	bar.serverStatusLabel.TextStyle = fyne.TextStyle{Italic: true}

	// QR code generation button - toggles inline QR
	bar.qrButton = customTheme.NewModernButton("QR Code", bar.toggleQR)
	bar.qrButton.SetImportance(widget.HighImportance)
	bar.qrButton.Disable() // Disabled until server starts

	// Tailscale status label - subtle text
	bar.tailscaleLabel = widget.NewLabel("Checking")
	bar.tailscaleLabel.TextStyle = fyne.TextStyle{Italic: true}

	// Refresh button - smaller, less prominent
	bar.refreshButton = customTheme.NewModernButton("Refresh", bar.refreshStatus)

	// Create a cleaner layout
	statusContent := container.NewHBox(
		bar.serverButton,
		bar.serverStatusLabel,
		layout.NewSpacer(),   // Push QR button to center
		bar.qrButton,
		layout.NewSpacer(),   // Balance the layout
		bar.tailscaleLabel,
		bar.refreshButton,
	)

	// Create QR section and connect its callbacks
	bar.qrSection = NewQRCodeSection()
	bar.qrSection.SetOnRefresh(bar.regenerateQR)
	bar.qrSection.SetOnClose(bar.toggleQR)

	// Main layout
	bar.content = container.NewVBox(statusContent, bar.qrSection)

	// Initial status update
	bar.updateUI()
	
	// Start monitoring for device connections to auto-hide QR codes
	bar.startDeviceMonitoring()
}

// toggleServer starts or stops the HTTP server
func (bar *ServerStatusBar) toggleServer() {
	if bar.serverManager.IsRunning {
		// Stop server
		bar.serverManager.StopServer()
		bar.serverButton.Text = "Start"
		bar.serverButton.Refresh()
		bar.serverStatusLabel.SetText("Stopped")
		bar.qrButton.Disable()
		
		// If QR is visible, hide it.
		if bar.qrSection.IsExpanded() {
			bar.toggleQR()
		}
	} else {
		// Start server manually (in case auto-start failed)
		go func() {
			err := bar.serverManager.StartServer()
			if err != nil {
				bar.serverStatusLabel.SetText("Error")
			} else {
				bar.serverButton.Text = "Stop"
				bar.serverButton.Refresh()
				bar.updateServerStatus()
				bar.qrButton.Enable()
				
				// Auto-generate QR code
				go bar.AutoGenerateQR()
			}
		}()
		bar.serverStatusLabel.SetText("Starting...")
	}
}

// toggleQR toggles between QR code and albums using the animation coordinator
func (bar *ServerStatusBar) toggleQR() {
	if !bar.serverManager.IsRunning && bar.animationCoord != nil && bar.animationCoord.IsAlbumsVisible() {
		return
	}

	// Use animation coordinator if available, otherwise fall back to old behavior
	if bar.animationCoord != nil {
		if bar.animationCoord.IsQRVisible() {
			// HIDE QR: Show albums
			log.Println("🎬 [SERVER] Hiding QR, showing albums")
			bar.qrButton.Text = "QR Code"
			bar.qrButton.Refresh()
			
			// Use animation coordinator for smooth transition
			bar.animationCoord.ShowAlbums(nil)
		} else {
			// SHOW QR: Hide albums, show QR
			log.Println("🎬 [SERVER] Showing QR, hiding albums")
			bar.qrButton.Text = "Hide QR"
			bar.qrButton.Refresh()
			
			// Generate QR code data first
			qrBytes, jsonData, err := bar.serverManager.GenerateQRCode()
			if err != nil {
				log.Printf("❌ QR generation failed: %v", err)
				return
			}
			bar.qrSection.SetQRCode(qrBytes, jsonData)
			
			// Use animation coordinator for smooth transition
			bar.animationCoord.ShowQRCode(nil)
		}
	} else {
		// Fallback to old behavior if no animation coordinator
		log.Println("⚠️ [SERVER] No animation coordinator, using fallback QR toggle")
		if bar.qrSection.IsExpanded() {
			bar.qrButton.Text = "QR Code"
			bar.qrButton.Refresh()
			bar.qrSection.Toggle(nil)
		} else {
			bar.qrButton.Text = "Hide QR"
			bar.qrButton.Refresh()
			
			qrBytes, jsonData, err := bar.serverManager.GenerateQRCode()
			if err != nil {
				log.Printf("❌ QR generation failed: %v", err)
				return
			}
			bar.qrSection.SetQRCode(qrBytes, jsonData)
			bar.qrSection.Toggle(nil)
		}
	}
}

// regenerateQR generates a new QR code while section is open
func (bar *ServerStatusBar) regenerateQR() {
	if !bar.serverManager.IsRunning {
		return
	}

	log.Println("🔄 Regenerating QR code...")

	qrBytes, jsonData, err := bar.serverManager.GenerateQRCode()
	if err != nil {
		log.Printf("❌ QR regeneration failed: %v", err)
		return
	}

	// Update QR section
	bar.qrSection.SetQRCode(qrBytes, jsonData)

	go func() {
		time.Sleep(1 * time.Second) // Give the server a moment
		bar.updateUI()
		bar.toggleQR() // Use the standard toggle logic
	}()
}

// showErrorDialog displays error messages
func (bar *ServerStatusBar) showErrorDialog(message string) {
	dialog := widget.NewPopUp(
		widget.NewCard("Error", message,
			widget.NewButton("OK", func() {}),
		),
		fyne.CurrentApp().Driver().AllWindows()[0].Canvas(),
	)
	dialog.Show()
}

// refreshStatus manually refreshes all status information
func (bar *ServerStatusBar) refreshStatus() {
	bar.serverManager.RefreshTailscaleStatus()
	bar.updateUI()
	
	bar.tailscaleLabel.SetText("Refreshing...")
	
	go func() {
		time.Sleep(2 * time.Second)
		bar.updateTailscaleStatus()
	}()
}

// updateUI updates all UI elements based on current server state
func (bar *ServerStatusBar) updateUI() {
	bar.updateServerStatus()
	bar.updateTailscaleStatus()
}

// updateServerStatus updates the server status display
func (bar *ServerStatusBar) updateServerStatus() {
	if bar.serverManager.IsRunning {
		bar.serverButton.Text = "Stop"
		bar.serverButton.Refresh()
		// Show clean status instead of long URL
		if bar.serverManager.IsTailscaleConfigured() {
			bar.serverStatusLabel.SetText("Running")
		} else {
			bar.serverStatusLabel.SetText("Local")
		}
		bar.qrButton.Enable()
	} else {
		bar.serverButton.Text = "Start"
		bar.serverButton.Refresh()
		bar.serverStatusLabel.SetText("Stopped")
		bar.qrButton.Disable()
	}
}

// updateTailscaleStatus updates the Tailscale status display
func (bar *ServerStatusBar) updateTailscaleStatus() {
	if bar.serverManager.IsTailscaleConfigured() {
		bar.tailscaleLabel.SetText("Tailscale")
	} else {
		bar.tailscaleLabel.SetText("No Tailscale")
	}
}

// startPeriodicUpdates starts background UI updates
func (bar *ServerStatusBar) startPeriodicUpdates() {
	go func() {
		ticker := time.NewTicker(5 * time.Second)
		defer ticker.Stop()

		for range ticker.C {
			bar.updateTailscaleStatus()
		}
	}()
}

// GetContent returns the UI content for display
func (bar *ServerStatusBar) GetContent() fyne.CanvasObject {
	return bar.content
}

// AutoGenerateQR automatically generates and shows QR code for seamless UX
func (bar *ServerStatusBar) AutoGenerateQR() {
	// Wait a moment for server to fully start
	time.Sleep(1 * time.Second)
	
	if !bar.serverManager.IsRunning {
		log.Println("⚠️ Cannot auto-generate QR: server not running")
		return
	}

	log.Println("🔑 Auto-generating QR code for seamless device pairing...")

	// Update UI to reflect server running state
	bar.updateServerStatus()

	// Use the standard toggleQR method to ensure proper animation sequence
	if !bar.qrSection.IsExpanded() {
		bar.toggleQR()
	}
	
	log.Println("✅ QR code auto-displayed inline - ready for device pairing!")
}

// startDeviceMonitoring monitors for device connections to auto-hide QR codes
func (bar *ServerStatusBar) startDeviceMonitoring() {
	log.Println("📱 [DEBUG] Starting device monitoring for QR auto-hide")
	
	go func() {
		var lastDeviceCount int
		for {
			time.Sleep(2 * time.Second)
			
			currentDeviceCount := len(bar.serverManager.GetConnectedDevices())
			
			// If a new device connected and the QR code is visible, hide it.
			if currentDeviceCount > lastDeviceCount && bar.qrSection.IsExpanded() {
				log.Printf("📱 [QR AUTO-HIDE] Device connected! Collapsing QR section.")
				bar.toggleQR()
			}
			
			lastDeviceCount = currentDeviceCount
		}
	}()
}

// SetOnResizeRequest sets the callback for when the status bar needs to resize the window
func (bar *ServerStatusBar) SetOnResizeRequest(callback func()) {
	bar.onResizeRequest = callback
}

// GetQRSection returns the QR code section for external access
func (bar *ServerStatusBar) GetQRSection() *QRCodeSection {
	return bar.qrSection
}

// SetAnimationCoordinator sets the animation coordinator for QR ↔ Albums transitions
func (bar *ServerStatusBar) SetAnimationCoordinator(coord *AnimationCoordinator) {
	bar.animationCoord = coord
}

// onQRSectionExpanded is no longer needed
// func (bar *ServerStatusBar) onQRSectionExpanded(expanded bool) {}

// generateAndShowQR is replaced by toggleQR
// func (bar *ServerStatusBar) generateAndShowQR() {} 