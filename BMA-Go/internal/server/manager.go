package server

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"sync"
	"time"

	"bma-go/internal/models"
	"github.com/google/uuid"
	"github.com/gorilla/mux"
)

// ServerManager manages the HTTP server lifecycle and configuration
// Equivalent to ServerManager.swift in the macOS version
type ServerManager struct {
	// Server state
	IsRunning     bool
	ServerURL     string
	TailscaleURL  string
	HasTailscale  bool
	Port          int

	// Server instance
	server       *http.Server
	router       *mux.Router
	
	// Music library
	musicLibrary *models.MusicLibrary
	
	// Device tracking
	connectedDevices []models.ConnectedDevice
	devicesMutex     sync.RWMutex
	
	// Token management  
	pairingTokens    map[string]time.Time // token -> expiration
	tokensMutex      sync.RWMutex
	currentPairingToken string
	
	// QR code caching for fast loading
	cachedQRBytes    []byte
	cachedQRJSON     string
	qrCacheMutex     sync.RWMutex
	
	// Flatpak detection
	useFlatpakSpawn bool
	
	// Shutdown context
	ctx        context.Context
	cancelFunc context.CancelFunc
}

// NewServerManager creates a new server manager instance
func NewServerManager() *ServerManager {
	ctx, cancel := context.WithCancel(context.Background())
	
	sm := &ServerManager{
		Port:            8008,
		pairingTokens:   make(map[string]time.Time),
		ctx:             ctx,
		cancelFunc:      cancel,
	}
	
	// Initialize Tailscale detection
	go sm.checkTailscaleStatus()
	
	// Start device monitoring
	sm.startDeviceMonitor()
	
	return sm
}

// SetMusicLibrary connects a music library to the server manager
func (sm *ServerManager) SetMusicLibrary(library *models.MusicLibrary) {
	sm.musicLibrary = library
	log.Println("🎵 MusicLibrary connected to ServerManager")
}

// StartServer starts the HTTP server on port 8008
func (sm *ServerManager) StartServer() error {
	if sm.IsRunning {
		log.Println("⚠️ Server start requested but already running")
		return fmt.Errorf("server already running")
	}
	
	log.Println("🚀 Starting BMA HTTP server...")
	log.Printf("📊 Tailscale available: %v", sm.HasTailscale)
	if sm.HasTailscale {
		log.Printf("🔗 Tailscale URL: %s", sm.TailscaleURL)
	}
	
	// Setup router and routes
	sm.setupRouter()
	
	// Create HTTP server
	addr := fmt.Sprintf("0.0.0.0:%d", sm.Port)
	sm.server = &http.Server{
		Addr:    addr,
		Handler: sm.router,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}
	
	// Start server in goroutine
	go func() {
		log.Printf("📡 HTTP server listening on %s", addr)
		if err := sm.server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Printf("❌ Server failed: %v", err)
			sm.IsRunning = false
		}
	}()
	
	// Set server state
	sm.IsRunning = true
	sm.updateServerURLs()
	
	log.Println("✅ BMA server started successfully!")
	sm.logServerInfo()
	
	// Preload QR code for instant access
	sm.PreloadQRCode()
	
	return nil
}

// StopServer stops the HTTP server
func (sm *ServerManager) StopServer() error {
	if !sm.IsRunning {
		log.Println("⚠️ Server stop requested but not running")
		return nil
	}
	
	log.Println("🛑 Stopping BMA server...")
	
	// Graceful shutdown with timeout
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	if sm.server != nil {
		if err := sm.server.Shutdown(ctx); err != nil {
			log.Printf("❌ Server shutdown error: %v", err)
			return err
		}
	}
	
	// Clear state
	sm.IsRunning = false
	sm.ClearQRCache() // Clear QR cache when server stops
	sm.ServerURL = ""
	sm.clearConnectedDevices()
	sm.revokeAllTokens()
	
	log.Println("✅ Server stopped successfully")
	return nil
}

