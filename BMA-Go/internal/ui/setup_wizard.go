package ui

import (
	"log"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/widget"

	"bma-go/internal/models"
	customTheme "bma-go/internal/ui/theme"
)

// SetupWizard represents the setup wizard controller
type SetupWizard struct {
	window         fyne.Window
	config         *models.Config
	contentContainer *fyne.Container
	currentStep    int
	steps          []SetupStep
	onComplete     func()
	backButton     *customTheme.ModernButton
	nextButton     *customTheme.ModernButton
}

// SetupStep interface for individual setup steps
type SetupStep interface {
	GetContent() fyne.CanvasObject
	GetTitle() string
	OnEnter() // Called when step becomes active
	OnExit()  // Called when leaving the step
	CanContinue() bool
	GetNextAction() func() // Custom action for "Next" button
}

// NewSetupWizard creates a new setup wizard
func NewSetupWizard(config *models.Config, onComplete func()) *SetupWizard {
	wizard := &SetupWizard{
		config:      config,
		currentStep: 0,
		onComplete:  onComplete,
	}
	
	// Initialize steps
	wizard.initializeSteps()
	
	return wizard
}

// SetWindow sets the window reference
func (sw *SetupWizard) SetWindow(window fyne.Window) {
	sw.window = window
	
	// Update window reference for music library step
	for _, step := range sw.steps {
		if musicLibraryStep, ok := step.(*MusicLibraryStep); ok {
			musicLibraryStep.SetWindow(window)
		}
	}
}

// initializeSteps creates all the setup steps
func (sw *SetupWizard) initializeSteps() {
	// Create Tailscale step with state change callback
	tailscaleStep := NewTailscaleStep()
	tailscaleStep.SetStateChangeCallback(func() {
		sw.updateButtonStates()
	})
	
	// Create Music Library step with state change callback
	musicLibraryStep := NewMusicLibraryStep(sw.config)
	musicLibraryStep.SetStateChangeCallback(func() {
		sw.updateButtonStates()
	})
	// Set window reference if available
	if sw.window != nil {
		musicLibraryStep.SetWindow(sw.window)
	}
	
	sw.steps = []SetupStep{
		NewWelcomeStep(),
		tailscaleStep,
		NewAndroidAppStep(),
		musicLibraryStep,
		NewSetupCompleteStep(),
	}
}

// GetContent returns the main content for the setup wizard
func (sw *SetupWizard) GetContent() fyne.CanvasObject {
	// Create scrollable container for step content
	sw.contentContainer = container.NewVBox()
	
	// Initialize with first step
	if len(sw.steps) > 0 {
		stepContent := sw.steps[0].GetContent()
		sw.contentContainer.Add(stepContent)
		sw.steps[0].OnEnter()
	}
	
	// Create scrollable content area
	scroll := container.NewScroll(sw.contentContainer)
	scroll.SetMinSize(fyne.NewSize(480, 300))
	
	// Create clean navigation buttons
	sw.backButton = customTheme.NewModernButton("Back", sw.goBack)
	sw.nextButton = customTheme.NewModernButton("Continue", sw.goNext)
	sw.nextButton.SetImportance(widget.HighImportance)
	
	// Set initial button states
	sw.updateButtonStates()
	
	// Spacer for centering buttons
	spacer := widget.NewLabel("")
	
	buttonContainer := container.NewBorder(
		nil, nil,
		sw.backButton,
		sw.nextButton,
		spacer,
	)
	
	// Main layout
	content := container.NewBorder(
		nil,                // top
		buttonContainer,    // bottom
		nil,                // left
		nil,                // right
		scroll,             // center - scrollable content
	)
	
	return content
}

