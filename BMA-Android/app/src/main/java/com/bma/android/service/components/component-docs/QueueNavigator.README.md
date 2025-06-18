# QueueNavigator

## Purpose
Handles the complex logic of navigating through the music queue with repeat modes.

## Key Responsibilities
- Implements skip next/previous logic
- Handles three repeat modes (off, all, one)
- Manages queue boundaries and looping
- Coordinates with queue and playback systems

## Repeat Mode Behaviors
- **Repeat Off**: Stop at queue boundaries
- **Repeat All**: Loop to beginning/end when reaching boundaries
- **Repeat One**: Restart current song instead of skipping

## Main Methods
- `skipToNext()` - Navigate to next song with repeat logic
- `skipToPrevious()` - Navigate to previous song with repeat logic
- `handleRepeatAll()` - Reset queue to beginning
- `handleRepeatAllReverse()` - Jump to end of queue

## Smart Features
- Preserves shuffle state when looping with repeat all
- Handles edge cases like empty queues
- Integrates playback control (stop when queue ends with repeat off) 