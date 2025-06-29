# Changelog

## Android App
- **UI Modernization**: Removed all emojis and refreshed the overall styling for a smoother, more modern user experience.
- **Navigation Fixes**: Resolved issues where navigation wasn’t functioning correctly.
- **Album Display**: Fixed bugs causing albums to not fully display.
- **Queue System Overhaul**: Redesigned the queue management UI to be more modern and intuitive.
- **Tailscale Connectivity**: Switched to using IP addresses instead of hostnames for more reliable Tailscale connections.
- **Connectivity Feedback**: Improved dialog messages for clearer information during Tailscale or connectivity issues.
- **Streaming Stats**: Introduced detailed streaming statistics—view your most played songs, albums, and artists.
- **Offline Mode Improvements**:
  - Track stats while offline.
  - Manually switch to offline mode.
  - Reliable syncing when toggling between online and offline modes.
- **Gesture Controls**: Added gesture support for song navigation in both the full player and miniplayer.
- **Improved Error Handling**: Enhanced stability with better error management to reduce app crashes.

## Server
- **Automatic Album Detection**: Server now automatically detects and indexes albums.
- **Library Version Tracking**: Added internal version tracking for the library database.

## Desktop App
- **QR Code Enhancements**: Replaced QR code generation with a more robust and reliable implementation.
- **Album Cover Art**: Album artwork is now properly displayed.
- **Tailscale Connectivity**: Improved Tailscale connection checks.
- **Stability Improvements**: Reduced the likelihood of crashes through better error handling and optimizations.
