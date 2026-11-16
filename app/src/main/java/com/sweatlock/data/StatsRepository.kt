package com.sweatlock.data

import com.sweatlock.data.db.WorkoutDao
import com.sweatlock.data.db.WorkoutLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class Stats(
    val today: Int = 0,
    val streak: Int = 0,
    val total: Int = 0,
    val week: List<Pair<Long, Int>> = emptyList() // (epochDay, reps), oldest -> today
)

/** Pure + testable: consecutive days with reps, ending today (or yesterday). */
fun computeStreak(days: List<Long>, today: Long): Int {
    val set = days.toSet()
    var d = if (today in set) today else today - 1
    var n = 0
    while (d in set) { n++; d-- }
    return n
}

@Singleton
class StatsRepository @Inject constructor(private val dao: WorkoutDao) {
    suspend fun addReps(n: Int) {
        dao.insert(WorkoutLog(day = LocalDate.now().toEpochDay(), reps = n))
    }

    val stats: Flow<Stats> = combine(dao.days(), dao.total(), dao.last7()) { days, total, last7 ->
        val today = LocalDate.now().toEpochDay()
        val map = last7.associate { it.day to it.reps }
        Stats(
            today = map[today] ?: 0,
            streak = computeStreak(days, today),
            total = total,
            week = (6 downTo 0).map { off -> (today - off) to (map[today - off] ?: 0) }
        )
    }
}
