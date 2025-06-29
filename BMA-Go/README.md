# BMA-Go - Personal Music Streaming Server

## What is BMA-Go?

BMA-Go is a modern desktop application that transforms your computer into a personal music streaming server. Built with Go and featuring a sleek graphical interface, it allows you to stream your personal MP3 collection to Android devices anywhere - whether at home or remotely through secure VPN connections.

Think of it as your own private Spotify, but for your personal music collection. No subscriptions, no internet required for local streaming, and you maintain complete control over your music library.

## Key Features

### 🎵 Smart Music Library Management

- **Automatic Music Discovery**: Recursively scans your chosen folders to find all MP3 files, organizing them intelligently by album and artist
- **Real-time Library Updates**: Watches your music folders for changes and automatically updates when you add or remove songs
- **Metadata Intelligence**: Extracts and displays ID3 tags including title, artist, album, and track numbers
- **Album Artwork Support**: Automatically extracts and serves embedded album artwork from your MP3 files
- **Smart Track Ordering**: Intelligently sorts tracks using ID3 track numbers and filename patterns (handles "01", "02", "10" ordering correctly)
- **Duplicate Detection**: Automatically identifies and removes duplicate songs based on metadata

### 📱 Seamless Device Connection

- **QR Code Pairing**: Generate QR codes for instant device connection - just scan with your Android device to connect
- **Token-Based Security**: Secure authentication system with time-limited pairing tokens (60-minute expiration)
- **Multiple Device Support**: Connect and stream to multiple Android devices simultaneously
- **Device Monitoring**: Real-time tracking of connected devices with status indicators
- **Automatic Disconnection Detection**: Heartbeat system monitors device connections and cleans up disconnected devices

### 🌐 Flexible Network Options

- **Local Network Streaming**: Works instantly on your home WiFi without any configuration
- **Remote Access via Tailscale**: Built-in Tailscale VPN integration for secure remote streaming from anywhere
- **Automatic Network Detection**: Intelligently detects your network setup and configures accordingly
- **HTTP Streaming**: Efficient MP3 streaming with range request support for smooth playback

### 🖥️ Modern Desktop Experience

- **Guided Setup Wizard**: First-run setup wizard walks you through:
  - Welcome and introduction
  - Optional Tailscale configuration for remote access
  - Android app installation guidance
  - Music folder selection
  - Completion confirmation

- **Intuitive Main Interface**:
  - **Server Status Bar**: Shows server state, IP address, and expandable QR code section
  - **Device Status View**: Live display of connected devices and library statistics
  - **Album Browser**: Visual album grid with artwork, artist info, and track counts
  - **Smooth Animations**: Fluid transitions and modern dark theme throughout

- **System Tray Integration**: 
  - Minimize to system tray for background operation
  - Quick access menu for show/hide and quit actions
  - One-time notification when minimizing to tray

- **Auto-Start Convenience**: Server automatically starts after setup for immediate use

### 🔧 Smart Features

- **Intelligent Album Organization**: 
  - Groups songs by album metadata
  - Falls back to folder structure when metadata is missing
  - Creates "folder albums" for mixed content directories

- **Enhanced Sorting Algorithm**:
  - Prioritizes numbered tracks (01, 02, 10) in correct order
  - Handles various naming conventions
  - Maintains consistent ordering across platforms

- **Performance Optimization**:
  - Efficient concurrent scanning of large music libraries
  - Smart caching of album artwork
  - Minimal resource usage during idle periods

### 📡 REST API Endpoints

The server provides a comprehensive API for client communication:

**Public Endpoints** (No authentication required):
- `GET /health` - Server health check
- `GET /info` - Server information and library statistics
- `POST /pair` - Generate device pairing token

**Authenticated Endpoints** (Require Bearer token):
- `GET /songs` - Retrieve complete music library with organization
- `GET /stream/{id}` - Stream MP3 file by song ID
- `GET /artwork/{id}` - Get album artwork for a song
- `POST /heartbeat` - Device connection heartbeat
- `POST /disconnect` - Disconnect a device

### 🛡️ Security & Privacy

- **Local-First Design**: Your music never leaves your network unless you explicitly enable remote access
- **Token Authentication**: Time-limited tokens ensure only authorized devices can connect
- **No Cloud Dependencies**: Completely self-hosted with no external service requirements
- **Private by Default**: No analytics, no tracking, no data collection

### 💻 Cross-Platform Support

- **Windows**: Full support with system tray integration
- **macOS**: Native experience with menu bar support
- **Linux**: Compatible with major distributions, includes Flatpak support
- **Consistent Experience**: Same features and interface across all platforms

## System Requirements

- **Operating System**: Windows 10+, macOS 10.14+, or Linux
- **Memory**: 512MB RAM minimum (1GB recommended)
- **Storage**: Depends on your music library size
- **Network**: Local network for home streaming, internet connection for remote access
- **Music Format**: MP3 files with proper ID3 tags (recommended)

## Getting Started

1. **Download and Install**: Get the latest release for your platform
2. **Run Setup Wizard**: The app guides you through initial configuration
3. **Select Music Folder**: Choose the folder containing your MP3 collection
4. **Connect Devices**: Scan the QR code with BMA-Android on your phone
5. **Start Streaming**: Enjoy your music anywhere!

## Use Cases

- **Home Music Server**: Stream your collection to any room in your house
- **Personal Cloud Music**: Access your music remotely through Tailscale VPN
- **Multi-Device Streaming**: Family members can each stream different songs
- **Offline Music Solution**: No internet required for local streaming
- **Music Library Organization**: Visualize and browse your collection by album

## Why BMA-Go?

Unlike commercial streaming services, BMA-Go gives you:
- Complete ownership and control of your music
- No monthly subscriptions or fees
- No internet requirement for local use
- No ads or interruptions
- Privacy-focused design
- Support for your existing MP3 collection

It's the perfect solution for music lovers who want the convenience of streaming with the freedom of self-hosting.
