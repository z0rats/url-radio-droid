package com.freqcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freqcast.R
import com.freqcast.data.RadioStation
import com.freqcast.data.RadioStationRepository
import com.freqcast.util.IconStorage
import com.freqcast.util.StreamValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class AddStationUiState(
    val name: String = "",
    val url: String = "",
    val customIcon: String? = null,
    val genre: String = "",
    val nameErrorRes: Int? = null,
    val urlErrorRes: Int? = null,
    val isSaving: Boolean = false,
    val isEditing: Boolean = false,
    /**
     * Carried through unedited from the loaded station (this form has no way to reorder) so
     * save()'s full-row @Update doesn't reset the station's manual list position back to 0. Only
     * meaningful when editing — a new station's sortOrder is always assigned by
     * RadioStationRepository.insertStation() regardless of this field's value.
     */
    val sortOrder: Int = 0,
    /** Carried through unedited from the loaded station (this form has no HLS toggle) so save() doesn't clear it. */
    val isHls: Boolean = false,
    /** Carried through unedited from the loaded station (this form has no way to set it) so save() doesn't clear it. */
    val radioBrowserUuid: String? = null,
)

sealed interface AddStationEvent {
    data class SaveSucceeded(
        val wasEditing: Boolean,
    ) : AddStationEvent

    data class SaveFailed(
        val message: String?,
    ) : AddStationEvent
}

class AddStationViewModel(
    private val repository: RadioStationRepository,
    private val editingStationId: Long?,
    private val streamValidator: StreamValidator = StreamValidator(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddStationUiState(isEditing = editingStationId != null))
    val uiState: StateFlow<AddStationUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<AddStationEvent>(Channel.BUFFERED)
    val events: Flow<AddStationEvent> = eventChannel.receiveAsFlow()

    /** The icon the station had in the DB before this edit session, for cleanup once a replacement is saved. */
    private var originalCustomIcon: String? = null

    init {
        editingStationId?.let { id ->
            viewModelScope.launch {
                repository.getStationById(id)?.let { station ->
                    originalCustomIcon = station.customIcon
                    _uiState.value =
                        _uiState.value.copy(
                            name = station.name,
                            url = station.streamUrl,
                            customIcon = station.customIcon,
                            genre = station.genre.orEmpty(),
                            sortOrder = station.sortOrder,
                            isHls = station.isHls,
                            radioBrowserUuid = station.radioBrowserUuid,
                        )
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value, nameErrorRes = null)
    }

    fun onUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(url = value, urlErrorRes = null)
    }

    fun onGenreChange(value: String) {
        _uiState.value = _uiState.value.copy(genre = value)
    }

    fun onEmojiIconSelected(emoji: String) {
        _uiState.value = _uiState.value.copy(customIcon = emoji)
    }

    fun onImageIconSelected(path: String) {
        _uiState.value = _uiState.value.copy(customIcon = path)
    }

    fun onRemoveIcon() {
        _uiState.value = _uiState.value.copy(customIcon = null)
    }

    fun save() {
        val nameTrimmed = _uiState.value.name.trim()
        val urlTrimmed = _uiState.value.url.trim()

        when {
            nameTrimmed.isEmpty() -> {
                _uiState.value = _uiState.value.copy(nameErrorRes = R.string.enter_name)
                return
            }

            urlTrimmed.isEmpty() -> {
                _uiState.value = _uiState.value.copy(urlErrorRes = R.string.enter_url)
                return
            }

            !AddStationActivity.isValidUrl(urlTrimmed) -> {
                _uiState.value = _uiState.value.copy(urlErrorRes = R.string.error_invalid_url)
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                val excludeId = editingStationId ?: 0L
                val nameTaken = repository.isNameTaken(nameTrimmed, excludeId)
                val urlTaken = repository.isUrlTaken(urlTrimmed, excludeId)

                when {
                    nameTaken -> {
                        _uiState.value =
                            _uiState.value.copy(isSaving = false, nameErrorRes = R.string.error_duplicate_name)
                    }

                    urlTaken -> {
                        _uiState.value =
                            _uiState.value.copy(isSaving = false, urlErrorRes = R.string.error_duplicate_url)
                    }

                    !streamValidator.isReachable(urlTrimmed) -> {
                        _uiState.value =
                            _uiState.value.copy(isSaving = false, urlErrorRes = R.string.error_stream_unreachable)
                    }

                    else -> {
                        val id = editingStationId
                        val finalIcon = _uiState.value.customIcon
                        val finalGenre =
                            _uiState.value.genre
                                .trim()
                                .ifBlank { null }
                        val station =
                            if (id != null) {
                                RadioStation(
                                    id = id,
                                    name = nameTrimmed,
                                    streamUrl = urlTrimmed,
                                    customIcon = finalIcon,
                                    sortOrder = _uiState.value.sortOrder,
                                    genre = finalGenre,
                                    isHls = _uiState.value.isHls,
                                    radioBrowserUuid = _uiState.value.radioBrowserUuid,
                                )
                            } else {
                                RadioStation(
                                    name = nameTrimmed,
                                    streamUrl = urlTrimmed,
                                    customIcon = finalIcon,
                                    genre = finalGenre,
                                )
                            }
                        if (id != null) {
                            repository.updateStation(station)
                        } else {
                            repository.insertStation(station)
                        }
                        if (originalCustomIcon != null && originalCustomIcon != finalIcon) {
                            IconStorage.delete(originalCustomIcon)
                        }
                        _uiState.value = _uiState.value.copy(isSaving = false)
                        eventChannel.send(AddStationEvent.SaveSucceeded(wasEditing = id != null))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false)
                eventChannel.send(AddStationEvent.SaveFailed(e.message))
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: RadioStationRepository,
            editingStationId: Long?,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AddStationViewModel(repository, editingStationId) as T
            }
    }
}
