package ui

import (
	"bytes"
	"image"
	_ "image/png"
	"log"
	"os"
	"os/exec"
	"runtime"
	"strings"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/dialog"
	"fyne.io/fyne/v2/widget"

	"bma-go/internal/models"
	customTheme "bma-go/internal/ui/theme"
)

// WelcomeStep - Step 1: Welcome screen
type WelcomeStep struct {
	content fyne.CanvasObject
}

func NewWelcomeStep() *WelcomeStep {
	return &WelcomeStep{}
}

func (s *WelcomeStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		// Title without emoji
		title := widget.NewLabelWithStyle("Welcome", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		subtitle := widget.NewLabelWithStyle("BMA - Basic Music App", fyne.TextAlignCenter, fyne.TextStyle{})
		
		description := widget.NewLabelWithStyle(
			"Let's get you set up to stream your music library.\n\nThis quick setup will help you:",
			fyne.TextAlignCenter, 
			fyne.TextStyle{},
		)
		
		// Feature list without icons - cleaner look
		features := widget.NewLabelWithStyle(
			"• Install Tailscale for remote access\n• Download the Android app\n• Select your music folder\n• Automatic server startup & pairing",
			fyne.TextAlignLeading,
			fyne.TextStyle{},
		)
		
		// Modern card with all content
		welcomeCard := customTheme.NewModernCard(
			"",
			"",
			container.NewVBox(
				title,
				subtitle,
				widget.NewSeparator(),
				description,
				features,
			),
		)
		
		s.content = container.NewPadded(welcomeCard)
	}
	return s.content
}

func (s *WelcomeStep) GetTitle() string { return "Welcome" }
func (s *WelcomeStep) OnEnter()         {}
func (s *WelcomeStep) OnExit()          {}
func (s *WelcomeStep) CanContinue() bool { return true }
func (s *WelcomeStep) GetNextAction() func() { return nil }

// TailscaleStep - Step 2: Tailscale installation
type TailscaleStep struct {
	content        fyne.CanvasObject
	isInstalled    bool
	statusBadge    *customTheme.StatusBadge
	downloadButton *customTheme.ModernButton
	recheckButton  *customTheme.ModernButton
	onStateChange  func() // Callback when installation state changes
}

func NewTailscaleStep() *TailscaleStep {
	return &TailscaleStep{}
}

// openBrowser opens the given URL in the default browser across platforms
func (s *TailscaleStep) openBrowser(url string) error {
	var cmd *exec.Cmd
	
	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", url)
	case "linux":
		cmd = exec.Command("xdg-open", url)
	case "windows":
		cmd = exec.Command("cmd", "/c", "start", url)
	default:
		log.Printf("🌐 [BROWSER] Unsupported platform: %s", runtime.GOOS)
		return nil // Don't return error, just log
	}
	
	if err := cmd.Start(); err != nil {
		log.Printf("🌐 [BROWSER] Failed to open browser: %v", err)
		return err
	}
	
	log.Printf("🌐 [BROWSER] Opened %s on %s", url, runtime.GOOS)
	return nil
}

func (s *TailscaleStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Tailscale Setup", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		description := widget.NewLabelWithStyle(
			"Tailscale enables secure remote access to your music server.\nInstall Tailscale to stream your music from anywhere.",
			fyne.TextAlignCenter,
			fyne.TextStyle{},
		)
		
		// Status badge
		s.statusBadge = customTheme.NewStatusBadge("Checking installation...", customTheme.StatusInfo)
		statusContainer := container.NewCenter(s.statusBadge)
		
		// Clean buttons without icons
		s.downloadButton = customTheme.NewModernButton("Open Download Page", func() {
			// Open browser to Tailscale download page using cross-platform method
			if err := s.openBrowser("https://tailscale.com/download"); err != nil {
				log.Printf("🌐 [BROWSER] Failed to open Tailscale download page: %v", err)
			}
		})
		s.downloadButton.SetImportance(widget.HighImportance)
		
		s.recheckButton = customTheme.NewModernButton("Check Again", func() {
			s.checkTailscaleInstallation()
		})
		
		buttonContainer := container.NewHBox(
			s.downloadButton,
			s.recheckButton,
		)
		
		// Modern card
		tailscaleCard := customTheme.NewModernCard(
			"",
			"",
			container.NewVBox(
				title,
				description,
				widget.NewSeparator(),
				statusContainer,
				buttonContainer,
			),
		)
		
		s.content = container.NewPadded(tailscaleCard)
	}
	return s.content
}

