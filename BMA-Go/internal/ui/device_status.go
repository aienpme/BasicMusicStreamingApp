package ui

import (
	"fmt"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/layout"
	"fyne.io/fyne/v2/widget"

	"bma-go/internal/server"
)

// DeviceStatusView shows connected device information
type DeviceStatusView struct {
	serverManager *server.ServerManager
	deviceLabel   *widget.Label
	libraryLabel  *widget.Label
	content       *fyne.Container
}

// NewDeviceStatusView creates a new device status view
func NewDeviceStatusView(serverManager *server.ServerManager) *DeviceStatusView {
	view := &DeviceStatusView{
		serverManager: serverManager,
	}
	view.initialize()
	view.startPeriodicUpdates()
	return view
}

// initialize sets up the UI components
func (view *DeviceStatusView) initialize() {
	view.deviceLabel = widget.NewLabel("No devices connected")
	view.deviceLabel.TextStyle = fyne.TextStyle{Italic: true}
	
	view.libraryLabel = widget.NewLabel("No library")
	view.libraryLabel.TextStyle = fyne.TextStyle{Bold: true}
	
	// Create horizontal layout with device status on left, library stats on right
	statusContent := container.NewHBox(
		view.deviceLabel,
		layout.NewSpacer(),
		view.libraryLabel,
	)
	
	view.content = container.NewVBox(statusContent)
	view.updateDeviceStatus()
}

// updateDeviceStatus refreshes the device connection display
func (view *DeviceStatusView) updateDeviceStatus() {
	devices := view.serverManager.GetConnectedDevices()
	count := len(devices)
	
	switch count {
	case 0:
		view.deviceLabel.SetText("No devices connected")
	case 1:
		view.deviceLabel.SetText("1 device connected")
	default:
		view.deviceLabel.SetText(fmt.Sprintf("%d devices connected", count))
	}
}

// startPeriodicUpdates starts background UI updates
func (view *DeviceStatusView) startPeriodicUpdates() {
	go func() {
		ticker := time.NewTicker(2 * time.Second)
		defer ticker.Stop()

		for range ticker.C {
			view.updateDeviceStatus()
		}
	}()
}

// UpdateLibraryStats updates the library statistics display
func (view *DeviceStatusView) UpdateLibraryStats(albumCount, songCount int) {
	if albumCount == 0 && songCount == 0 {
		view.libraryLabel.SetText("No library")
	} else {
		view.libraryLabel.SetText(fmt.Sprintf("%d albums • %d songs", albumCount, songCount))
	}
}

// GetContent returns the UI content for display
func (view *DeviceStatusView) GetContent() fyne.CanvasObject {
	return view.content
} 