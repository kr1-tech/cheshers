package app.cheshers.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import app.cheshers.core.data.local.entity.*

@Database(
    entities = [GameEntity::class, ProfileEntity::class, QuestEntity::class,
        AchievementEntity::class, CosmeticEntity::class, AiCacheEntity::class, PuzzleResultEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CheshersDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun profiles(): ProfileDao
    abstract fun quests(): QuestDao
    abstract fun achievements(): AchievementDao
    abstract fun cosmetics(): CosmeticDao
    abstract fun aiCache(): AiCacheDao
    abstract fun puzzleResults(): PuzzleResultDao
}
