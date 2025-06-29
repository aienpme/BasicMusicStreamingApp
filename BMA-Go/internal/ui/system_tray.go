package ui

import (
	"log"
	
	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/driver/desktop"
)

// SystemTrayManager handles system tray functionality
type SystemTrayManager struct {
	app           fyne.App
	mainWindow    fyne.Window
	quitCallback  func()
	hasShownNotification bool
}

// NewSystemTrayManager creates a new system tray manager
func NewSystemTrayManager(app fyne.App, window fyne.Window, quitCallback func()) *SystemTrayManager {
	return &SystemTrayManager{
		app:          app,
		mainWindow:   window,
		quitCallback: quitCallback,
		hasShownNotification: false,
	}
}

// Setup initializes the system tray
func (st *SystemTrayManager) Setup() {
	// Check if the app supports system tray
	if desk, ok := st.app.(desktop.App); ok {
		// Create system tray menu
		showHideItem := fyne.NewMenuItem("Show Window", func() {
			st.showWindow()
		})
		
		quitItem := fyne.NewMenuItem("Quit BMA", func() {
			log.Println("🛑 Quit BMA selected from system tray")
			if st.quitCallback != nil {
				st.quitCallback()
			}
			st.app.Quit()
		})
		
		menu := fyne.NewMenu("BMA", showHideItem, quitItem)
		desk.SetSystemTrayMenu(menu)
		
		// Set up icon if available
		st.setupIcon(desk)
		
		// Store reference to update menu items
		st.mainWindow.SetOnClosed(func() {
			// Window is closing/hiding - update menu to show "Show Window"
			showHideItem.Label = "Show Window"
			showHideItem.Action = func() {
				st.showWindow()
			}
			menu.Refresh()
		})
		
		log.Println("✅ System tray initialized")
	} else {
		log.Println("⚠️ System tray not supported on this platform")
	}
}

// setupIcon sets up the system tray icon
func (st *SystemTrayManager) setupIcon(desk desktop.App) {
	// For now, we'll use the default app icon
	// In the future, we can embed a custom icon resource
	// The default icon is usually set in the app metadata or falls back to Fyne's default
	log.Println("✅ Using default app icon for system tray")
}

// HandleWindowClose handles the window close event
func (st *SystemTrayManager) HandleWindowClose() {
	// Show notification only once
	if !st.hasShownNotification {
		st.app.SendNotification(&fyne.Notification{
			Title:   "BMA",
			Content: "BMA is still running in the menu bar",
		})
		st.hasShownNotification = true
		log.Println("📢 Showed tray notification")
	}
	
	// Hide the window instead of quitting
	st.mainWindow.Hide()
	log.Println("🙈 Window hidden to system tray")
}

// showWindow shows the main window
func (st *SystemTrayManager) showWindow() {
	st.mainWindow.Show()
	st.mainWindow.RequestFocus()
	log.Println("👁️ Window shown from system tray")
} 