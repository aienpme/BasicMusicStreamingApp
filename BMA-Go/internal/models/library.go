package models

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/dialog"
	"github.com/fsnotify/fsnotify"
	"github.com/google/uuid"
)

// Album represents a collection of songs grouped by album name
type Album struct {
	ID     uuid.UUID `json:"id"`
	Name   string    `json:"name"`
	Songs  []*Song   `json:"songs"`
	Artist string    `json:"artist,omitempty"`
}

// DisplayItem interface for items that can be displayed in the UI (albums or folders)
type DisplayItem interface {
	GetID() string
	GetName() string
	GetSubtitle() string
	GetSongs() []*Song
	IsFolder() bool
	HasArtwork() bool
	GetArtwork() []byte
}

// FolderItem represents a folder containing mixed songs for UI display
type FolderItem struct {
	ID            string    `json:"id"`
	FolderName    string    `json:"folderName"`
	FolderPath    string    `json:"folderPath"`
	Songs         []*Song   `json:"songs"`
}

// AlbumItem wraps an Album for UI display
type AlbumItem struct {
	Album *Album `json:"album"`
}

// TrackCount returns the number of songs in the album
func (a *Album) TrackCount() int {
	return len(a.Songs)
}

// FolderItem implementation of DisplayItem interface
func (f *FolderItem) GetID() string          { return f.ID }
func (f *FolderItem) GetName() string        { return f.FolderName }
func (f *FolderItem) GetSubtitle() string    { 
	trackText := "tracks"
	if len(f.Songs) == 1 {
		trackText = "track"
	}
	return fmt.Sprintf("Folder • %d %s", len(f.Songs), trackText) 
}
func (f *FolderItem) GetSongs() []*Song      { return f.Songs }
func (f *FolderItem) IsFolder() bool         { return true }
func (f *FolderItem) HasArtwork() bool       { 
	for _, song := range f.Songs {
		if song.HasArtwork() {
			return true
		}
	}
	return false
}
func (f *FolderItem) GetArtwork() []byte     { 
	for _, song := range f.Songs {
		if song.HasArtwork() {
			return song.GetArtwork()
		}
	}
	return nil
}

// AlbumItem implementation of DisplayItem interface  
func (a *AlbumItem) GetID() string          { return a.Album.ID.String() }
func (a *AlbumItem) GetName() string        { return a.Album.Name }
func (a *AlbumItem) GetSubtitle() string    { 
	trackText := "tracks"
	if a.Album.TrackCount() == 1 {
		trackText = "track"
	}
	return a.Album.Artist + " • " + fmt.Sprintf("%d %s", a.Album.TrackCount(), trackText)
}
func (a *AlbumItem) GetSongs() []*Song      { return a.Album.Songs }
func (a *AlbumItem) IsFolder() bool         { return false }
func (a *AlbumItem) HasArtwork() bool       { return a.Album.HasArtwork() }
func (a *AlbumItem) GetArtwork() []byte     { return a.Album.GetArtwork() }

// GetArtwork returns the artwork from the first song that has artwork
func (a *Album) GetArtwork() []byte {
	for _, song := range a.Songs {
		if song.HasArtwork() {
			return song.GetArtwork()
		}
	}
	return nil
}

// HasArtwork returns true if any song in the album has artwork
func (a *Album) HasArtwork() bool {
	for _, song := range a.Songs {
		if song.HasArtwork() {
			return true
		}
	}
	return false
}

// MusicLibrary manages the collection of songs and albums (equivalent to MusicLibrary.swift)
type MusicLibrary struct {
	mutex               sync.RWMutex
	Songs               []*Song   `json:"songs"`
	Albums              []*Album  `json:"albums"`
	SelectedFolderPath  string    `json:"selectedFolderPath,omitempty"`
	IsScanning          bool      `json:"isScanning"`
	LibraryVersion      int64     `json:"libraryVersion"`  // NEW: Unix timestamp for version tracking
	versionMutex        sync.RWMutex                       // NEW: Separate mutex for version operations
	watcher             *fsnotify.Watcher
	isWatching          bool
	onScanningChanged   func(bool)
	onLibraryChanged    []func()  // Changed to slice to support multiple callbacks
}

// NewMusicLibrary creates a new music library instance
func NewMusicLibrary() *MusicLibrary {
	return &MusicLibrary{
		Songs:            make([]*Song, 0),
		Albums:           make([]*Album, 0),
		onLibraryChanged: make([]func(), 0),
	}
}