// Cleanup performs cleanup when app terminates
func (sm *ServerManager) Cleanup() {
	log.Println("🧹 ServerManager cleanup...")
	sm.cancelFunc()
	if sm.IsRunning {
		sm.StopServer()
	}
	log.Println("✅ ServerManager cleanup completed")
}

// updateServerURLs sets the appropriate server URLs
func (sm *ServerManager) updateServerURLs() {
	localIP := sm.getLocalIPAddress()
	
	if sm.HasTailscale && sm.TailscaleURL != "" {
		// Use HTTP over Tailscale (network-level encryption)
		sm.ServerURL = fmt.Sprintf("%s:%d", sm.TailscaleURL, sm.Port)
		log.Printf("🔒 Tailscale URL: %s", sm.ServerURL)
		log.Printf("🌐 Local URL: http://%s:%d", localIP, sm.Port)
	} else {
		// Use local HTTP URL
		sm.ServerURL = fmt.Sprintf("http://%s:%d", localIP, sm.Port)
		log.Printf("🌐 Local URL: %s", sm.ServerURL)
	}
}

// logServerInfo logs detailed server information
func (sm *ServerManager) logServerInfo() {
	localIP := sm.getLocalIPAddress()
	
	log.Println("\n📡 SERVER NETWORK INFORMATION:")
	log.Printf("   Local IP: %s", localIP)
	log.Printf("   HTTP Port: %d", sm.Port)
	log.Printf("   Listening on: 0.0.0.0:%d", sm.Port)
	
	if sm.HasTailscale && sm.TailscaleURL != "" {
		log.Println("\n🔒 TAILSCALE CONFIGURATION:")
		log.Printf("   Tailscale URL: %s", sm.TailscaleURL)
		log.Printf("   Public Access: %s", sm.ServerURL)
		log.Println("   Note: HTTP over Tailscale (network-level encryption)")
		
		log.Println("\n✅ AVAILABLE CONNECTION URLs:")
		log.Printf("   📱 Android (via Tailscale): %s", sm.ServerURL)
		log.Printf("   🌐 Local network: http://%s:%d", localIP, sm.Port)
	} else {
		log.Println("\n🌐 LOCAL NETWORK CONFIGURATION:")
		log.Printf("   Server URL: %s", sm.ServerURL)
		log.Println("   Protocol: HTTP only (no Tailscale)")
		
		log.Println("\n✅ AVAILABLE CONNECTION URLs:")
		log.Printf("   📱 Android (local): %s", sm.ServerURL)
		log.Printf("   🖥️ Browser test: %s/health", sm.ServerURL)
	}
	
	log.Println("\n🎵 Server is ready for music streaming!")
	log.Println("📱 Generate a QR code to pair devices")
	log.Println("🔍 Watching for incoming connections...")
}

// getLocalIPAddress gets the local network IP address
func (sm *ServerManager) getLocalIPAddress() string {
	// Get local IP address by connecting to a remote address
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err != nil {
		return "localhost"
	}
	defer conn.Close()
	
	localAddr := conn.LocalAddr().(*net.UDPAddr)
	return localAddr.IP.String()
}

// Device tracking methods

// TrackDeviceConnection tracks a successful device connection
func (sm *ServerManager) TrackDeviceConnection(token, ipAddress, userAgent string) {
	sm.devicesMutex.Lock()
	defer sm.devicesMutex.Unlock()
	
	deviceName := sm.parseDeviceName(userAgent)
	
	// Check if device already exists (update last seen)
	for i, device := range sm.connectedDevices {
		if device.Token == token {
			sm.connectedDevices[i].LastSeenAt = time.Now()
			log.Printf("📱 Updated device activity: %s", sm.connectedDevices[i].DeviceName)
			return
		}
	}
	
	// Add new device
	device := models.ConnectedDevice{
		ID:          uuid.New(),
		Token:       token,
		DeviceName:  deviceName,
		IPAddress:   ipAddress,
		UserAgent:   userAgent,
		ConnectedAt: time.Now(),
		LastSeenAt:  time.Now(),
	}
	
	sm.connectedDevices = append(sm.connectedDevices, device)
	log.Printf("📱 New device connected: %s (%s)", device.DeviceName, device.IPAddress)
	
	// Clean up inactive devices
	sm.cleanupInactiveDevices()
}

