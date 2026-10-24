package app.cheshers.core.ai

import app.cheshers.core.chess.ChessGame
import app.cheshers.core.chess.Difficulty
import app.cheshers.core.chess.Personality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private class FakeClient(vararg val replies: String) : TextAiClient {
    val requests = mutableListOf<String>()
    override suspend fun complete(apiKey: String, model: String, systemPrompt: String, userPrompt: String, temperature: Double, maxTokens: Int): String {
        requests += userPrompt
        return replies.getOrElse(requests.lastIndex) { replies.last() }
    }
}

private class MemoryCache : AiCache {
    val values = mutableMapOf<String, String>()
    override suspend fun get(key: String): String? = values[key]
    override suspend fun put(key: String, content: String) { values[key] = content }
}

class OpenRouterAiEngineTest {
    private val game = ChessGame()
    private fun request(clientKey: String = "test", purpose: MovePurpose = MovePurpose.BOT_MOVE) = MoveProposalRequest(
        game.fen, game.legalMoves(), Difficulty.INTERMEDIATE, Personality.BALANCED,
        "test-model", clientKey, purpose,
    )

    @Test fun validJsonMoveIsAppliedOnlyAfterLocalValidation() = runTest {
        val engine = OpenRouterAiEngine(FakeClient("```json\n{\"move\":\"e2e4\",\"reasoning\":\"Center\"}\n```"), MemoryCache(), backoff = {})
        val result = engine.proposeMove(request())
        assertEquals("e2e4", result.move?.uci)
        assertEquals(AiSource.OPENROUTER, result.source)
        assertTrue(game.isLegal(result.move!!))
    }

    @Test fun illegalResponsesRetryThreeTimesThenUseLegalFallback() = runTest {
        val client = FakeClient("{\"move\":\"e2e5\"}")
        val engine = OpenRouterAiEngine(client, MemoryCache(), Random(4), backoff = {})
        val result = engine.proposeMove(request())
        assertEquals(3, client.requests.size)
        assertEquals(3, result.attempts)
        assertEquals(AiSource.FALLBACK_INVALID, result.source)
        assertNotNull(result.move)
        assertTrue(game.isLegal(result.move!!))
    }

    @Test fun missingKeyDoesNotCallNetworkAndNeverStallsBot() = runTest {
        val client = FakeClient("{\"move\":\"e2e4\"}")
        val result = OpenRouterAiEngine(client, MemoryCache(), Random(2), backoff = {}).proposeMove(request(""))
        assertTrue(client.requests.isEmpty())
        assertEquals(AiSource.FALLBACK_NO_KEY, result.source)
        assertNotNull(result.move)
    }

    @Test fun repeatPositionUsesCacheWithoutAnotherCall() = runTest {
        val client = FakeClient("{\"move\":\"d2d4\",\"reasoning\":\"cached\"}")
        val cache = MemoryCache()
        val engine = OpenRouterAiEngine(client, cache, backoff = {})
        engine.proposeMove(request())
        val second = engine.proposeMove(request())
        assertEquals(1, client.requests.size)
        assertEquals(AiSource.CACHE, second.source)
        assertEquals("d2d4", second.move?.uci)
    }

    @Test fun hintFailureDoesNotPretendRandomMoveIsAHint() = runTest {
        val client = FakeClient("not json")
        val result = OpenRouterAiEngine(client, MemoryCache(), backoff = {}).proposeMove(request(purpose = MovePurpose.HINT))
        assertNull(result.move)
        assertEquals(AiSource.FALLBACK_INVALID, result.source)
    }

    @Test fun analysisRejectsOutOfRangeAnnotations() = runTest {
        val client = FakeClient("{\"accuracy\":82.5,\"summary\":\"Good game\",\"coach\":\"Watch tactics\",\"annotations\":[{\"ply\":1,\"severity\":\"BLUNDER\",\"comment\":\"Test\"},{\"ply\":999,\"severity\":\"GOOD\",\"comment\":\"Invalid\"}]}")
        val result = OpenRouterAiEngine(client, MemoryCache(), backoff = {}).analyzeGame("key", "model", game.toPgn(), 10)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().annotations.size)
        assertEquals(82.5, result.getOrThrow().accuracy, 0.01)
    }
}
