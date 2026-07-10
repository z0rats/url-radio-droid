package com.urlradiodroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.data.RadioStationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlarmViewModel(
    private val repository: RadioStationRepository,
) : ViewModel() {
    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    fun loadStations() {
        viewModelScope.launch {
            _stations.value = repository.getAllStations()
        }
    }

    companion object {
        fun provideFactory(repository: RadioStationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AlarmViewModel(repository) as T
            }
    }
}