// DisconnectDevice removes a device by token
func (sm *ServerManager) DisconnectDevice(token string) bool {
	sm.devicesMutex.Lock()
	defer sm.devicesMutex.Unlock()
	
	for i, device := range sm.connectedDevices {
		if device.Token == token {
			// Remove device
			sm.connectedDevices = append(sm.connectedDevices[:i], sm.connectedDevices[i+1:]...)
			log.Printf("📱 Device disconnected: %s (%s)", device.DeviceName, device.IPAddress)
			
			// Revoke token
			sm.revokePairingToken(token)
			return true
		}
	}
	
	return false
}

// GetConnectedDevices returns a copy of connected devices
func (sm *ServerManager) GetConnectedDevices() []models.ConnectedDevice {
	sm.devicesMutex.RLock()
	defer sm.devicesMutex.RUnlock()
	
	devices := make([]models.ConnectedDevice, len(sm.connectedDevices))
	copy(devices, sm.connectedDevices)
	return devices
}

// clearConnectedDevices removes all connected devices
func (sm *ServerManager) clearConnectedDevices() {
	sm.devicesMutex.Lock()
	defer sm.devicesMutex.Unlock()
	
	sm.connectedDevices = []models.ConnectedDevice{}
	log.Println("📱 All devices disconnected")
}

// cleanupInactiveDevices removes devices that haven't been seen recently
func (sm *ServerManager) cleanupInactiveDevices() {
	cutoff := time.Now().Add(-2 * time.Minute) // 2 minutes inactive = removed
	activeDevices := []models.ConnectedDevice{}
	
	for _, device := range sm.connectedDevices {
		if device.LastSeenAt.After(cutoff) {
			activeDevices = append(activeDevices, device)
		}
	}
	
	if len(activeDevices) != len(sm.connectedDevices) {
		removed := len(sm.connectedDevices) - len(activeDevices)
		sm.connectedDevices = activeDevices
		log.Printf("📱 Cleaned up %d inactive devices", removed)
	}
}

// startDeviceMonitor starts background monitoring for device connections
func (sm *ServerManager) startDeviceMonitor() {
	go func() {
		ticker := time.NewTicker(30 * time.Second)
		defer ticker.Stop()
		
		for {
			select {
			case <-ticker.C:
				if sm.IsRunning {
					sm.cleanupInactiveDevices()
				}
			case <-sm.ctx.Done():
				return
			}
		}
	}()
}

// parseDeviceName extracts device info from user agent
func (sm *ServerManager) parseDeviceName(userAgent string) string {
	if userAgent == "" {
		return "Unknown Device"
	}
	
	// Simple parsing to extract device info from user agent
	switch {
	case contains(userAgent, "Android"):
		return "Android Device"
	case contains(userAgent, "iPhone"):
		return "iPhone"
	case contains(userAgent, "iPad"):
		return "iPad"
	case contains(userAgent, "Mac"):
		return "Mac"
	case contains(userAgent, "BMA"):
		return "BMA App"
	default:
		return "Unknown Device"
	}
}

// Helper function for string contains check
func contains(s, substr string) bool {
	return len(s) >= len(substr) && 
		   (s == substr || 
		    (len(s) > len(substr) && 
		     (s[:len(substr)] == substr || 
		      s[len(s)-len(substr):] == substr || 
		      containsAtIndex(s, substr))))
}

func containsAtIndex(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}

// Token management methods

// GeneratePairingToken creates a new pairing token with expiration
func (sm *ServerManager) GeneratePairingToken(expiresInMinutes int) string {
	sm.tokensMutex.Lock()
	defer sm.tokensMutex.Unlock()
	
	token := uuid.New().String()
	expiration := time.Now().Add(time.Duration(expiresInMinutes) * time.Minute)
	
	sm.pairingTokens[token] = expiration
	sm.currentPairingToken = token
	
	// Clean up expired tokens
	sm.cleanupExpiredTokensUnsafe()
	
	log.Printf("🔑 Generated pairing token: %s... (expires in %d minutes)", token[:8], expiresInMinutes)
	return token
}

