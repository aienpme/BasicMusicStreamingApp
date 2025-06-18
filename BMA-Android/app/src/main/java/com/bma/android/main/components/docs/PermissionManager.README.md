# PermissionManager

## Purpose
Handles runtime permission requests for Android, with a focus on notification permissions for Android 13+.

## Key Responsibilities
- **Permission Checking**: Verifies if permissions are already granted
- **Permission Requests**: Launches system permission dialogs
- **Version Compatibility**: Only requests permissions on Android versions that require them
- **Result Handling**: Processes user's permission decisions
- **Activity Result API**: Uses modern ActivityResult API instead of deprecated methods

## Main Methods
- `requestNotificationPermission()` - Requests POST_NOTIFICATIONS permission on Android 13+
- `hasNotificationPermission()` - Checks if notification permission is currently granted

## Android Version Handling
- **Android 13+ (API 33+)**: Explicitly requests notification permission
- **Android 12 and below**: Returns true (permission granted by default)

## Features
- Automatically registers permission launchers during initialization
- Logs permission grant/denial for debugging
- Handles all permission boilerplate code
- Easy to extend for additional permissions

## Future Extensions
Can easily be extended to handle other runtime permissions like:
- Camera permission
- Location permission
- Storage permissions
- Microphone permission

## Usage
Simplifies permission handling by abstracting away the complexity of Android's permission system, version checks, and result handling into a single, reusable component. 