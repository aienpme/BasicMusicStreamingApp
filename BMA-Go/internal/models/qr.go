package models

import (
	"time"

	"github.com/skip2/go-qrcode"
)

// PairingData represents the data to be embedded in the QR code.
type PairingData struct {
	ServerURL string    `json:"serverUrl"`
	Token     string    `json:"token"`
	ExpiresAt time.Time `json:"expiresAt"`
}

// GenerateQRCode generates a QR code from a string and returns the PNG image as bytes.
func GenerateQRCode(jsonData string) ([]byte, error) {
	// Generate QR code with medium redundancy and a fixed size of 256x256 pixels
	return qrcode.Encode(jsonData, qrcode.Medium, 256)
}

// GenerateSimpleQR creates a basic QR code for text/URLs, used in the setup wizard.
func GenerateSimpleQR(text string, size int) ([]byte, error) {
	return qrcode.Encode(text, qrcode.Medium, size)
}

// TODO: Phase 4 Implementation
// - QR code generation using github.com/skip2/go-qrcode
// - Token generation and management
// - Expiration handling
// - Display in Fyne GUI 