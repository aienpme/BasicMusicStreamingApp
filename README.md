🎵 **BMA** – Basic Music App

A secure, self-hosted music streaming solution built in Go (Desktop & CLI) and Kotlin (Android).

🖥️ What is **BMA**?

BMA turns your personal music library into a private streaming service — powered by your own devices. Whether you're on a PC, Mac, Linux machine, or a Raspberry Pi, BMA lets you stream music to your Android phone securely using Tailscale, without exposing anything to the public internet.
There’s no cloud, no port forwarding, and no privacy trade-offs — just instant, encrypted access to your music wherever you are.

🛠️ **How It Works**

    - The desktop app (Go + Fyne) lets you select a local folder as your music library.
    - The server runs locally and streams your music over HTTP via Tailscale’s secure mesh VPN.
    - Your Android device connects by scanning a QR code, pairing instantly with the server.
    - Metadata is retrieved, albums are organized, and the music starts flowing — just like a private Spotify.
    - A CLI version is available for Raspberry Pi, ideal for low-power streaming setups. Supports setup via browser and runs headlessly.
    🔐 **Note: Music currently streams over HTTP within Tailscale’s encrypted tunnel. Native HTTPS support is planned.**

🚀 **Key Features**

    ✅ One-tap pairing – Instantly connect your Android device to the server using a secure QR code.
    🎵 Organized album browsing – Albums are displayed with full track listings for smooth navigation.
    🔊 Intuitive mini player – Access playback controls (play/pause, next, previous, shuffle, repeat) at any time.
    🔍 Powerful search – Quickly find albums or individual tracks from your library.
    📶 Live connection feedback – View server status and connection info directly in the app’s settings.
    📡 Auto-reconnect – The app automatically reconnects to your server whenever it's available.

🧩 **Planned Enhancements**

    📊Listening stats – Track how much time you’ve spent streaming music.
    📁 Playlist creation – Support for building and saving custom playlists.
    📃 Fix Buggy Queue Management
    💬 Community-driven improvements – Actively shaped by user feedback and contributions.

🤝 **Tech Stack**

    🖥️ Desktop Server: Go (with optional GUI via Fyne)
    📱 Mobile App: Kotlin (Jetpack Compose)
    🛜 Networking Layer: Tailscale (zero-config, encrypted mesh VPN)
    🔐 Streaming Protocol: HTTP (served securely over Tailscale’s encrypted tunnel)

🛠 Improvements & Fixes - Update 10/06/2025 

Desktop App

    - Improved reliability on Linux systems by enhancing Tailscale integration checks and overall environment detection, addressing prior setup failures.

    - Developed a CLI version optimized for Raspberry Pi deployment. Best performance is achieved on Pi 4/5 models due to stronger SoCs and network performance. An external SSD is recommended for smoother playback, though testing on Pi 3 shows the app is functional, albeit occasionally sluggish.
    
Android App

    - Resolved an issue with shuffle playback not behaving as expected.

    - Implemented a queue management system (currently in early stages and may require refinement).

    - Fixed a crash that occurred when the app disconnected from the server. Users now see a "Disconnected" screen with a prompt to reconnect.


