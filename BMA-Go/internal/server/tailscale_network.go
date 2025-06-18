package server

import (
	"log"
	"os/exec"
	"strings"
)

// Network detection functionality for Tailscale

// detectTailscaleNetwork checks if Tailscale network interface exists
func (sm *ServerManager) detectTailscaleNetwork() bool {
	log.Println("🔍 [NETWORK DEBUG] Checking for Tailscale network interface...")
	
	// Check for tailscale0 interface
	if sm.checkTailscaleInterface() {
		return true
	}
	
	// Check for Tailscale IP ranges in any interface
	if sm.checkTailscaleIPRanges() {
		return true
	}
	
	log.Println("❌ [NETWORK DEBUG] No Tailscale network detected")
	return false
}

// checkTailscaleInterface checks for tailscale0 network interface
func (sm *ServerManager) checkTailscaleInterface() bool {
	log.Println("🔍 [NETWORK DEBUG] Checking for tailscale0 interface...")
	
	// Use ip command (Linux)
	cmd := exec.Command("ip", "link", "show", "tailscale0")
	err := cmd.Run()
	if err == nil {
		log.Println("✅ [NETWORK DEBUG] Found tailscale0 interface")
		return true
	}
	
	// Use ifconfig as fallback
	cmd = exec.Command("ifconfig", "tailscale0")
	err = cmd.Run()
	if err == nil {
		log.Println("✅ [NETWORK DEBUG] Found tailscale0 interface via ifconfig")
		return true
	}
	
	// Check if any interface has tailscale in name
	cmd = exec.Command("ip", "link", "show")
	output, err := cmd.Output()
	if err == nil {
		if strings.Contains(string(output), "tailscale") {
			log.Println("✅ [NETWORK DEBUG] Found tailscale-related interface")
			return true
		}
	}
	
	log.Println("❌ [NETWORK DEBUG] No tailscale interface found")
	return false
}

// checkTailscaleIPRanges checks for Tailscale IP addresses (100.x.x.x)
func (sm *ServerManager) checkTailscaleIPRanges() bool {
	log.Println("🔍 [NETWORK DEBUG] Checking for Tailscale IP ranges...")
	
	// Use ip addr show to get all IP addresses
	cmd := exec.Command("ip", "addr", "show")
	output, err := cmd.Output()
	if err == nil {
		lines := strings.Split(string(output), "\n")
		for _, line := range lines {
			if strings.Contains(line, "inet 100.") {
				log.Printf("✅ [NETWORK DEBUG] Found Tailscale IP range: %s", strings.TrimSpace(line))
				return true
			}
		}
	}
	
	// Fallback to ifconfig
	cmd = exec.Command("ifconfig")
	output, err = cmd.Output()
	if err == nil {
		if strings.Contains(string(output), "100.") {
			log.Println("✅ [NETWORK DEBUG] Found potential Tailscale IP via ifconfig")
			return true
		}
	}
	
	log.Println("❌ [NETWORK DEBUG] No Tailscale IP ranges found")
	return false
}

// getTailscaleIPFromNetwork extracts Tailscale IP from network interfaces
func (sm *ServerManager) getTailscaleIPFromNetwork() string {
	log.Println("🔍 [NETWORK DEBUG] Extracting Tailscale IP from network...")
	
	// Use ip addr show to get IP addresses
	cmd := exec.Command("ip", "addr", "show")
	output, err := cmd.Output()
	if err == nil {
		lines := strings.Split(string(output), "\n")
		for _, line := range lines {
			if strings.Contains(line, "inet 100.") {
				// Extract IP from line like "    inet 100.93.9.29/32 scope global tailscale0"
				parts := strings.Fields(line)
				for _, part := range parts {
					if strings.HasPrefix(part, "100.") {
						ip := strings.Split(part, "/")[0] // Remove CIDR notation
						log.Printf("✅ [NETWORK DEBUG] Extracted Tailscale IP: %s", ip)
						return ip
					}
				}
			}
		}
	}
	
	// Fallback to hostname -I and filter for 100.x.x.x
	cmd = exec.Command("hostname", "-I")
	output, err = cmd.Output()
	if err == nil {
		ips := strings.Fields(string(output))
		for _, ip := range ips {
			if strings.HasPrefix(ip, "100.") {
				log.Printf("✅ [NETWORK DEBUG] Found Tailscale IP via hostname: %s", ip)
				return ip
			}
		}
	}
	
	log.Println("❌ [NETWORK DEBUG] Could not extract Tailscale IP")
	return ""
} 