// IsValidToken checks if a token is valid and not expired
func (sm *ServerManager) IsValidToken(token string) bool {
	sm.tokensMutex.RLock()
	defer sm.tokensMutex.RUnlock()
	
	expiration, exists := sm.pairingTokens[token]
	if !exists {
		return false
	}
	
	// Check if token is expired
	if time.Now().After(expiration) {
		// Remove expired token (but need to upgrade to write lock)
		sm.tokensMutex.RUnlock()
		sm.tokensMutex.Lock()
		delete(sm.pairingTokens, token)
		if sm.currentPairingToken == token {
			sm.currentPairingToken = ""
		}
		sm.tokensMutex.Unlock()
		sm.tokensMutex.RLock()
		return false
	}
	
	return true
}

// revokePairingToken removes a specific token
func (sm *ServerManager) revokePairingToken(token string) {
	sm.tokensMutex.Lock()
	defer sm.tokensMutex.Unlock()
	
	delete(sm.pairingTokens, token)
	if sm.currentPairingToken == token {
		sm.currentPairingToken = ""
	}
	log.Printf("🔒 Revoked pairing token: %s...", token[:8])
}

// revokeAllTokens removes all tokens
func (sm *ServerManager) revokeAllTokens() {
	sm.tokensMutex.Lock()
	defer sm.tokensMutex.Unlock()
	
	sm.pairingTokens = make(map[string]time.Time)
	sm.currentPairingToken = ""
	log.Println("🔒 All pairing tokens revoked")
}

// cleanupExpiredTokensUnsafe removes expired tokens (assumes write lock held)
func (sm *ServerManager) cleanupExpiredTokensUnsafe() {
	now := time.Now()
	for token, expiration := range sm.pairingTokens {
		if now.After(expiration) {
			delete(sm.pairingTokens, token)
			if sm.currentPairingToken == token {
				sm.currentPairingToken = ""
			}
		}
	}
}

// GetCurrentPairingToken returns the current pairing token
func (sm *ServerManager) GetCurrentPairingToken() string {
	sm.tokensMutex.RLock()
	defer sm.tokensMutex.RUnlock()
	return sm.currentPairingToken
}

// Router setup

// setupRouter initializes the HTTP router with all endpoints
func (sm *ServerManager) setupRouter() {
	sm.router = mux.NewRouter()
	
	// Add request logging middleware
	sm.router.Use(sm.requestLoggingMiddleware)
	
	// Setup all routes
	sm.setupRoutes()
	
	log.Println("✅ HTTP router configured with all endpoints")
}

// GetRouter returns the HTTP router (for use in UI)
func (sm *ServerManager) GetRouter() *mux.Router {
	return sm.router
}

// GetServerURL returns the current server URL
func (sm *ServerManager) GetServerURL() string {
	if sm.HasTailscale && sm.TailscaleURL != "" {
		return fmt.Sprintf("%s:%d", sm.TailscaleURL, sm.Port)
	}
	return fmt.Sprintf("http://%s:%d", sm.getLocalIPAddress(), sm.Port)
}

// Middleware

// requestLoggingMiddleware logs all HTTP requests
func (sm *ServerManager) requestLoggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		
		// Extract client info
		clientIP := r.RemoteAddr
		if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
			clientIP = forwarded
		}
		userAgent := r.Header.Get("User-Agent")
		if userAgent == "" {
			userAgent = "unknown"
		}
		
		// Extract auth info
		auth := "none"
		if authHeader := r.Header.Get("Authorization"); authHeader != "" {
			if len(authHeader) > 15 { // "Bearer " + some token
				auth = authHeader[7:15] + "..." // Show first 8 chars after "Bearer "
			}
		}
		
		log.Printf("📥 [REQUEST] %s %s", r.Method, r.URL.Path)
		log.Printf("   └─ From: %s", clientIP)
		log.Printf("   └─ User-Agent: %s", userAgent)
		log.Printf("   └─ Auth: %s", auth)
		
		// Wrap ResponseWriter to capture status code
		wrapped := &responseWriter{ResponseWriter: w, statusCode: 200}
		
		// Call next handler
		next.ServeHTTP(wrapped, r)
		
		// Log response
		duration := time.Since(start)
		log.Printf("📤 [RESPONSE] %d (%s)", wrapped.statusCode, duration)
	})
}

