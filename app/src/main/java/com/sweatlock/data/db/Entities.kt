package com.sweatlock.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per batch of reps. `day` = LocalDate.toEpochDay(). */
@Entity(tableName = "workout_log")
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val reps: Int
)

/** Per-app unlock expiry (epoch millis). Survives process death. */
@Entity(tableName = "unlocks")
data class UnlockEntity(
    @PrimaryKey val pkg: String,
    val expiresAt: Long
)

data class DayReps(val day: Long, val reps: Int)
