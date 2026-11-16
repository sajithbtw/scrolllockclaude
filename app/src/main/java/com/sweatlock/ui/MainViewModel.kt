package com.sweatlock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sweatlock.blocking.AppInfo
import com.sweatlock.blocking.InstalledAppsProvider
import com.sweatlock.data.AppSettings
import com.sweatlock.data.SettingsRepository
import com.sweatlock.data.Stats
import com.sweatlock.data.StatsRepository
import com.sweatlock.workout.Exercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: SettingsRepository,
    statsRepo: StatsRepository
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val stats = statsRepo.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Stats())

    fun setEnabled(v: Boolean) { viewModelScope.launch { repo.setEnabled(v) } }
    fun setReps(v: Int) { viewModelScope.launch { repo.setReps(v) } }
    fun setDuration(v: Int) { viewModelScope.launch { repo.setDuration(v) } }
    fun setExercise(e: Exercise) {
        viewModelScope.launch { repo.setExercise(e); repo.setReps(e.defaultGoal) }
    }
}

@HiltViewModel
class AppsViewModel @Inject constructor(
    provider: InstalledAppsProvider,
    private val repo: SettingsRepository
) : ViewModel() {
    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    val apps: StateFlow<List<AppInfo>?> = _apps.asStateFlow()
    val blocked = repo.settings.map { it.blocked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init { viewModelScope.launch { _apps.value = provider.load() } }

    fun toggle(pkg: String) { viewModelScope.launch { repo.toggleBlocked(pkg) } }
}
