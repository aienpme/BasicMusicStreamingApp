# PlaybackStatsTracker

## Purpose
Tracks listening statistics and saves playback time to user playlists.

## Key Responsibilities
- Monitors playback duration for each song
- Accumulates listening time in minutes
- Saves stats to server every 5 minutes
- Handles offline mode gracefully

## How It Works
1. Starts tracking when music plays
2. Stops tracking when paused/stopped
3. Accumulates time and saves periodically
4. Only saves if more than 10 seconds elapsed

## Main Methods
- `startTracking()` - Begin tracking playback time
- `stopTracking()` - Stop and save any remaining time
- `saveListeningTime()` - Send stats to server

## Server Integration
Updates the "Listening Minutes" playlist on the server with accumulated playback time, allowing users to track their listening habits. 