package theme

import (
	"image/color"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/driver/desktop"
	"fyne.io/fyne/v2/layout"
	"fyne.io/fyne/v2/widget"
)

// ModernCard creates a modern styled card with rounded corners and shadow
type ModernCard struct {
	widget.BaseWidget
	Title    string
	Subtitle string
	Content  fyne.CanvasObject
	OnTap    func()
}

// NewModernCard creates a new modern card widget
func NewModernCard(title, subtitle string, content fyne.CanvasObject) *ModernCard {
	card := &ModernCard{
		Title:    title,
		Subtitle: subtitle,
		Content:  content,
	}
	card.ExtendBaseWidget(card)
	return card
}

// CreateRenderer creates the renderer for ModernCard
func (c *ModernCard) CreateRenderer() fyne.WidgetRenderer {
	// Background with rounded corners
	bg := canvas.NewRectangle(BackgroundCard)
	bg.CornerRadius = 8  // Reduced corner radius for more professional look
	
	// Subtle shadow effect
	shadow1 := canvas.NewRectangle(color.NRGBA{0, 0, 0, 15})
	shadow1.CornerRadius = 8
	shadow2 := canvas.NewRectangle(color.NRGBA{0, 0, 0, 8})
	shadow2.CornerRadius = 8
	
	// Title label
	titleLabel := widget.NewLabelWithStyle(c.Title, fyne.TextAlignLeading, fyne.TextStyle{Bold: true})
	
	// Subtitle label
	subtitleLabel := widget.NewLabel(c.Subtitle)
	
	// Header container
	var header fyne.CanvasObject
	if c.Subtitle != "" {
		header = container.NewVBox(titleLabel, subtitleLabel)
	} else if c.Title != "" {
		header = titleLabel
	}
	
	// Content container with padding
	var content fyne.CanvasObject
	if header != nil && c.Content != nil {
		content = container.NewBorder(
			container.NewPadded(header),
			nil, nil, nil,
			container.NewPadded(c.Content),
		)
	} else if c.Content != nil {
		content = container.NewPadded(c.Content)
	} else if header != nil {
		content = container.NewPadded(header)
	}
	
	return &modernCardRenderer{
		card:         c,
		bg:           bg,
		shadow1:      shadow1,
		shadow2:      shadow2,
		content:      content,
		titleLabel:   titleLabel,
		subtitleLabel: subtitleLabel,
	}
}

// Tapped handles tap events
func (c *ModernCard) Tapped(_ *fyne.PointEvent) {
	if c.OnTap != nil {
		c.OnTap()
	}
}

type modernCardRenderer struct {
	card         *ModernCard
	bg           *canvas.Rectangle
	shadow1      *canvas.Rectangle
	shadow2      *canvas.Rectangle
	content      fyne.CanvasObject
	titleLabel   *widget.Label
	subtitleLabel *widget.Label
}

func (r *modernCardRenderer) Layout(size fyne.Size) {
	// Shadow positions (offset for depth effect)
	r.shadow2.Resize(size)
	r.shadow2.Move(fyne.NewPos(2, 2))
	
	r.shadow1.Resize(size)
	r.shadow1.Move(fyne.NewPos(1, 1))
	
	// Background
	r.bg.Resize(size)
	
	// Content
	if r.content != nil {
		r.content.Resize(size)
	}
}

func (r *modernCardRenderer) MinSize() fyne.Size {
	if r.content != nil {
		return r.content.MinSize()
	}
	return fyne.NewSize(100, 50)
}

func (r *modernCardRenderer) Refresh() {
	r.bg.FillColor = BackgroundCard
	r.bg.Refresh()
	
	// Update title and subtitle labels with current card values
	if r.titleLabel != nil {
		r.titleLabel.SetText(r.card.Title)
	}
	if r.subtitleLabel != nil {
		r.subtitleLabel.SetText(r.card.Subtitle)
	}
}

func (r *modernCardRenderer) Objects() []fyne.CanvasObject {
	return []fyne.CanvasObject{r.shadow2, r.shadow1, r.bg, r.content}
}

func (r *modernCardRenderer) Destroy() {}

// ModernButton creates a modern styled button with gradient and hover effects
type ModernButton struct {
	widget.BaseWidget
	Text         string
	Icon         fyne.Resource
	OnTapped     func()
	Importance   widget.ButtonImportance
	
	hovered      bool
	disabled     bool
	savedHandler func() // Store handler when disabled
}

