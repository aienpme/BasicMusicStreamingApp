# 🎵 BMA CLI Setup Guide

**A simple, step-by-step guide to get your music server running on Raspberry Pi or any computer.**

## What is BMA CLI?

BMA CLI is a music server that lets you stream your music collection to your phone from anywhere. It's perfect for Raspberry Pi but works on any computer running Linux or macOS.

---

## 📋 Before You Start

You'll need:
- A computer or Raspberry Pi with internet connection
- A folder with your MP3 music files
- A web browser (Chrome, Firefox, Safari, etc.)
- A Tailscale account (free - we'll set this up)

---

## 🔗 Step 1: Set Up Tailscale (For Remote Access)

**Tailscale lets you access your music from anywhere in the world securely!**

### Create Tailscale Account:
1. Go to https://tailscale.com/
2. Click "Get started for free"
3. Sign up with Google, Microsoft, or email
4. **Keep this browser tab open - you'll need it later!**

### Install Tailscale on your Raspberry Pi/Computer:

**On Raspberry Pi or Ubuntu/Debian:**
```bash
curl -fsSL https://tailscale.com/install.sh | sh
```


### Connect to Tailscale:
1. In terminal, type:
```bash
sudo tailscale up
```

2. You'll see a message like:
```
To authenticate, visit: https://login.tailscale.com/a/xxxxxxxxx
```

3. **Copy this URL and open it in your browser**
4. Log in with the same account you created
5. Click "Connect" or "Authorize"
6. You should see "Success! You are now connected to Tailscale"

**✅ Tailscale is now ready! Your device has a secure IP address that works from anywhere.**

---

## 🚀 Step 2: Install Go Programming Language

BMA CLI is written in Go, so we need to install it first.

### On Raspberry Pi or Ubuntu/Debian:

**Option 1: Try the system Go first (might be too old)**
```bash
sudo apt update
sudo apt install golang-go
go version
```

**If you see Go 1.19 or older, use Option 2 to install Go 1.20+:**

**Option 2: Install newer Go manually (recommended for Raspberry Pi)**
```bash
# Remove old Go if installed
sudo apt remove golang-go

# Download and install Go 1.20 for ARM64 (or ARM32 for older Pi)
cd ~/Downloads
wget https://go.dev/dl/go1.20.14.linux-arm64.tar.gz

# For older Raspberry Pi (32-bit), use this instead:
# wget https://go.dev/dl/go1.20.14.linux-armv6l.tar.gz

# Extract and install
sudo tar -C /usr/local -xzf go1.20.14.linux-arm64.tar.gz

# Add Go to your PATH
echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc
source ~/.bashrc

# Verify installation
go version
```

You should see "go version go1.20.14 linux/arm64" or similar.


## 📥 Step 3: Download BMA CLI

1. Open Terminal
2. Navigate to where you want to put BMA CLI:
```bash
cd ~/Desktop
```

3. Download the code:
```bash
git clone https://github.com/picccassso/BMA.git
```

4. Go into the BMA CLI folder:
```bash
cd BMA/bma-cli
```

---

## 🔨 Step 4: Build BMA CLI

1. Download the required components:
```bash
go mod tidy
```

2. Build the program:
```bash
go build -o bma-cli .
```

3. Make it executable:
```bash
chmod +x bma-cli
```

---

## 🎯 Step 5: First Run (Setup)

1. Start BMA CLI for the first time:
```bash
./bma-cli
```

2. You should see something like:
```
==================================================
🎵 BMA CLI Setup
==================================================
Setup server is running at: http://localhost:8080/setup
Open this URL in your web browser to configure BMA CLI
==================================================
```

3. **Important**: Keep this terminal window open! Don't close it.

---

## 🌐 Step 6: Web Setup

1. **Find your Raspberry Pi's IP address** (you'll need this to access the setup page):
   ```bash
   hostname -I
   ```
   You'll see something like `192.168.1.100` - this is your Pi's IP address.

2. **Open your web browser** on your laptop, phone, or any device on the same WiFi network

3. **Go to the setup page** using your Pi's IP address:
   ```
   http://[YOUR-PI-IP]:8080/setup
   ```
   
   **Example**: If your Pi's IP is `192.168.1.100`, go to:
   ```
   http://192.168.1.100:8080/setup
   ```

   **Note**: Don't use `localhost` - that only works if you're browsing directly on the Pi itself!

3. You'll see the BMA CLI setup page with 3 steps:

### Step 1: Tailscale Setup
- **You should see a green checkmark** ✅ saying "Tailscale is configured and authenticated!"
- This means the Tailscale setup from Step 1 worked correctly
- **If you see a red X** ❌, go back to Step 1 and make sure Tailscale is properly installed and connected
- **If you see a QR code**, use the Tailscale app on your phone to scan it, or visit the shown URL

### Step 2: Music Directory
- **Enter the path to your music folder**
- Examples:
  - `/home/pi/Music` (Raspberry Pi)
  - `/Users/yourname/Music` (Mac)
  - `/home/yourname/Music` (Linux)
- Click "Validate Directory"
- You should see: "✅ Valid music directory! Found X music files."

### Step 3: Complete Setup
- Review your settings
- Click "Complete Setup"
- You should see: "🎉 Setup complete! BMA CLI will now restart as a music server."

---

## 🎵 Step 7: Your Music Server is Ready!

After setup completes, you'll see:

```
============================================================
🎵 BMA CLI Music Server
============================================================
Music Library: /path/to/your/music
Server running at: http://localhost:8080
Ready for connections from BMA mobile apps
============================================================
```

**Congratulations! Your music server is now running!**

---

## 🧪 Step 8: Test Your Server

1. Open your web browser
2. Try these addresses to test:

