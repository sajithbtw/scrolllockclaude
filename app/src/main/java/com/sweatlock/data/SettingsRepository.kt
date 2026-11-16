package com.sweatlock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sweatlock.workout.Exercise
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sweatlock_settings")

data class AppSettings(
    val reps: Int = 10,
    val durationMin: Int = 15,
    val exercise: Exercise = Exercise.PUSHUP,
    val blocked: Set<String> = emptySet(),
    val enabled: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object K {
        val REPS = intPreferencesKey("reps")
        val DURATION = intPreferencesKey("duration_min")
        val EXERCISE = stringPreferencesKey("exercise")
        val BLOCKED = stringSetPreferencesKey("blocked")
        val ENABLED = booleanPreferencesKey("enabled")
        val EM_DAY = longPreferencesKey("emergency_day")
        val EM_USED = intPreferencesKey("emergency_used")
    }

    val settings: Flow<AppSettings> = ctx.dataStore.data.map { p ->
        AppSettings(
            reps = p[K.REPS] ?: 10,
            durationMin = p[K.DURATION] ?: 15,
            exercise = runCatching { Exercise.valueOf(p[K.EXERCISE] ?: "") }.getOrDefault(Exercise.PUSHUP),
            blocked = p[K.BLOCKED] ?: emptySet(),
            enabled = p[K.ENABLED] ?: true
        )
    }

    suspend fun setReps(v: Int) { ctx.dataStore.edit { it[K.REPS] = v } }
    suspend fun setDuration(v: Int) { ctx.dataStore.edit { it[K.DURATION] = v } }
    suspend fun setExercise(e: Exercise) { ctx.dataStore.edit { it[K.EXERCISE] = e.name } }
    suspend fun setEnabled(v: Boolean) { ctx.dataStore.edit { it[K.ENABLED] = v } }
    suspend fun toggleBlocked(pkg: String) {
        ctx.dataStore.edit {
            val cur = it[K.BLOCKED] ?: emptySet()
            it[K.BLOCKED] = if (pkg in cur) cur - pkg else cur + pkg
        }
    }

    // ---- emergency passes: a safety valve so a broken camera can never lock you out of your own phone ----
    suspend fun emergencyLeft(): Int {
        val p = ctx.dataStore.data.first()
        val used = if (p[K.EM_DAY] == LocalDate.now().toEpochDay()) p[K.EM_USED] ?: 0 else 0
        return (MAX_EMERGENCY - used).coerceAtLeast(0)
    }

    /** Returns true and uses up one pass if any are left today. */
    suspend fun consumeEmergency(): Boolean {
        var ok = false
        ctx.dataStore.edit { p ->
            val today = LocalDate.now().toEpochDay()
            val used = if (p[K.EM_DAY] == today) p[K.EM_USED] ?: 0 else 0
            if (used < MAX_EMERGENCY) { p[K.EM_DAY] = today; p[K.EM_USED] = used + 1; ok = true }
        }
        return ok
    }

    companion object {
        const val MAX_EMERGENCY = 3
        const val EMERGENCY_MIN = 5
    }
}
