package app.cheshers.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.cheshers.core.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(game: GameEntity)
    @Query("SELECT * FROM games WHERE id = :id") suspend fun byId(id: String): GameEntity?
    @Query("SELECT * FROM games WHERE active = 1 ORDER BY updatedAt DESC LIMIT 1") suspend fun latestActive(): GameEntity?
    @Query("SELECT * FROM games WHERE active = 1 ORDER BY updatedAt DESC LIMIT 1") fun observeLatestActive(): Flow<GameEntity?>
    @Query("SELECT * FROM games WHERE active = 0 ORDER BY updatedAt DESC") fun observeHistory(): Flow<List<GameEntity>>
    @Query("SELECT * FROM games WHERE active = 0 ORDER BY updatedAt DESC LIMIT :limit") suspend fun recent(limit: Int = 50): List<GameEntity>
    @Query("DELETE FROM games WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE games SET analysisJson = :analysisJson, accuracy = :accuracy WHERE id = :id") suspend fun saveAnalysis(id: String, analysisJson: String, accuracy: Double)
}

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(profile: ProfileEntity)
    @Query("SELECT * FROM profile WHERE id = 1") suspend fun get(): ProfileEntity?
    @Query("SELECT * FROM profile WHERE id = 1") fun observe(): Flow<ProfileEntity?>
}

@Dao
interface QuestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(quests: List<QuestEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(quest: QuestEntity)
    @Query("SELECT * FROM quests WHERE expiresAt > :now ORDER BY period, expiresAt") suspend fun active(now: Long): List<QuestEntity>
    @Query("SELECT * FROM quests WHERE expiresAt > :now ORDER BY period, expiresAt") fun observeActive(now: Long): Flow<List<QuestEntity>>
    @Query("SELECT * FROM quests") suspend fun all(): List<QuestEntity>
    @Query("DELETE FROM quests WHERE expiresAt <= :now") suspend fun deleteExpired(now: Long)
}

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<AchievementEntity>)
    @Query("SELECT * FROM achievements ORDER BY unlockedAt IS NULL, unlockedAt DESC, title") fun observe(): Flow<List<AchievementEntity>>
    @Query("SELECT * FROM achievements") suspend fun all(): List<AchievementEntity>
}

@Dao
interface CosmeticDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<CosmeticEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: CosmeticEntity)
    @Query("SELECT * FROM cosmetics ORDER BY type, cost") fun observe(): Flow<List<CosmeticEntity>>
    @Query("SELECT * FROM cosmetics WHERE id = :id") suspend fun byId(id: String): CosmeticEntity?
    @Query("SELECT * FROM cosmetics") suspend fun all(): List<CosmeticEntity>
}

@Dao
interface AiCacheDao {
    @Query("SELECT content FROM ai_cache WHERE key = :key AND createdAt > :expiresAfter") suspend fun get(key: String, expiresAfter: Long): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(item: AiCacheEntity)
    @Query("DELETE FROM ai_cache WHERE createdAt <= :expiresAfter") suspend fun expire(expiresAfter: Long)
}

@Dao
interface PuzzleResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(result: PuzzleResultEntity)
    @Query("SELECT * FROM puzzle_results WHERE puzzleId = :id") suspend fun byId(id: String): PuzzleResultEntity?
    @Query("SELECT puzzleId FROM puzzle_results") fun observeSolvedIds(): Flow<List<String>>
}