// ShowFolderSelectionDialog opens a folder selection dialog (equivalent to selectFolder() in Swift)
func (ml *MusicLibrary) ShowFolderSelectionDialog(parentWindow fyne.Window) {
	log.Println("📁 [DEBUG] Opening folder selection dialog...")
	
	// Create folder open dialog
	folderDialog := dialog.NewFolderOpen(func(folder fyne.ListableURI, err error) {
		log.Println("📁 [DEBUG] Folder dialog callback triggered")
		
		if err != nil {
			log.Printf("❌ [LIBRARY] Error selecting folder: %v", err)
			return
		}
		
		if folder == nil {
			log.Println("📁 [LIBRARY] No folder selected (user cancelled)")
			return
		}
		
		// Get the folder path
		folderPath := folder.Path()
		log.Printf("📁 [LIBRARY] User selected folder: %s", folderPath)
		
		// Validate the folder path
		if folderPath == "" {
			log.Println("❌ [LIBRARY] Empty folder path received")
			return
		}
		
		// Check if folder exists and is accessible
		if _, err := os.Stat(folderPath); err != nil {
			log.Printf("❌ [LIBRARY] Cannot access selected folder: %v", err)
			return
		}
		
		log.Println("📁 [DEBUG] About to call SelectFolder...")
		
		// Start scanning the selected folder in a goroutine to avoid blocking UI
		go func() {
			defer func() {
				if r := recover(); r != nil {
					log.Printf("🔥 [CRASH] Panic in folder scanning: %v", r)
				}
			}()
			
			ml.SelectFolder(folderPath)
		}()
		
	}, parentWindow)
	
	log.Println("📁 [DEBUG] Configuring dialog...")
	
	// Configure dialog
	folderDialog.Resize(fyne.NewSize(800, 600))        // Reasonable dialog size
	
	log.Println("📁 [DEBUG] Showing dialog...")
	
	// Show the dialog
	folderDialog.Show()
	
	log.Println("📁 [DEBUG] Dialog shown successfully")
}

// SetScanningChangedCallback sets the callback for scanning state changes
func (ml *MusicLibrary) SetScanningChangedCallback(callback func(bool)) {
	ml.mutex.Lock()
	defer ml.mutex.Unlock()
	ml.onScanningChanged = callback
}

// SetLibraryChangedCallback adds a callback for library updates
func (ml *MusicLibrary) SetLibraryChangedCallback(callback func()) {
	ml.mutex.Lock()
	defer ml.mutex.Unlock()
	ml.onLibraryChanged = append(ml.onLibraryChanged, callback)
}

// SelectFolder sets the selected folder path for music scanning
func (ml *MusicLibrary) SelectFolder(folderPath string) {
	log.Printf("📁 [DEBUG] SelectFolder called with: %s", folderPath)
	
	// Stop any existing watcher before changing folders
	ml.StopWatching()
	
	ml.mutex.Lock()
	ml.SelectedFolderPath = folderPath
	ml.mutex.Unlock()
	
	log.Printf("📁 [LIBRARY] Selected folder: %s", folderPath)
	
	// Scan the folder first
	ml.ScanFolder()
	
	// Start watching for new albums after initial scan
	if err := ml.StartWatching(); err != nil {
		log.Printf("❌ [LIBRARY] Failed to start watching folder: %v", err)
	}
}

