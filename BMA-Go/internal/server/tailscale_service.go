package server

import (
	"log"
	"os/exec"
	"strings"
)

// Service detection functionality for Tailscale

// detectTailscaleService checks if Tailscale service/daemon is running
func (sm *ServerManager) detectTailscaleService() bool {
	log.Println("🔍 [SERVICE DEBUG] Checking for Tailscale service/daemon...")
	
	// Check systemd service (Linux)
	if sm.checkSystemdService() {
		return true
	}
	
	// Check process existence (cross-platform)
	if sm.checkTailscaleProcess() {
		return true
	}
	
	// Check launchd service (macOS)
	if sm.checkLaunchdService() {
		return true
	}
	
	// Check Windows service
	if sm.checkWindowsService() {
		return true
	}
	
	log.Println("❌ [SERVICE DEBUG] No Tailscale service detected")
	return false
}

// checkSystemdService checks Linux systemd service
func (sm *ServerManager) checkSystemdService() bool {
	log.Println("🔍 [SERVICE DEBUG] Checking systemd service...")
	
	// Check if tailscaled service is active
	cmd := exec.Command("systemctl", "is-active", "tailscaled")
	output, err := cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) == "active" {
		log.Println("✅ [SERVICE DEBUG] tailscaled systemd service is active")
		return true
	}
	
	// Also check if service exists but might be inactive
	cmd = exec.Command("systemctl", "status", "tailscaled")
	err = cmd.Run()
	if err == nil {
		log.Println("✅ [SERVICE DEBUG] tailscaled systemd service exists")
		return true
	}
	
	log.Println("❌ [SERVICE DEBUG] No systemd tailscaled service found")
	return false
}

// checkTailscaleProcess checks if tailscaled process is running
func (sm *ServerManager) checkTailscaleProcess() bool {
	log.Println("🔍 [SERVICE DEBUG] Checking for tailscaled process...")
	
	// Try pgrep (Unix/Linux/macOS)
	cmd := exec.Command("pgrep", "tailscaled")
	output, err := cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("✅ [SERVICE DEBUG] Found tailscaled process: %s", strings.TrimSpace(string(output)))
		return true
	}
	
	// Try ps with grep (fallback)
	cmd = exec.Command("sh", "-c", "ps aux | grep tailscaled | grep -v grep")
	output, err = cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Println("✅ [SERVICE DEBUG] Found tailscaled process via ps")
		return true
	}
	
	// Try pidof (Linux)
	cmd = exec.Command("pidof", "tailscaled")
	output, err = cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("✅ [SERVICE DEBUG] Found tailscaled via pidof: %s", strings.TrimSpace(string(output)))
		return true
	}
	
	log.Println("❌ [SERVICE DEBUG] No tailscaled process found")
	return false
}

// checkLaunchdService checks macOS launchd service
func (sm *ServerManager) checkLaunchdService() bool {
	log.Println("🔍 [SERVICE DEBUG] Checking launchd service...")
	
	cmd := exec.Command("launchctl", "list", "com.tailscale.ipnextension")
	err := cmd.Run()
	if err == nil {
		log.Println("✅ [SERVICE DEBUG] Tailscale launchd service found")
		return true
	}
	
	log.Println("❌ [SERVICE DEBUG] No Tailscale launchd service found")
	return false
}

// checkWindowsService checks Windows service
func (sm *ServerManager) checkWindowsService() bool {
	log.Println("🔍 [SERVICE DEBUG] Checking Windows service...")
	
	cmd := exec.Command("sc", "query", "Tailscale")
	output, err := cmd.Output()
	if err == nil && strings.Contains(string(output), "RUNNING") {
		log.Println("✅ [SERVICE DEBUG] Tailscale Windows service is running")
		return true
	}
	
	log.Println("❌ [SERVICE DEBUG] No Tailscale Windows service found")
	return false
} 