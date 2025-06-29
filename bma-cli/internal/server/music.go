package server

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"html/template"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"strings"
	"time"

	"bma-cli/internal/models"
	"github.com/google/uuid"
	"github.com/gorilla/mux"
	"github.com/skip2/go-qrcode"
)

// MusicServer handles music streaming and API endpoints
type MusicServer struct {
	config       *models.Config
	musicLibrary *models.MusicLibrary
	server       *http.Server
	router       *mux.Router
}

// NewMusicServer creates a new music server
func NewMusicServer(config *models.Config, musicLibrary *models.MusicLibrary) *MusicServer {
	ms := &MusicServer{
		config:       config,
		musicLibrary: musicLibrary,
	}
	
	ms.setupRoutes()
	return ms
}

// setupRoutes configures all music streaming endpoints
func (ms *MusicServer) setupRoutes() {
	ms.router = mux.NewRouter()
	
	// Add CORS middleware for mobile app access
	ms.router.Use(ms.corsMiddleware)
	ms.router.Use(ms.requestLoggingMiddleware)
	
	// Public endpoints (no authentication required for now)
	ms.router.HandleFunc("/health", ms.handleHealth).Methods("GET")
	ms.router.HandleFunc("/info", ms.handleInfo).Methods("GET")
	ms.router.HandleFunc("/songs", ms.handleSongs).Methods("GET")
	ms.router.HandleFunc("/albums", ms.handleAlbums).Methods("GET")
	ms.router.HandleFunc("/stream/{songId}", ms.handleStream).Methods("GET")
	ms.router.HandleFunc("/artwork/{songId}", ms.handleArtwork).Methods("GET")
	
	// Pairing endpoints
	ms.router.HandleFunc("/pair", ms.handlePair).Methods("POST")
	ms.router.HandleFunc("/qr", ms.handleQRPage).Methods("GET")
	
	log.Println("✅ Music server routes configured")
}

// Start starts the music server
func (ms *MusicServer) Start() error {
	ms.server = &http.Server{
		Addr:    ":8080",
		Handler: ms.router,
	}
	
	log.Println("🚀 Music server starting on :8080")
	return ms.server.ListenAndServe()
}

// Shutdown gracefully shuts down the server
func (ms *MusicServer) Shutdown() error {
	if ms.server == nil {
		return nil
	}
	
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	return ms.server.Shutdown(ctx)
}

// corsMiddleware adds CORS headers for mobile app access
func (ms *MusicServer) corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		
		// Handle preflight requests
		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusOK)
			return
		}
		
		next.ServeHTTP(w, r)
	})
}

