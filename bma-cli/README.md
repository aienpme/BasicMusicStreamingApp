# BMA CLI - Basic Music App Command Line Interface

A headless music streaming server for Raspberry Pi and other Linux systems. BMA CLI provides a web-based setup interface and serves music files to BMA mobile applications.

## Features

- **Headless Operation**: No GUI required, perfect for servers and Raspberry Pi
- **Web-based Setup**: Easy configuration through browser interface
- **Tailscale Integration**: Secure remote access (optional)
- **Music Library Management**: Automatic scanning and organization of MP3 files
- **Mobile App Compatible**: Works with BMA Android/iOS applications
- **RESTful API**: Provides endpoints for music streaming and metadata

## Installation

### Prerequisites

1. **Go Language**: Install Go 1.21 or later
   ```bash
   # On Ubuntu/Debian
   sudo apt update
   sudo apt install golang-go
   
   # On Raspberry Pi OS
   sudo apt update
   sudo apt install golang-go
   
   # Verify installation
   go version
   ```

2. **Git**: For cloning the repository
   ```bash
   sudo apt install git
   ```

### Building BMA CLI

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd BasicStreamingApp/bma-cli
   ```

2. **Download dependencies**:
   ```bash
   go mod tidy
   ```

3. **Build the executable**:
   ```bash
   go build -o bma-cli .
   ```

## Setup and Usage

### First Run (Setup)

1. **Run BMA CLI for the first time**:
   ```bash
   ./bma-cli
   ```

2. **Open the setup interface**:
   - The application will start a web server at `http://localhost:8080/setup`
   - Open this URL in your web browser
   - You can access this from any device on the same network

3. **Follow the setup wizard**:
   - **Step 1**: Configure Tailscale (optional but recommended for remote access)
   - **Step 2**: Specify your music directory path
   - **Step 3**: Complete the setup

### Running as Music Server

After setup is complete, BMA CLI will automatically restart as a music streaming server:

```bash
./bma-cli
```

The server will display:
```
==============================================================
🎵 BMA CLI Music Server
==============================================================
Music Library: /path/to/your/music
Server running at: http://localhost:8080
Tailscale access: http://your-tailscale-ip:8080
Ready for connections from BMA mobile apps
==============================================================
```

## API Endpoints

BMA CLI provides the following REST endpoints:

### Public Endpoints

- `GET /health` - Server health check
- `GET /info` - Server and library information
- `GET /songs` - List all songs
- `GET /albums` - List all albums
- `GET /stream/{songId}` - Stream audio file
- `GET /artwork/{songId}` - Get album artwork

### Example Responses

**GET /info**:
```json
{
  "server": "BMA CLI Music Server",
  "version": "1.0",
  "httpPort": 8080,
  "protocol": "http",
  "library": {
    "albumCount": 25,
    "songCount": 342,
    "hasLibrary": true,
    "musicPath": "/home/user/music"
  }
}
```

**GET /songs**:
```json
[
  {
    "id": "uuid-string",
    "filename": "song.mp3",
    "title": "Song Title",
    "artist": "Artist Name",
    "album": "Album Name",
    "trackNumber": 1,
    "hasArtwork": true,
    "sortOrder": 0
  }
]
```

## Configuration

Configuration is stored in `~/.bma-cli/config.json`:

```json
{
  "setupComplete": true,
  "musicFolder": "/path/to/music",
  "tailscaleIP": "100.x.x.x"
}
```

## Supported Audio Formats

- **MP3**: Primary format with full metadata support
- **M4A**: Basic support
- **FLAC**: Basic support  
- **WAV**: Basic support

## Mobile App Integration

BMA CLI is designed to work with BMA mobile applications:

1. **Discovery**: Mobile apps can discover the server via network scanning
2. **Pairing**: Future versions will support QR code pairing
3. **Streaming**: Apps connect to the REST API for music streaming
4. **Remote Access**: Use Tailscale for secure access outside your network

## Systemd Service (Optional)

To run BMA CLI as a system service:

1. **Create service file** `/etc/systemd/system/bma-cli.service`:
   ```ini
   [Unit]
   Description=BMA CLI Music Server
   After=network.target
   
   [Service]
   Type=simple
   User=your-username
   WorkingDirectory=/path/to/bma-cli
   ExecStart=/path/to/bma-cli/bma-cli
   Restart=always
   RestartSec=5
   
   [Install]
   WantedBy=multi-user.target
   ```

2. **Enable and start the service**:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable bma-cli
   sudo systemctl start bma-cli
   ```

3. **Check status**:
   ```bash
   sudo systemctl status bma-cli
   ```

## Troubleshooting

### Common Issues

1. **Permission Denied**: Ensure the executable has correct permissions
   ```bash
   chmod +x bma-cli
   ```

2. **Port Already in Use**: Another service is using port 8080
   ```bash
   sudo lsof -i :8080
   ```

3. **Music Directory Access**: Ensure BMA CLI can read your music directory
   ```bash
   ls -la /path/to/music
   ```

4. **No Music Found**: Check supported formats and directory structure

### Logs

BMA CLI outputs detailed logs to stdout. For persistent logging:

```bash
./bma-cli 2>&1 | tee bma-cli.log
```

## Development

### Project Structure

```
bma-cli/
├── main.go                 # Entry point
├── internal/
│   ├── models/            # Data models
│   │   ├── config.go      # Configuration management
│   │   ├── library.go     # Music library management
│   │   └── song.go        # Song metadata handling
│   └── server/            # HTTP servers
│       ├── setup.go       # Setup web interface
│       └── music.go       # Music streaming server
├── web/                   # Web assets (future)
│   ├── templates/
│   └── static/
├── go.mod                 # Go module definition
└── README.md
```

### Building for Different Architectures

**For Raspberry Pi (ARM64)**:
```bash
GOOS=linux GOARCH=arm64 go build -o bma-cli-arm64 .
```

**For Raspberry Pi (ARM32)**:
```bash
GOOS=linux GOARCH=arm go build -o bma-cli-arm .
```

**For Linux (AMD64)**:
```bash
GOOS=linux GOARCH=amd64 go build -o bma-cli-linux .
```

## License

[Include your license information here]

## Contributing

[Include contribution guidelines here]

## Support

For support and issues, please check:
1. This README for common solutions
2. The project's issue tracker
3. Community forums