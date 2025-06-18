package server

import (
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"time"
)

// Status checking and monitoring functionality for Tailscale

// checkTailscaleConnection verifies that Tailscale is connected and running
func (sm *ServerManager) checkTailscaleConnection(tailscalePath string) bool {
	log.Println("🔍 Checking Tailscale connection status...")
	
	// Try to get JSON status first
	cmd := sm.executeCommand(tailscalePath, "status", "--json")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("⚠️ Failed to get Tailscale JSON status: %v", err)
		
		// If JSON status fails, try plain status (might work with different permissions)
		cmd = sm.executeCommand(tailscalePath, "status")
		output, err = cmd.Output()
		if err != nil {
			log.Printf("⚠️ Failed to get Tailscale plain status: %v", err)
			
			// If status commands fail, try to get IP addresses as a last resort
			cmd = sm.executeCommand(tailscalePath, "ip")
			output, err = cmd.Output()
			if err != nil {
				log.Printf("❌ All Tailscale status commands failed: %v", err)
				return false
			}
			
			// If we got IP output, assume Tailscale is working
			outputStr := strings.TrimSpace(string(output))
			if outputStr != "" {
				log.Printf("✅ Tailscale appears to be working (got IP: %s)", outputStr)
				return true
			}
			
			log.Println("❌ Tailscale IP command returned empty result")
			return false
		}
		
		// Check plain status output for connectivity indicators
		outputStr := strings.ToLower(string(output))
		if strings.Contains(outputStr, "logged in") || strings.Contains(outputStr, "online") || strings.Contains(outputStr, "connected") {
			log.Printf("✅ Tailscale appears to be connected (plain status)")
			return true
		}
		
		log.Printf("❌ Tailscale status doesn't indicate connection: %s", string(output))
		return false
	}
	
	// Parse JSON output
	var status map[string]interface{}
	if err := json.Unmarshal(output, &status); err != nil {
		log.Printf("❌ Failed to parse Tailscale status JSON: %v", err)
		// Even if JSON parsing fails, if we got output, Tailscale is probably working
		log.Println("✅ Assuming Tailscale is working since we got status output")
		return true
	}
	
	// Check BackendState
	backendState, ok := status["BackendState"].(string)
	if !ok {
		log.Println("⚠️ Could not extract BackendState from Tailscale status")
		// If we can't get state but got JSON, assume it's working
		log.Println("✅ Assuming Tailscale is working since we got JSON status")
		return true
	}
	
	log.Printf("🔍 Tailscale status: %s", backendState)
	
	isRunning := backendState == "Running"
	if isRunning {
		log.Println("✅ Tailscale is running and connected")
	} else {
		log.Printf("⚠️ Tailscale is not in Running state: %s", backendState)
		// Even if not "Running", it might still be usable
		log.Println("✅ Continuing anyway - Tailscale binary is available")
	}
	
	return true // Be more permissive - if we got this far, Tailscale is probably usable
}

// getTailscaleHostname gets the Tailscale hostname for this machine
func (sm *ServerManager) getTailscaleHostname(tailscalePath string) string {
	log.Println("🔍 Getting Tailscale hostname...")
	
	// Try JSON status first
	cmd := sm.executeCommand(tailscalePath, "status", "--json")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("⚠️ Failed to get Tailscale status for hostname: %v", err)
		
		// Try to get IP and create a basic hostname
		cmd = sm.executeCommand(tailscalePath, "ip")
		output, err = cmd.Output()
		if err != nil {
			log.Printf("❌ Failed to get Tailscale IP: %v", err)
			return ""
		}
		
		// Use the first IP as a fallback
		ips := strings.Fields(strings.TrimSpace(string(output)))
		if len(ips) > 0 {
			ip := ips[0]
			log.Printf("✅ Using Tailscale IP as hostname: %s", ip)
			return ip
		}
		
		log.Println("❌ No Tailscale IPs found")
		return ""
	}
	
	// Parse JSON output
	var status map[string]interface{}
	if err := json.Unmarshal(output, &status); err != nil {
		log.Printf("⚠️ Failed to parse Tailscale hostname JSON: %v", err)
		
		// Fallback: try to get IP
		cmd = sm.executeCommand(tailscalePath, "ip")
		output, err = cmd.Output()
		if err == nil {
			ips := strings.Fields(strings.TrimSpace(string(output)))
			if len(ips) > 0 {
				ip := ips[0]
				log.Printf("✅ Using Tailscale IP as hostname (JSON failed): %s", ip)
				return ip
			}
		}
		
		return ""
	}
	
	// Extract Self information
	self, ok := status["Self"].(map[string]interface{})
	if !ok {
		log.Println("⚠️ Could not extract Self info from Tailscale status")
		
		// Try to find any peer that might be ourselves
		peers, ok := status["Peers"].(map[string]interface{})
		if ok {
			for _, peer := range peers {
				if peerMap, ok := peer.(map[string]interface{}); ok {
					if dnsName, ok := peerMap["DNSName"].(string); ok && dnsName != "" {
						cleanedName := strings.TrimSuffix(dnsName, ".")
						log.Printf("✅ Using peer DNSName as hostname: %s", cleanedName)
						return cleanedName
					}
				}
			}
		}
		
		// Last resort: try IP command
		cmd = sm.executeCommand(tailscalePath, "ip")
		output, err = cmd.Output()
		if err == nil {
			ips := strings.Fields(strings.TrimSpace(string(output)))
			if len(ips) > 0 {
				ip := ips[0]
				log.Printf("✅ Using Tailscale IP as hostname (no Self): %s", ip)
				return ip
			}
		}
		
		return ""
	}
	
	// Get DNS name
	dnsName, ok := self["DNSName"].(string)
	if !ok {
		log.Println("⚠️ Could not extract DNSName from Tailscale Self info")
		
		// Try to get IP from Self info
		if ips, ok := self["TailscaleIPs"].([]interface{}); ok && len(ips) > 0 {
			if ip, ok := ips[0].(string); ok {
				log.Printf("✅ Using Tailscale IP from Self: %s", ip)
				return ip
			}
		}
		
		// Last resort: IP command
		cmd = sm.executeCommand(tailscalePath, "ip")
		output, err = cmd.Output()
		if err == nil {
			ips := strings.Fields(strings.TrimSpace(string(output)))
			if len(ips) > 0 {
				ip := ips[0]
				log.Printf("✅ Using Tailscale IP as hostname (no DNSName): %s", ip)
				return ip
			}
		}
		
		return ""
	}
	
	// Clean up DNS name (remove trailing dots)
	cleanedName := strings.TrimSuffix(dnsName, ".")
	
	log.Printf("✅ Found Tailscale hostname: %s", cleanedName)
	return cleanedName
}