// NewModernButton creates a new modern button
func NewModernButton(text string, onTapped func()) *ModernButton {
	btn := &ModernButton{
		Text:       text,
		OnTapped:   onTapped,
		Importance: widget.MediumImportance,
	}
	btn.ExtendBaseWidget(btn)
	return btn
}

// NewModernButtonWithIcon creates a new modern button with an icon
func NewModernButtonWithIcon(text string, icon fyne.Resource, onTapped func()) *ModernButton {
	btn := &ModernButton{
		Text:     text,
		Icon:     icon,
		OnTapped: onTapped,
		Importance: widget.MediumImportance,
	}
	btn.ExtendBaseWidget(btn)
	return btn
}

// SetImportance sets the button importance (affects styling)
func (b *ModernButton) SetImportance(importance widget.ButtonImportance) {
	b.Importance = importance
	b.Refresh()
}

// Enable enables the button
func (b *ModernButton) Enable() {
	if b.disabled && b.savedHandler != nil {
		b.OnTapped = b.savedHandler
		b.savedHandler = nil
	}
	b.disabled = false
	b.Refresh()
}

// Disable disables the button
func (b *ModernButton) Disable() {
	if !b.disabled && b.OnTapped != nil {
		b.savedHandler = b.OnTapped
		b.OnTapped = nil
	}
	b.disabled = true
	b.Refresh()
}

// Show shows the button
func (b *ModernButton) Show() {
	b.BaseWidget.Show()
}

// Hide hides the button
func (b *ModernButton) Hide() {
	b.BaseWidget.Hide()
}

// CreateRenderer creates the renderer for ModernButton
func (b *ModernButton) CreateRenderer() fyne.WidgetRenderer {
	// Background - start with transparent for proper initial state
	bg := canvas.NewRectangle(color.Transparent)
	bg.CornerRadius = 4  // Smaller radius for cleaner look
	
	// Text canvas object for better color control
	text := canvas.NewText(b.Text, TextPrimary)
	text.TextStyle = fyne.TextStyle{Bold: true}
	text.Alignment = fyne.TextAlignCenter
	
	// Icon if provided
	var content fyne.CanvasObject
	if b.Icon != nil {
		icon := widget.NewIcon(b.Icon)
		content = container.New(layout.NewHBoxLayout(),
			icon,
			text,
		)
	} else {
		content = container.NewCenter(text)
	}
	
	// Wrap in padded container
	paddedContent := container.NewPadded(content)
	
	renderer := &modernButtonRenderer{
		button:  b,
		bg:      bg,
		content: paddedContent,
		text:    text,
	}
	
	// Apply initial styling
	renderer.applyStyle()
	
	return renderer
}

// Tapped handles tap events
func (b *ModernButton) Tapped(_ *fyne.PointEvent) {
	if b.OnTapped != nil {
		b.OnTapped()
	}
}

// MouseIn handles mouse enter events
func (b *ModernButton) MouseIn(*desktop.MouseEvent) {
	b.hovered = true
	b.Refresh()
}

// MouseOut handles mouse leave events
func (b *ModernButton) MouseOut() {
	b.hovered = false
	b.Refresh()
}

// MouseMoved handles mouse move events
func (b *ModernButton) MouseMoved(*desktop.MouseEvent) {}

type modernButtonRenderer struct {
	button  *ModernButton
	bg      *canvas.Rectangle
	content fyne.CanvasObject
	text    *canvas.Text
}

func (r *modernButtonRenderer) Layout(size fyne.Size) {
	r.bg.Resize(size)
	r.content.Resize(size)
}

func (r *modernButtonRenderer) MinSize() fyne.Size {
	min := r.content.MinSize()
	return fyne.NewSize(
		fyne.Max(min.Width+20, 80),  // Reduced minimum width
		fyne.Max(min.Height+8, 32),   // Reduced minimum height
	)
}

func (r *modernButtonRenderer) Refresh() {
	r.applyStyle()
	r.bg.Refresh()
	r.text.Refresh()
}