// updateButtonStates manages button state updates based on current step
func (sw *SetupWizard) updateButtonStates() {
	// Back button - disabled on first step
	if sw.currentStep == 0 {
		sw.backButton.Disable()
	} else {
		sw.backButton.Enable()
	}
	
	// Next button - depends on step's CanContinue and button text
	if sw.currentStep >= len(sw.steps)-1 {
		// Last step - complete setup
		sw.nextButton.Text = "Start Streaming"
		sw.nextButton.Refresh()
	} else {
		sw.nextButton.Text = "Continue"
		sw.nextButton.Refresh()
	}
	
	// Enable/disable based on step's continuation rules
	if len(sw.steps) > sw.currentStep && !sw.steps[sw.currentStep].CanContinue() {
		sw.nextButton.Disable()
	} else {
		sw.nextButton.Enable()
	}
}

// goNext advances to the next step with fade animation
func (sw *SetupWizard) goNext() {
	if sw.currentStep >= len(sw.steps)-1 {
		// Last step - complete setup
		sw.completeSetup()
		return
	}
	
	// Check if current step allows continuation
	if !sw.steps[sw.currentStep].CanContinue() {
		return
	}
	
	// Handle custom next action if available
	if customAction := sw.steps[sw.currentStep].GetNextAction(); customAction != nil {
		customAction()
		return
	}
	
	// Standard next step
	sw.transitionToStep(sw.currentStep + 1)
}

// goBack goes to the previous step with fade animation
func (sw *SetupWizard) goBack() {
	if sw.currentStep > 0 {
		sw.transitionToStep(sw.currentStep - 1)
	}
}

// transitionToStep handles animated transition between steps
func (sw *SetupWizard) transitionToStep(newStep int) {
	if newStep < 0 || newStep >= len(sw.steps) {
		return
	}
	
	oldStep := sw.currentStep
	sw.currentStep = newStep
	
	// Exit current step
	sw.steps[oldStep].OnExit()
	
	// Create fade animation
	sw.animateStepTransition(newStep)
}

// animateStepTransition creates a smooth fade transition between steps
func (sw *SetupWizard) animateStepTransition(newStep int) {
	// Get new content
	newContent := sw.steps[newStep].GetContent()
	
	// Simple transition with timing
	fadeOut := fyne.NewAnimation(150*time.Millisecond, func(progress float32) {
		// Fade effect - for now we'll just do the transition
	})
	
	fadeOut.Curve = fyne.AnimationEaseInOut
	
	// Set callback for when fade out completes
	go func() {
		fadeOut.Start()
		time.Sleep(150 * time.Millisecond)
		
		// Update content on UI thread
		sw.updateStepContent(newContent, newStep)
		
		// Fade in
		fadeIn := fyne.NewAnimation(150*time.Millisecond, func(progress float32) {
			// Fade in effect
		})
		fadeIn.Curve = fyne.AnimationEaseInOut
		fadeIn.Start()
	}()
}

// updateStepContent updates the content container with new step
func (sw *SetupWizard) updateStepContent(newContent fyne.CanvasObject, newStep int) {
	// Clear content and add new step
	sw.contentContainer.Objects = nil
	sw.contentContainer.Add(newContent)
	
	// Enter new step
	sw.steps[newStep].OnEnter()
	
	// Update window title
	if sw.window != nil {
		sw.window.SetTitle("BMA Setup - " + sw.steps[newStep].GetTitle())
	}
	
	// Update button states for new step
	sw.updateButtonStates()
	
	// Refresh the container
	sw.contentContainer.Refresh()
}

// completeSetup finalizes the setup process
func (sw *SetupWizard) completeSetup() {
	log.Println("🔧 [DEBUG] completeSetup() called - Start Streaming clicked")
	
	// Mark setup as complete in config
	if err := sw.config.MarkSetupComplete(); err != nil {
		log.Printf("🔧 [DEBUG] MarkSetupComplete failed: %v", err)
		return
	}
	log.Println("🔧 [DEBUG] MarkSetupComplete succeeded")
	
	// Call completion callback
	if sw.onComplete != nil {
		log.Println("🔧 [DEBUG] Calling onComplete callback")
		sw.onComplete()
		log.Println("🔧 [DEBUG] onComplete callback finished")
	}
} 