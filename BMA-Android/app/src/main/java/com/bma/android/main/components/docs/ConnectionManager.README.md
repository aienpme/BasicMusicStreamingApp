# ConnectionManager

## Purpose
Manages server connectivity checks and monitors connection health throughout the app lifecycle.

## Key Responsibilities
- **Initial Connection Check**: Verifies server connectivity on app startup with 10-second timeout
- **Health Monitoring**: Performs periodic health checks every 30 seconds while app is active
- **Connection States**: Handles connected, disconnected, token expired, and no credentials states
- **Offline Mode Detection**: Checks if offline mode is enabled before attempting connections
- **Lifecycle Awareness**: Pauses/resumes health checks based on app foreground/background state

## Main Methods
- `checkConnection()` - Performs initial connection verification
- `startHealthCheckTimer()` - Begins periodic health monitoring
- `stopHealthCheckTimer()` - Stops all health checks
- `pauseHealthCheck()` - Temporarily pauses checks (app backgrounded)
- `resumeHealthCheck()` - Resumes checks (app foregrounded)

## Connection States
- **CONNECTED**: Server is reachable and auth is valid
- **DISCONNECTED**: Server unreachable but credentials exist
- **TOKEN_EXPIRED**: Auth token needs refresh
- **NO_CREDENTIALS**: No stored credentials, need setup

## Usage
Ensures the app maintains awareness of server connectivity status and can gracefully handle connection issues, suggesting offline mode when appropriate or prompting for re-authentication when needed. 