# BMA CLI - Your Personal Music Streaming Server

## What is BMA CLI?

BMA CLI is a lightweight, headless music streaming server that transforms any computer or Raspberry Pi into your personal music streaming service. It allows you to access your entire music library from anywhere in the world through secure connections, streaming to mobile devices with full metadata support and album artwork.

Think of it as your own private Spotify, but for your personal music collection.

## 🎯 Perfect For

- **Raspberry Pi enthusiasts** who want to repurpose their device as a music server
- **Music collectors** with large personal libraries stored on home computers
- **Remote workers** who want access to their music from anywhere
- **Privacy-conscious users** who prefer self-hosted solutions over cloud services
- **Families** who want to share a music library across multiple devices

## ✨ Key Features

### 🎵 **Personal Music Streaming**
- Stream your entire music collection from anywhere in the world
- Support for MP3, M4A, FLAC, and WAV audio formats
- High-quality audio streaming with proper content-type headers
- Real-time music library updates when you add new files

### 🌐 **Secure Remote Access**
- **Tailscale VPN integration** for secure access from anywhere
- **Local network support** for home WiFi streaming
- **No port forwarding required** - works through encrypted tunnels
- **QR code pairing** for instant mobile device setup

### 🖥️ **Headless Operation**
- Runs as a background service without requiring a desktop interface
- **Perfect for Raspberry Pi** and other headless systems
- **Low resource usage** - optimized for minimal hardware
- **Command-line interface** for easy server management

### 📱 **Mobile-First Design**
- **RESTful API** designed specifically for mobile app integration
- **Album artwork serving** with automatic extraction from music files
- **Rich metadata support** including artist, album, track numbers, and duration
- **Cross-platform compatibility** with iOS and Android apps

### 🎛️ **Smart Music Organization**
- **Automatic library scanning** with recursive folder support
- **Intelligent album grouping** based on metadata and folder structure
- **Duplicate detection** to keep your library clean
- **Real-time file monitoring** - automatically detects new music files
- **Metadata extraction** from ID3 tags with filename fallback

### ⚙️ **Easy Setup & Management**
- **Web-based setup wizard** for initial configuration
- **One-time setup process** with persistent configuration
- **Automatic Tailscale detection** and authentication
- **Music folder validation** to ensure proper library setup
- **Health monitoring** with status endpoints

## 🚀 How It Works

### Initial Setup
1. **First Run**: BMA CLI starts in setup mode with a web interface
2. **Tailscale Configuration**: Automatic detection and setup for remote access
3. **Music Library Selection**: Choose your music folder with validation
4. **One-Click Completion**: Setup is saved and server switches to streaming mode

### Music Library Management
- **Recursive Scanning**: Discovers all music files in your selected folder and subfolders
- **Metadata Extraction**: Reads ID3 tags from audio files for artist, album, and track information
- **Smart Organization**: Groups songs into albums based on metadata
- **Artwork Handling**: Extracts and serves embedded album artwork
- **Live Updates**: Monitors file system changes and updates library automatically

### Remote Access
- **Tailscale Integration**: Creates secure VPN connections for worldwide access
- **Mobile Pairing**: Generate QR codes for instant device setup
- **Local & Remote**: Works on home WiFi or from anywhere with internet

### API Endpoints
- **Health Checks**: Monitor server status and library statistics
- **Song Streaming**: Direct audio file streaming with range request support
- **Library Browsing**: List all songs and albums with full metadata
- **Artwork Serving**: High-quality album artwork with proper caching

## 🏠 Home Media Server Benefits

- **Privacy Control**: Your music never leaves your home network (unless you allow remote access)
- **No Monthly Fees**: One-time setup with no ongoing subscription costs
- **Unlimited Storage**: Only limited by your device's storage capacity
- **Custom Organization**: Organize your music exactly how you want it
- **High Quality**: Stream at the original quality of your music files
- **Offline Capable**: Works without internet for local network access

## 💡 Use Cases

### **Home Music Server**
Set up on a Raspberry Pi connected to your home network. Family members can stream music throughout the house using their phones or tablets.

### **Remote Music Access**
Access your entire home music collection while traveling, at work, or anywhere with internet connection through secure Tailscale VPN.

### **Legacy Music Collection**
Breathe new life into old CD collections or music purchases by making them accessible on modern mobile devices.

### **Multi-Device Streaming**
Stream to multiple devices simultaneously from your central music server.

## 🔒 Security & Privacy

- **VPN-Based Security**: Uses Tailscale's enterprise-grade encryption for remote access
- **No Cloud Dependencies**: Your music stays on your device - no uploads to third-party services  
- **Local Network Priority**: Prioritizes local network access when available
- **Token-Based Authentication**: Secure device pairing with generated access tokens

## 📊 Technical Specifications

- **Supported Formats**: MP3, M4A, FLAC, WAV
- **Metadata Support**: ID3 tags, album artwork, track numbers
- **Platform Compatibility**: Linux, macOS, Raspberry Pi OS
- **Network Requirements**: Local WiFi or internet connection for Tailscale
- **Resource Usage**: Minimal CPU and RAM usage, suitable for Raspberry Pi 3+
- **API Protocol**: RESTful HTTP API with JSON responses

## 🎮 Getting Started

BMA CLI is designed to be simple to set up, even for non-technical users:

1. **Download and build** the application
2. **Run the setup command** to start the configuration wizard
3. **Open the web setup page** in your browser
4. **Configure Tailscale** for remote access (optional)
5. **Select your music folder** and validate the library
6. **Complete setup** - your music server is ready!

The built-in setup wizard guides you through each step with clear instructions and validation.

## 🌟 Why Choose BMA CLI?

Unlike cloud-based music services, BMA CLI gives you complete control over your music collection while providing modern streaming capabilities. It's the perfect solution for music enthusiasts who want the convenience of streaming with the privacy and control of self-hosting.

Whether you're a Raspberry Pi hobbyist, a privacy-conscious music lover, or someone who wants to modernize their music collection, BMA CLI provides an elegant solution that's both powerful and easy to use.