// ScanFolder scans the selected folder for MP3 files (equivalent to scanFolder() in Swift)
func (ml *MusicLibrary) ScanFolder() {
	log.Println("🔍 [DEBUG] ScanFolder started")
	
	ml.mutex.RLock()
	folderPath := ml.SelectedFolderPath
	ml.mutex.RUnlock()
	
	if folderPath == "" {
		log.Println("❌ [LIBRARY] No folder selected for scanning")
		return
	}
	
	log.Printf("🔍 [DEBUG] About to scan folder: %s", folderPath)
	
	// Set scanning state
	ml.mutex.Lock()
	ml.IsScanning = true
	ml.Songs = make([]*Song, 0)
	ml.Albums = make([]*Album, 0)
	ml.mutex.Unlock()
	
	log.Println("🔍 [DEBUG] Set scanning state to true")
	
	// Notify scanning started
	if ml.onScanningChanged != nil {
		log.Println("🔍 [DEBUG] Calling onScanningChanged(true)")
		ml.onScanningChanged(true)
	}
	
	log.Println("🔍 [LIBRARY] Starting enhanced music library scan...")
	
	// Scan for songs
	var discoveredSongs []*Song
	log.Println("🔍 [DEBUG] About to call scanDirectory")
	err := ml.scanDirectory(folderPath, &discoveredSongs)
	
	log.Printf("🔍 [DEBUG] scanDirectory completed, found %d songs", len(discoveredSongs))
	
	// Handle scanning errors
	if err != nil {
		log.Printf("❌ [LIBRARY] Error scanning folder: %v", err)
		
		// Update state without holding mutex during callback
		ml.mutex.Lock()
		ml.IsScanning = false
		ml.mutex.Unlock()
		
		// Call callback after releasing mutex
		if ml.onScanningChanged != nil {
			ml.onScanningChanged(false)
		}
		return
	}
	
	// Apply enhanced sorting and organization BEFORE acquiring the final lock
	log.Println("🔍 [DEBUG] About to organize and sort songs")
	sortedSongs := ml.organizeAndSortSongs(discoveredSongs)
	
	log.Println("🔍 [DEBUG] About to organize into albums")
	organizedAlbums := ml.organizeIntoAlbums(sortedSongs)
	
	// Now acquire lock only to update the final state
	ml.mutex.Lock()
	ml.Songs = sortedSongs
	ml.Albums = organizedAlbums
	ml.IsScanning = false
	ml.mutex.Unlock()
	
	log.Printf("🔍 [LIBRARY] Scan complete: %d songs in %d albums", len(sortedSongs), len(organizedAlbums))
	ml.printLibraryDebugInfo()
	
	log.Println("🔍 [DEBUG] About to call callbacks")
	
	// Update library version since content has changed
	ml.updateLibraryVersion()
	
	// Call callbacks AFTER releasing the mutex to avoid deadlock
	if ml.onScanningChanged != nil {
		log.Println("🔍 [DEBUG] Calling onScanningChanged(false)")
		ml.onScanningChanged(false)
	}
	if len(ml.onLibraryChanged) > 0 {
		log.Printf("🔍 [DEBUG] Calling %d onLibraryChanged callbacks", len(ml.onLibraryChanged))
		for i, callback := range ml.onLibraryChanged {
			if callback != nil {
				log.Printf("🔍 [DEBUG] Calling onLibraryChanged callback %d", i+1)
				callback()
			}
		}
	}
	
	log.Println("🔍 [DEBUG] ScanFolder completed successfully")
}

// scanDirectory recursively scans a directory for MP3 files (equivalent to scanDirectory in Swift)
func (ml *MusicLibrary) scanDirectory(dirPath string, songs *[]*Song) error {
	entries, err := os.ReadDir(dirPath)
	if err != nil {
		return fmt.Errorf("failed to read directory %s: %w", dirPath, err)
	}
	
	for _, entry := range entries {
		fullPath := filepath.Join(dirPath, entry.Name())
		
		if entry.IsDir() {
			// Recursively scan subdirectories (album folders)
			if err := ml.scanDirectory(fullPath, songs); err != nil {
				log.Printf("⚠️ [LIBRARY] Warning: failed to scan subdirectory %s: %v", fullPath, err)
				continue
			}
		} else if strings.HasSuffix(strings.ToLower(entry.Name()), ".mp3") {
			// Create song from MP3 file
			song, err := NewSongFromFile(fullPath)
			if err != nil {
				log.Printf("⚠️ [LIBRARY] Warning: failed to process MP3 file %s: %v", fullPath, err)
				continue
			}
			*songs = append(*songs, song)
		}
	}
	
	return nil
}

