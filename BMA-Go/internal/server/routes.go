package server

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"time"

	"bma-go/internal/models"
	"github.com/gorilla/mux"
)


// setupRoutes configures all API endpoints
func (sm *ServerManager) setupRoutes() {
	log.Println("📝 Setting up API routes...")
	
	// Create auth middleware
	authMiddleware := NewAuthMiddleware(sm)
	
	// Public endpoints (no authentication required)
	sm.router.HandleFunc("/health", sm.handleHealth).Methods("GET")
	sm.router.HandleFunc("/info", sm.handleInfo).Methods("GET")
	sm.router.HandleFunc("/pair", sm.handlePair).Methods("POST")
	
	// Authenticated endpoints (require Bearer token)
	sm.router.HandleFunc("/disconnect", authMiddleware.RequireAuth(sm.handleDisconnect)).Methods("POST")
	sm.router.HandleFunc("/heartbeat", authMiddleware.RequireAuth(sm.handleHeartbeat)).Methods("POST")
	sm.router.HandleFunc("/songs", authMiddleware.RequireAuth(sm.handleSongs)).Methods("GET")
	sm.router.HandleFunc("/stream/{songId}", authMiddleware.RequireAuth(sm.handleStream)).Methods("GET")
	sm.router.HandleFunc("/artwork/{songId}", authMiddleware.RequireAuth(sm.handleArtwork)).Methods("GET")
	
	log.Println("✅ All API routes configured")
}

// Public endpoints

