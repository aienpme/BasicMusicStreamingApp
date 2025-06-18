package server

import (
	"log"
	"os"
	"os/exec"
	"os/user"
	"strings"
)

// Binary detection functionality for Tailscale

// detectTailscaleBinary finds the Tailscale binary in common locations
func (sm *ServerManager) detectTailscaleBinary() string {
	// If we're in Flatpak mode and flatpak-spawn works, use "tailscale" 
	if sm.useFlatpakSpawn {
		log.Println("✅ [FLATPAK] Using flatpak-spawn for tailscale access")
		return "tailscale"
	}
	
	// Try which/where command first for PATH detection
	if pathBinary := sm.findTailscaleInPath(); pathBinary != "" {
		return pathBinary
	}
	
	// Possible Tailscale installation paths - comprehensive cross-platform list
	possiblePaths := []string{
		// macOS paths
		"/usr/local/bin/tailscale",         // Homebrew
		"/opt/homebrew/bin/tailscale",      // Apple Silicon Homebrew
		"/usr/bin/tailscale",               // System install
		"/usr/sbin/tailscale",              // System install (sbin)
		"/Applications/Tailscale.app/Contents/MacOS/Tailscale", // macOS app
		
		// Linux package manager paths
		"/snap/bin/tailscale",              // Snap packages
		"/usr/local/sbin/tailscale",        // Manual installs
		"/opt/tailscale/bin/tailscale",     // Custom installs
		"/usr/local/bin/tailscale",         // Local installs
		"/bin/tailscale",                   // System bin
		"/sbin/tailscale",                  // System sbin
		
		// Windows paths
		"C:\\Program Files\\Tailscale\\tailscale.exe",
		"C:\\Program Files (x86)\\Tailscale\\tailscale.exe",
		
		// User-specific paths (will be dynamically expanded)
		"~/.local/bin/tailscale",           // User local bin
		"~/bin/tailscale",                  // User bin
	}
	
	// Expand user home directory paths
	expandedPaths := sm.expandUserPaths(possiblePaths)
	
	log.Printf("🔍 [TAILSCALE DEBUG] Checking %d possible paths...", len(expandedPaths))
	
	for i, path := range expandedPaths {
		log.Printf("🔍 [TAILSCALE DEBUG] Checking path %d/%d: %s", i+1, len(expandedPaths), path)
		
		if sm.isTailscaleBinaryValid(path) {
			log.Printf("✅ [TAILSCALE DEBUG] Found working Tailscale at: %s", path)
			return path
		} else {
			log.Printf("❌ [TAILSCALE DEBUG] Path failed validation: %s", path)
		}
	}
	
	log.Println("❌ [TAILSCALE DEBUG] Tailscale binary not found in any expected locations")
	return ""
}

// findTailscaleInPath uses which/where command to find tailscale in PATH
func (sm *ServerManager) findTailscaleInPath() string {
	log.Println("🔍 [TAILSCALE DEBUG] Checking PATH for tailscale binary...")
	
	// Try 'which' command (Unix/Linux/macOS)
	cmd := exec.Command("which", "tailscale")
	output, err := cmd.Output()
	if err == nil {
		path := strings.TrimSpace(string(output))
		if path != "" && sm.isTailscaleBinaryValid(path) {
			log.Printf("✅ [TAILSCALE DEBUG] Found tailscale in PATH via 'which': %s", path)
			return path
		}
	}
	
	// Try 'where' command (Windows)
	cmd = exec.Command("where", "tailscale")
	output, err = cmd.Output()
	if err == nil {
		lines := strings.Split(strings.TrimSpace(string(output)), "\n")
		for _, path := range lines {
			path = strings.TrimSpace(path)
			if path != "" && sm.isTailscaleBinaryValid(path) {
				log.Printf("✅ [TAILSCALE DEBUG] Found tailscale in PATH via 'where': %s", path)
				return path
			}
		}
	}
	
	// Try direct PATH lookup
	if path, err := exec.LookPath("tailscale"); err == nil {
		if sm.isTailscaleBinaryValid(path) {
			log.Printf("✅ [TAILSCALE DEBUG] Found tailscale via LookPath: %s", path)
			return path
		}
	}
	
	log.Println("❌ [TAILSCALE DEBUG] Tailscale not found in PATH")
	return ""
}

