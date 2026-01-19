package com.urlradiodroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.urlradiodroid.data.AppDatabase
import com.urlradiodroid.data.RadioStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainViewModel(private val database: AppDatabase) : ViewModel() {
    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentPlayingStationId = MutableStateFlow<Long?>(null)
    val currentPlayingStationId: StateFlow<Long?> = _currentPlayingStationId.asStateFlow()

    val filteredStations = combine(_stations, _searchQuery) { stations, query ->
        if (query.isBlank()) {
            stations
        } else {
            val queryLower = query.lowercase().trim()
            stations.filter { station ->
                station.name.lowercase().contains(queryLower) ||
                station.streamUrl.lowercase() == queryLower
            }
        }
    }

    init {
        loadStations()
    }

    fun loadStations() {
        viewModelScope.launch {
            _stations.value = database.radioStationDao().getAllStations()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCurrentPlayingStation(stationId: Long?) {
        _currentPlayingStationId.value = stationId
    }

    fun deleteStation(stationId: Long) {
        viewModelScope.launch {
            database.radioStationDao().deleteStation(stationId)
            loadStations()
        }
    }

    companion object {
        fun provideFactory(database: AppDatabase): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(database) as T
                }
            }
        }
    }
}
