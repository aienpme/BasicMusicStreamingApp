# MediaSessionManager

## Purpose
Manages Android MediaSession for system-wide media integration.

## Key Responsibilities
- Creates and maintains MediaSession for the app
- Updates playback state for system UI
- Sets media metadata (title, artist, album art)
- Handles media button events (play/pause from headphones, car, etc.)

## System Integration
- **Lock Screen Controls**: Shows media controls on lock screen
- **Notification Controls**: Enables media notification actions
- **Bluetooth/Car Integration**: Works with car systems and Bluetooth devices
- **Google Assistant**: Enables voice control of playback

## Main Methods
- `initializeMediaSession()` - Set up media session with callbacks
- `updatePlaybackState()` - Update play/pause/position state
- `updateMediaMetadata()` - Set current song information
- `release()` - Clean up when done

## Why It's Important
Makes your music app a first-class citizen in Android's media ecosystem, integrating with system controls, wearables, and voice assistants. 