// isTailscaleBinaryValid checks if a path contains a valid Tailscale binary
func (sm *ServerManager) isTailscaleBinaryValid(path string) bool {
	log.Printf("🔍 [TAILSCALE DEBUG] Validating binary at: %s", path)
	
	// First, check if the file exists and is executable
	if path != "tailscale" { // Skip file check for PATH lookup
		if _, err := exec.LookPath(path); err != nil {
			log.Printf("❌ [TAILSCALE DEBUG] Binary not found or not executable: %v", err)
			return false
		}
	}
	
	// Try version command first (less likely to require permissions)
	cmd := sm.executeCommand(path, "version")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("❌ [TAILSCALE DEBUG] 'version' command failed: %v", err)
		
		// If version fails, try a simple help command
		cmd = sm.executeCommand(path, "--help")
		err = cmd.Run()
		if err != nil {
			log.Printf("❌ [TAILSCALE DEBUG] '--help' command also failed: %v", err)
			return false
		} else {
			log.Printf("✅ [TAILSCALE DEBUG] '--help' command succeeded, binary is valid")
			return true
		}
	}
	
	// Check if output contains "tailscale" to verify it's actually the right binary
	outputStr := string(output)
	if strings.Contains(strings.ToLower(outputStr), "tailscale") {
		log.Printf("✅ [TAILSCALE DEBUG] 'version' command succeeded: %s", strings.TrimSpace(outputStr))
		return true
	}
	
	log.Printf("❌ [TAILSCALE DEBUG] Binary exists but doesn't appear to be Tailscale: %s", outputStr)
	return false
}

// findTailscaleWithFallbacks tries aggressive methods to find tailscale binary
func (sm *ServerManager) findTailscaleWithFallbacks() string {
	log.Println("🔍 [FALLBACK DEBUG] Trying aggressive binary detection...")
	
	// Try to find the binary via running process
	if path := sm.findBinaryFromProcess(); path != "" {
		return path
	}
	
	// Try package manager queries
	if path := sm.findBinaryViaPackageManager(); path != "" {
		return path
	}
	
	// Try locate command
	if path := sm.findBinaryViaLocate(); path != "" {
		return path
	}
	
	// Try find command as last resort
	if path := sm.findBinaryViaFind(); path != "" {
		return path
	}
	
	log.Println("❌ [FALLBACK DEBUG] All fallback methods failed")
	return ""
}

// findBinaryFromProcess tries to find binary path from running process
func (sm *ServerManager) findBinaryFromProcess() string {
	log.Println("🔍 [FALLBACK DEBUG] Finding binary from running process...")
	
	// Try to get process info and extract binary path
	cmd := exec.Command("sh", "-c", "ps aux | grep tailscaled | grep -v grep | awk '{print $11}'")
	output, err := cmd.Output()
	if err == nil {
		lines := strings.Split(strings.TrimSpace(string(output)), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if line != "" && (strings.Contains(line, "tailscaled") || strings.Contains(line, "tailscale")) {
				// Extract directory and look for tailscale binary
				if strings.Contains(line, "/") {
					dir := strings.TrimSuffix(line, "/tailscaled")
					tailscalePath := dir + "/tailscale"
					if sm.isTailscaleBinaryValid(tailscalePath) {
						log.Printf("✅ [FALLBACK DEBUG] Found tailscale via process: %s", tailscalePath)
						return tailscalePath
					}
				}
			}
		}
	}
	
	log.Println("❌ [FALLBACK DEBUG] Could not find binary from process")
	return ""
}

