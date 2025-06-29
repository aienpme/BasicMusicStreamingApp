# 🎵 BMA – Basic Music App

A secure, self-hosted music streaming solution built in Go (Desktop & CLI) and Kotlin (Android).

---

## 🖥️ What is BMA?

BMA transforms your personal music library into a private, full-featured streaming service powered entirely by your own devices. Whether you're running it on a PC, Mac, Linux box, or Raspberry Pi, BMA streams your collection to your Android phone using **Tailscale** — no port forwarding, no public IPs, and no cloud dependency.

💡 **Zero cloud. Zero exposure. 100% encrypted.**

---

## 🚀 Key Features

### 📱 Android App
- ✨ **Modernized UI** – Sleek new design with smoother performance.
- 🔗 **One-tap pairing** – Connect to your server via a secure QR code.
- 🧭 **Navigation fixes** – Reliable and intuitive screen transitions.
- 🎵 **Improved album browsing** – Better rendering of album artwork and tracks.
- 📐 **Queue redesign** – Streamlined queue interface with new styling and controls.
- 📊 **Advanced streaming stats** – View top songs, albums, and artists by play frequency.
- 📡 **Offline mode upgrades**:
  - Track your stats even when offline.
  - Manually enter offline mode.
  - Seamless sync when switching between modes.
- 🎛️ **Gesture support** – Swipe between songs on both full player and miniplayer.
- 🛠️ **Error handling improvements** – Greater app stability and fewer crashes.
- 🔧 **Tailscale enhancements** – Uses IPs instead of hostnames, with clearer connection issue messages.

### 🖥️ Desktop App
- 🖼️ **Cover art support** – Album artwork now displayed properly.
- 📷 **Improved QR code generation** – More reliable and faster device pairing.
- 📡 **Tailscale checks** – Smarter network connectivity validation.
- 🧹 **Stability improvements** – Reduced crashes and better error handling.

### 🖧 Server
- 📁 **Auto album detection** – Albums are now automatically detected from your library.
- 🔄 **Library version tracking** – Enables better sync and data consistency.

---

## 🤖 Tech Stack

- **Desktop Server**: Go (with optional GUI via Fyne)
- **Mobile App**: Kotlin (Jetpack Compose)
- **Networking**: Tailscale (zero-config, encrypted mesh VPN)
- **Streaming Protocol**: HTTP (securely tunneled via Tailscale)

---

## 📦 Raspberry Pi Support

Includes a CLI version ideal for headless Raspberry Pi setups, with browser-based configuration and low-power streaming support.

---

## 🔐 Note

Music is streamed over HTTP within Tailscale’s encrypted tunnel — ensuring your data remains private without additional network exposure.