### Check if server is working:
```
http://localhost:8080/health
```
Should show: `{"status":"healthy"}`

### See your music library info:
```
http://localhost:8080/info
```
Should show details about your music collection

### List all your songs:
```
http://localhost:8080/songs
```
Should show a list of all your music files

---

## 📱 Step 9: Connect Your Phone with QR Code

Once you have the BMA mobile app:

### **Easy QR Code Pairing (Recommended):**

1. **Open the QR code page** in your web browser:
   ```
   http://[PI-IP]:8080/qr
   ```
   Example: `http://192.168.1.100:8080/qr`

2. **Open the BMA app** on your phone

3. **Tap "Scan QR Code"** in the app

4. **Scan the QR code** displayed on your screen

5. **Your phone will automatically connect!** ✅
   
   **Note:** First connection may take 10-30 seconds over mobile data due to VPN encryption and Pi processing time.

### **Manual Connection (Alternative):**

**For Local Access (same WiFi):**
1. Make sure your phone is on the same WiFi network  
2. In the BMA app, manually enter: `http://[PI-IP]:8080`

**For Remote Access (anywhere in the world):**
1. **Install Tailscale on your phone**:
   - iPhone: Get "Tailscale" from App Store
   - Android: Get "Tailscale" from Play Store
2. **Log in with the same Tailscale account** you created in Step 1
3. **In the BMA app, enter your Tailscale URL**: `http://100.87.136.73:8080`

**🌍 With Tailscale, you can access your music from anywhere in the world!**

---

## 🔧 Troubleshooting

### Problem: "go.mod file indicates go 1.20, but maximum version supported by tidy is 1.19" OR "undefined: time.DateOnly"
**Solution:** Your Raspberry Pi has an older Go version. You need Go 1.20 or newer. Go back to Step 2 and use **Option 2** to install Go 1.20:

```bash
# Quick install of Go 1.20 for Raspberry Pi
sudo apt remove golang-go
cd ~/Downloads
wget https://go.dev/dl/go1.20.14.linux-arm64.tar.gz
sudo tar -C /usr/local -xzf go1.20.14.linux-arm64.tar.gz
echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc
source ~/.bashrc
go version
```

Then try building again:
```bash
go mod tidy
go build -o bma-cli .
```

### Problem: "Permission denied" when running ./bma-cli
**Solution:**
```bash
chmod +x bma-cli
```

### Problem: "Port already in use"
**Solution:** Another program is using port 8080. Try:
```bash
sudo lsof -i :8080
```
Then stop that program or restart your computer.

### Problem: "No music files found"
**Solution:** 
- Check your music folder path is correct
- Make sure you have MP3 files in that folder
- The folder needs MP3, M4A, FLAC, or WAV files

### Problem: Can't access setup page from web browser
**Solution:**
- **Don't use `localhost`** - that only works on the Pi itself
- **Use your Pi's IP address instead**:
  ```bash
  hostname -I
  ```
  Then go to `http://[PI-IP]:8080/setup`
- Make sure the terminal with BMA CLI is still running
- Make sure both devices are on the same WiFi network
- Check for typos in the web address

### Problem: Tailscale setup shows red X or QR code
**Solution:**
- Check if Tailscale is running: `sudo tailscale status`
- If it says "Logged out", run: `sudo tailscale up`
- Follow the authentication link that appears
- Wait a minute and refresh the setup page

### Problem: Can't access music server remotely
**Solution:**
- Make sure Tailscale is running on both devices
- Check your Tailscale IP: `tailscale ip -4`
- Make sure both devices are logged into the same Tailscale account
- Try `http://TAILSCALE-IP:8080/health` to test connection

---

## 🛑 How to Stop BMA CLI

In the terminal where BMA CLI is running:
- Press `Ctrl + C` (hold Ctrl and press C)

---

## 🔄 How to Run Again Later

1. Open Terminal
2. Go to the BMA CLI folder:
```bash
cd ~/Desktop/BasicStreamingApp/bma-cli
```

3. Start it:
```bash
./bma-cli
```

Since you already did setup, it will start directly as a music server!

---

## 🌐 Keep Server Running (Remote SSH/VNC)

If you're connecting to your Pi remotely and want the server to keep running after you disconnect:

### **Run Persistently:**
```bash
nohup ./bma-cli > bma-cli.log 2>&1 &
```

This will:
- Keep running even after you close the remote connection
- Save all output to `bma-cli.log`
- Run in the background

### **Check if Running:**
```bash
ps aux | grep bma-cli
```

### **Stop the Server:**
```bash
pkill bma-cli
```

### **View Logs:**
```bash
tail -f bma-cli.log
```

---

## 🏃‍♂️ Run BMA CLI Automatically on Startup (Advanced)

If you want BMA CLI to start automatically when your Raspberry Pi boots up:

1. Create a service file:
```bash
sudo nano /etc/systemd/system/bma-cli.service
```

2. Add this content (change the paths to match your setup):
```ini
[Unit]
Description=BMA CLI Music Server
After=network.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/Desktop/BasicStreamingApp/bma-cli
ExecStart=/home/pi/Desktop/BasicStreamingApp/bma-cli/bma-cli
Restart=always

[Install]
WantedBy=multi-user.target
```

3. Save and exit (Ctrl+X, then Y, then Enter)

4. Enable the service:
```bash
sudo systemctl enable bma-cli
sudo systemctl start bma-cli
```

Now BMA CLI will start automatically every time you boot your Raspberry Pi!

---

## 🎉 You're Done!

Your BMA CLI music server is now running and ready to stream your music. When the mobile app is available, you'll be able to connect to it and enjoy your music from anywhere!

**Need help?** Check the main README.md file for more technical details.
