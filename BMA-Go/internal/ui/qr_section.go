package ui

import (
	"log"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/layout"
	"fyne.io/fyne/v2/widget"

	customTheme "bma-go/internal/ui/theme"
)

// QRCodeSection represents a QR code display section that animates smoothly.
type QRCodeSection struct {
	widget.BaseWidget
	
	// State
	isAnimating bool
	
	// Content
	qrImage      *canvas.Image
	qrBytes      []byte
	jsonData     string
	instructions *widget.Label
	buttonRow    *fyne.Container
	
	// Layout
	content *fyne.Container
	
	// Callbacks
	onExpanded func(bool)
	onRefresh  func()
	onClose    func()
}

// NewQRCodeSection creates a new QR code section.
func NewQRCodeSection() *QRCodeSection {
	s := &QRCodeSection{}
	s.ExtendBaseWidget(s)
	return s
}

// setupUI creates the UI components for the QR section. This is called once.
func (s *QRCodeSection) setupUI() {
	s.qrImage = canvas.NewImageFromResource(nil)
	s.qrImage.FillMode = canvas.ImageFillOriginal
	s.qrImage.SetMinSize(fyne.NewSize(200, 200)) // A more reasonable fixed size

	instructionText := "1. Open BMA Android app  2. Scan QR  3. Point at code  4. Done!"
	s.instructions = widget.NewLabel(instructionText)
	s.instructions.Alignment = fyne.TextAlignCenter
	s.instructions.TextStyle = fyne.TextStyle{Italic: true}
	
	refreshButton := customTheme.NewModernButton("Refresh", func() {
		// This will be connected by the parent
	})
	
	closeButton := customTheme.NewModernButton("Close", func() {
		// This will also be connected by the parent
	})
	closeButton.Importance = widget.HighImportance
	
	s.buttonRow = container.New(layout.NewHBoxLayout(),
		refreshButton, layout.NewSpacer(), closeButton,
	)
	
	// Create a more compact layout with minimal spacing
	qrContainer := container.NewCenter(s.qrImage)
	
	// Use NewBorder for tighter control instead of VBox with default spacing
	s.content = container.NewBorder(
		// Top: QR code and instructions in compact VBox
		container.NewVBox(qrContainer, s.instructions),
		// Bottom: Button row
		s.buttonRow,
		// Left, Right: nil
		nil, nil,
		// Center: nil (content goes in top/bottom)
		nil,
	)
	s.content.Hide() // Start hidden
}

// SetQRCode updates the QR code image resource.
func (q *QRCodeSection) SetQRCode(qrBytes []byte, jsonData string) {
	q.qrBytes = qrBytes
	q.jsonData = jsonData
	if len(qrBytes) > 0 {
		q.qrImage.Resource = fyne.NewStaticResource("qr_code.png", qrBytes)
		q.qrImage.Refresh()
		
		// Force complete UI refresh to ensure visual update
		if q.content != nil {
			q.content.Refresh()
		}
		q.Refresh()
		
		log.Printf("📱 QR code resource updated: %d bytes", len(qrBytes))
	}
}

// Toggle manages the animation state and is called by the parent.
func (q *QRCodeSection) Toggle(onComplete func()) {
	if q.isAnimating {
		return
	}
	
	if q.content.Visible() {
		// HIDING: Fade out and collapse QR content first, then resize window
		q.AnimateOut(func() {
			// Notify parent about expansion state change AFTER content is hidden
			if q.onExpanded != nil {
				q.onExpanded(false)
			}
			if onComplete != nil {
				onComplete()
			}
		})
	} else {
		// SHOWING: Resize window first, then show and fade in QR content
		if q.onExpanded != nil {
			q.onExpanded(true)
		}
		
		// Wait for window animation to complete before showing content
		time.AfterFunc(350*time.Millisecond, func() {
			q.AnimateIn(onComplete)
		})
	}
}

// AnimateIn shows and fades in the content.
func (q *QRCodeSection) AnimateIn(onComplete func()) {
	if q.isAnimating {
		return
	}
	q.isAnimating = true
	q.content.Show()
	q.Refresh() // Trigger layout recalculation
	
	// Start fully transparent
	q.qrImage.Translucency = 1.0

	completed := false
	anim := fyne.NewAnimation(300*time.Millisecond, func(p float32) {
		q.qrImage.Translucency = 1.0 - float64(p)
		q.qrImage.Refresh()
		
		// Handle completion
		if p >= 1.0 && !completed {
			completed = true
			q.isAnimating = false
			if onComplete != nil {
				onComplete()
			}
		}
	})
	anim.Curve = fyne.AnimationEaseInOut
	anim.Start()
}

