# DialogManager

## Purpose
Centralizes all dialog creation and management for consistent user interactions across the app.

## Key Responsibilities
- **Dialog Creation**: Builds and displays all system dialogs
- **User Choice Handling**: Routes dialog button clicks to appropriate callbacks
- **Consistent Styling**: Ensures all dialogs have the same look and behavior
- **Non-Cancelable Dialogs**: Prevents dismissal without user action for critical choices

## Dialog Types

### 1. Offline Mode Option Dialog
- **When**: Server is unavailable but downloaded content exists
- **Options**: Use Offline Mode, Try Again, Cancel
- **Purpose**: Let users access downloaded music when server is down

### 2. Connection Lost Dialog  
- **When**: Server connection drops during active use
- **Options**: Use Offline Mode, Try Reconnect, Exit
- **Purpose**: Handle unexpected disconnections gracefully

### 3. Connection Timeout Dialog
- **When**: Initial connection check takes too long (>10 seconds)
- **Options**: Enter App Anyway, Try Offline Mode, Try Again
- **Purpose**: Prevent infinite loading states

## Callback Interface
All dialogs trigger callbacks for:
- `onOfflineModeSelected()` - User chose offline mode
- `onRetryConnection()` - User wants to retry connection
- `onDisconnectSelected()` - User chose to show disconnection screen
- `onBypassConnection()` - User wants to enter app despite connection issues

## Usage
Eliminates duplicate dialog code throughout the app and ensures consistent messaging and options for all connection-related user decisions. 