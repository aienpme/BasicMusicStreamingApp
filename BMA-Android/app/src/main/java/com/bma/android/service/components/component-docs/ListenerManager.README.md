# ListenerManager

## Purpose
Manages communication between MusicService and UI components (Activities/Fragments).

## Key Responsibilities
- Maintains list of UI listeners interested in playback events
- Broadcasts service events to all registered listeners
- Handles listener lifecycle (add/remove)
- Provides immediate updates to newly registered listeners

## Events Broadcasted
- **Song Changed**: When a new song starts playing
- **Playback State Changed**: Play/pause/stop events
- **Progress Changed**: Regular position updates during playback
- **Queue Changed**: When songs are added/removed/reordered
- **Buffering Changed**: Loading state updates

## Main Methods
- `addListener()` - Register a UI component
- `removeListener()` - Unregister when UI destroyed
- `notifyNewListener()` - Send current state to new listener
- `notify*()` - Various methods to broadcast events

## Design Pattern
Implements Observer pattern to decouple service from UI, allowing multiple UI components to react to playback changes without tight coupling. 