// organizeAndSortSongs applies enhanced sorting with numbered track priority (equivalent to Swift)
func (ml *MusicLibrary) organizeAndSortSongs(songs []*Song) []*Song {
	log.Println("🔍 [LIBRARY] Applying enhanced sorting algorithm...")
	
	// Create a copy to avoid modifying the original slice
	sortedSongs := make([]*Song, len(songs))
	copy(sortedSongs, songs)
	
	// Debug: Log some sample songs before sorting
	if len(sortedSongs) > 0 {
		log.Printf("🔍 [DEBUG] Sample songs before sorting:")
		for i, song := range sortedSongs[:min(3, len(sortedSongs))] {
			log.Printf("🔍 [DEBUG]   %d: %s - %s (Track: %d, Album: %s)", 
				i, song.Artist, song.Title, song.TrackNumber, song.Album)
		}
	}
	
	sort.Slice(sortedSongs, func(i, j int) bool {
		song1, song2 := sortedSongs[i], sortedSongs[j]
		
		// First sort by album
		album1 := song1.Album
		if album1 == "" {
			album1 = song1.InferredAlbum()
		}
		if album1 == "" {
			album1 = "Unknown Album"
		}
		
		album2 := song2.Album
		if album2 == "" {
			album2 = song2.InferredAlbum()
		}
		if album2 == "" {
			album2 = "Unknown Album"
		}
		
		if album1 != album2 {
			return strings.ToLower(album1) < strings.ToLower(album2)
		}
		
		// Within same album, apply numbered track priority
		return ml.compareTracksWithNumberPriority(song1, song2)
	})
	
	// Debug: Log some sample songs after sorting
	if len(sortedSongs) > 0 {
		log.Printf("🔍 [DEBUG] Sample songs after sorting:")
		for i, song := range sortedSongs[:min(5, len(sortedSongs))] {
			log.Printf("🔍 [DEBUG]   %d: %s - %s (Track: %d, Album: %s)", 
				i, song.Artist, song.Title, song.TrackNumber, song.Album)
		}
	}
	
	// Apply deduplication to remove duplicate songs
	log.Println("🔍 [LIBRARY] Applying deduplication...")
	deduplicatedSongs := ml.deduplicate(sortedSongs)
	
	return deduplicatedSongs
}

// compareTracksWithNumberPriority implements lexicographic sorting with numbered track priority (01, 02, 10)
func (ml *MusicLibrary) compareTracksWithNumberPriority(song1, song2 *Song) bool {
	// First priority: Use actual track numbers from ID3 tags if available
	if song1.TrackNumber > 0 && song2.TrackNumber > 0 {
		if song1.TrackNumber != song2.TrackNumber {
			return song1.TrackNumber < song2.TrackNumber
		}
		// If track numbers are same, compare titles
		return strings.ToLower(song1.Title) < strings.ToLower(song2.Title)
	}
	
	// Second priority: One has track number, other doesn't
	if song1.TrackNumber > 0 && song2.TrackNumber == 0 {
		return true // Song with track number comes first
	}
	if song1.TrackNumber == 0 && song2.TrackNumber > 0 {
		return false // Song with track number comes first
	}
	
	// Third priority: Fall back to filename-based track number parsing
	number1 := ml.extractLeadingNumber(song1.Title)
	number2 := ml.extractLeadingNumber(song2.Title)
	
	switch {
	case number1 != nil && number2 != nil:
		// Both have numbers - compare lexicographically (01 comes before 10)
		str1 := fmt.Sprintf("%02d", *number1)
		str2 := fmt.Sprintf("%02d", *number2)
		if str1 != str2 {
			return strings.ToLower(str1) < strings.ToLower(str2)
		}
		// If numbers are same, compare rest of title
		return strings.ToLower(song1.Title) < strings.ToLower(song2.Title)
		
	case number1 != nil && number2 == nil:
		// First has number, second doesn't - numbered comes first
		return true
		
	case number1 == nil && number2 != nil:
		// Second has number, first doesn't - numbered comes first
		return false
		
	default:
		// Neither has numbers - normal alphabetical
		return strings.ToLower(song1.Title) < strings.ToLower(song2.Title)
	}
}

// extractLeadingNumber extracts the leading number from a track title
func (ml *MusicLibrary) extractLeadingNumber(title string) *int {
	pattern := regexp.MustCompile(`^(\d+)`)
	matches := pattern.FindStringSubmatch(title)
	if len(matches) >= 2 {
		if num, err := strconv.Atoi(matches[1]); err == nil {
			return &num
		}
	}
	return nil
}

