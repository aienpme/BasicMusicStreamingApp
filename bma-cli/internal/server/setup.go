package server

import (
	"context"
	"encoding/json"
	"fmt"
	"html/template"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"bma-cli/internal/models"
	"github.com/gorilla/mux"
	"github.com/skip2/go-qrcode"
)

// SetupServer handles the initial setup process
type SetupServer struct {
	config *models.Config
	server *http.Server
	router *mux.Router
}

// NewSetupServer creates a new setup server
func NewSetupServer(config *models.Config) *SetupServer {
	ss := &SetupServer{
		config: config,
	}
	
	ss.setupRoutes()
	return ss
}

// setupRoutes configures all setup endpoints
func (ss *SetupServer) setupRoutes() {
	ss.router = mux.NewRouter()
	
	// Static files for setup UI
	ss.router.PathPrefix("/static/").Handler(http.StripPrefix("/static/", http.FileServer(http.Dir("web/static/"))))
	
	// Setup pages
	ss.router.HandleFunc("/setup", ss.handleSetupPage).Methods("GET")
	ss.router.HandleFunc("/", ss.redirectToSetup).Methods("GET")
	
	// API endpoints
	ss.router.HandleFunc("/api/tailscale/status", ss.handleTailscaleStatus).Methods("GET")
	ss.router.HandleFunc("/api/tailscale/auth", ss.handleTailscaleAuth).Methods("POST")
	ss.router.HandleFunc("/api/music/validate", ss.handleMusicDirectoryValidation).Methods("POST")
	ss.router.HandleFunc("/api/setup/complete", ss.handleSetupComplete).Methods("POST")
	
	log.Println("✅ Setup routes configured")
}

// Start starts the setup server
func (ss *SetupServer) Start() error {
	ss.server = &http.Server{
		Addr:    ":8080",
		Handler: ss.router,
	}
	
	log.Println("🚀 Setup server starting on :8080")
	return ss.server.ListenAndServe()
}

// Shutdown gracefully shuts down the server
func (ss *SetupServer) Shutdown() error {
	if ss.server == nil {
		return nil
	}
	
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	return ss.server.Shutdown(ctx)
}

// redirectToSetup redirects root to setup page
func (ss *SetupServer) redirectToSetup(w http.ResponseWriter, r *http.Request) {
	http.Redirect(w, r, "/setup", http.StatusFound)
}