// findBinaryViaPackageManager queries package managers for tailscale location
func (sm *ServerManager) findBinaryViaPackageManager() string {
	log.Println("🔍 [FALLBACK DEBUG] Checking package managers...")
	
	// Try dpkg (Debian/Ubuntu)
	cmd := exec.Command("dpkg", "-L", "tailscale")
	output, err := cmd.Output()
	if err == nil {
		lines := strings.Split(string(output), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if strings.HasSuffix(line, "/tailscale") && sm.isTailscaleBinaryValid(line) {
				log.Printf("✅ [FALLBACK DEBUG] Found tailscale via dpkg: %s", line)
				return line
			}
		}
	}
	
	// Try rpm (RedHat/CentOS/Fedora)
	cmd = exec.Command("rpm", "-ql", "tailscale")
	output, err = cmd.Output()
	if err == nil {
		lines := strings.Split(string(output), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if strings.HasSuffix(line, "/tailscale") && sm.isTailscaleBinaryValid(line) {
				log.Printf("✅ [FALLBACK DEBUG] Found tailscale via rpm: %s", line)
				return line
			}
		}
	}
	
	log.Println("❌ [FALLBACK DEBUG] Package managers didn't help")
	return ""
}

// findBinaryViaLocate uses locate command to find tailscale
func (sm *ServerManager) findBinaryViaLocate() string {
	log.Println("🔍 [FALLBACK DEBUG] Trying locate command...")
	
	cmd := exec.Command("locate", "tailscale")
	output, err := cmd.Output()
	if err == nil {
		lines := strings.Split(string(output), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if strings.HasSuffix(line, "/tailscale") && !strings.Contains(line, ".") && sm.isTailscaleBinaryValid(line) {
				log.Printf("✅ [FALLBACK DEBUG] Found tailscale via locate: %s", line)
				return line
			}
		}
	}
	
	log.Println("❌ [FALLBACK DEBUG] locate command didn't help")
	return ""
}

// findBinaryViaFind uses find command as last resort
func (sm *ServerManager) findBinaryViaFind() string {
	log.Println("🔍 [FALLBACK DEBUG] Trying find command (last resort)...")
	
	// Search common directories with find
	searchDirs := []string{"/usr", "/opt", "/snap", "/home"}
	
	for _, dir := range searchDirs {
		cmd := exec.Command("find", dir, "-name", "tailscale", "-type", "f", "-executable", "2>/dev/null")
		output, err := cmd.Output()
		if err == nil {
			lines := strings.Split(strings.TrimSpace(string(output)), "\n")
			for _, line := range lines {
				line = strings.TrimSpace(line)
				if line != "" && sm.isTailscaleBinaryValid(line) {
					log.Printf("✅ [FALLBACK DEBUG] Found tailscale via find: %s", line)
					return line
				}
			}
		}
	}
	
	log.Println("❌ [FALLBACK DEBUG] find command didn't help")
	return ""
}

// findAnyTailscaleBinary tries to find tailscale binary by any means necessary
func (sm *ServerManager) findAnyTailscaleBinary() string {
	log.Println("🔍 [NETWORK DEBUG] Final attempt to find ANY tailscale binary...")
	
	// Try all our existing methods one more time
	if path := sm.findTailscaleInPath(); path != "" {
		return path
	}
	
	if path := sm.findTailscaleWithFallbacks(); path != "" {
		return path
	}
	
	// Try even more aggressive searches
	commonCommands := []string{"tailscale", "/usr/bin/tailscale", "/bin/tailscale"}
	for _, cmd := range commonCommands {
		if sm.isTailscaleBinaryValid(cmd) {
			log.Printf("✅ [NETWORK DEBUG] Found working tailscale: %s", cmd)
			return cmd
		}
	}
	
	log.Println("❌ [NETWORK DEBUG] No tailscale binary found")
	return ""
}

// expandUserPaths expands ~ paths to full user home directory paths
func (sm *ServerManager) expandUserPaths(paths []string) []string {
	var expandedPaths []string
	
	// Get user home directory using os/user package
	homeDir := ""
	if usr, err := user.Current(); err == nil {
		homeDir = usr.HomeDir
	}
	
	// Fallback to environment variable
	if homeDir == "" {
		if cmd := exec.Command("sh", "-c", "echo $HOME"); cmd != nil {
			if output, err := cmd.Output(); err == nil {
				homeDir = strings.TrimSpace(string(output))
			}
		}
	}
	
	log.Printf("🔍 [TAILSCALE DEBUG] Using home directory: %s", homeDir)
	
	for _, path := range paths {
		if strings.HasPrefix(path, "~/") && homeDir != "" {
			// Expand ~ to home directory
			expandedPath := strings.Replace(path, "~", homeDir, 1)
			expandedPaths = append(expandedPaths, expandedPath)
			log.Printf("🔍 [TAILSCALE DEBUG] Expanded %s -> %s", path, expandedPath)
		} else {
			// Keep original path
			expandedPaths = append(expandedPaths, path)
		}
	}
	
	return expandedPaths
}