func (s *TailscaleStep) checkTailscaleInstallation() {
	log.Println("🔍 [TAILSCALE] Starting Tailscale detection...")
	
	// Update status to show we're checking
	s.statusBadge.Status = "Checking..."
	s.statusBadge.BadgeColor = customTheme.StatusInfo
	s.statusBadge.Refresh()
	
	// Check if Tailscale is installed
	installPath, isInstalled := s.findTailscaleInstallation()
	
	if !isInstalled {
		log.Println("❌ [TAILSCALE] Not found - showing download options")
		s.isInstalled = false
		s.statusBadge.Status = "Not Found"
		s.statusBadge.BadgeColor = customTheme.TextMuted
		s.statusBadge.Refresh()
		s.downloadButton.Show()
		s.recheckButton.Show()
		
		if s.onStateChange != nil {
			s.onStateChange()
		}
		return
	}
	
	log.Printf("✅ [TAILSCALE] Found installation at: %s", installPath)
	
	// Now check if it's actually running/functional
	s.statusBadge.Status = "Verifying..."
	s.statusBadge.BadgeColor = customTheme.StatusInfo
	s.statusBadge.Refresh()
	
	isRunning, statusMsg := s.checkTailscaleStatus(installPath)
	
	if isRunning {
		log.Printf("🟢 [TAILSCALE] Running and connected: %s", statusMsg)
		s.isInstalled = true
		s.statusBadge.Status = "Connected"
		s.statusBadge.BadgeColor = customTheme.StatusSuccess
		s.statusBadge.Refresh()
		s.downloadButton.Hide()
		s.recheckButton.Hide()
	} else {
		log.Printf("🟡 [TAILSCALE] Installed but not running: %s", statusMsg)
		s.isInstalled = false
		s.statusBadge.Status = "Installed (Not Running)"
		s.statusBadge.BadgeColor = customTheme.StatusWarning
		s.statusBadge.Refresh()
		s.downloadButton.Hide()
		s.recheckButton.Show()
	}
	
	// Notify wizard that state has changed
	if s.onStateChange != nil {
		s.onStateChange()
	}
}

// findTailscaleInstallation checks for Tailscale installation and returns path + status
func (s *TailscaleStep) findTailscaleInstallation() (string, bool) {
	// Method 1: Check if running in Flatpak and use flatpak-spawn
	if s.isRunningInFlatpak() {
		log.Println("🔍 [TAILSCALE] Checking Flatpak environment...")
		if s.checkTailscaleViaFlatpak() {
			log.Println("✅ [TAILSCALE] Found via flatpak-spawn")
			return "flatpak-spawn", true
		}
		log.Println("❌ [TAILSCALE] Not found in Flatpak")
	}
	
	// Method 2: Check if tailscale CLI is in PATH
	log.Println("🔍 [TAILSCALE] Checking PATH...")
	if path, err := exec.LookPath("tailscale"); err == nil {
		log.Printf("✅ [TAILSCALE] Found in PATH: %s", path)
		return path, true
	}
	log.Println("❌ [TAILSCALE] Not found in PATH")
	
	// Method 3: Check for macOS app installation
	if runtime.GOOS == "darwin" {
		log.Println("🔍 [TAILSCALE] Checking macOS app locations...")
		if path := s.checkMacOSApp(); path != "" {
			log.Printf("✅ [TAILSCALE] Found macOS app: %s", path)
			return path, true
		}
		log.Println("❌ [TAILSCALE] macOS app not found")
	}
	
	// Method 4: Check for Homebrew installation
	if runtime.GOOS == "darwin" {
		log.Println("🔍 [TAILSCALE] Checking Homebrew locations...")
		if path := s.checkHomebrewInstallation(); path != "" {
			log.Printf("✅ [TAILSCALE] Found Homebrew installation: %s", path)
			return path, true
		}
		log.Println("❌ [TAILSCALE] Homebrew installation not found")
	}
	
	// Method 5: Check for Linux package manager installations
	if runtime.GOOS == "linux" {
		log.Println("🔍 [TAILSCALE] Checking Linux package locations...")
		linuxPaths := []string{
			"/usr/bin/tailscale",
			"/usr/local/bin/tailscale",
			"/snap/bin/tailscale",
		}
		for _, path := range linuxPaths {
			if _, err := os.Stat(path); err == nil {
				log.Printf("✅ [TAILSCALE] Found Linux installation: %s", path)
				return path, true
			}
		}
		log.Println("❌ [TAILSCALE] Linux package installations not found")
	}
	
	log.Println("❌ [TAILSCALE] No installation found")
	return "", false
}

