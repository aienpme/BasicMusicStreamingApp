# ProgressUpdateManager

## Purpose
Manages periodic progress updates during music playback and triggers state saves.

## Key Responsibilities
- Sends progress updates every 250ms during playback
- Notifies UI components of position changes
- Triggers periodic state saves (every 30 seconds)
- Efficiently starts/stops updates based on listener presence

## Update Schedule
- **Progress Updates**: Every 250ms while playing
- **State Saves**: Every 30 seconds to preserve playback position
- **Smart Management**: Only runs when listeners are active

## Main Methods
- `startProgressUpdates()` - Begin periodic updates
- `stopProgressUpdates()` - Stop updates when paused/no listeners
- `sendProgressUpdate()` - Broadcast current position
- `hasListeners()` - Check if updates are needed

## Efficiency
Only runs update timer when music is playing AND UI components are listening, preventing unnecessary battery drain when app is in background. 