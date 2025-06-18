🎵 **BMA** – Basic Music App

A secure, self-hosted music streaming solution built in Go (Desktop & CLI) and Kotlin (Android).

YouTube Video Demonstration: 

🖥️ What is **BMA**?

BMA turns your personal music library into a private streaming service — powered by your own devices. Whether you're on a PC, Mac, Linux machine, or a Raspberry Pi, BMA lets you stream music to your Android phone securely using Tailscale, without exposing anything to the public internet.
There's no cloud, no port forwarding, and no privacy trade-offs — just instant, encrypted access to your music wherever you are.

🛠️ **How It Works**

- The desktop app (Go + Fyne) lets you select a local folder as your music library.
- The server runs locally and streams your music over HTTP via Tailscale's secure mesh VPN.
- Your Android device connects by scanning a QR code, pairing instantly with the server.
- Metadata is retrieved, albums are organized, and the music starts flowing — just like a private Spotify.
- A CLI version is available for Raspberry Pi, ideal for low-power streaming setups. Supports setup via browser and runs headlessly.

🔐 **Note: Music currently streams over HTTP within Tailscale's encrypted tunnel.**

🚀 **Key Features**

✅ **One-tap pairing** – Instantly connect your Android device to the server using a secure QR code.  
🎵 **Organized album browsing** – Albums are displayed with full track listings for smooth navigation.  
🔊 **Intuitive mini player** – Access playbook controls (play/pause, next, previous, shuffle, repeat) at any time.  
🔍 **Powerful search** – Quickly find albums or individual tracks from your library.  
📶 **Live connection feedback** – View server status and connection info directly in the app's settings.  
📡 **Auto-reconnect** – The app automatically reconnects to your server whenever it's available.  
🎚️ **Notification controls** – Control playback directly from your notification shade and lockscreen.  
📊 **Streaming stats** – Track your listening time with real-time minute counters.  
⬇️ **Library downloads** – Download your entire music collection to your device for offline access.  
📡 **True offline mode** – Seamlessly switch to offline playback when disconnected, with full search functionality.  
⚡ **Smart caching** – Recently played tracks load faster with intelligent caching.  
💾 **Improved backups** – Properly restore streaming stats and playlists.

🧩 **Recent Updates**

🖥️ **Desktop & CLI Improvements:**
- Completely refactored codebase with modular architecture
- Fresh, modern UI design for desktop app
- Enhanced stability and error handling
- Fixed Tailscale detection issues on Linux systems

📱 **Android App Overhaul:**
- Real-time streaming statistics
- Full library downloading capabilities
- Intelligent offline mode with connectivity detection
- Smart caching for faster track loading
- Robust error handling and graceful connection management

🤝 **Tech Stack**

🖥️ Desktop Server: Go (with optional GUI via Fyne)  
📱 Mobile App: Kotlin (Jetpack Compose)  
🛜 Networking Layer: Tailscale (zero-config, encrypted mesh VPN)  
🔐 Streaming Protocol: HTTP (served securely over Tailscale's encrypted tunnel)
