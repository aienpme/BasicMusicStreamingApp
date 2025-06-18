package server

import (
	"log"
	"os/exec"
	"strings"
)

// Flatpak-specific functionality for Tailscale integration

// isRunningInFlatpak checks if the application is running inside a Flatpak sandbox
func (sm *ServerManager) isRunningInFlatpak() bool {
	cmd := exec.Command("sh", "-c", "echo $FLATPAK_ID")
	output, err := cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("🔍 [FLATPAK] Running in Flatpak: %s", strings.TrimSpace(string(output)))
		return true
	}
	return false
}

// executeCommand creates a command that automatically uses flatpak-spawn when in Flatpak environment
func (sm *ServerManager) executeCommand(name string, args ...string) *exec.Cmd {
	if sm.useFlatpakSpawn && (name == "tailscale" || strings.HasSuffix(name, "/tailscale")) {
		// Prepend flatpak-spawn --host for tailscale commands (whether path or just "tailscale")
		flatpakArgs := append([]string{"--host", "tailscale"}, args...)
		log.Printf("🔧 [FLATPAK] Using flatpak-spawn: flatpak-spawn %v", flatpakArgs)
		return exec.Command("flatpak-spawn", flatpakArgs...)
	}
	return exec.Command(name, args...)
}

// debugFlatpakHostAccess performs comprehensive flatpak-spawn testing and diagnostics
func (sm *ServerManager) debugFlatpakHostAccess() {
	log.Println("🔧 [FLATPAK DEBUG] Testing flatpak-spawn host access capabilities...")
	
	// Test 1: Basic flatpak-spawn functionality
	log.Println("🔧 [FLATPAK DEBUG] Test 1: Basic flatpak-spawn functionality")
	testCmd := exec.Command("flatpak-spawn", "--host", "echo", "flatpak-spawn works")
	if output, err := testCmd.CombinedOutput(); err == nil {
		log.Printf("✅ [FLATPAK DEBUG] Basic test passed: %s", strings.TrimSpace(string(output)))
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Basic test failed: %v", err)
		log.Printf("❌ [FLATPAK DEBUG] Output: %s", string(output))
		return // If basic functionality fails, no point continuing
	}
	
	// Test 2: Host environment visibility
	log.Println("🔧 [FLATPAK DEBUG] Test 2: Host environment analysis")
	envCmd := exec.Command("flatpak-spawn", "--host", "env")
	if output, err := envCmd.CombinedOutput(); err == nil {
		log.Printf("✅ [FLATPAK DEBUG] Host environment accessible")
		// Look for PATH in environment
		envLines := strings.Split(string(output), "\n")
		for _, line := range envLines {
			if strings.HasPrefix(line, "PATH=") {
				log.Printf("🔧 [FLATPAK DEBUG] Host PATH: %s", line)
				break
			}
		}
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Host environment test failed: %v", err)
	}
	
	// Test 3: Host user context
	log.Println("🔧 [FLATPAK DEBUG] Test 3: Host user context")
	whoamiCmd := exec.Command("flatpak-spawn", "--host", "whoami")
	if output, err := whoamiCmd.CombinedOutput(); err == nil {
		log.Printf("✅ [FLATPAK DEBUG] Host user: %s", strings.TrimSpace(string(output)))
	} else {
		log.Printf("❌ [FLATPAK DEBUG] whoami test failed: %v", err)
	}
	
	// Test 4: Try to find tailscale via different methods on host
	log.Println("🔧 [FLATPAK DEBUG] Test 4: Tailscale discovery on host")
	
	// Try which command on host
	whichCmd := exec.Command("flatpak-spawn", "--host", "which", "tailscale")
	if output, err := whichCmd.CombinedOutput(); err == nil {
		hostTailscalePath := strings.TrimSpace(string(output))
		log.Printf("✅ [FLATPAK DEBUG] Host 'which tailscale': %s", hostTailscalePath)
		
		// Test the found path
		versionCmd := exec.Command("flatpak-spawn", "--host", hostTailscalePath, "version")
		if versionOutput, versionErr := versionCmd.CombinedOutput(); versionErr == nil {
			log.Printf("✅ [FLATPAK DEBUG] Host tailscale version: %s", strings.TrimSpace(string(versionOutput)))
			sm.useFlatpakSpawn = true
			return
		} else {
			log.Printf("❌ [FLATPAK DEBUG] Host tailscale version failed: %v", versionErr)
			log.Printf("❌ [FLATPAK DEBUG] Version output: %s", string(versionOutput))
		}
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Host 'which tailscale' failed: %v", err)
		log.Printf("❌ [FLATPAK DEBUG] Which output: %s", string(output))
	}
	
	// Try locate command on host
	locateCmd := exec.Command("flatpak-spawn", "--host", "locate", "tailscale")
	if output, err := locateCmd.CombinedOutput(); err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("✅ [FLATPAK DEBUG] Host 'locate tailscale':\n%s", string(output))
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Host 'locate tailscale' failed: %v", err)
	}
	
	// Try find command on host (search /usr/bin specifically)
	findCmd := exec.Command("flatpak-spawn", "--host", "find", "/usr/bin", "-name", "*tailscale*", "-type", "f")
	if output, err := findCmd.CombinedOutput(); err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("✅ [FLATPAK DEBUG] Host find results:\n%s", string(output))
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Host find failed: %v", err)
	}
	
	// Test 5: Try direct tailscale command with full error capture
	log.Println("🔧 [FLATPAK DEBUG] Test 5: Direct tailscale command test")
	directCmd := exec.Command("flatpak-spawn", "--host", "tailscale", "version")
	if output, err := directCmd.CombinedOutput(); err == nil {
		log.Printf("✅ [FLATPAK DEBUG] Direct 'tailscale version' works: %s", strings.TrimSpace(string(output)))
		sm.useFlatpakSpawn = true
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Direct 'tailscale version' failed: %v", err)
		log.Printf("❌ [FLATPAK DEBUG] Full output: %s", string(output))
		sm.useFlatpakSpawn = false
	}
	
	if sm.useFlatpakSpawn {
		log.Println("✅ [FLATPAK DEBUG] Flatpak-spawn mode enabled - Tailscale accessible on host")
	} else {
		log.Println("❌ [FLATPAK DEBUG] Flatpak-spawn mode disabled - falling back to sandbox detection")
	}
} 