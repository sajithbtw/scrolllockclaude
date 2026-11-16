package com.sweatlock.di

import android.content.Context
import androidx.room.Room
import com.sweatlock.data.db.AppDatabase
import com.sweatlock.data.db.UnlockDao
import com.sweatlock.data.db.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext c: Context): AppDatabase =
        Room.databaseBuilder(c, AppDatabase::class.java, "sweatlock.db").build()

    @Provides fun workoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun unlockDao(db: AppDatabase): UnlockDao = db.unlockDao()
}
