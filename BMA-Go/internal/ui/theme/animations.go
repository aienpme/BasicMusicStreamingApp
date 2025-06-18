package theme

import (
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/widget"
)

// AnimationType defines the type of animation
type AnimationType int

const (
	AnimationFadeIn AnimationType = iota
	AnimationFadeOut
	AnimationSlideIn
	AnimationSlideOut
	AnimationBounce
	AnimationScale
)

// AnimatedContainer provides smooth transitions for content changes
type AnimatedContainer struct {
	widget.BaseWidget
	content      fyne.CanvasObject
	animation    *fyne.Animation
	animType     AnimationType
	duration     time.Duration
	currentAlpha float32
}

// NewAnimatedContainer creates a new animated container
func NewAnimatedContainer(content fyne.CanvasObject) *AnimatedContainer {
	a := &AnimatedContainer{
		content:      content,
		duration:     300 * time.Millisecond,
		currentAlpha: 1.0,
	}
	a.ExtendBaseWidget(a)
	return a
}

// SetContent changes the content with animation
func (a *AnimatedContainer) SetContent(content fyne.CanvasObject, animType AnimationType) {
	// Stop current animation if running
	if a.animation != nil {
		a.animation.Stop()
	}

	// Fade out old content
	fadeOut := fyne.NewAnimation(150*time.Millisecond, func(progress float32) {
		a.currentAlpha = 1.0 - progress
		a.Refresh()
	})
	fadeOut.Curve = fyne.AnimationEaseInOut

	fadeOut.Start()
	
	// After fade out, switch content and fade in
	go func() {
		time.Sleep(150 * time.Millisecond)
		a.content = content
		
		fadeIn := fyne.NewAnimation(150*time.Millisecond, func(progress float32) {
			a.currentAlpha = progress
			a.Refresh()
		})
		fadeIn.Curve = fyne.AnimationEaseInOut
		fadeIn.Start()
	}()
}

// CreateRenderer creates the renderer for AnimatedContainer
func (a *AnimatedContainer) CreateRenderer() fyne.WidgetRenderer {
	return &animatedContainerRenderer{
		container: a,
		objects:   []fyne.CanvasObject{a.content},
	}
}

type animatedContainerRenderer struct {
	container *AnimatedContainer
	objects   []fyne.CanvasObject
}

func (r *animatedContainerRenderer) Layout(size fyne.Size) {
	if r.container.content != nil {
		r.container.content.Resize(size)
	}
}

func (r *animatedContainerRenderer) MinSize() fyne.Size {
	if r.container.content != nil {
		return r.container.content.MinSize()
	}
	return fyne.NewSize(100, 100)
}

func (r *animatedContainerRenderer) Refresh() {
	// Apply alpha to content if it's a canvas object
	// Note: This is simplified - in a real implementation you'd need to handle this better
	canvas.Refresh(r.container.content)
}

func (r *animatedContainerRenderer) Objects() []fyne.CanvasObject {
	return r.objects
}

func (r *animatedContainerRenderer) Destroy() {}

// PulseAnimation creates a pulsing effect for important UI elements
func PulseAnimation(obj fyne.CanvasObject) *fyne.Animation {
	var forward bool = true
	pulse := fyne.NewAnimation(1*time.Second, func(progress float32) {
		// Create a pulsing effect
		if forward {
			// Scale up slightly
			if rect, ok := obj.(*canvas.Rectangle); ok {
				// For demo - would need proper implementation
				rect.Refresh()
			}
		}
	})
	pulse.RepeatCount = fyne.AnimationRepeatForever
	pulse.AutoReverse = true
	return pulse
}

