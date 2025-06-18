# Major Update Released! 🚀

## Desktop & CLI Enhancements 🖥️
**Codebase Restructure:** Both desktop and CLI applications have been completely refactored with a modular architecture. Previously bloated files have been broken down into digestible, maintainable components while preserving full functionality.

**Redesigned Interface:** The desktop app features a fresh, modern UI that's much more polished than the previous version. ✨

**Enhanced Stability:** Significantly improved error handling prevents crashes, especially when Tailscale isn't running. Fixed critical issues with Tailscale detection on Linux systems. 🛠️

## Android App Overhaul 📱
I've essentially built a complete music player from the ground up—one I'd actually want to use daily.

**New Features:**
- **Streaming Stats** – Track your listening time with real-time minute counters 📊
- **Library Downloads** – Download your entire music collection to your device (currently stored securely within the app) ⬇️
- **True Offline Mode** – Seamlessly switch to offline playback when disconnected. The app intelligently detects connectivity issues and prompts you to enter offline mode, with full search functionality preserved 📡
- **Smart Caching** – Recently played tracks load faster with intelligent caching ⚡
- **Improved Backups** – Properly restores streaming stats and playlists 💾

**Reliability Improvements:**
- Robust error handling eliminates most crash scenarios
- Clear, specific error messages help you understand exactly what's happening
- Graceful handling of connection drops without requiring app restarts 🔧

## Known Issues & Upcoming Fixes:
- Working on allowing downloads to custom device folders
- Addressing rare crash scenarios in specific edge cases  
- Implementing proper state preservation when switching between online/offline modes 🔄

These improvements should make BMA significantly more stable and user-friendly across all platforms! 🎵
