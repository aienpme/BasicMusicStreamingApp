# StateManager

## Purpose
Handles saving and restoring playback state across app sessions.

## Key Responsibilities
- Saves current playback state when app closes
- Restores queue, position, and settings on app restart
- Handles both online and offline mode restoration
- Preserves shuffle/repeat settings

## What Gets Saved
- Current song and playback position
- Entire queue and queue position
- Shuffle enabled/disabled state
- Repeat mode setting
- Timestamp for freshness checking

## Main Methods
- `saveCurrentPlaybackState()` - Persist current state
- `restorePlaybackState()` - Load and apply saved state
- `setupOfflineRestore()` - Restore using local files
- `setupOnlineRestore()` - Restore using streaming

## User Experience
Allows users to close the app and return later with their music queue intact, resuming exactly where they left off - crucial for long playlists or albums. 