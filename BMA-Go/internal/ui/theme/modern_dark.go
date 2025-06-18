package theme

import (
	"image/color"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/theme"
)

// ModernDarkTheme represents our custom modern dark theme
type ModernDarkTheme struct{}

// Modern color palette - minimal two-tone design
var (
	// Background colors - sophisticated grays
	BackgroundDark      = &color.NRGBA{R: 18, G: 18, B: 18, A: 255}    // #121212 - Main background
	BackgroundElevated  = &color.NRGBA{R: 28, G: 28, B: 28, A: 255}    // #1C1C1C - Elevated surfaces
	BackgroundCard      = &color.NRGBA{R: 35, G: 35, B: 35, A: 255}    // #232323 - Card backgrounds
	
	// Accent colors - subtle monochrome
	AccentPrimary       = &color.NRGBA{R: 255, G: 255, B: 255, A: 255} // #FFFFFF - Primary (white)
	AccentSecondary     = &color.NRGBA{R: 200, G: 200, B: 200, A: 255} // #C8C8C8 - Secondary (light gray)
	AccentGradientStart = &color.NRGBA{R: 255, G: 255, B: 255, A: 255} // #FFFFFF
	AccentGradientEnd   = &color.NRGBA{R: 200, G: 200, B: 200, A: 255} // #C8C8C8
	
	// Status colors - muted for professional look
	StatusSuccess       = &color.NRGBA{R: 134, G: 239, B: 172, A: 255} // #86EFAC - Soft green
	StatusWarning       = &color.NRGBA{R: 253, G: 224, B: 71, A: 255}  // #FDE047 - Soft yellow
	StatusError         = &color.NRGBA{R: 248, G: 113, B: 113, A: 255} // #F87171 - Soft red
	StatusInfo          = &color.NRGBA{R: 147, G: 197, B: 253, A: 255} // #93C5FD - Soft blue
	
	// Text colors - clean contrast
	TextPrimary         = &color.NRGBA{R: 255, G: 255, B: 255, A: 255} // #FFFFFF - Primary text
	TextSecondary       = &color.NRGBA{R: 180, G: 180, B: 180, A: 255} // #B4B4B4 - Secondary text
	TextMuted           = &color.NRGBA{R: 120, G: 120, B: 120, A: 255} // #787878 - Muted text
	TextOnAccent        = &color.NRGBA{R: 18, G: 18, B: 18, A: 255}    // #121212 - Text on accent (dark)
	
	// Border and divider colors
	BorderDefault       = &color.NRGBA{R: 60, G: 60, B: 60, A: 255}    // #3C3C3C - Default border
	BorderFocused       = &color.NRGBA{R: 255, G: 255, B: 255, A: 255} // #FFFFFF - White when focused
	DividerColor        = &color.NRGBA{R: 50, G: 50, B: 50, A: 255}    // #323232 - Divider
	
	// Shadow colors for subtle depth
	ShadowLight         = &color.NRGBA{R: 0, G: 0, B: 0, A: 25}        // 10% black
	ShadowMedium        = &color.NRGBA{R: 0, G: 0, B: 0, A: 51}        // 20% black
	ShadowDark          = &color.NRGBA{R: 0, G: 0, B: 0, A: 76}        // 30% black
)

// Color returns the color for the specified theme color name
func (m ModernDarkTheme) Color(name fyne.ThemeColorName, variant fyne.ThemeVariant) color.Color {
	switch name {
	case theme.ColorNameBackground:
		return BackgroundDark
	case theme.ColorNameButton:
		return BackgroundCard
	case theme.ColorNameDisabledButton:
		return BackgroundElevated
	case theme.ColorNameDisabled:
		return TextMuted
	case theme.ColorNameError:
		return StatusError
	case theme.ColorNameFocus:
		return AccentPrimary
	case theme.ColorNameForeground:
		return TextPrimary
	case theme.ColorNameHover:
		return BackgroundElevated
	case theme.ColorNameInputBackground:
		return BackgroundElevated
	case theme.ColorNameInputBorder:
		return BorderDefault
	case theme.ColorNameMenuBackground:
		return BackgroundElevated
	case theme.ColorNameOverlayBackground:
		return BackgroundCard
	case theme.ColorNamePlaceHolder:
		return TextMuted
	case theme.ColorNamePressed:
		return AccentSecondary
	case theme.ColorNamePrimary:
		return AccentPrimary
	case theme.ColorNameScrollBar:
		return BorderDefault
	case theme.ColorNameSelection:
		return AccentSecondary
	case theme.ColorNameSeparator:
		return DividerColor
	case theme.ColorNameShadow:
		return ShadowMedium
	case theme.ColorNameSuccess:
		return StatusSuccess
	case theme.ColorNameWarning:
		return StatusWarning
	}

	return theme.DefaultTheme().Color(name, variant)
}

// Font returns the font resource for the specified text style
func (m ModernDarkTheme) Font(style fyne.TextStyle) fyne.Resource {
	// Use default fonts for now, but we could add custom fonts here
	return theme.DefaultTheme().Font(style)
}

// Icon returns the icon resource for the specified theme icon name
func (m ModernDarkTheme) Icon(name fyne.ThemeIconName) fyne.Resource {
	// Use default icons for now, but we could add custom icons here
	return theme.DefaultTheme().Icon(name)
}

// Size returns the size for the specified theme size name
func (m ModernDarkTheme) Size(name fyne.ThemeSizeName) float32 {
	switch name {
	case theme.SizeNamePadding:
		return 6 // Reduced padding for cleaner look
	case theme.SizeNameInlineIcon:
		return 16
	case theme.SizeNameInnerPadding:
		return 8 // Less internal padding
	case theme.SizeNameLineSpacing:
		return 6
	case theme.SizeNameScrollBar:
		return 16
	case theme.SizeNameScrollBarSmall:
		return 3
	case theme.SizeNameSeparatorThickness:
		return 1
	case theme.SizeNameText:
		return 13 // Refined base text size
	case theme.SizeNameHeadingText:
		return 20 // More proportional headings
	case theme.SizeNameSubHeadingText:
		return 16
	case theme.SizeNameCaptionText:
		return 11
	case theme.SizeNameInputBorder:
		return 2 // Thicker borders for modern look
	}

	return theme.DefaultTheme().Size(name)
}

// NewModernDarkTheme creates a new instance of our modern dark theme
func NewModernDarkTheme() fyne.Theme {
	return &ModernDarkTheme{}
} 