// AnimateOut fades out and hides the content.
func (q *QRCodeSection) AnimateOut(onComplete func()) {
	if q.isAnimating {
		return
	}
	q.isAnimating = true

	completed := false
	anim := fyne.NewAnimation(300*time.Millisecond, func(p float32) {
		q.qrImage.Translucency = float64(p)
		q.qrImage.Refresh()
		
		// Handle completion
		if p >= 1.0 && !completed {
			completed = true
			q.content.Hide()
			q.Refresh() // Trigger layout recalculation
			q.isAnimating = false
			if onComplete != nil {
				onComplete()
			}
		}
	})
	anim.Curve = fyne.AnimationEaseInOut
	anim.Start()
}

// IsExpanded returns the visibility state of the content.
func (q *QRCodeSection) IsExpanded() bool {
	return q.content.Visible()
}

// SetOnExpanded sets the callback for when the expansion state changes.
func (q *QRCodeSection) SetOnExpanded(callback func(bool)) {
	q.onExpanded = callback
}

// SetOnRefresh sets the callback for the refresh button.
func (q *QRCodeSection) SetOnRefresh(callback func()) {
	q.onRefresh = callback
}

// SetOnClose sets the callback for the close button.
func (q *QRCodeSection) SetOnClose(callback func()) {
	q.onClose = callback
}

// CreateRenderer initializes the widget's renderer.
func (q *QRCodeSection) CreateRenderer() fyne.WidgetRenderer {
	q.setupUI()

	// Connect the refresh and close buttons now that they exist.
	q.buttonRow.Objects[0].(*customTheme.ModernButton).OnTapped = func() {
		if q.onRefresh != nil {
			q.onRefresh()
		}
	}
	q.buttonRow.Objects[2].(*customTheme.ModernButton).OnTapped = func() {
		if q.onClose != nil {
			q.onClose()
		}
	}

	return &qrCodeSectionRenderer{
		section: q,
	}
}

// qrCodeSectionRenderer handles the rendering of the QR code section.
type qrCodeSectionRenderer struct {
	section *QRCodeSection
}

func (r *qrCodeSectionRenderer) Layout(size fyne.Size) {
	r.section.content.Resize(size)
}

func (r *qrCodeSectionRenderer) MinSize() fyne.Size {
	// Return zero size when content is hidden so it doesn't take up space
	if !r.section.content.Visible() {
		return fyne.NewSize(0, 0)
	}
	return r.section.content.MinSize()
}

func (r *qrCodeSectionRenderer) Refresh() {
	r.section.content.Refresh()
}

func (r *qrCodeSectionRenderer) Objects() []fyne.CanvasObject {
	return []fyne.CanvasObject{r.section.content}
}

func (r *qrCodeSectionRenderer) Destroy() {}


// Animation Coordinator Interface Methods

// ForceShow immediately shows the QR section without animation
func (q *QRCodeSection) ForceShow() {
	if q.isAnimating {
		log.Println("📱 [WARNING] ForceShow called during animation")
		return
	}
	
	// Safety check - ensure content is initialized
	if q.content == nil {
		log.Println("📱 [WARNING] ForceShow called before content is initialized")
		return
	}
	
	log.Println("📱 [DEBUG] QRCodeSection force show")
	q.content.Show()
	if q.qrImage != nil {
		q.qrImage.Translucency = 0.0
		q.qrImage.Refresh()
	}
	q.Refresh()
}

// ForceHide immediately hides the QR section without animation
func (q *QRCodeSection) ForceHide() {
	if q.isAnimating {
		log.Println("📱 [WARNING] ForceHide called during animation")
		return
	}
	
	// Safety check - ensure content is initialized
	if q.content == nil {
		log.Println("📱 [WARNING] ForceHide called before content is initialized")
		return
	}
	
	log.Println("📱 [DEBUG] QRCodeSection force hide")
	q.content.Hide()
	if q.qrImage != nil {
		q.qrImage.Translucency = 1.0
		q.qrImage.Refresh()
	}
	q.Refresh()
}

// IsAnimating returns whether the QR section is currently animating
func (q *QRCodeSection) IsAnimating() bool {
	return q.isAnimating
}

// IsVisible returns whether the QR section content is currently visible
func (q *QRCodeSection) IsVisible() bool {
	return q.content.Visible()
} 