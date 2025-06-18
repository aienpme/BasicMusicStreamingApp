# PlaybackManager

## Purpose
Manages the ExoPlayer instance and handles all audio playback operations.

## Key Responsibilities
- Creates and configures ExoPlayer
- Handles online streaming and offline file playback
- Manages play/pause/stop/seek operations
- Reports playback events (ready, ended, errors)
- Switches between cached and streaming sources

## Playback Features
- **Dual Mode**: Supports both streaming and local file playback
- **Automatic Caching**: Detects and uses cached files when available
- **Error Handling**: Retries on network errors, reports auth failures
- **State Management**: Tracks player state and position
- **Gapless Playback**: Smooth transitions between songs

## Main Methods
- `prepareNewSong()` - Load and prepare a song for playback
- `play()/pause()/stop()` - Control playback
- `seekTo()` - Jump to position
- `setVolume()` - Adjust volume (for audio ducking)
- `setupOfflineRestore()` - Restore from saved position

## Technical Details
Uses ExoPlayer's HlsMediaSource for streaming and ProgressiveMediaSource for local files, with automatic format detection and buffering configuration. 