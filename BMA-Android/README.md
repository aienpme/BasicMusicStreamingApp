# BMA - Basic Music App (Android)

Android client for BMA music streaming server. Connect to your macOS BMA server and stream your music collection.

## Features

- Connect to BMA server on local network
- Browse and search songs
- Stream MP3 files from server
- Basic playback controls
- Minimalist dark UI

## Requirements

- Android 7.0 (API 24) or later
- Android Studio Arctic Fox or later
- macOS BMA server running on the same network

## Building

1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Connect your Android device or start an emulator
4. Click Run (or press Shift+F10)

## Usage

1. Make sure the macOS BMA server is running
2. Launch the Android app
3. Enter the server URL shown in the macOS app (e.g., http://192.168.68.63:8008)
4. Tap "Connect"
5. Browse songs and tap to play

## Configuration

The app will remember the last server URL used. To change servers, simply enter a new URL and tap Connect.

## Troubleshooting

- **Can't connect**: Ensure both devices are on the same WiFi network
- **No songs appear**: Check that the macOS app has scanned a folder with MP3 files
- **Playback issues**: Make sure the server is running and accessible

## Dependencies

- ExoPlayer for audio streaming
- Retrofit for network requests
- Kotlin Coroutines for async operations 