// ShakeAnimation creates a shake effect for error states
func ShakeAnimation(obj fyne.CanvasObject) *fyne.Animation {
	originalPos := obj.Position()
	shakeDistance := float32(5)
	
	shake := fyne.NewAnimation(500*time.Millisecond, func(progress float32) {
		// Calculate shake offset
		offset := shakeDistance * (1 - progress)
		if int(progress*10)%2 == 0 {
			obj.Move(fyne.NewPos(originalPos.X+offset, originalPos.Y))
		} else {
			obj.Move(fyne.NewPos(originalPos.X-offset, originalPos.Y))
		}
	})
	shake.Curve = fyne.AnimationEaseOut
	return shake
}

// LoadingSpinner creates a modern loading spinner
type LoadingSpinner struct {
	widget.BaseWidget
	animation *fyne.Animation
	rotation  float32
}

// NewLoadingSpinner creates a new loading spinner
func NewLoadingSpinner() *LoadingSpinner {
	s := &LoadingSpinner{}
	s.ExtendBaseWidget(s)
	
	// Start spinning animation
	s.animation = fyne.NewAnimation(2*time.Second, func(progress float32) {
		s.rotation = progress * 360
		s.Refresh()
	})
	s.animation.RepeatCount = fyne.AnimationRepeatForever
	s.animation.Start()
	
	return s
}

// CreateRenderer creates the renderer for LoadingSpinner
func (s *LoadingSpinner) CreateRenderer() fyne.WidgetRenderer {
	// Create spinner graphics
	circle1 := canvas.NewCircle(AccentPrimary)
	circle1.StrokeWidth = 3
	circle1.StrokeColor = AccentPrimary
	circle1.FillColor = nil
	
	circle2 := canvas.NewCircle(AccentSecondary)
	circle2.StrokeWidth = 3
	circle2.StrokeColor = AccentSecondary
	circle2.FillColor = nil
	
	return &loadingSpinnerRenderer{
		spinner: s,
		circles: []fyne.CanvasObject{circle1, circle2},
	}
}

// Stop stops the spinner animation
func (s *LoadingSpinner) Stop() {
	if s.animation != nil {
		s.animation.Stop()
	}
}

type loadingSpinnerRenderer struct {
	spinner *LoadingSpinner
	circles []fyne.CanvasObject
}

func (r *loadingSpinnerRenderer) Layout(size fyne.Size) {
	// Position circles
	for i, circle := range r.circles {
		circle.Resize(fyne.NewSize(size.Width-float32(i*10), size.Height-float32(i*10)))
		circle.Move(fyne.NewPos(float32(i*5), float32(i*5)))
	}
}

func (r *loadingSpinnerRenderer) MinSize() fyne.Size {
	return fyne.NewSize(40, 40)
}

func (r *loadingSpinnerRenderer) Refresh() {
	// Update rotation - simplified for demo
	canvas.Refresh(r.circles[0])
}

func (r *loadingSpinnerRenderer) Objects() []fyne.CanvasObject {
	return r.circles
}

func (r *loadingSpinnerRenderer) Destroy() {
	r.spinner.Stop()
}

// SuccessAnimation shows a success checkmark with animation
func ShowSuccessAnimation(parent fyne.Window) {
	// Create success icon - simplified version
	successCircle := canvas.NewCircle(StatusSuccess)
	successCircle.StrokeWidth = 0
	successCircle.Resize(fyne.NewSize(100, 100))
	
	// Create simple checkmark overlay
	checkText := canvas.NewText("", TextOnAccent)
	checkText.TextSize = 48
	checkText.Alignment = fyne.TextAlignCenter
	
	// Combine into content
	content := container.NewStack(
		container.NewCenter(successCircle),
		container.NewCenter(checkText),
	)
	
	popup := widget.NewModalPopUp(content, parent.Canvas())
	
	// Animate and auto-close
	go func() {
		popup.Show()
		time.Sleep(1500 * time.Millisecond)
		
		// Fade out
		fadeOut := fyne.NewAnimation(300*time.Millisecond, func(progress float32) {
			// In a real implementation, we'd fade the popup
		})
		fadeOut.Start()
		time.Sleep(300 * time.Millisecond)
		popup.Hide()
	}()
} 