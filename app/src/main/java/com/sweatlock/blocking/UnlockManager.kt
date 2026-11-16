package com.sweatlock.blocking

import com.sweatlock.data.db.UnlockDao
import com.sweatlock.data.db.UnlockEntity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Per-app unlock timers. In-memory cache (fast, used every 1.5s) + Room (survives process death). */
@Singleton
class UnlockManager @Inject constructor(private val dao: UnlockDao) {
    private val cache = ConcurrentHashMap<String, Long>()

    suspend fun load() { dao.all().forEach { cache[it.pkg] = it.expiresAt } }

    fun isUnlocked(pkg: String): Boolean = (cache[pkg] ?: 0L) > System.currentTimeMillis()

    suspend fun unlock(pkg: String, minutes: Int) {
        val expires = System.currentTimeMillis() + minutes * 60_000L
        cache[pkg] = expires              // set synchronously so the service sees it immediately
        dao.upsert(UnlockEntity(pkg, expires))
    }
}
