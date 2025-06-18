# MusicQueue

## Purpose
Manages the playback queue for music, handling both normal and shuffled playback order.

## Key Responsibilities
- Maintains the original playlist order and current playing order
- Handles shuffle/unshuffle operations while preserving the current song position
- Provides navigation through the queue (next/previous)
- Supports dynamic queue modifications (add, remove, move, jump to position)

## Important Features
- **Smart Shuffle**: When shuffling, keeps the current song at position 0 and shuffles remaining songs
- **Position Tracking**: Maintains current position in queue for seamless navigation
- **Queue Persistence**: Preserves original order when shuffled for easy restoration

## Main Methods
- `setPlaylist()` - Initialize queue with songs
- `shuffle()/unshuffle()` - Toggle shuffle mode
- `next()/previous()` - Navigate through queue
- `addToQueue()/addNext()` - Add songs dynamically
- `removeFromQueue()` - Remove songs (except currently playing)
- `moveQueueItem()` - Reorder songs in queue
- `jumpToQueuePosition()` - Skip to specific position 