// applyStyle applies the current style based on button state
func (r *modernButtonRenderer) applyStyle() {
	// Update background color based on importance and state
	if r.button.disabled {
		// Disabled state - very subtle
		r.bg.FillColor = color.NRGBA{R: 40, G: 40, B: 40, A: 255}
		r.bg.StrokeColor = color.NRGBA{R: 60, G: 60, B: 60, A: 255}
		r.bg.StrokeWidth = 1
	} else {
		switch r.button.Importance {
		case widget.HighImportance:
			// Primary buttons - white with dark text
			if r.button.hovered {
				r.bg.FillColor = color.NRGBA{R: 230, G: 230, B: 230, A: 255}
			} else {
				r.bg.FillColor = color.NRGBA{R: 255, G: 255, B: 255, A: 255}
			}
			r.bg.StrokeWidth = 0
		case widget.DangerImportance:
			if r.button.hovered {
				r.bg.FillColor = color.NRGBA{R: 220, G: 38, B: 38, A: 255}
			} else {
				r.bg.FillColor = StatusError
			}
			r.bg.StrokeWidth = 0
		default:
			// Secondary buttons - transparent with border
			if r.button.hovered {
				r.bg.FillColor = color.NRGBA{R: 255, G: 255, B: 255, A: 20}
			} else {
				r.bg.FillColor = color.Transparent
			}
			r.bg.StrokeColor = color.NRGBA{R: 100, G: 100, B: 100, A: 255}
			r.bg.StrokeWidth = 1
		}
	}
	
	// Update text and color
	r.text.Text = r.button.Text
	
	if r.button.disabled {
		r.text.Color = TextMuted
		r.text.TextStyle = fyne.TextStyle{Bold: false}
	} else if r.button.Importance == widget.HighImportance {
		// Dark text on white background for high importance buttons
		r.text.Color = TextOnAccent  // This is dark color for text on light backgrounds
		r.text.TextStyle = fyne.TextStyle{Bold: true}
	} else if r.button.Importance == widget.DangerImportance {
		// White text on red background
		r.text.Color = TextPrimary
		r.text.TextStyle = fyne.TextStyle{Bold: true}
	} else {
		// Normal text color for other buttons
		r.text.Color = TextPrimary
		r.text.TextStyle = fyne.TextStyle{Bold: false}
	}
}

func (r *modernButtonRenderer) Objects() []fyne.CanvasObject {
	return []fyne.CanvasObject{r.bg, r.content}
}

func (r *modernButtonRenderer) Destroy() {}

// ModernEntry creates a modern styled text entry with better borders
func NewModernEntry() *widget.Entry {
	entry := widget.NewEntry()
	// The theme will handle the styling
	return entry
}

// ModernLabel creates a label with specific styling
func NewModernLabel(text string, style fyne.TextStyle) *widget.Label {
	label := widget.NewLabelWithStyle(text, fyne.TextAlignLeading, style)
	return label
}

// StatusBadge creates a small status indicator badge
type StatusBadge struct {
	widget.BaseWidget
	Status      string
	BadgeColor  color.Color
}

// NewStatusBadge creates a new status badge
func NewStatusBadge(status string, badgeColor color.Color) *StatusBadge {
	badge := &StatusBadge{
		Status:     status,
		BadgeColor: badgeColor,
	}
	badge.ExtendBaseWidget(badge)
	return badge
}

// CreateRenderer creates the renderer for StatusBadge
func (b *StatusBadge) CreateRenderer() fyne.WidgetRenderer {
	bg := canvas.NewRectangle(b.BadgeColor)
	bg.CornerRadius = 4  // Smaller radius for cleaner look
	
	label := widget.NewLabelWithStyle(b.Status, fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
	
	content := container.NewPadded(label)
	
	return &statusBadgeRenderer{
		badge:   b,
		bg:      bg,
		label:   label,
		content: content,
	}
}

type statusBadgeRenderer struct {
	badge   *StatusBadge
	bg      *canvas.Rectangle
	label   *widget.Label
	content fyne.CanvasObject
}

func (r *statusBadgeRenderer) Layout(size fyne.Size) {
	r.bg.Resize(size)
	r.content.Resize(size)
}

func (r *statusBadgeRenderer) MinSize() fyne.Size {
	min := r.content.MinSize()
	return fyne.NewSize(
		fyne.Max(min.Width, 80),
		fyne.Max(min.Height, 24),
	)
}

func (r *statusBadgeRenderer) Refresh() {
	r.bg.FillColor = r.badge.BadgeColor
	r.bg.Refresh()
	r.label.SetText(r.badge.Status)
}

func (r *statusBadgeRenderer) Objects() []fyne.CanvasObject {
	return []fyne.CanvasObject{r.bg, r.content}
}

func (r *statusBadgeRenderer) Destroy() {} 