// checkMacOSApp checks if Tailscale is installed as a macOS app and returns the path
func (s *TailscaleStep) checkMacOSApp() string {
	// Check for standard macOS app installation
	appPaths := []string{
		"/Applications/Tailscale.app/Contents/MacOS/Tailscale",
		"/System/Applications/Tailscale.app/Contents/MacOS/Tailscale",
	}
	
	for _, path := range appPaths {
		if _, err := os.Stat(path); err == nil {
			return path
		}
	}
	
	return ""
}

// checkHomebrewInstallation checks for Homebrew-installed tailscale and returns the path
func (s *TailscaleStep) checkHomebrewInstallation() string {
	brewPaths := []string{
		"/usr/local/bin/tailscale",
		"/opt/homebrew/bin/tailscale",
	}
	
	for _, path := range brewPaths {
		if _, err := os.Stat(path); err == nil {
			return path
		}
	}
	
	return ""
}

// isRunningInFlatpak checks if the application is running inside a Flatpak sandbox
func (s *TailscaleStep) isRunningInFlatpak() bool {
	cmd := exec.Command("sh", "-c", "echo $FLATPAK_ID")
	output, err := cmd.Output()
	return err == nil && strings.TrimSpace(string(output)) != ""
}

// checkTailscaleViaFlatpak checks if Tailscale is accessible via flatpak-spawn --host
func (s *TailscaleStep) checkTailscaleViaFlatpak() bool {
	// Test if flatpak-spawn can access tailscale on host
	cmd := exec.Command("flatpak-spawn", "--host", "tailscale", "version")
	err := cmd.Run()
	return err == nil
}

// checkTailscaleStatus verifies if Tailscale is actually running and connected
func (s *TailscaleStep) checkTailscaleStatus(installPath string) (bool, string) {
	log.Printf("🔍 [TAILSCALE] Checking status using: %s", installPath)
	
	var cmd *exec.Cmd
	
	// Handle different installation types
	if installPath == "flatpak-spawn" {
		cmd = exec.Command("flatpak-spawn", "--host", "tailscale", "status")
	} else {
		// For regular installations, try to run tailscale status
		cmd = exec.Command("tailscale", "status")
	}
	
	output, err := cmd.Output()
	if err != nil {
		log.Printf("❌ [TAILSCALE] Status command failed: %v", err)
		return false, "Status command failed"
	}
	
	statusOutput := string(output)
	log.Printf("📊 [TAILSCALE] Status output: %s", statusOutput)
	
	// Check for common indicators that Tailscale is connected
	if strings.Contains(statusOutput, "logged out") {
		return false, "Not logged in"
	}
	if strings.Contains(statusOutput, "stopped") {
		return false, "Stopped"
	}
	if strings.Contains(statusOutput, "NeedsLogin") {
		return false, "Needs login"
	}
	if strings.Contains(statusOutput, "NoState") {
		return false, "Not initialized"
	}
	
	// If we get here and have some output, Tailscale is likely running
	// Look for positive indicators
	if strings.Contains(statusOutput, "100.") || // Tailscale IP address
	   strings.Contains(statusOutput, "Running") ||
	   len(strings.TrimSpace(statusOutput)) > 10 { // Has substantial output
		return true, "Connected"
	}
	
	return false, "Unknown status"
}

// SetStateChangeCallback sets the callback for when installation state changes
func (s *TailscaleStep) SetStateChangeCallback(callback func()) {
	s.onStateChange = callback
}

func (s *TailscaleStep) GetTitle() string { return "Tailscale Setup" }
func (s *TailscaleStep) OnEnter() {
	s.checkTailscaleInstallation()
}
func (s *TailscaleStep) OnExit()          {}
func (s *TailscaleStep) CanContinue() bool { return s.isInstalled }
func (s *TailscaleStep) GetNextAction() func() { return nil }

// AndroidAppStep - Step 3: Android app download
type AndroidAppStep struct {
	content fyne.CanvasObject
}

func NewAndroidAppStep() *AndroidAppStep {
	return &AndroidAppStep{}
}

func (s *AndroidAppStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Download Android App", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		description := widget.NewLabelWithStyle(
			"Scan the QR code below with your Android device\nto download the BMA Android app from GitHub.",
			fyne.TextAlignCenter,
			fyne.TextStyle{},
		)
		
		// Generate QR code for GitHub repository
		qrCode := s.createGitHubQRCode()
		
		// QR code in a card
		qrCard := customTheme.NewModernCard("", "", container.NewCenter(qrCode))
		
		// Clean instructions without icons
		instructions := widget.NewLabelWithStyle(
			"1. Open your camera app\n2. Point it at the QR code\n3. Tap the notification to open GitHub\n4. Download the APK file",
			fyne.TextAlignLeading,
			fyne.TextStyle{},
		)
		
		// Main card
		androidCard := customTheme.NewModernCard(
			"",
			"",
			container.NewVBox(
				title,
				description,
				widget.NewSeparator(),
				qrCard,
				widget.NewSeparator(),
				instructions,
			),
		)
		
		s.content = container.NewPadded(androidCard)
	}
	return s.content
}