// deduplicate removes duplicate songs based on metadata (Artist + Title + Album)
func (ml *MusicLibrary) deduplicate(songs []*Song) []*Song {
	seen := make(map[string]bool)
	var uniqueSongs []*Song
	duplicateCount := 0
	
	for _, song := range songs {
		// Create unique key from metadata (case-insensitive)
		artist := strings.ToLower(strings.TrimSpace(song.Artist))
		title := strings.ToLower(strings.TrimSpace(song.Title))
		album := strings.ToLower(strings.TrimSpace(song.Album))
		
		// Handle empty metadata gracefully
		if artist == "" {
			artist = "unknown_artist"
		}
		if title == "" {
			title = "unknown_title"
		}
		if album == "" {
			album = "unknown_album"
		}
		
		key := artist + "|" + title + "|" + album
		
		if !seen[key] {
			seen[key] = true
			uniqueSongs = append(uniqueSongs, song)
		} else {
			duplicateCount++
			log.Printf("🚫 [DEDUP] Skipping duplicate: %s - %s (Album: %s)", song.Artist, song.Title, song.Album)
		}
	}
	
	if duplicateCount > 0 {
		log.Printf("🚫 [DEDUP] Removed %d duplicate songs, kept %d unique songs", duplicateCount, len(uniqueSongs))
	}
	
	return uniqueSongs
}

// organizeIntoAlbums groups songs into albums (equivalent to Swift version)
func (ml *MusicLibrary) organizeIntoAlbums(songs []*Song) []*Album {
	log.Println("🔍 [LIBRARY] Organizing songs into albums...")
	
	// Group songs by album name (ONLY real ID3 album metadata, ignore folder names)
	albumMap := make(map[string][]*Song)
	
	for _, song := range songs {
		albumName := song.Album
		if albumName == "" {
			// Skip songs without real album tags - they'll remain as standalone songs
			continue
		}
		
		albumMap[albumName] = append(albumMap[albumName], song)
	}
	
	// Create Album structs ONLY for groups with 2+ songs
	var albums []*Album
	for albumName, albumSongs := range albumMap {
		if len(albumSongs) < 2 {
			// Skip single songs - they should become folder items instead
			log.Printf("🔍 [LIBRARY] Skipping single song album: %s (%d song)", albumName, len(albumSongs))
			continue
		}
		
		log.Printf("🔍 [LIBRARY] Creating album: %s (%d songs)", albumName, len(albumSongs))
		
		// Determine album artist (use first song's artist)
		var artist string
		if len(albumSongs) > 0 {
			artist = albumSongs[0].Artist
			if artist == "" {
				artist = albumSongs[0].InferredArtist()
			}
		}
		
		album := &Album{
			ID:     uuid.New(),
			Name:   albumName,
			Songs:  albumSongs,
			Artist: artist,
		}
		albums = append(albums, album)
	}
	
	// Sort albums by name
	sort.Slice(albums, func(i, j int) bool {
		return strings.ToLower(albums[i].Name) < strings.ToLower(albums[j].Name)
	})
	
	log.Printf("🔍 [LIBRARY] Created %d albums from %d song groups", len(albums), len(albumMap))
	
	return albums
}

// printLibraryDebugInfo prints debug information about the library organization
func (ml *MusicLibrary) printLibraryDebugInfo() {
	log.Println("🔍 [LIBRARY DEBUG] ===== LIBRARY ORGANIZATION =====")
	
	// Show first 3 albums
	maxAlbums := 3
	if len(ml.Albums) < maxAlbums {
		maxAlbums = len(ml.Albums)
	}
	
	for i := 0; i < maxAlbums; i++ {
		album := ml.Albums[i]
		log.Printf("🔍 [LIBRARY DEBUG] Album: %s (%d songs)", album.Name, album.TrackCount())
		
		// Show first 5 songs per album
		maxSongs := 5
		if len(album.Songs) < maxSongs {
			maxSongs = len(album.Songs)
		}
		
		for j := 0; j < maxSongs; j++ {
			song := album.Songs[j]
			log.Printf("🔍 [LIBRARY DEBUG]   - %s", song.Title)
		}
	}
	
	log.Println("🔍 [LIBRARY DEBUG] ===================================")
}

// GetSongByID finds a song by its UUID (equivalent to getSong(by:) in Swift)
func (ml *MusicLibrary) GetSongByID(id string) *Song {
	ml.mutex.RLock()
	defer ml.mutex.RUnlock()
	
	for _, song := range ml.Songs {
		if song.ID.String() == id {
			return song
		}
	}
	return nil
}

// GetSongCount returns the total number of songs in the library
func (ml *MusicLibrary) GetSongCount() int {
	ml.mutex.RLock()
	defer ml.mutex.RUnlock()
	return len(ml.Songs)
}

