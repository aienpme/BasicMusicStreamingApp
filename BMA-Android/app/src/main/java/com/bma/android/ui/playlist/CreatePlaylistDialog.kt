package com.bma.android.ui.playlist

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bma.android.adapters.SongSelectionAdapter
import com.bma.android.databinding.DialogCreatePlaylistBinding
import com.bma.android.models.Song
import com.bma.android.storage.PlaylistManager
import kotlinx.coroutines.launch

/**
 * Spotify-style playlist creation dialog
 */
class CreatePlaylistDialog : DialogFragment() {

    private var _binding: DialogCreatePlaylistBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var songSelectionAdapter: SongSelectionAdapter
    private var allSongs = listOf<Song>()
    private var onPlaylistCreated: ((playlistId: String) -> Unit)? = null
    
    companion object {
        private const val ARG_SONGS = "songs"
        
        fun newInstance(songs: List<Song>): CreatePlaylistDialog {
            val dialog = CreatePlaylistDialog()
            val args = Bundle()
            args.putSerializable(ARG_SONGS, ArrayList(songs))
            dialog.arguments = args
            return dialog
        }
    }
    
    fun setOnPlaylistCreatedListener(listener: (String) -> Unit) {
        onPlaylistCreated = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreatePlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get songs from arguments
        @Suppress("UNCHECKED_CAST")
        allSongs = arguments?.getSerializable(ARG_SONGS) as? List<Song> ?: emptyList()
        
        setupRecyclerView()  // Setup adapter first
        setupViews()         // Then setup views (which calls updateDoneButtonState)
        setupTextWatchers()
    }
    
    override fun onStart() {
        super.onStart()
        // Make dialog full screen
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun setupViews() {
        // Cancel button
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        
        // Done button
        binding.doneButton.setOnClickListener {
            createPlaylist()
        }
        
        // Initially disable done button
        updateDoneButtonState()
    }

    private fun setupRecyclerView() {
        songSelectionAdapter = SongSelectionAdapter { selectedSongIds ->
            updateDoneButtonState()
        }
        
        binding.songsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songSelectionAdapter
        }
        
        songSelectionAdapter.updateSongs(allSongs)
    }

    private fun setupTextWatchers() {
        // Playlist name input
        binding.playlistNameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateDoneButtonState()
            }
        })
        
        // Search input
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                songSelectionAdapter.filterSongs(query)
            }
        })
    }

    private fun updateDoneButtonState() {
        val hasName = binding.playlistNameInput.text?.isNotBlank() == true
        val hasSelectedSongs = songSelectionAdapter.getSelectedSongIds().isNotEmpty()
        
        binding.doneButton.isEnabled = hasName && hasSelectedSongs
        binding.doneButton.alpha = if (binding.doneButton.isEnabled) 1.0f else 0.5f
    }

    private fun createPlaylist() {
        val playlistName = binding.playlistNameInput.text?.toString()?.trim() ?: ""
        val selectedSongIds = songSelectionAdapter.getSelectedSongIds().toList()
        
        if (playlistName.isBlank()) {
            Toast.makeText(requireContext(), "Please enter a playlist name", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedSongIds.isEmpty()) {
            Toast.makeText(requireContext(), "Please select at least one song", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Disable UI while creating
        binding.doneButton.isEnabled = false
        binding.doneButton.text = "Creating..."
        
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                val createdPlaylist = playlistManager.createPlaylist(playlistName, selectedSongIds)
                
                
                onPlaylistCreated?.invoke(createdPlaylist.id)
                dismiss()
                
            } catch (e: Exception) {
                android.util.Log.e("CreatePlaylistDialog", "Error creating playlist", e)
                Toast.makeText(
                    requireContext(), 
                    "Failed to create playlist: ${e.message}", 
                    Toast.LENGTH_LONG
                ).show()
                
                // Re-enable UI
                binding.doneButton.isEnabled = true
                binding.doneButton.text = "Done"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}