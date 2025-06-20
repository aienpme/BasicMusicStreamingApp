# Task: Inline QR Code Display with Smooth Animations

## Overview
Transform the QR code display from a separate dialog window to an inline expandable section within the main window, with smooth animations for expansion/collapse.

## Current State
- QR code displays in a separate modal window
- Auto-hides when device connects
- Main window has fixed size

## Desired State
- QR code appears inline below the server controls
- Window smoothly expands to accommodate QR code
- Window smoothly collapses when QR code is hidden
- Auto-collapse when device connects (maintain existing functionality)

## Implementation Plan

### 1. UI Structure Changes

#### Current Layout:
```
┌─────────────────────────┐
│   Server Controls       │
│   Music Selection       │
│   Status Bar           │
└─────────────────────────┘
```

#### New Layout:
```
┌─────────────────────────┐
│   Server Controls       │
│   [QR Section Hidden]   │  ← Collapsed state
│   Music Selection       │
│   Status Bar           │
└─────────────────────────┘

┌─────────────────────────┐
│   Server Controls       │
├─────────────────────────┤
│                         │
│      QR Code           │  ← Expanded state
│   Instructions         │
│   Action Buttons       │
│                         │
├─────────────────────────┤
│   Music Selection       │
│   Status Bar           │
└─────────────────────────┘
```

### 2. Animation Strategy

#### Approach Options:
1. **Window Resize Animation** - Smoothly resize the window height
2. **Content Slide Animation** - Slide the QR section in/out
3. **Fade + Resize Combination** - Fade content while resizing

**Recommended: Option 3** - Most polished user experience

### 3. Technical Components

#### A. New UI Components
```go
// QRCodeSection - Collapsible QR code display
type QRCodeSection struct {
    widget.BaseWidget
    
    // State
    expanded     bool
    animating    bool
    currentHeight float32
    targetHeight  float32
    
    // Content
    qrImage      *canvas.Image
    instructions *widget.RichText
    buttons      *fyne.Container
    
    // Animation
    animation    *fyne.Animation
}
```

#### B. Animation System
```go
// Smooth height animation
func (q *QRCodeSection) animateHeight(from, to float32, onComplete func()) {
    q.animation = canvas.NewPositionAnimation(
        fyne.NewPos(0, from),
        fyne.NewPos(0, to),
        300*time.Millisecond,
        func(p fyne.Position) {
            q.currentHeight = p.Y
            q.Refresh()
        },
    )
    q.animation.Curve = fyne.AnimationEaseInOut
    q.animation.Start()
}
```

#### C. Window Management
- Track window's base height
- Calculate expanded height based on QR section
- Smooth window resize during animation

### 4. User Flow

1. **User clicks "QR Code" button**
   - Button changes to "Hide QR"
   - QR section begins expansion animation
   - Window smoothly grows
   - QR code fades in

2. **QR Code is displayed**
   - Shows QR code at optimal size (150x150)
   - Instructions below
   - Action buttons (Copy JSON, Save PNG, Refresh)

3. **Auto-hide on device connection**
   - Detect device connection (existing logic)
   - Trigger collapse animation
   - Window shrinks back to original size

4. **Manual hide**
   - User clicks "Hide QR"
   - Same collapse animation

### 5. Implementation Steps

#### Phase 1: Create QRCodeSection Component
- [ ] Create new `qr_section.go` file
- [ ] Implement basic expandable container
- [ ] Add height animation support

#### Phase 2: Integrate with ServerStatusBar
- [ ] Replace QR dialog with inline section
- [ ] Connect to existing QR generation logic
- [ ] Update button behavior

#### Phase 3: Window Resize Animation
- [ ] Track base window size
- [ ] Implement smooth window resize
- [ ] Coordinate with content animation

#### Phase 4: Polish & Testing
- [ ] Fine-tune animation timing
- [ ] Add loading states
- [ ] Test auto-hide functionality
- [ ] Ensure smooth performance

### 6. Key Considerations

#### Performance
- Use Fyne's built-in animation system
- Avoid recreating QR image unnecessarily
- Cache rendered components

#### User Experience
- Animation duration: 300-400ms (feels responsive but smooth)
- Easing curve: EaseInOut for natural motion
- Provide visual feedback during transitions

#### Edge Cases
- Multiple rapid clicks on show/hide
- Window resize during animation
- QR generation failures
- Device connection during animation

### 7. Benefits

1. **Cleaner UI** - No modal dialogs blocking the interface
2. **Better Context** - QR code appears where user expects it
3. **Smoother Flow** - Natural expansion feels more integrated
4. **Modern Feel** - Animations add polish and professionalism

### 8. Alternative Approaches Considered

1. **Slide-out Panel** - Rejected: Breaks window flow
2. **Overlay** - Rejected: Similar to current dialog approach
3. **Tab System** - Rejected: Adds unnecessary complexity

## Next Steps

1. Review and approve this plan
2. Create the QRCodeSection component
3. Implement basic expand/collapse without animation
4. Add smooth animations
5. Integrate with existing auto-hide logic
6. Polish and test

## Questions for Discussion

1. Should the QR code section push content down or overlay it?
2. What should happen if user resizes window during animation?
3. Should we add a subtle bounce effect at the end of animations?
4. Do we want to persist the QR state between app launches?

---

Ready to proceed with implementation once we agree on the approach! 