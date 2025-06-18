# ShuffleRepeatController

## Purpose
Manages shuffle and repeat modes for music playback.

## Key Responsibilities
- Controls shuffle on/off state
- Manages repeat mode cycling (off → all → one)
- Notifies when queue order changes due to shuffle
- Maintains mode state across sessions

## Modes Supported
**Shuffle:**
- Off: Play in original order
- On: Randomized playback order

**Repeat:**
- Off: Stop at end of queue
- All: Loop entire queue
- One: Repeat current song

## Main Methods
- `toggleShuffle()` - Switch shuffle on/off
- `cycleRepeatMode()` - Cycle through repeat modes
- `isShuffleEnabled()` - Check shuffle state
- `getRepeatMode()` - Get current repeat mode
- `setRepeatMode()` - Set specific repeat mode

## User Interface
Typically connected to shuffle and repeat buttons in the player UI, providing visual feedback of current modes. 