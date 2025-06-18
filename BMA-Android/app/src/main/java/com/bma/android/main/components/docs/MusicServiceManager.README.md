# MusicServiceManager

## Purpose
Manages the Android service connection lifecycle for the music playback service.

## Key Responsibilities
- **Service Binding**: Handles binding/unbinding to MusicService
- **Connection State**: Tracks whether the service is currently bound
- **Auto-Reconnect**: Automatically attempts to rebind if service disconnects
- **Playback Restoration**: Restores previous playback state when service connects with no active song
- **Listener Management**: Provides methods to add/remove music service listeners

## Main Methods
- `bindMusicService()` - Starts and binds to the music service
- `unbindMusicService()` - Cleanly unbinds from the service
- `getMusicService()` - Returns the current service instance (if bound)
- `isServiceBound()` - Checks if service is currently connected

## Usage
The component acts as a middleman between MainActivity and MusicService, abstracting away the complexity of Android service connections and ensuring reliable music playback service access. 