// GetAlbumCount returns the total number of albums in the library
func (ml *MusicLibrary) GetAlbumCount() int {
	ml.mutex.RLock()
	defer ml.mutex.RUnlock()
	return len(ml.Albums)
}

// GetSongs returns a copy of all songs (thread-safe)
func (ml *MusicLibrary) GetSongs() []*Song {
	ml.mutex.RLock()
	defer ml.mutex.RUnlock()
	
	songs := make([]*Song, len(ml.Songs))
	copy(songs, ml.Songs)
	return songs
}

// GetAlbums returns a copy of all albums (thread-safe)
func (ml *MusicLibrary) GetAlbums() []*Album {
	ml.mutex.RLock()
	defer ml.mutex.RUnlock()
	
	albums := make([]*Album, len(ml.Albums))
	copy(albums, ml.Albums)
	return albums
}

// IsCurrentlyScanning returns true if the library is currently scanning
func (ml *MusicLibrary) IsCurrentlyScanning() bool {
	ml.mutex.RLock()
	defer ml.mutex.RUnlock()
	return ml.IsScanning
}

// Helper function for min
func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// StartWatching initializes and starts the file system watcher for automatic album detection
func (ml *MusicLibrary) StartWatching() error {
	ml.mutex.Lock()
	defer ml.mutex.Unlock()
	
	// Don't start if already watching
	if ml.isWatching {
		log.Println("👀 [WATCHER] Already watching folder")
		return nil
	}
	
	// Don't start if no folder selected
	if ml.SelectedFolderPath == "" {
		log.Println("👀 [WATCHER] No folder selected, cannot start watching")
		return nil
	}
	
	// Create new watcher
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		log.Printf("❌ [WATCHER] Failed to create watcher: %v", err)
		return err
	}
	
	ml.watcher = watcher
	ml.isWatching = true
	
	// Add the selected folder to watcher
	err = ml.watcher.Add(ml.SelectedFolderPath)
	if err != nil {
		log.Printf("❌ [WATCHER] Failed to watch folder %s: %v", ml.SelectedFolderPath, err)
		ml.watcher.Close()
		ml.watcher = nil
		ml.isWatching = false
		return err
	}
	
	log.Printf("👀 [WATCHER] Started watching folder: %s", ml.SelectedFolderPath)
	
	// Start the event processing goroutine
	go ml.watchEvents()
	
	return nil
}

// StopWatching stops the file system watcher and cleans up resources
func (ml *MusicLibrary) StopWatching() {
	ml.mutex.Lock()
	defer ml.mutex.Unlock()
	
	if !ml.isWatching || ml.watcher == nil {
		return
	}
	
	log.Println("👀 [WATCHER] Stopping file system watcher")
	
	// Close the watcher
	ml.watcher.Close()
	ml.watcher = nil
	ml.isWatching = false
	
	log.Println("👀 [WATCHER] File system watcher stopped")
}

// watchEvents processes file system events in a separate goroutine
func (ml *MusicLibrary) watchEvents() {
	defer func() {
		if r := recover(); r != nil {
			log.Printf("🔥 [WATCHER] Panic in event processing: %v", r)
		}
	}()
	
	for {
		ml.mutex.RLock()
		watcher := ml.watcher
		isWatching := ml.isWatching
		ml.mutex.RUnlock()
		
		if !isWatching || watcher == nil {
			log.Println("👀 [WATCHER] Stopping event loop - watcher closed")
			return
		}
		
		select {
		case event, ok := <-watcher.Events:
			if !ok {
				log.Println("👀 [WATCHER] Events channel closed")
				return
			}
			ml.handleFileSystemEvent(event)
			
		case err, ok := <-watcher.Errors:
			if !ok {
				log.Println("👀 [WATCHER] Errors channel closed")
				return
			}
			log.Printf("❌ [WATCHER] File system error: %v", err)
		}
	}
}

// handleFileSystemEvent processes individual file system events
func (ml *MusicLibrary) handleFileSystemEvent(event fsnotify.Event) {
	log.Printf("👀 [WATCHER] Event: %s %s", event.Op.String(), event.Name)
	
	// Only handle directory creation events
	if event.Op&fsnotify.Create == fsnotify.Create {
		// Check if it's a directory
		if info, err := os.Stat(event.Name); err == nil && info.IsDir() {
			log.Printf("👀 [WATCHER] New directory detected: %s", event.Name)
			
			// Check if the directory contains music files
			if ml.isDirectoryWithMusic(event.Name) {
				log.Printf("🎵 [WATCHER] New album detected: %s", event.Name)
				
				// Trigger a full library rescan
				go func() {
					log.Println("🔍 [WATCHER] Triggering automatic library rescan...")
					ml.ScanFolder()
				}()
			} else {
				log.Printf("📁 [WATCHER] Directory contains no music files: %s", event.Name)
			}
		}
	}
}