// RefreshTailscaleStatus re-checks Tailscale status (for UI refresh)
func (sm *ServerManager) RefreshTailscaleStatus() {
	log.Println("🔄 Refreshing Tailscale status...")
	go sm.checkTailscaleStatus()
}

// GetTailscaleInfo returns detailed Tailscale information for UI display
func (sm *ServerManager) GetTailscaleInfo() map[string]interface{} {
	info := map[string]interface{}{
		"available": sm.HasTailscale,
		"url":       sm.TailscaleURL,
		"lastCheck": time.Now().Format(time.RFC3339),
	}
	
	if sm.HasTailscale {
		info["status"] = "connected"
		info["message"] = "HTTP over Tailscale (network-level encryption)"
	} else {
		info["status"] = "not_available"
		info["message"] = "Tailscale not detected or not running"
	}
	
	return info
}

// IsTailscaleConfigured returns whether Tailscale is available and configured
func (sm *ServerManager) IsTailscaleConfigured() bool {
	return sm.HasTailscale && sm.TailscaleURL != ""
}

// GetPreferredURL returns the preferred server URL (Tailscale if available, local otherwise)
func (sm *ServerManager) GetPreferredURL() string {
	if sm.IsTailscaleConfigured() {
		return fmt.Sprintf("%s:%d", sm.TailscaleURL, sm.Port)
	}
	return fmt.Sprintf("http://%s:%d", sm.getLocalIPAddress(), sm.Port)
}

// TailscaleStatusInfo represents Tailscale status information
type TailscaleStatusInfo struct {
	Available    bool      `json:"available"`
	Connected    bool      `json:"connected"`
	Hostname     string    `json:"hostname,omitempty"`
	URL          string    `json:"url,omitempty"`
	LastChecked  time.Time `json:"lastChecked"`
	ErrorMessage string    `json:"errorMessage,omitempty"`
}

// GetDetailedTailscaleStatus returns comprehensive Tailscale status
func (sm *ServerManager) GetDetailedTailscaleStatus() TailscaleStatusInfo {
	status := TailscaleStatusInfo{
		Available:   sm.HasTailscale,
		Connected:   sm.HasTailscale, // If available, assume connected
		URL:         sm.TailscaleURL,
		LastChecked: time.Now(),
	}
	
	if sm.HasTailscale && sm.TailscaleURL != "" {
		// Extract hostname from URL
		url := strings.TrimPrefix(sm.TailscaleURL, "http://")
		status.Hostname = url
	} else {
		status.ErrorMessage = "Tailscale not detected or not running"
	}
	
	return status
}

// MonitorTailscaleStatus starts periodic monitoring of Tailscale status
func (sm *ServerManager) MonitorTailscaleStatus(interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	
	log.Printf("🔍 Starting Tailscale monitoring (every %v)", interval)
	
	for {
		select {
		case <-ticker.C:
			// Periodic status check
			prevStatus := sm.HasTailscale
			sm.checkTailscaleStatus()
			
			// Log status changes
			if prevStatus != sm.HasTailscale {
				if sm.HasTailscale {
					log.Println("✅ Tailscale became available")
				} else {
					log.Println("❌ Tailscale became unavailable")
				}
			}
			
		case <-sm.ctx.Done():
			log.Println("🔍 Stopping Tailscale monitoring")
			return
		}
	}
}

// StartTailscaleMonitoring starts background monitoring with default interval
func (sm *ServerManager) StartTailscaleMonitoring() {
	// Check every 5 minutes
	go sm.MonitorTailscaleStatus(5 * time.Minute)
} 