package app.cheshers.core.data.repository

import app.cheshers.core.ai.AiCache
import app.cheshers.core.data.local.AiCacheDao
import app.cheshers.core.data.local.entity.AiCacheEntity

class RoomAiCache(private val dao: AiCacheDao) : AiCache {
    override suspend fun get(key: String): String? = dao.get(key, System.currentTimeMillis() - MAX_AGE_MS)
    override suspend fun put(key: String, content: String) {
        dao.expire(System.currentTimeMillis() - MAX_AGE_MS)
        dao.put(AiCacheEntity(key, content, System.currentTimeMillis()))
    }
    private companion object { const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1_000 }
}