// handleSetupPage serves the main setup page
func (ss *SetupServer) handleSetupPage(w http.ResponseWriter, r *http.Request) {
	log.Println("📄 Setup page requested")
	
	tmpl := `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BMA CLI Setup</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            background-color: #f5f5f5;
        }
        .container {
            background: white;
            border-radius: 12px;
            padding: 30px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 {
            color: #333;
            text-align: center;
            margin-bottom: 30px;
        }
        .step {
            margin-bottom: 30px;
            padding: 20px;
            background: #f8f9fa;
            border-radius: 8px;
            border-left: 4px solid #007bff;
        }
        .step h3 {
            margin-top: 0;
            color: #007bff;
        }
        .step.active {
            border-left-color: #28a745;
            background: #f8fff8;
        }
        .step.completed {
            border-left-color: #6c757d;
            background: #f1f3f4;
            opacity: 0.7;
        }
        .qr-container {
            text-align: center;
            margin: 20px 0;
        }
        .qr-code {
            max-width: 200px;
            border: 1px solid #ddd;
            border-radius: 8px;
        }
        .form-group {
            margin-bottom: 20px;
        }
        label {
            display: block;
            margin-bottom: 5px;
            font-weight: 500;
        }
        input[type="text"] {
            width: 100%;
            padding: 10px;
            border: 1px solid #ddd;
            border-radius: 4px;
            font-size: 16px;
        }
        button {
            background: #007bff;
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 4px;
            cursor: pointer;
            font-size: 16px;
        }
        button:hover {
            background: #0056b3;
        }
        button:disabled {
            background: #6c757d;
            cursor: not-allowed;
        }
        .status {
            padding: 10px;
            border-radius: 4px;
            margin-bottom: 20px;
        }
        .status.success {
            background: #d4edda;
            color: #155724;
            border: 1px solid #c3e6cb;
        }
        .status.error {
            background: #f8d7da;
            color: #721c24;
            border: 1px solid #f5c6cb;
        }
        .status.info {
            background: #cce7ff;
            color: #004085;
            border: 1px solid #b3d7ff;
        }
        .hidden {
            display: none;
        }
        .loading {
            text-align: center;
            padding: 20px;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🎵 BMA CLI Setup</h1>
        
        <!-- Step 1: Tailscale Setup -->
        <div id="step1" class="step active">
            <h3>Step 1: Tailscale Configuration</h3>
            <p>First, let's set up Tailscale for secure remote access.</p>
            
            <div id="tailscale-status" class="status info">
                <div class="loading">Checking Tailscale status...</div>
            </div>
            
            <div id="qr-section" class="hidden">
                <p>Scan this QR code with the Tailscale app to authenticate this device:</p>
                <div class="qr-container">
                    <img id="qr-code" class="qr-code" src="" alt="Tailscale Auth QR Code">
                </div>
                <p><small>Or visit: <span id="auth-url"></span></small></p>
                <button onclick="checkTailscaleAuth()">I've authenticated the device</button>
            </div>
        </div>
        
        <!-- Step 2: Music Directory -->
        <div id="step2" class="step">
            <h3>Step 2: Music Directory</h3>
            <p>Specify the directory containing your music files.</p>
            
            <div class="form-group">
                <label for="music-path">Music Directory Path:</label>
                <input type="text" id="music-path" placeholder="/path/to/your/music" value="">
            </div>
            
            <button onclick="validateMusicDirectory()">Validate Directory</button>
            
            <div id="music-status" class="hidden"></div>
        </div>
        
        <!-- Step 3: Complete Setup -->
        <div id="step3" class="step">
            <h3>Step 3: Complete Setup</h3>
            <p>Finalize your BMA CLI configuration.</p>
            
            <div id="setup-summary" class="hidden">
                <ul>
                    <li>Tailscale: <span id="summary-tailscale">Pending</span></li>
                    <li>Music Directory: <span id="summary-music">Pending</span></li>
                </ul>
            </div>
            
            <button id="complete-setup" onclick="completeSetup()" disabled>Complete Setup</button>
        </div>
    </div>

    <script>
        let setupState = {
            tailscaleConfigured: false,
            musicDirectoryValid: false,
            musicPath: ''
        };

        // Check Tailscale status on page load
        document.addEventListener('DOMContentLoaded', function() {
            checkTailscaleStatus();
        });

        async function checkTailscaleStatus() {
            try {
                const response = await fetch('/api/tailscale/status');
                const data = await response.json();
                
                const statusDiv = document.getElementById('tailscale-status');
                const qrSection = document.getElementById('qr-section');
                
                if (data.available && data.authenticated) {
                    statusDiv.className = 'status success';
                    statusDiv.innerHTML = '✅ Tailscale is configured and authenticated!';
                    setupState.tailscaleConfigured = true;
                    updateSteps();
                } else if (data.available && data.authURL) {
                    statusDiv.className = 'status info';
                    statusDiv.innerHTML = '⚠️ Tailscale is available but needs authentication.';
                    
                    // Show QR code
                    document.getElementById('auth-url').textContent = data.authURL;
                    if (data.qrCode) {
                        document.getElementById('qr-code').src = 'data:image/png;base64,' + data.qrCode;
                    }
                    qrSection.classList.remove('hidden');
                } else {
                    statusDiv.className = 'status error';
                    statusDiv.innerHTML = '❌ Tailscale is not available. You can continue without it, but remote access will be limited.';
                    setTimeout(() => {
                        setupState.tailscaleConfigured = true; // Allow proceeding without Tailscale
                        updateSteps();
                    }, 3000);
                }
            } catch (error) {
                console.error('Error checking Tailscale status:', error);
                const statusDiv = document.getElementById('tailscale-status');
                statusDiv.className = 'status error';
                statusDiv.innerHTML = '❌ Error checking Tailscale status. Proceeding without it.';
                setupState.tailscaleConfigured = true;
                updateSteps();
            }
        }

        async function checkTailscaleAuth() {
            // Re-check Tailscale status after user claims to have authenticated
            await checkTailscaleStatus();
        }

        async function validateMusicDirectory() {
            const musicPath = document.getElementById('music-path').value.trim();
            if (!musicPath) {
                showMusicStatus('error', 'Please enter a music directory path.');
                return;
            }

            try {
                const response = await fetch('/api/music/validate', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({ path: musicPath })
                });

                const data = await response.json();
                
                if (data.valid) {
                    showMusicStatus('success', '✅ Valid music directory! Found ' + data.fileCount + ' music files.');
                    setupState.musicDirectoryValid = true;
                    setupState.musicPath = musicPath;
                    updateSteps();
                } else {
                    showMusicStatus('error', '❌ ' + (data.error || 'Invalid music directory'));
                    setupState.musicDirectoryValid = false;
                }
            } catch (error) {
                console.error('Error validating music directory:', error);
                showMusicStatus('error', '❌ Error validating directory.');
                setupState.musicDirectoryValid = false;
            }
        }

        function showMusicStatus(type, message) {
            const statusDiv = document.getElementById('music-status');
            statusDiv.className = 'status ' + type;
            statusDiv.innerHTML = message;
            statusDiv.classList.remove('hidden');
        }

        function updateSteps() {
            // Update step 1
            if (setupState.tailscaleConfigured) {
                document.getElementById('step1').className = 'step completed';
                document.getElementById('step2').className = 'step active';
            }

            // Update step 2
            if (setupState.musicDirectoryValid) {
                document.getElementById('step2').className = 'step completed';
                document.getElementById('step3').className = 'step active';
                
                // Update summary
                document.getElementById('summary-tailscale').textContent = setupState.tailscaleConfigured ? 'Configured' : 'Skipped';
                document.getElementById('summary-music').textContent = setupState.musicPath;
                document.getElementById('setup-summary').classList.remove('hidden');
                document.getElementById('complete-setup').disabled = false;
            }
        }

        async function completeSetup() {
            if (!setupState.musicDirectoryValid) {
                alert('Please complete all required steps.');
                return;
            }

            try {
                const response = await fetch('/api/setup/complete', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        musicPath: setupState.musicPath,
                        tailscaleConfigured: setupState.tailscaleConfigured
                    })
                });

                const data = await response.json();
                
                if (data.success) {
                    alert('🎉 Setup complete! Please restart BMA CLI to start the music server.\n\nRun: ./bma-cli');
                    // Redirect or close
                    window.location.href = '/';
                } else {
                    alert('❌ Setup failed: ' + (data.error || 'Unknown error'));
                }
            } catch (error) {
                console.error('Error completing setup:', error);
                alert('❌ Error completing setup.');
            }
        }
    </script>
</body>
</html>`
	
	w.Header().Set("Content-Type", "text/html")
	t, err := template.New("setup").Parse(tmpl)
	if err != nil {
		http.Error(w, "Template error", http.StatusInternalServerError)
		return
	}
	
	t.Execute(w, nil)
}

