package com.sweatlock.workout

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sweatlock.blocking.UnlockManager
import com.sweatlock.data.SettingsRepository
import com.sweatlock.data.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutUiState(
    val exercise: Exercise = Exercise.PUSHUP,
    val goal: Int = 10,
    val durationMin: Int = 15,
    val state: RepState = RepState(0, RepPhase.UP, 180f, false),
    val landmarks: Map<Int, Landmark> = emptyMap(),
    val motion: Float = 0f,
    val completed: Boolean = false,
    val emergencyLeft: Int = SettingsRepository.MAX_EMERGENCY,
    val emergencyMsg: String? = null
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val stats: StatsRepository,
    private val unlocks: UnlockManager
) : ViewModel() {

    private val lock = Any()
    private var counter = RepCounter(Exercise.PUSHUP)
    private var pages = PageTurnDetector()
    private var lockedPkg: String? = null
    private var logged = 0
    private var finished = false

    private val _ui = MutableStateFlow(WorkoutUiState())
    val ui: StateFlow<WorkoutUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            synchronized(lock) {
                counter = RepCounter(s.exercise); pages = PageTurnDetector()
                _ui.update { WorkoutUiState(exercise = s.exercise, goal = s.reps, durationMin = s.durationMin) }
            }
            val left = settingsRepo.emergencyLeft()
            _ui.update { it.copy(emergencyLeft = left) }
        }
    }

    fun setLockedPackage(pkg: String?) { lockedPkg = pkg }

    /** Safety valve (hold-to-confirm in the UI): 3 passes per day, each unlocks for 5 minutes. */
    fun useEmergency() {
        val pkg = lockedPkg ?: return
        viewModelScope.launch {
            if (settingsRepo.consumeEmergency()) {
                unlocks.unlock(pkg, SettingsRepository.EMERGENCY_MIN)
                synchronized(lock) { finished = true }
                val left = settingsRepo.emergencyLeft()
                _ui.update { it.copy(completed = true, durationMin = SettingsRepository.EMERGENCY_MIN, emergencyLeft = left) }
            } else {
                _ui.update { it.copy(emergencyLeft = 0, emergencyMsg = "No emergency passes left today") }
            }
        }
    }

    /** Pose exercises: called with ML Kit landmarks. */
    fun onLandmarks(lm: Map<Int, Landmark>) {
        synchronized(lock) {
            if (_ui.value.exercise.isStudy) return
            if (finished) { _ui.update { it.copy(landmarks = lm) }; return }
            handle(counter.update(lm, SystemClock.elapsedRealtime()), lm, 0f)
        }
    }

    /** Study mode: called with a tiny brightness grid of each camera frame. */
    fun onGrid(grid: FloatArray) {
        synchronized(lock) {
            if (!_ui.value.exercise.isStudy || finished) return
            val p = pages.update(grid, SystemClock.elapsedRealtime())
            val s = RepState(p.count, if (p.moving) RepPhase.DOWN else RepPhase.UP, 0f, true, p.rejected)
            handle(s, emptyMap(), p.motion)
        }
    }

    private fun handle(s: RepState, lm: Map<Int, Landmark>, motion: Float) {
        val delta = s.reps - logged
        if (delta > 0) { logged = s.reps; viewModelScope.launch { stats.addReps(delta) } }

        val done = s.reps >= _ui.value.goal
        if (done) {
            finished = true
            lockedPkg?.let { pkg -> viewModelScope.launch { unlocks.unlock(pkg, _ui.value.durationMin) } }
        }
        _ui.update { it.copy(state = s, landmarks = lm, motion = motion, completed = done) }
    }

    fun setExercise(e: Exercise) {
        synchronized(lock) {
            counter = RepCounter(e); pages = PageTurnDetector(); logged = 0; finished = false
            _ui.update {
                it.copy(
                    exercise = e, goal = e.defaultGoal,
                    state = RepState(0, RepPhase.UP, 180f, false),
                    landmarks = emptyMap(), motion = 0f, completed = false
                )
            }
        }
    }

    fun restart() {
        synchronized(lock) {
            val e = _ui.value.exercise
            counter = RepCounter(e); pages = PageTurnDetector(); logged = 0; finished = false
            _ui.update {
                it.copy(state = RepState(0, RepPhase.UP, 180f, false), landmarks = emptyMap(), motion = 0f, completed = false)
            }
        }
    }
}