func (s *AndroidAppStep) createGitHubQRCode() fyne.CanvasObject {
	// Generate QR code for GitHub repository
	qrBytes, err := models.GenerateSimpleQR("https://github.com/picccassso/BasicMusicStreamingApp", 200)
	if err != nil {
		log.Printf("Error generating QR code: %v", err)
		return widget.NewLabel("📱 QR Code Generation Failed\n(Visit GitHub manually)")
	}
	
	// Convert bytes to image
	img, _, err := image.Decode(bytes.NewReader(qrBytes))
	if err != nil {
		log.Printf("Error decoding QR image: %v", err)
		return widget.NewLabel("📱 QR Code Display Failed\n(Visit GitHub manually)")
	}
	
	// Create canvas image
	qrImage := canvas.NewImageFromImage(img)
	qrImage.FillMode = canvas.ImageFillOriginal
	qrImage.SetMinSize(fyne.NewSize(200, 200))
	
	return qrImage
}

func (s *AndroidAppStep) GetTitle() string { return "Android App" }
func (s *AndroidAppStep) OnEnter()         {}
func (s *AndroidAppStep) OnExit()          {}
func (s *AndroidAppStep) CanContinue() bool { return true }
func (s *AndroidAppStep) GetNextAction() func() { return nil }

// PairingStep - Step 4: Device pairing
type PairingStep struct {
	content fyne.CanvasObject
}

func NewPairingStep() *PairingStep {
	return &PairingStep{}
}

func (s *PairingStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Pair Your Device", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		description := widget.NewLabelWithStyle(
			"Scan this QR code with the BMA Android app\nto pair your device with this music server.",
			fyne.TextAlignCenter,
			fyne.TextStyle{},
		)
		
		// Generate pairing QR code
		qrCode := s.createPairingQRCode()
		
		instructions := widget.NewLabelWithStyle(
			"1. Open the BMA app on your Android device\n2. Tap 'Scan QR Code'\n3. Point your camera at this code",
			fyne.TextAlignLeading,
			fyne.TextStyle{},
		)
		
		s.content = container.NewVBox(
			widget.NewCard("", "", container.NewVBox(
				title,
				description,
				widget.NewSeparator(),
				container.NewCenter(qrCode),
				widget.NewSeparator(),
				instructions,
			)),
		)
	}
	return s.content
}

func (s *PairingStep) createPairingQRCode() fyne.CanvasObject {
	// For setup wizard, create a simple placeholder QR code
	// In real implementation, this would generate actual pairing data
	placeholderData := `{"serverUrl": "http://localhost:8008", "token": "setup-placeholder", "expiresAt": "2024-12-31T23:59:59Z"}`
	
	qrBytes, err := models.GenerateSimpleQR(placeholderData, 200)
	if err != nil {
		log.Printf("Error generating pairing QR code: %v", err)
		return widget.NewLabel("🔗 Pairing QR Code\n(Will be generated when server starts)")
	}
	
	// Convert bytes to image
	img, _, err := image.Decode(bytes.NewReader(qrBytes))
	if err != nil {
		log.Printf("Error decoding pairing QR image: %v", err)
		return widget.NewLabel("🔗 Pairing QR Display Failed\n(Manual pairing available)")
	}
	
	// Create canvas image
	qrImage := canvas.NewImageFromImage(img)
	qrImage.FillMode = canvas.ImageFillOriginal
	qrImage.SetMinSize(fyne.NewSize(200, 200))
	
	return qrImage
}

func (s *PairingStep) GetTitle() string { return "Device Pairing" }
func (s *PairingStep) OnEnter()         {}
func (s *PairingStep) OnExit()          {}
func (s *PairingStep) CanContinue() bool { return true }
func (s *PairingStep) GetNextAction() func() { return nil }

// MusicLibraryStep - Step 5: Music folder selection
type MusicLibraryStep struct {
	content       fyne.CanvasObject
	config        *models.Config
	folderPath    string
	pathCard      *customTheme.ModernCard
	pathLabel     *widget.Label
	selectButton  *customTheme.ModernButton
	window        fyne.Window
	onStateChange func() // Callback when folder selection changes
}

