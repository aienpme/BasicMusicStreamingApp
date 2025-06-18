# NotificationCoordinator

## Purpose
Coordinates updates between notification, media session, and metadata to keep all UI elements in sync.

## Key Responsibilities
- Synchronizes notification updates with media session
- Updates metadata across all system interfaces
- Manages album art loading coordination
- Ensures consistent state across Android's media APIs

## What It Coordinates
- **Notification**: Music notification in shade
- **Media Session**: System-wide playback state
- **Metadata**: Song info for lock screen/wearables
- **Album Art**: Artwork across all surfaces

## Main Methods
- `updateNotification()` - Refresh notification display
- `updatePlaybackState()` - Sync play/pause state
- `updateMediaMetadata()` - Set current song info
- `updateAll()` - Full sync of all components

## Why It Exists
Android has multiple APIs for media display (notification, session, metadata) that must be kept in sync. This coordinator ensures they all show consistent information. 