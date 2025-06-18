# MusicNotificationManager

## Purpose
Creates and manages the music playback notification that appears in the notification shade.

## Key Responsibilities
- Creates persistent notification for foreground service
- Shows current song info (title, artist, album art)
- Provides playback controls (play/pause, next, previous)
- Loads and caches album artwork
- Creates notification channel for Android O+

## Notification Features
- **Media Style**: Uses Android's MediaStyle for consistent look
- **Album Art**: Downloads and displays album artwork
- **Quick Actions**: Play/pause, skip tracks without opening app
- **Non-Dismissible**: Stays visible while music service runs
- **Color Extraction**: Extracts dominant color from album art

## Main Methods
- `createNotification()` - Build full notification with controls
- `updateNotification()` - Update existing notification
- `loadAlbumArt()` - Async load album artwork with caching
- `createBasicNotification()` - Minimal notification for startup

## Integration
Works with MediaSession to provide synchronized controls across notification, lock screen, and system UI. 