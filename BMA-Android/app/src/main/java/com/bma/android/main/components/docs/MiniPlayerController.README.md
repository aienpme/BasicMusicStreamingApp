# MiniPlayerController

## Purpose
Controls the mini player UI widget that appears at the bottom of the app for quick music controls.

## Key Responsibilities
- **UI Updates**: Updates song title, artist, and playback state in the mini player
- **Playback Controls**: Handles play/pause, next, and previous button clicks
- **Progress Bar**: Updates the playback progress indicator
- **Album Artwork**: Loads and displays album art with proper caching and auth headers
- **Navigation**: Opens full PlayerActivity when mini player is tapped

## Main Methods
- `setup()` - Initializes click listeners for all controls
- `update()` - Refreshes the entire mini player state
- `updateProgress()` - Updates only the progress bar
- `hide()` - Hides the mini player when no music is playing

## Features
- Supports both local and remote artwork loading
- Handles offline mode gracefully
- Prevents UI updates during screen transitions
- Efficient image caching with Glide

## Usage
The mini player provides quick access to playback controls from any screen in the app, eliminating the need to navigate back to the full player view for basic operations. 