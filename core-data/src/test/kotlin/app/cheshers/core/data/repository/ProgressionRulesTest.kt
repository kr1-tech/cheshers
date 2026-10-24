package app.cheshers.core.data.repository

import app.cheshers.core.chess.Elo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionRulesTest {
    @Test fun levelsHaveIncreasingThresholds() {
        assertEquals(1, ProgressionRules.levelForXp(0))
        assertEquals(1, ProgressionRules.levelForXp(99))
        assertEquals(2, ProgressionRules.levelForXp(100))
        assertEquals(3, ProgressionRules.levelForXp(400))
        assertTrue(ProgressionRules.nextLevelXp(4) > ProgressionRules.nextLevelXp(3))
    }

    @Test fun rewardsNeverDependOnRealMoney() {
        assertEquals(50, ProgressionRules.gameXp(GameOutcome.WIN, online = false))
        assertEquals(60, ProgressionRules.gameXp(GameOutcome.WIN, online = true))
        assertEquals(10, ProgressionRules.gameXp(GameOutcome.LOSS, online = false))
    }

    @Test fun eloIsSymmetricForEqualRatedDraw() {
        assertEquals(1200, Elo.updatedRating(1200, 1200, 0.5, gamesPlayed = 100))
        assertTrue(Elo.updatedRating(1200, 1200, 1.0, 100) > 1200)
        assertTrue(Elo.updatedRating(1200, 1200, 0.0, 100) < 1200)
    }
}