// expandSystemPath adds common system directories to PATH
func (sm *ServerManager) expandSystemPath(currentPath string) string {
	// Common system directories that might contain binaries
	systemDirs := []string{
		"/usr/bin",
		"/bin", 
		"/usr/sbin",
		"/sbin",
		"/usr/local/bin",
		"/usr/local/sbin",
		"/opt/bin",
		"/snap/bin",
	}
	
	// Start with current path
	pathParts := []string{}
	if currentPath != "" {
		pathParts = strings.Split(currentPath, ":")
	}
	
	// Add system directories if not already present
	for _, dir := range systemDirs {
		found := false
		for _, existing := range pathParts {
			if existing == dir {
				found = true
				break
			}
		}
		if !found {
			pathParts = append(pathParts, dir)
		}
	}
	
	return strings.Join(pathParts, ":")
}

// debugEnvironment shows what the Go app can see for debugging
func (sm *ServerManager) debugEnvironment() {
	log.Println("🔧 [DEBUG] Environment Analysis:")
	
	// Show PATH
	originalPath := os.Getenv("PATH")
	if originalPath != "" {
		log.Printf("🔧 [DEBUG] Original PATH: %s", originalPath)
	} else {
		log.Println("🔧 [DEBUG] Original PATH: (empty)")
	}
	
	// Expand PATH to include common system directories
	expandedPath := sm.expandSystemPath(originalPath)
	log.Printf("🔧 [DEBUG] Expanded PATH: %s", expandedPath)
	os.Setenv("PATH", expandedPath)
	
	// Show current user
	if usr, err := user.Current(); err == nil {
		log.Printf("🔧 [DEBUG] User: %s (UID: %s, GID: %s, Home: %s)", usr.Username, usr.Uid, usr.Gid, usr.HomeDir)
	}
	
	// Test direct tailscale command (after PATH expansion)
	log.Println("🔧 [DEBUG] Testing direct 'tailscale status' command (with expanded PATH):")
	cmd := sm.executeCommand("tailscale", "status")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("🔧 [DEBUG] 'tailscale status' still failed: %v", err)
		
		// Try with absolute paths
		log.Println("🔧 [DEBUG] Trying absolute paths for tailscale:")
		absolutePaths := []string{"/usr/bin/tailscale", "/bin/tailscale", "/usr/sbin/tailscale", "/sbin/tailscale", "/usr/local/bin/tailscale", "/snap/bin/tailscale"}
		for _, path := range absolutePaths {
			cmd = exec.Command(path, "status")
			output, err = cmd.Output()
			if err == nil {
				log.Printf("🔧 [DEBUG] SUCCESS with '%s': %s", path, string(output))
				break
			} else {
				log.Printf("🔧 [DEBUG] Failed with '%s': %v", path, err)
			}
		}
	} else {
		log.Printf("🔧 [DEBUG] SUCCESS - 'tailscale status' output: %s", string(output))
	}
	
	// Show all network interfaces
	log.Println("🔧 [DEBUG] Network interfaces:")
	
	// Try ip command with absolute paths
	ipPaths := []string{"/usr/bin/ip", "/bin/ip", "/sbin/ip", "/usr/sbin/ip"}
	ipWorked := false
	for _, ipPath := range ipPaths {
		cmd = exec.Command(ipPath, "addr", "show")
		output, err = cmd.Output()
		if err == nil {
			log.Printf("🔧 [DEBUG] SUCCESS with '%s addr show':\n%s", ipPath, string(output))
			ipWorked = true
			break
		}
	}
	
	if !ipWorked {
		log.Println("🔧 [DEBUG] All 'ip' paths failed, trying 'ifconfig':")
		// Try ifconfig with absolute paths
		ifconfigPaths := []string{"/usr/bin/ifconfig", "/bin/ifconfig", "/sbin/ifconfig", "/usr/sbin/ifconfig"}
		for _, ifconfigPath := range ifconfigPaths {
			cmd = exec.Command(ifconfigPath)
			output, err = cmd.Output()
			if err == nil {
				log.Printf("🔧 [DEBUG] SUCCESS with '%s':\n%s", ifconfigPath, string(output))
				break
			}
		}
	}
	
	// Show which/whereis results
	log.Println("🔧 [DEBUG] Binary location tests:")
	for _, cmd := range []string{"which tailscale", "whereis tailscale", "type tailscale"} {
		parts := strings.Fields(cmd)
		execCmd := exec.Command(parts[0], parts[1:]...)
		output, err := execCmd.Output()
		if err != nil {
			log.Printf("🔧 [DEBUG] '%s' failed: %v", cmd, err)
		} else {
			log.Printf("🔧 [DEBUG] '%s' output: %s", cmd, strings.TrimSpace(string(output)))
		}
	}
	
	// Aggressive system-wide search for tailscale
	log.Println("🔧 [DEBUG] Performing aggressive system-wide tailscale search:")
	sm.aggressiveTailscaleSearch()
	
	log.Println("🔧 [DEBUG] Environment analysis complete")
}

