# FragmentNavigationManager

## Purpose
Manages all fragment navigation, transitions, and detail view overlays with smooth animations.

## Key Responsibilities
- **Fragment Loading**: Handles loading and replacing fragments in the container
- **Navigation Animations**: Provides smooth transitions between main navigation tabs
- **Detail View Overlays**: Manages album and playlist detail views as overlays
- **State Tracking**: Tracks current fragment, display mode, and animation states
- **Back Navigation**: Handles custom back behavior for detail views

## Display Modes
- **NORMAL**: Regular navigation between Library, Search, Settings
- **ALBUM_DETAIL**: Album detail overlay is showing
- **PLAYLIST_DETAIL**: Playlist detail overlay is showing

## Main Methods
- `loadFragment()` - Basic fragment replacement without animation
- `navigateToFragmentWithAnimation()` - Animated navigation between tabs
- `showAlbumDetail()` - Opens album detail with fade transition
- `showPlaylistDetail()` - Opens playlist detail with fade transition
- `handleAlbumDetailBack()` - Animates album detail closing
- `handlePlaylistDetailBack()` - Animates playlist detail closing

## Animation Features
- Fade-to-black transitions for detail views
- Background fragment preservation during overlays
- Animation state tracking to prevent overlapping transitions
- Smooth navigation tab transitions

## Usage
Centralizes all fragment navigation logic, ensuring consistent transitions and preventing common issues like fragment state loss or animation conflicts. Makes the app feel more polished with professional transitions. 