// requestLoggingMiddleware logs all HTTP requests
func (ms *MusicServer) requestLoggingMiddleware(next http.Handler) http.Handler {
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
		
		log.Printf("📥 [REQUEST] %s %s from %s", r.Method, r.URL.Path, clientIP)
		log.Printf("   └─ User-Agent: %s", userAgent)
		
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

// handleHealth returns server health status
func (ms *MusicServer) handleHealth(w http.ResponseWriter, r *http.Request) {
	log.Printf("🔍 Health check requested from %s", r.RemoteAddr)
	
	response := map[string]string{
		"status": "healthy",
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// handleInfo returns server information
func (ms *MusicServer) handleInfo(w http.ResponseWriter, r *http.Request) {
	log.Println("ℹ️ Server info requested")
	
	// Get music library statistics
	var albumCount, songCount int
	var libraryVersion int64
	if ms.musicLibrary != nil {
		albumCount = ms.musicLibrary.GetAlbumCount()
		songCount = ms.musicLibrary.GetSongCount()
		libraryVersion = ms.musicLibrary.GetLibraryVersion()
		log.Printf("📊 Music library stats: %d albums, %d songs, version: %d", albumCount, songCount, libraryVersion)
	}
	
	response := map[string]interface{}{
		"server":      "BMA CLI Music Server",
		"version":     "1.0",
		"httpPort":    8080,
		"protocol":    "http",
		// Music library statistics
		"library": map[string]interface{}{
			"albumCount":     albumCount,
			"songCount":      songCount,
			"hasLibrary":     ms.musicLibrary != nil,
			"musicPath":      ms.config.MusicFolder,
			"libraryVersion": libraryVersion,
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

// handleSongs returns the list of all songs
func (ms *MusicServer) handleSongs(w http.ResponseWriter, r *http.Request) {
	log.Println("🎵 Songs list requested")
	
	// Check if music library is available
	if ms.musicLibrary == nil {
		log.Println("❌ No music library available")
		songs := []map[string]interface{}{}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(songs)
		return
	}
	
	// Get songs from the music library
	librarySongs := ms.musicLibrary.GetSongs()
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
			"sortOrder":       i, // Explicit sort order
		}
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

// handleAlbums returns the list of all albums
func (ms *MusicServer) handleAlbums(w http.ResponseWriter, r *http.Request) {
	log.Println("📀 Albums list requested")
	
	// Check if music library is available
	if ms.musicLibrary == nil {
		log.Println("❌ No music library available")
		albums := []map[string]interface{}{}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(albums)
		return
	}
	
	// Get albums from the music library
	libraryAlbums := ms.musicLibrary.GetAlbums()
	log.Printf("📊 Retrieved %d albums from music library", len(libraryAlbums))
	
	// Convert albums to JSON-compatible format
	albums := make([]map[string]interface{}, len(libraryAlbums))
	for i, album := range libraryAlbums {
		// Convert songs in album
		songs := make([]map[string]interface{}, len(album.Songs))
		for j, song := range album.Songs {
			songs[j] = map[string]interface{}{
				"id":          song.ID.String(),
				"title":       song.Title,
				"artist":      song.Artist,
				"trackNumber": song.TrackNumber,
				"hasArtwork":  song.HasArtwork(),
			}
		}
		
		albums[i] = map[string]interface{}{
			"id":         album.ID.String(),
			"name":       album.Name,
			"artist":     album.Artist,
			"trackCount": album.TrackCount(),
			"songs":      songs,
		}
	}
	
	log.Printf("📊 Returning %d albums to client", len(albums))
	
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(albums); err != nil {
		log.Printf("❌ Failed to encode albums data: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
	
	log.Println("✅ Albums list sent successfully")
}

// handleStream serves MP3 file content for a given song ID
func (ms *MusicServer) handleStream(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	songID := vars["songId"]
	
	if songID == "" {
		http.Error(w, "Missing song ID", http.StatusBadRequest)
		return
	}
	
	log.Printf("🎵 Stream requested for song ID: %s", songID)
	
	// Check if music library is available
	if ms.musicLibrary == nil {
		log.Println("❌ No music library available")
		http.Error(w, "Music library not available", http.StatusServiceUnavailable)
		return
	}
	
	// Find the song in the music library
	song := ms.musicLibrary.GetSongByID(songID)
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
	if err := ms.writeFileResponse(w, song.Path, "audio/mpeg"); err != nil {
		log.Printf("❌ Failed to stream MP3 file: %v", err)
		http.Error(w, "Failed to stream file", http.StatusInternalServerError)
		return
	}
	
	log.Printf("✅ Successfully streamed: %s", song.Title)
}

// handleArtwork serves album artwork for a given song ID  
func (ms *MusicServer) handleArtwork(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	songID := vars["songId"]
	
	if songID == "" {
		http.Error(w, "Missing song ID", http.StatusBadRequest)
		return
	}
	
	log.Printf("🎨 Artwork requested for song ID: %s", songID)
	
	// Check if music library is available
	if ms.musicLibrary == nil {
		log.Println("❌ No music library available")
		http.Error(w, "Music library not available", http.StatusServiceUnavailable)
		return
	}
	
	// Find the song in the music library
	song := ms.musicLibrary.GetSongByID(songID)
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

// writeFileResponse streams a file as HTTP response
func (ms *MusicServer) writeFileResponse(w http.ResponseWriter, filePath, contentType string) error {
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
	w.Header().Set("Accept-Ranges", "bytes")
	
	// Stream file content
	_, err = io.Copy(w, file)
	return err
}

// handleQRPage serves the QR code pairing page
func (ms *MusicServer) handleQRPage(w http.ResponseWriter, r *http.Request) {
	log.Println("🔗 QR code page requested")
	
	// Generate pairing data
	pairingData := ms.generatePairingData()
	
	// Generate QR code
	qrCode, err := qrcode.Encode(pairingData, qrcode.Medium, 256)
	if err != nil {
		log.Printf("❌ Failed to generate QR code: %v", err)
		http.Error(w, "Failed to generate QR code", http.StatusInternalServerError)
		return
	}
	
	// Convert to base64 for embedding in HTML
	qrCodeBase64 := base64.StdEncoding.EncodeToString(qrCode)
	
	// HTML template for QR code page
	tmpl := `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BMA CLI - Pair Device</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            max-width: 600px;
            margin: 0 auto;
            padding: 20px;
            background-color: #f5f5f5;
            text-align: center;
        }
        .container {
            background: white;
            border-radius: 12px;
            padding: 30px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 {
            color: #333;
            margin-bottom: 20px;
        }
        .qr-container {
            margin: 30px 0;
        }
        .qr-code {
            border: 1px solid #ddd;
            border-radius: 8px;
            max-width: 256px;
        }
        .info {
            background: #e7f3ff;
            border: 1px solid #b3d7ff;
            border-radius: 8px;
            padding: 15px;
            margin: 20px 0;
        }
        .server-info {
            text-align: left;
            background: #f8f9fa;
            border-radius: 8px;
            padding: 15px;
            margin: 20px 0;
        }
        button {
            background: #007bff;
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 4px;
            cursor: pointer;
            font-size: 16px;
            margin: 10px;
        }
        button:hover {
            background: #0056b3;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🎵 BMA CLI - Pair Your Device</h1>
        
        <div class="info">
            <strong>Instructions:</strong><br>
            1. Open the BMA app on your phone<br>
            2. Tap "Scan QR Code"<br>
            3. Scan the QR code below
        </div>
        
        <div class="qr-container">
            <img class="qr-code" src="data:image/png;base64,{{.QRCode}}" alt="Pairing QR Code">
        </div>
        
        <div class="server-info">
            <strong>Server Information:</strong><br>
            <strong>Local URL:</strong> {{.LocalURL}}<br>
            {{if .TailscaleURL}}<strong>Remote URL:</strong> {{.TailscaleURL}}<br>{{end}}
            <strong>Music Library:</strong> {{.MusicPath}}<br>
            <strong>Songs:</strong> {{.SongCount}} | <strong>Albums:</strong> {{.AlbumCount}}
        </div>
        
        <button onclick="window.location.reload()">🔄 Refresh QR Code</button>
        <button onclick="window.location.href='/info'">📊 Server Info</button>
    </div>
</body>
</html>`
	
	// Prepare template data
	data := struct {
		QRCode       string
		LocalURL     string
		TailscaleURL string
		MusicPath    string
		SongCount    int
		AlbumCount   int
	}{
		QRCode:       qrCodeBase64,
		LocalURL:     ms.getLocalURL(),
		TailscaleURL: ms.getTailscaleURL(),
		MusicPath:    ms.config.MusicFolder,
		SongCount:    ms.musicLibrary.GetSongCount(),
		AlbumCount:   ms.musicLibrary.GetAlbumCount(),
	}
	
	// Parse and execute template
	t, err := template.New("qr").Parse(tmpl)
	if err != nil {
		http.Error(w, "Template error", http.StatusInternalServerError)
		return
	}
	
	w.Header().Set("Content-Type", "text/html")
	t.Execute(w, data)
	
	log.Println("✅ QR code page served successfully")
}

// handlePair creates a new pairing token for device authentication
func (ms *MusicServer) handlePair(w http.ResponseWriter, r *http.Request) {
	log.Println("📱 Pairing request received")
	
	// Generate simple pairing response matching mobile app expectations
	response := map[string]interface{}{
		"serverUrl": ms.getPreferredURL(),
		"token":     uuid.New().String(),
		"expiresAt": time.Now().Add(60 * time.Minute).Format(time.RFC3339),
	}
	
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(response); err != nil {
		log.Printf("❌ Failed to encode pairing response: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
	
	log.Println("✅ Pairing response sent successfully")
}

// generatePairingData creates the JSON data for QR code
func (ms *MusicServer) generatePairingData() string {
	// Match exact format expected by mobile app
	pairingInfo := map[string]interface{}{
		"serverUrl": ms.getPreferredURL(),
		"token":     uuid.New().String(),
		"expiresAt": time.Now().Add(60 * time.Minute).Format(time.RFC3339),
	}
	
	data, _ := json.Marshal(pairingInfo)
	return string(data)
}

// getLocalURL returns the local network URL
func (ms *MusicServer) getLocalURL() string {
	ip := ms.getLocalIPAddress()
	return fmt.Sprintf("http://%s:8080", ip)
}

// getTailscaleURL returns the Tailscale URL if available
func (ms *MusicServer) getTailscaleURL() string {
	if ms.config.TailscaleIP != "" {
		return fmt.Sprintf("http://%s:8080", ms.config.TailscaleIP)
	}
	
	// Try to get Tailscale IP dynamically
	cmd := exec.Command("tailscale", "ip", "-4")
	output, err := cmd.Output()
	if err == nil {
		ip := strings.TrimSpace(string(output))
		if ip != "" {
			return fmt.Sprintf("http://%s:8080", ip)
		}
	}
	
	return ""
}

// getPreferredURL returns the preferred URL (Tailscale if available, local otherwise)
func (ms *MusicServer) getPreferredURL() string {
	if tailscaleURL := ms.getTailscaleURL(); tailscaleURL != "" {
		return tailscaleURL
	}
	return ms.getLocalURL()
}

// getLocalIPAddress gets the local network IP address
func (ms *MusicServer) getLocalIPAddress() string {
	// Get local IP address by connecting to a remote address
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err != nil {
		return "localhost"
	}
	defer conn.Close()
	
	localAddr := conn.LocalAddr().(*net.UDPAddr)
	return localAddr.IP.String()
}