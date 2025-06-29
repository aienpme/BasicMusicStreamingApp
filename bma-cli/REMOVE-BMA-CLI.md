# Complete BMA-CLI Removal Script

This script completely kills and removes bma-cli from your Raspberry Pi.

## Command to Run

```bash
# Kill all running bma-cli processes
sudo pkill -f bma-cli

# Kill any processes using port 8080 (default bma-cli port)
sudo lsof -ti:8080 | xargs -r sudo kill -9

# Remove systemd service if it exists
sudo systemctl stop bma-cli 2>/dev/null || true
sudo systemctl disable bma-cli 2>/dev/null || true
sudo rm -f /etc/systemd/system/bma-cli.service
sudo systemctl daemon-reload

# Remove binary and config files
sudo rm -f /usr/local/bin/bma-cli
sudo rm -f /usr/bin/bma-cli
sudo rm -f ./bma-cli
sudo rm -rf ~/.bma-cli
sudo rm -rf /opt/bma-cli
sudo rm -rf /var/lib/bma-cli

# Remove any log files
sudo rm -f /var/log/bma-cli.log
sudo rm -f /tmp/bma-cli*

# Clean up any remaining processes
ps aux | grep bma-cli | grep -v grep | awk '{print $2}' | xargs -r sudo kill -9

echo "✅ bma-cli completely removed from system"
```

## What This Does

- Kills all running bma-cli processes (including stuck ones)
- Stops and removes any systemd services
- Removes all binary files from common locations
- Cleans up config and log files
- Force kills any remaining processes

## After Running This

1. Transfer your updated bma-cli code to the Pi
2. Build the new version with the fixes: `go build -o bma-cli main.go`
3. Run it fresh to test the folder issue fix