func NewMusicLibraryStep(config *models.Config) *MusicLibraryStep {
	return &MusicLibraryStep{
		config: config,
	}
}

func (s *MusicLibraryStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Select Music Library", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		description := widget.NewLabelWithStyle(
			"Choose the folder containing your music files.\nThe app will scan for MP3 files in this folder and subfolders.",
			fyne.TextAlignCenter,
			fyne.TextStyle{},
		)
		
		// Path display card - keep reference to label for updates
		s.pathLabel = widget.NewLabelWithStyle("No folder selected", fyne.TextAlignCenter, fyne.TextStyle{Italic: true})
		s.pathCard = customTheme.NewModernCard("Selected Folder", "", s.pathLabel)
		
		// Clean select button
		s.selectButton = customTheme.NewModernButton("Select Music Folder", func() {
			s.showFolderDialog()
		})
		s.selectButton.SetImportance(widget.HighImportance)
		
		// Main card
		libraryCard := customTheme.NewModernCard(
			"",
			"",
			container.NewVBox(
				title,
				description,
				widget.NewSeparator(),
				s.pathCard,
				container.NewCenter(s.selectButton),
			),
		)
		
		s.content = container.NewPadded(libraryCard)
	}
	return s.content
}

func (s *MusicLibraryStep) showFolderDialog() {
	if s.window == nil {
		return
	}
	
	dialog.ShowFolderOpen(func(folder fyne.ListableURI, err error) {
		if err != nil || folder == nil {
			return
		}
		
		s.folderPath = folder.Path()
		
		// Update the existing label instead of creating a new one
		s.pathLabel.SetText(s.folderPath)
		s.pathLabel.TextStyle = fyne.TextStyle{} // Remove italic style
		s.pathLabel.Refresh()
		
		// Update the card title
		s.pathCard.Title = "Music Folder Selected"
		s.pathCard.Refresh()
		
		// Save to config
		s.config.SetMusicFolder(s.folderPath)
		
		// Notify wizard that state has changed
		if s.onStateChange != nil {
			s.onStateChange()
		}
	}, s.window)
}

func (s *MusicLibraryStep) SetWindow(window fyne.Window) {
	s.window = window
}

// SetStateChangeCallback sets the callback for when folder selection changes
func (s *MusicLibraryStep) SetStateChangeCallback(callback func()) {
	s.onStateChange = callback
}

func (s *MusicLibraryStep) GetTitle() string { return "Music Library" }
func (s *MusicLibraryStep) OnEnter() {
	// Check if music folder is already configured
	if s.config != nil && s.config.MusicFolder != "" {
		s.folderPath = s.config.MusicFolder
		if s.pathLabel != nil {
			s.pathLabel.SetText(s.folderPath)
			s.pathLabel.TextStyle = fyne.TextStyle{} // Remove italic style
			s.pathLabel.Refresh()
		}
		if s.pathCard != nil {
			s.pathCard.Title = "Music Folder Selected"
			s.pathCard.Refresh()
		}
	}
}
func (s *MusicLibraryStep) OnExit()          {}
func (s *MusicLibraryStep) CanContinue() bool { return s.folderPath != "" }
func (s *MusicLibraryStep) GetNextAction() func() { return nil }

// SetupCompleteStep - Step 6: Setup completion
type SetupCompleteStep struct {
	content fyne.CanvasObject
}

func NewSetupCompleteStep() *SetupCompleteStep {
	return &SetupCompleteStep{}
}

func (s *SetupCompleteStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Setup Complete", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		description := widget.NewLabelWithStyle(
			"Your music server is ready to go.\n\nAfter clicking 'Start Streaming', the server will start automatically\nand a QR code will appear for device pairing.",
			fyne.TextAlignCenter,
			fyne.TextStyle{},
		)
		
		// Clean list without icons
		features := widget.NewLabelWithStyle(
			"• Tailscale installed for remote access\n• Android app ready for download\n• Music library selected\n• Automatic server startup & QR code generation",
			fyne.TextAlignLeading,
			fyne.TextStyle{},
		)
		
		// Success card with gradient effect
		successCard := customTheme.NewModernCard(
			"",
			"",
			container.NewVBox(
				title,
				description,
				widget.NewSeparator(),
				features,
			),
		)
		
		s.content = container.NewPadded(successCard)
	}
	return s.content
}

func (s *SetupCompleteStep) GetTitle() string { return "Setup Complete" }
func (s *SetupCompleteStep) OnEnter()         {}
func (s *SetupCompleteStep) OnExit()          {}
func (s *SetupCompleteStep) CanContinue() bool { return true }
func (s *SetupCompleteStep) GetNextAction() func() { return nil } 