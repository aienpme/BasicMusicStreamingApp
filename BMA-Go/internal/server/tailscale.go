package server

import (
	"fmt"
	"log"
	"time"
)

// Tailscale Integration for BMA Go+Fyne
// 
// This package provides Tailscale network integration for secure, peer-to-peer
// communication. The functionality is split across multiple files:
//
// - tailscale.go: Core orchestration and main detection logic
// - tailscale_flatpak.go: Flatpak sandbox detection and handling
// - tailscale_detection.go: Binary detection and validation
// - tailscale_service.go: Service/daemon detection across platforms
// - tailscale_network.go: Network interface and IP detection
// - tailscale_status.go: Connection status and monitoring
//
// Features:
// - Auto-detection of Tailscale installation and status
// - HTTP over Tailscale (network-level encryption)
// - Dynamic URL generation (local vs Tailscale)
// - Cross-platform support (Linux, macOS, Windows)
// - Flatpak container support

// TailscaleManager handles Tailscale detection and integration
type TailscaleManager struct {
	isAvailable bool
	hostname    string
	status      string
	lastCheck   time.Time
}

// NewTailscaleManager creates a new Tailscale manager
func NewTailscaleManager() *TailscaleManager {
	return &TailscaleManager{}
}

// checkTailscaleStatus is the main orchestration function that detects Tailscale
// installation and configures the server to use it if available
func (sm *ServerManager) checkTailscaleStatus() {
	log.Println("🔍 Starting Tailscale detection...")
	
	// Check for Flatpak environment first
	if sm.isRunningInFlatpak() {
		log.Println("🔍 [FLATPAK] Flatpak environment detected, running comprehensive host access tests")
		sm.debugFlatpakHostAccess()
		
		// Skip old debugging if Flatpak detection succeeded
		if sm.useFlatpakSpawn {
			log.Println("🔍 [FLATPAK] Skipping sandbox debugging - flatpak-spawn working")
		} else {
			log.Println("🔍 [FLATPAK] Flatpak-spawn failed, falling back to sandbox debugging")
			sm.debugEnvironment()
		}
	} else {
		// Not in Flatpak, run normal debugging
		sm.debugEnvironment()
	}
	
	// Try to detect Tailscale binary first
	tailscalePath := sm.detectTailscaleBinary()
	
	// If binary not found, try service/daemon detection
	if tailscalePath == "" {
		log.Println("⚠️ Tailscale binary not found, trying service/daemon detection...")
		if sm.detectTailscaleService() {
			// Service is running, try to find binary again with fallback methods
			if fallbackPath := sm.findTailscaleWithFallbacks(); fallbackPath != "" {
				tailscalePath = fallbackPath
				log.Printf("✅ Found Tailscale via service detection: %s", tailscalePath)
			} else {
				log.Println("❌ Tailscale service detected but no usable binary found")
				sm.HasTailscale = false
				sm.TailscaleURL = ""
				return
			}
		} else {
			log.Println("⚠️ Tailscale service not detected, trying network interface detection...")
			if sm.detectTailscaleNetwork() {
				// Network interface detected, try to find ANY tailscale binary as final attempt
				if networkPath := sm.findAnyTailscaleBinary(); networkPath != "" {
					tailscalePath = networkPath
					log.Printf("✅ Found Tailscale via network detection: %s", tailscalePath)
				} else {
					// Even without binary, we can try to use Tailscale via network
					log.Println("⚠️ Tailscale network detected but no binary found - will try IP-based connection")
					if tailscaleIP := sm.getTailscaleIPFromNetwork(); tailscaleIP != "" {
						sm.HasTailscale = true
						sm.TailscaleURL = fmt.Sprintf("http://%s", tailscaleIP)
						sm.ClearQRCache() // Clear cache when Tailscale config changes
						log.Printf("✅ Tailscale configured via network IP: %s", sm.TailscaleURL)
						return
					}
					log.Println("❌ Could not determine Tailscale IP from network")
					sm.HasTailscale = false
					sm.TailscaleURL = ""
					return
				}
			} else {
				log.Println("❌ Tailscale not detected - binary, service, and network not found")
				sm.HasTailscale = false
				sm.TailscaleURL = ""
				return
			}
		}
	}
	
	log.Printf("✅ Found Tailscale binary at: %s", tailscalePath)
	
	// Check if Tailscale is actually connected
	if sm.checkTailscaleConnection(tailscalePath) {
		// Get Tailscale hostname
		if hostname := sm.getTailscaleHostname(tailscalePath); hostname != "" {
			sm.HasTailscale = true
			sm.TailscaleURL = fmt.Sprintf("http://%s", hostname)
			sm.ClearQRCache() // Clear cache when Tailscale config changes
			log.Printf("✅ Tailscale configured: %s", sm.TailscaleURL)
		} else {
			log.Println("❌ Failed to get Tailscale hostname")
			sm.HasTailscale = false
			sm.TailscaleURL = ""
		}
	} else {
		log.Println("❌ Tailscale is not in Running state")
		sm.HasTailscale = false
		sm.TailscaleURL = ""
	}
} 