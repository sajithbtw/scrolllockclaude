package com.sweatlock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert suspend fun insert(log: WorkoutLog)

    @Query("SELECT DISTINCT day FROM workout_log ORDER BY day DESC")
    fun days(): Flow<List<Long>>

    @Query("SELECT COALESCE(SUM(reps), 0) FROM workout_log")
    fun total(): Flow<Int>

    @Query("SELECT day AS day, SUM(reps) AS reps FROM workout_log GROUP BY day ORDER BY day DESC LIMIT 7")
    fun last7(): Flow<List<DayReps>>
}

@Dao
interface UnlockDao {
    @Upsert suspend fun upsert(e: UnlockEntity)
    @Query("SELECT * FROM unlocks") suspend fun all(): List<UnlockEntity>
}
