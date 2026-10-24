package app.cheshers.core.ai

import app.cheshers.core.chess.ChessMove
import app.cheshers.core.chess.Difficulty
import app.cheshers.core.chess.Personality
import app.cheshers.core.chess.randomLegalMove
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.security.MessageDigest
import kotlin.math.min
import kotlin.random.Random

/**
 * Validates every model proposal locally. Three attempts are allowed in total;
 * bot play then falls back to a random legal move. Hints/analysis never fabricate
 * a response when the provider is unavailable.
 */
class OpenRouterAiEngine(
    private val client: TextAiClient,
    private val cache: AiCache,
    private val random: Random = Random.Default,
    private val backoff: suspend (attempt: Int) -> Unit = { attempt ->
        delay(min(2_000L, 250L * (1L shl (attempt - 1))))
    },
) {
    private val gson = Gson()

    suspend fun proposeMove(request: MoveProposalRequest): AiMoveResult {
        if (request.legalMoves.isEmpty()) {
            return AiMoveResult(null, "No legal moves", AiSource.NO_LEGAL_MOVES, 0)
        }
        val cacheKey = cacheKey(
            "move:${request.purpose}", request.fen, request.model,
            request.difficulty.name, request.personality.name, request.puzzleObjective.orEmpty(),
        )
        readCachedMove(cacheKey, request.legalMoves)?.let { cached ->
            return cached.copy(source = AiSource.CACHE, attempts = 0)
        }
        if (request.apiKey.isBlank()) {
            return moveFallback(request, AiSource.FALLBACK_NO_KEY, "Add an OpenRouter key to enable AI", 0)
        }

        var lastSource = AiSource.FALLBACK_NETWORK
        repeat(MAX_ATTEMPTS) { index ->
            val attempt = index + 1
            try {
                val content = client.complete(
                    apiKey = request.apiKey,
                    model = request.model,
                    systemPrompt = moveSystemPrompt(request),
                    userPrompt = moveUserPrompt(request, attempt),
                    temperature = temperatureFor(request.difficulty),
                    maxTokens = if (request.difficulty == Difficulty.MASTER) 700 else 400,
                )
                val parsed = parseMoveContent(content, request.legalMoves)
                if (parsed != null) {
                    cache.put(cacheKey, content)
                    return parsed.copy(source = AiSource.OPENROUTER, attempts = attempt)
                }
                lastSource = AiSource.FALLBACK_INVALID
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                lastSource = AiSource.FALLBACK_NETWORK
            }
            if (attempt < MAX_ATTEMPTS) backoff(attempt)
        }
        return moveFallback(
            request,
            lastSource,
            if (lastSource == AiSource.FALLBACK_INVALID) "AI returned invalid moves" else "OpenRouter is unavailable",
            MAX_ATTEMPTS,
        )
    }

    suspend fun analyzeGame(
        apiKey: String,
        model: String,
        pgn: String,
        maxPly: Int,
    ): Result<GameAnalysis> = runCatching {
        if (apiKey.isBlank()) throw AiUnavailableException("Add an OpenRouter key to enable game review")
        val key = cacheKey("analysis", model, pgn)
        val content = cache.get(key) ?: run {
            var last: Exception? = null
            repeat(MAX_ATTEMPTS) { index ->
                try {
                    val produced = client.complete(
                        apiKey, model, ANALYSIS_SYSTEM_PROMPT,
                        "Analyze this PGN. It contains $maxPly plies. Only annotate valid ply numbers.\n\n$pgn",
                        0.2, 1_800,
                    )
                    cache.put(key, produced)
                    return@run produced
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    last = exception
                    if (index + 1 < MAX_ATTEMPTS) backoff(index + 1)
                }
            }
            throw AiUnavailableException()
        }
        parseAnalysis(content, maxPly) ?: throw AiUnavailableException("AI analysis could not be validated")
    }.recoverCatching {
        if (it is CancellationException) throw it
        throw AiUnavailableException()
    }

    suspend fun evaluatePosition(
        apiKey: String,
        model: String,
        fen: String,
        legalMoves: List<ChessMove>,
    ): Result<PositionAdvice> = runCatching {
        if (apiKey.isBlank()) throw AiUnavailableException("Add an OpenRouter key to enable analysis")
        val key = cacheKey("evaluation", model, fen)
        val content = cache.get(key) ?: client.complete(
            apiKey, model, EVALUATION_SYSTEM_PROMPT,
            evaluationPrompt(fen, legalMoves), 0.1, 500,
        ).also { cache.put(key, it) }
        val json = parseJsonObject(content) ?: throw AiUnavailableException()
        val evaluation = json.get("evaluation")?.takeIf { it.isJsonPrimitive }?.asDouble
            ?.takeIf { it.isFinite() }?.coerceIn(-1.0, 1.0) ?: throw AiUnavailableException()
        val bestMove = json.get("bestMove")?.takeIf { it.isJsonPrimitive }?.asString
            ?.let(ChessMove::parse)?.takeIf { candidate -> legalMoves.any { it.uci == candidate.uci } }
        PositionAdvice(
            evaluation,
            bestMove,
            json.get("explanation")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            if (cache.get(key) != null) AiSource.CACHE else AiSource.OPENROUTER,
        )
    }.recoverCatching {
        if (it is CancellationException) throw it
        throw AiUnavailableException()
    }

    suspend fun respondToDraw(
        apiKey: String,
        model: String,
        fen: String,
        legalMoves: List<ChessMove>,
    ): DrawDecision {
        if (apiKey.isBlank()) return DrawDecision(false, "Draw offer declined while offline", AiSource.FALLBACK_NO_KEY)
        return try {
            val content = client.complete(
                apiKey, model,
                "You are a chess bot deciding a draw offer. Return only JSON: {\"accept\":true|false,\"message\":\"short friendly response\"}. Accept reasonable draw offers in equal or inferior late-game positions; otherwise decline.",
                "FEN: $fen\nLegal moves: ${legalMoves.joinToString(" ") { it.uci }}",
                0.1, 180,
            )
            val json = parseJsonObject(content) ?: return DrawDecision(false, "Draw offer declined", AiSource.FALLBACK_INVALID)
            DrawDecision(
                json.get("accept")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,
                json.get("message")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().ifBlank { "Draw offer declined" },
                AiSource.OPENROUTER,
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            DrawDecision(false, "Draw offer declined while AI is unavailable", AiSource.FALLBACK_NETWORK)
        }
    }

    private suspend fun readCachedMove(key: String, legalMoves: List<ChessMove>): AiMoveResult? =
        cache.get(key)?.let { parseMoveContent(it, legalMoves) }

    private fun moveFallback(request: MoveProposalRequest, source: AiSource, reason: String, attempts: Int): AiMoveResult {
        if (request.purpose != MovePurpose.BOT_MOVE) {
            return AiMoveResult(null, reason, source, attempts)
        }
        return AiMoveResult(
            randomLegalMove(request.legalMoves, random),
            "$reason; selected a random legal move",
            source,
            attempts,
        )
    }

    private fun parseMoveContent(content: String, legalMoves: List<ChessMove>): AiMoveResult? {
        val json = parseJsonObject(content) ?: return null
        val move = json.get("move")?.takeIf { it.isJsonPrimitive }?.asString?.let(ChessMove::parse)
            ?: return null
        if (legalMoves.none { it.uci == move.uci }) return null
        val reasoning = json.get("reasoning")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        return AiMoveResult(move, reasoning.take(1_500), AiSource.OPENROUTER, 1)
    }

    private fun parseAnalysis(content: String, maxPly: Int): GameAnalysis? {
        val json = parseJsonObject(content) ?: return null
        val accuracy = json.get("accuracy")?.takeIf { it.isJsonPrimitive }?.asDouble
            ?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: return null
        val summary = json.get("summary")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val coach = json.get("coach")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        if (summary.isBlank() && coach.isBlank()) return null
        val annotations = json.getAsJsonArray("annotations")?.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val item = element.asJsonObject
            val ply = item.get("ply")?.takeIf { it.isJsonPrimitive }?.asInt ?: return@mapNotNull null
            if (ply !in 1..maxPly) return@mapNotNull null
            val severity = runCatching {
                AnnotationSeverity.valueOf(item.get("severity")?.asString?.uppercase() ?: "GOOD")
            }.getOrDefault(AnnotationSeverity.GOOD)
            val comment = item.get("comment")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (comment.isBlank()) return@mapNotNull null
            MoveAnnotation(ply, severity, comment.take(700), item.get("bestMove")?.takeIf { it.isJsonPrimitive }?.asString)
        }.orEmpty()
        return GameAnalysis(accuracy, summary.take(2_000), coach.take(2_000), annotations)
    }

    private fun parseJsonObject(raw: String): JsonObject? = runCatching {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end < start) return@runCatching null
        JsonParser.parseString(cleaned.substring(start, end + 1)).asJsonObject
    }.getOrNull()

    private fun cacheKey(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u001f").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun moveSystemPrompt(request: MoveProposalRequest): String = buildString {
        append("You propose one chess move for Cheshers. chesslib, not you, is the rules authority. ")
        append("Return only one JSON object: {\"move\":\"e2e4\",\"reasoning\":\"brief explanation\"}. ")
        append("Copy move exactly from the supplied UCI legalMoves list. Never return SAN, commentary, or an illegal move. ")
        append(difficultyInstruction(request.difficulty)).append(' ')
        append(personalityInstruction(request.personality))
        if (request.purpose == MovePurpose.HINT) append(" Give the most instructive legal move for the human player.")
        if (!request.puzzleObjective.isNullOrBlank()) append(" Puzzle objective: ").append(request.puzzleObjective)
    }

    private fun moveUserPrompt(request: MoveProposalRequest, attempt: Int): String = buildString {
        append("FEN: ").append(request.fen).append('\n')
        append("legalMoves: ").append(request.legalMoves.joinToString(" ") { it.uci }).append('\n')
        append("Choose for the side to move in the FEN.")
        if (attempt > 1) append(" Your previous response was missing, illegal, or not valid JSON. Correct it and copy one legal UCI move exactly.")
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        private const val ANALYSIS_SYSTEM_PROMPT = "You are Cheshers' chess coach. Analyze the supplied PGN without claiming to run an engine. Identify clear tactical or strategic mistakes from the moves. Return only JSON with this schema: {\"accuracy\":0..100,\"summary\":\"...\",\"coach\":\"constructive coaching message\",\"annotations\":[{\"ply\":1,\"severity\":\"GOOD|INACCURACY|MISTAKE|BLUNDER\",\"comment\":\"...\",\"bestMove\":\"optional UCI\"}]}. Use sparse annotations; do not invent variations or certainty."
        private const val EVALUATION_SYSTEM_PROMPT = "You are Cheshers' position coach, not a chess engine. Return only JSON: {\"evaluation\":number from -1 to 1 from White's perspective,\"bestMove\":\"a legal UCI move or empty\",\"explanation\":\"brief, appropriately uncertain explanation\"}. Copy bestMove exactly from legalMoves."
    }
}

