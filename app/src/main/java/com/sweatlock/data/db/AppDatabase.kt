package com.sweatlock.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [WorkoutLog::class, UnlockEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun unlockDao(): UnlockDao
}
