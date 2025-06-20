package ui

import (
	"fmt"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/widget"

	"bma-go/internal/server"
)

// DeviceStatusView shows connected device information
type DeviceStatusView struct {
	serverManager *server.ServerManager
	deviceLabel   *widget.Label
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
	
	view.content = container.NewVBox(view.deviceLabel)
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

// GetContent returns the UI content for display
func (view *DeviceStatusView) GetContent() fyne.CanvasObject {
	return view.content
} 