private fun difficultyInstruction(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.BEGINNER -> "Play like a beginner: choose a legal but often weak move, overlooking simple tactics. Never explain that you are throwing the game."
    Difficulty.EASY -> "Play casually and allow occasional suboptimal moves while still responding to immediate threats."
    Difficulty.INTERMEDIATE -> "Choose solid legal moves using development, king safety, material, and simple tactics."
    Difficulty.ADVANCED -> "Choose the strongest legal move you can, checking captures, checks, threats, and positional factors."
    Difficulty.EXPERT -> "Choose the strongest legal move and compare several candidate moves before answering; keep reasoning concise."
    Difficulty.MASTER -> "Choose the strongest legal move with rigorous candidate comparison, tactical checks, pawn structure, king safety, and long-term planning."
}

private fun personalityInstruction(personality: Personality): String = when (personality) {
    Personality.BALANCED -> "Use a balanced style."
    Personality.AGGRESSIVE -> "Prefer an aggressive style, initiative, and king attacks without making obviously unsound sacrifices."
    Personality.POSITIONAL -> "Prefer a positional style, space, structure, piece activity, and prophylaxis."
}

private fun temperatureFor(difficulty: Difficulty): Double = when (difficulty) {
    Difficulty.BEGINNER -> 0.95
    Difficulty.EASY -> 0.75
    Difficulty.INTERMEDIATE -> 0.45
    Difficulty.ADVANCED -> 0.25
    Difficulty.EXPERT -> 0.15
    Difficulty.MASTER -> 0.05
}

private fun evaluationPrompt(fen: String, legalMoves: List<ChessMove>): String =
    "FEN: $fen\nlegalMoves: ${legalMoves.joinToString(" ") { it.uci }}\nEvaluate and choose for the side to move."
