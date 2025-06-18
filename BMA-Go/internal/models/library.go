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

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/dialog"
	"github.com/google/uuid"
)

// Album represents a collection of songs grouped by album name
type Album struct {
	ID     uuid.UUID `json:"id"`
	Name   string    `json:"name"`
	Songs  []*Song   `json:"songs"`
	Artist string    `json:"artist,omitempty"`
}

// TrackCount returns the number of songs in the album
func (a *Album) TrackCount() int {
	return len(a.Songs)
}

// MusicLibrary manages the collection of songs and albums (equivalent to MusicLibrary.swift)
type MusicLibrary struct {
	mutex               sync.RWMutex
	Songs               []*Song   `json:"songs"`
	Albums              []*Album  `json:"albums"`
	SelectedFolderPath  string    `json:"selectedFolderPath,omitempty"`
	IsScanning          bool      `json:"isScanning"`
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
	
	ml.mutex.Lock()
	ml.SelectedFolderPath = folderPath
	ml.mutex.Unlock()
	
	log.Printf("📁 [LIBRARY] Selected folder: %s", folderPath)
	ml.ScanFolder()
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
	
	return sortedSongs
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

// organizeIntoAlbums groups songs into albums (equivalent to Swift version)
func (ml *MusicLibrary) organizeIntoAlbums(songs []*Song) []*Album {
	log.Println("🔍 [LIBRARY] Organizing songs into albums...")
	
	// Group songs by album name
	albumMap := make(map[string][]*Song)
	
	for _, song := range songs {
		albumName := song.Album
		if albumName == "" {
			albumName = song.InferredAlbum()
		}
		if albumName == "" {
			albumName = "Unknown Album"
		}
		
		albumMap[albumName] = append(albumMap[albumName], song)
	}
	
	// Create Album structs
	var albums []*Album
	for albumName, albumSongs := range albumMap {
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

// TODO: Phase 3 Implementation
// - Recursive directory scanning
// - Album organization with folder-based inference
// - Enhanced sorting (numbered track priority: 01, 02, 10)
// - Real-time folder monitoring
// - Metadata extraction and caching 