// aggressiveTailscaleSearch performs system-wide search for tailscale binary
func (sm *ServerManager) aggressiveTailscaleSearch() {
	log.Println("🔧 [DEBUG] Starting aggressive search...")
	
	// Try find command on entire filesystem
	log.Println("🔧 [DEBUG] Searching entire filesystem with find:")
	cmd := exec.Command("sh", "-c", "find / -name '*tailscale*' -type f -executable 2>/dev/null | head -20")
	output, err := cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("🔧 [DEBUG] Found tailscale-related files:\n%s", string(output))
		
		// Test each found binary
		lines := strings.Split(strings.TrimSpace(string(output)), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if line != "" && strings.Contains(line, "tailscale") && !strings.Contains(line, ".") {
				log.Printf("🔧 [DEBUG] Testing found binary: %s", line)
				testCmd := exec.Command(line, "version")
				testOutput, testErr := testCmd.Output()
				if testErr == nil && strings.Contains(strings.ToLower(string(testOutput)), "tailscale") {
					log.Printf("🔧 [DEBUG] SUCCESS! Working tailscale found at: %s", line)
					log.Printf("🔧 [DEBUG] Version output: %s", string(testOutput))
					
					// Try status command
					statusCmd := exec.Command(line, "status")
					statusOutput, statusErr := statusCmd.Output()
					if statusErr == nil {
						log.Printf("🔧 [DEBUG] Status command works: %s", string(statusOutput))
					} else {
						log.Printf("🔧 [DEBUG] Status command failed: %v", statusErr)
					}
					return
				}
			}
		}
	} else {
		log.Printf("🔧 [DEBUG] Find command failed or no results: %v", err)
	}
	
	// Try alternative search methods
	log.Println("🔧 [DEBUG] Trying alternative search methods:")
	
	// Check if running from flatpak
	cmd = exec.Command("sh", "-c", "echo $FLATPAK_ID")
	output, err = cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("🔧 [DEBUG] Running in Flatpak: %s", strings.TrimSpace(string(output)))
		
		// Try flatpak-spawn for host access
		cmd = exec.Command("flatpak-spawn", "--host", "tailscale", "status")
		output, err = cmd.Output()
		if err == nil {
			log.Printf("🔧 [DEBUG] SUCCESS via flatpak-spawn: %s", string(output))
			return
		} else {
			log.Printf("🔧 [DEBUG] flatpak-spawn failed: %v", err)
		}
	}
	
	// Check if running in container
	cmd = exec.Command("sh", "-c", "cat /proc/1/cgroup 2>/dev/null | grep -q docker && echo 'docker' || echo 'not-docker'")
	output, err = cmd.Output()
	if err == nil && strings.Contains(string(output), "docker") {
		log.Println("🔧 [DEBUG] Running in Docker container")
	}
	
	log.Println("🔧 [DEBUG] Aggressive search complete")
} 