// responseWriter wraps http.ResponseWriter to capture status code
type responseWriter struct {
	http.ResponseWriter
	statusCode int
}

func (rw *responseWriter) WriteHeader(code int) {
	rw.statusCode = code
	rw.ResponseWriter.WriteHeader(code)
}

// QR Code Generation Methods

// GenerateQRCode creates a QR code for device pairing with caching for speed
func (sm *ServerManager) GenerateQRCode() ([]byte, string, error) {
	if !sm.IsRunning {
		return nil, "", fmt.Errorf("server is not running")
	}

	// Check cache first for instant loading
	sm.qrCacheMutex.RLock()
	if sm.cachedQRBytes != nil && sm.cachedQRJSON != "" {
		log.Printf("⚡ Using cached QR code (%d bytes)", len(sm.cachedQRBytes))
		cachedBytes := make([]byte, len(sm.cachedQRBytes))
		copy(cachedBytes, sm.cachedQRBytes)
		cachedJSON := sm.cachedQRJSON
		sm.qrCacheMutex.RUnlock()
		return cachedBytes, cachedJSON, nil
	}
	sm.qrCacheMutex.RUnlock()

	// Generate new QR code if not cached
	log.Println("🔄 Generating new QR code...")
	jsonData, err := sm.GetPairingDataAsJSON()
	if err != nil {
		return nil, "", fmt.Errorf("failed to get pairing data: %w", err)
	}

	qrBytes, err := models.GenerateQRCode(jsonData)
	if err != nil {
		return nil, "", fmt.Errorf("failed to generate QR code: %w", err)
	}

	// Cache the result for future fast access
	sm.qrCacheMutex.Lock()
	sm.cachedQRBytes = make([]byte, len(qrBytes))
	copy(sm.cachedQRBytes, qrBytes)
	sm.cachedQRJSON = jsonData
	sm.qrCacheMutex.Unlock()

	log.Printf("✅ QR code generated and cached (%d bytes)", len(qrBytes))
	return qrBytes, jsonData, nil
}

// ClearQRCache clears the cached QR code (call when server stops or URLs change)
func (sm *ServerManager) ClearQRCache() {
	sm.qrCacheMutex.Lock()
	sm.cachedQRBytes = nil
	sm.cachedQRJSON = ""
	sm.qrCacheMutex.Unlock()
	log.Println("🗑️ QR code cache cleared")
}

// PreloadQRCode generates and caches QR code in background for instant access
func (sm *ServerManager) PreloadQRCode() {
	if !sm.IsRunning {
		return
	}
	
	go func() {
		log.Println("⚡ Preloading QR code for instant access...")
		_, _, err := sm.GenerateQRCode()
		if err != nil {
			log.Printf("⚠️ QR preload failed: %v", err)
		} else {
			log.Println("✅ QR code preloaded successfully")
		}
	}()
}

// GetPairingDataAsJSON returns the pairing data as JSON
func (sm *ServerManager) GetPairingDataAsJSON() (string, error) {
	if !sm.IsRunning {
		return "", fmt.Errorf("server is not running")
	}

	token := sm.GeneratePairingToken(60) // 60-minute expiration
	expiresAt := time.Now().Add(60 * time.Minute)
	serverURL := sm.GetPreferredURL()

	pairingData := models.PairingData{
		ServerURL: serverURL,
		Token:     token,
		ExpiresAt: expiresAt,
	}

	jsonData, err := json.MarshalIndent(pairingData, "", "  ")
	if err != nil {
		return "", fmt.Errorf("failed to marshal pairing data: %w", err)
	}

	return string(jsonData), nil
}