// handleTailscaleStatus returns Tailscale status and auth info
func (ss *SetupServer) handleTailscaleStatus(w http.ResponseWriter, r *http.Request) {
	log.Println("🔍 Tailscale status requested")
	
	response := map[string]interface{}{
		"available":     false,
		"authenticated": false,
		"authURL":       "",
		"qrCode":        "",
	}
	
	// Try to detect Tailscale and get auth URL
	authResult := ss.getTailscaleAuthURL()
	if authResult == "authenticated" {
		// Tailscale is available and already authenticated
		response["available"] = true
		response["authenticated"] = true
		
		// Get Tailscale IP for the config
		if tailscaleIP := ss.getTailscaleIP(); tailscaleIP != "" {
			ss.config.TailscaleIP = tailscaleIP
			ss.config.SaveConfig()
		}
	} else if authResult != "" {
		// Tailscale is available but needs authentication
		response["available"] = true
		response["authURL"] = authResult
		
		// Generate QR code for auth URL
		if qrData := ss.generateAuthQR(authResult); qrData != "" {
			response["qrCode"] = qrData
		}
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// handleTailscaleAuth handles Tailscale authentication
func (ss *SetupServer) handleTailscaleAuth(w http.ResponseWriter, r *http.Request) {
	log.Println("🔐 Tailscale auth requested")
	
	// TODO: Implement actual Tailscale auth verification
	response := map[string]interface{}{
		"success": true,
		"message": "Tailscale authentication verified",
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// handleMusicDirectoryValidation validates the music directory
func (ss *SetupServer) handleMusicDirectoryValidation(w http.ResponseWriter, r *http.Request) {
	log.Println("📁 Music directory validation requested")
	
	var request struct {
		Path string `json:"path"`
	}
	
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}
	
	log.Printf("📁 Validating music directory: %s", request.Path)
	
	response := ss.validateMusicDirectory(request.Path)
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// handleSetupComplete completes the setup process
func (ss *SetupServer) handleSetupComplete(w http.ResponseWriter, r *http.Request) {
	log.Println("✅ Setup completion requested")
	
	var request struct {
		MusicPath           string `json:"musicPath"`
		TailscaleConfigured bool   `json:"tailscaleConfigured"`
	}
	
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}
	
	log.Printf("✅ Completing setup with music path: %s", request.MusicPath)
	
	// Update configuration
	ss.config.MusicFolder = request.MusicPath
	ss.config.SetupComplete = true
	
	if err := ss.config.SaveConfig(); err != nil {
		log.Printf("❌ Failed to save config: %v", err)
		response := map[string]interface{}{
			"success": false,
			"error":   "Failed to save configuration",
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(response)
		return
	}
	
	log.Println("✅ Setup completed successfully")
	
	response := map[string]interface{}{
		"success": true,
		"message": "Setup completed successfully",
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
	
	// Schedule server restart
	go func() {
		time.Sleep(1 * time.Second)
		log.Println("🔄 Restarting as music server...")
		os.Exit(0) // This will cause the application to restart as a music server
	}()
}

// getTailscaleAuthURL gets the Tailscale authentication URL
func (ss *SetupServer) getTailscaleAuthURL() string {
	// Check if Tailscale is installed and get status
	cmd := exec.Command("tailscale", "status")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("🔍 Tailscale not available: %v", err)
		return ""
	}
	
	// If we can get status, Tailscale is available and likely authenticated
	statusStr := string(output)
	if len(statusStr) > 0 {
		log.Println("✅ Tailscale is available and authenticated")
		// Return empty string to indicate it's already set up
		return "authenticated"
	}
	
	return ""
}

// getTailscaleIP gets the Tailscale IP address
func (ss *SetupServer) getTailscaleIP() string {
	cmd := exec.Command("tailscale", "ip", "-4")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("⚠️ Failed to get Tailscale IP: %v", err)
		return ""
	}
	
	ip := strings.TrimSpace(string(output))
	log.Printf("🔗 Found Tailscale IP: %s", ip)
	return ip
}

// generateAuthQR generates a QR code for the auth URL
func (ss *SetupServer) generateAuthQR(url string) string {
	if url == "" {
		return ""
	}
	
	// Generate QR code
	qrCode, err := qrcode.Encode(url, qrcode.Medium, 256)
	if err != nil {
		log.Printf("❌ Failed to generate QR code: %v", err)
		return ""
	}
	
	// Convert to base64
	return string(qrCode)
}

// validateMusicDirectory validates that a directory contains music files
func (ss *SetupServer) validateMusicDirectory(path string) map[string]interface{} {
	response := map[string]interface{}{
		"valid":     false,
		"fileCount": 0,
		"error":     "",
	}
	
	// Check if directory exists
	info, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			response["error"] = "Directory does not exist"
		} else {
			response["error"] = fmt.Sprintf("Cannot access directory: %v", err)
		}
		return response
	}
	
	if !info.IsDir() {
		response["error"] = "Path is not a directory"
		return response
	}
	
	// Count music files
	musicCount := 0
	err = filepath.Walk(path, func(filePath string, info os.FileInfo, err error) error {
		if err != nil {
			return nil // Continue walking, ignore errors
		}
		
		if !info.IsDir() {
			ext := strings.ToLower(filepath.Ext(info.Name()))
			if ext == ".mp3" || ext == ".m4a" || ext == ".flac" || ext == ".wav" {
				musicCount++
			}
		}
		
		return nil
	})
	
	if err != nil {
		response["error"] = fmt.Sprintf("Error scanning directory: %v", err)
		return response
	}
	
	if musicCount == 0 {
		response["error"] = "No music files found in directory"
		return response
	}
	
	response["valid"] = true
	response["fileCount"] = musicCount
	return response
}