// handleHealth returns server health status
func (sm *ServerManager) handleHealth(w http.ResponseWriter, r *http.Request) {
	log.Printf("🔍 Health check requested from %s", r.RemoteAddr)
	
	response := map[string]string{
		"status": "healthy",
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// handleInfo returns server information
func (sm *ServerManager) handleInfo(w http.ResponseWriter, r *http.Request) {
	log.Println("ℹ️ Server info requested")
	
	// Get music library statistics
	var albumCount, songCount int
	if sm.musicLibrary != nil {
		albumCount = sm.musicLibrary.GetAlbumCount()
		songCount = sm.musicLibrary.GetSongCount()
		log.Printf("📊 Music library stats: %d albums, %d songs", albumCount, songCount)
	}
	
	response := map[string]interface{}{
		"server":      "BMA Music Server",
		"version":     "2.0",
		"hasTailscale": sm.HasTailscale,
		"tailscaleUrl": sm.TailscaleURL,
		"httpsPort":   8443, // For compatibility
		"httpPort":    sm.Port,
		"protocol":    func() string {
			if sm.HasTailscale {
				return "http" // HTTP over Tailscale
			}
			return "http"
		}(),
		// Music library statistics
		"library": map[string]interface{}{
			"albumCount": albumCount,
			"songCount":  songCount,
			"hasLibrary": sm.musicLibrary != nil,
		},
	}
	
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(response); err != nil {
		log.Printf("❌ Failed to encode server info: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
	
	log.Printf("✅ Server info sent successfully (albums: %d, songs: %d)", albumCount, songCount)
}

// handlePair creates a new pairing token for device authentication
func (sm *ServerManager) handlePair(w http.ResponseWriter, r *http.Request) {
	log.Println("📱 Pairing request received")
	
	// Generate pairing token (60 minutes expiration)
	token := sm.GeneratePairingToken(60)
	
	// Determine server URL
	serverURL := sm.GetServerURL()
	
	// Create pairing response
	pairingInfo := models.PairingData{
		ServerURL: serverURL,
		Token:     token,
		ExpiresAt: time.Now().Add(60 * time.Minute),
	}
	
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(pairingInfo); err != nil {
		log.Printf("❌ Failed to encode pairing info: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
	
	log.Printf("✅ Pairing token generated: %s... (expires in 60 minutes)", token[:8])
}

// Authenticated endpoints

// handleDisconnect removes a device from connected devices list
func (sm *ServerManager) handleDisconnect(w http.ResponseWriter, r *http.Request) {
	log.Println("📱 Disconnect request received")
	
	// Extract token from request context (set by auth middleware)
	token, ok := r.Context().Value(TokenContextKey).(string)
	if !ok {
		log.Println("❌ No token found in disconnect request context")
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}
	
	clientIP, _ := r.Context().Value(ClientIPContextKey).(string)
	log.Printf("📱 Processing disconnect for token: %s... from IP: %s", token[:8], clientIP)
	
	// Find and remove the device
	if sm.DisconnectDevice(token) {
		log.Printf("📱 Device successfully disconnected")
	} else {
		log.Printf("⚠️ No device found with token for disconnect")
	}
	
	// Return success response
	response := map[string]string{
		"status":  "disconnected",
		"message": "Device successfully disconnected",
	}
	
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(response); err != nil {
		log.Printf("❌ Failed to encode disconnect response: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
	
	log.Println("✅ Disconnect response sent successfully")
}

// handleHeartbeat processes device heartbeat pings for connection monitoring
func (sm *ServerManager) handleHeartbeat(w http.ResponseWriter, r *http.Request) {
	// Auth middleware already called TrackDeviceConnection(), so device activity is updated
	
	response := map[string]interface{}{
		"status":     "alive",
		"serverTime": time.Now().Format(time.RFC3339),
	}
	
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(response); err != nil {
		log.Printf("❌ Failed to encode heartbeat response: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
}

// handleSongs returns the list of all songs with album organization
func (sm *ServerManager) handleSongs(w http.ResponseWriter, r *http.Request) {
	// Extract token for logging
	if token, ok := r.Context().Value(TokenContextKey).(string); ok {
		log.Printf("🎵 Songs requested with auth: %s...", token[:8])
	}
	
	// Check if music library is available
	if sm.musicLibrary == nil {
		log.Println("❌ No music library connected to server")
		songs := []map[string]interface{}{}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(songs)
		return
	}
	
	// Get songs from the music library
	librarySongs := sm.musicLibrary.GetSongs()
	log.Printf("📊 Retrieved %d songs from music library", len(librarySongs))
	
	// Convert songs to JSON-compatible format
	songs := make([]map[string]interface{}, len(librarySongs))
	for i, song := range librarySongs {
		songs[i] = map[string]interface{}{
			"id":              song.ID.String(),
			"filename":        song.Filename,
			"title":           song.Title,
			"artist":          song.Artist,
			"album":           song.Album,
			"trackNumber":     song.TrackNumber,
			"parentDirectory": song.ParentDirectory,
			"hasArtwork":      song.HasArtwork(),
			"sortOrder":       i, // Explicit sort order for Android to maintain
		}
	}
	
	// Debug: Log the exact order being sent to Android
	log.Printf("📊 [DEBUG] Sending songs to Android in this order:")
	for i, song := range librarySongs {
		if i < 10 { // Show first 10 songs
			log.Printf("📊 [DEBUG]   %d: %s - %s (Track: %d, Album: %s)", 
				i+1, song.Artist, song.Title, song.TrackNumber, song.Album)
		}
	}
	if len(librarySongs) > 10 {
		log.Printf("📊 [DEBUG]   ... and %d more songs", len(librarySongs)-10)
	}
	
	log.Printf("📊 Returning %d songs to client", len(songs))
	
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(songs); err != nil {
		log.Printf("❌ Failed to encode songs data: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
	
	log.Println("✅ Songs list sent successfully")
}

// handleStream serves MP3 file content for a given song ID
func (sm *ServerManager) handleStream(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	songID := vars["songId"]
	
	if songID == "" {
		http.Error(w, "Missing song ID", http.StatusBadRequest)
		return
	}
	
	log.Printf("🎵 Stream requested for song ID: %s", songID)
	
	// Check if music library is available
	if sm.musicLibrary == nil {
		log.Println("❌ No music library connected to server")
		http.Error(w, "Music library not available", http.StatusServiceUnavailable)
		return
	}
	
	// Find the song in the music library
	song := sm.musicLibrary.GetSongByID(songID)
	if song == nil {
		log.Printf("❌ Song not found: %s", songID)
		http.Error(w, "Song not found", http.StatusNotFound)
		return
	}
	
	log.Printf("🎵 Streaming song: %s - %s", song.Artist, song.Title)
	log.Printf("🎵 File path: %s", song.Path)
	
	// Check if file exists
	if _, err := os.Stat(song.Path); os.IsNotExist(err) {
		log.Printf("❌ MP3 file not found at path: %s", song.Path)
		http.Error(w, "Music file not found", http.StatusNotFound)
		return
	}
	
	// Stream the MP3 file
	if err := writeFileResponse(w, song.Path, "audio/mpeg"); err != nil {
		log.Printf("❌ Failed to stream MP3 file: %v", err)
		http.Error(w, "Failed to stream file", http.StatusInternalServerError)
		return
	}
	
	log.Printf("✅ Successfully streamed: %s", song.Title)
}

// handleArtwork serves album artwork for a given song ID  
func (sm *ServerManager) handleArtwork(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	songID := vars["songId"]
	
	if songID == "" {
		http.Error(w, "Missing song ID", http.StatusBadRequest)
		return
	}
	
	log.Printf("🎨 Artwork requested for song ID: %s", songID)
	
	// Check if music library is available
	if sm.musicLibrary == nil {
		log.Println("❌ No music library connected to server")
		http.Error(w, "Music library not available", http.StatusServiceUnavailable)
		return
	}
	
	// Find the song in the music library
	song := sm.musicLibrary.GetSongByID(songID)
	if song == nil {
		log.Printf("❌ Song not found: %s", songID)
		http.Error(w, "Song not found", http.StatusNotFound)
		return
	}
	
	// Check if song has artwork
	artworkData := song.GetArtwork()
	if len(artworkData) == 0 {
		log.Printf("❌ No artwork found for song: %s - %s", song.Artist, song.Title)
		http.Error(w, "Artwork not found", http.StatusNotFound)
		return
	}
	
	log.Printf("🎨 Serving artwork for: %s - %s (%d bytes)", song.Artist, song.Title, len(artworkData))
	
	// Determine content type (most MP3 artwork is JPEG, but could be PNG)
	contentType := "image/jpeg"
	if len(artworkData) >= 8 {
		// Check for PNG signature
		if artworkData[0] == 0x89 && artworkData[1] == 0x50 && artworkData[2] == 0x4E && artworkData[3] == 0x47 {
			contentType = "image/png"
		}
	}
	
	// Set headers and serve artwork
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Content-Length", fmt.Sprintf("%d", len(artworkData)))
	w.Header().Set("Cache-Control", "public, max-age=3600") // Cache for 1 hour
	
	if _, err := w.Write(artworkData); err != nil {
		log.Printf("❌ Failed to serve artwork: %v", err)
		return
	}
	
	log.Printf("✅ Successfully served artwork for: %s", song.Title)
}

// Helper functions

// writeJSONResponse writes a JSON response with proper headers
func writeJSONResponse(w http.ResponseWriter, data interface{}) error {
	w.Header().Set("Content-Type", "application/json")
	return json.NewEncoder(w).Encode(data)
}

// writeFileResponse streams a file as HTTP response
func writeFileResponse(w http.ResponseWriter, filePath, contentType string) error {
	file, err := os.Open(filePath)
	if err != nil {
		return err
	}
	defer file.Close()
	
	// Get file info for content length
	fileInfo, err := file.Stat()
	if err != nil {
		return err
	}
	
	// Set headers
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Content-Length", fmt.Sprintf("%d", fileInfo.Size()))
	
	// Stream file content
	_, err = io.Copy(w, file)
	return err
} 