// isDirectoryWithMusic checks if a directory contains MP3 files
func (ml *MusicLibrary) isDirectoryWithMusic(dirPath string) bool {
	entries, err := os.ReadDir(dirPath)
	if err != nil {
		log.Printf("❌ [WATCHER] Cannot read directory %s: %v", dirPath, err)
		return false
	}
	
	for _, entry := range entries {
		if !entry.IsDir() {
			filename := strings.ToLower(entry.Name())
			if strings.HasSuffix(filename, ".mp3") {
				log.Printf("🎵 [WATCHER] Found MP3 file: %s", entry.Name())
				return true
			}
		}
	}
	
	log.Printf("📁 [WATCHER] No MP3 files found in: %s", dirPath)
	return false
}

// Library Version Management Methods (NEW for automatic refresh detection)

// GetLibraryVersion returns the current library version timestamp (thread-safe)
func (ml *MusicLibrary) GetLibraryVersion() int64 {
	ml.versionMutex.RLock()
	defer ml.versionMutex.RUnlock()
	return ml.LibraryVersion
}

// updateLibraryVersion sets the library version to current timestamp (called internally)
func (ml *MusicLibrary) updateLibraryVersion() {
	ml.versionMutex.Lock()
	defer ml.versionMutex.Unlock()
	ml.LibraryVersion = time.Now().Unix()
	log.Printf("📊 [VERSION] Library version updated to: %d", ml.LibraryVersion)
}

// GetDisplayItems returns a mixed list of albums and folders for UI display
func (ml *MusicLibrary) GetDisplayItems() []DisplayItem {
	ml.mutex.RLock()
	defer ml.mutex.RUnlock()
	
	var displayItems []DisplayItem
	
	// Add all real albums as AlbumItems
	for _, album := range ml.Albums {
		albumItem := &AlbumItem{Album: album}
		displayItems = append(displayItems, albumItem)
	}
	
	// Group standalone songs by folder and create FolderItems
	folderGroups := make(map[string][]*Song)
	
	// Find songs that are NOT in any album
	albumSongIDs := make(map[string]bool)
	for _, album := range ml.Albums {
		for _, song := range album.Songs {
			albumSongIDs[song.ID.String()] = true
		}
	}
	
	// Group standalone songs by parent directory
	for _, song := range ml.Songs {
		if !albumSongIDs[song.ID.String()] {
			// This song is not in any album, add to folder group
			folderPath := song.ParentDirectory
			folderGroups[folderPath] = append(folderGroups[folderPath], song)
		}
	}
	
	// Create FolderItems for each folder group
	for folderPath, songs := range folderGroups {
		if len(songs) > 0 {
			folderName := filepath.Base(folderPath)
			if folderName == "" || folderName == "." {
				folderName = "Unknown Folder"
			}
			
			folderItem := &FolderItem{
				ID:         folderPath, // Use path as unique ID
				FolderName: folderName,
				FolderPath: folderPath,
				Songs:      songs,
			}
			displayItems = append(displayItems, folderItem)
		}
	}
	
	// Sort display items by name for consistent UI
	sort.Slice(displayItems, func(i, j int) bool {
		return strings.ToLower(displayItems[i].GetName()) < strings.ToLower(displayItems[j].GetName())
	})
	
	log.Printf("🔍 [LIBRARY] Created %d display items (%d albums, %d folders)", 
		len(displayItems), len(ml.Albums), len(folderGroups))
	
	return displayItems
}

// GetDisplayItemCount returns the total number of display items (albums + folders)
func (ml *MusicLibrary) GetDisplayItemCount() int {
	return len(ml.GetDisplayItems())
}

// TODO: Phase 3 Implementation
// - Recursive directory scanning
// - Album organization with folder-based inference
// - Enhanced sorting (numbered track priority: 01, 02, 10)
// - Real-time folder monitoring
// - Metadata extraction and caching 