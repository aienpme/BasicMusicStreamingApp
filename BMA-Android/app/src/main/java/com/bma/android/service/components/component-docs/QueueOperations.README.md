# QueueOperations

## Purpose
Wraps MusicQueue operations with notification callbacks to keep UI in sync with queue changes.

## Key Responsibilities
- Provides high-level queue manipulation API
- Triggers UI updates after queue modifications
- Handles logging and error cases
- Coordinates song changes with playback

## Operations Supported
- **Add to Queue**: Append songs to end
- **Add Next**: Insert after current song
- **Remove**: Delete songs from queue
- **Move**: Reorder songs in queue
- **Jump**: Skip to specific position
- **Get Queue**: Retrieve current or upcoming songs

## Main Methods
- `addToQueue()` - Add songs to end
- `addNext()` - Play after current
- `removeFromQueue()` - Remove at position
- `moveQueueItem()` - Drag to reorder
- `jumpToQueuePosition()` - Direct navigation

## Callback Integration
Every operation that modifies the queue triggers callbacks to update UI lists, ensuring the displayed queue always matches the actual playback order. 