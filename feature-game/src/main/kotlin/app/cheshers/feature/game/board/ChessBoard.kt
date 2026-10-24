package app.cheshers.feature.game.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheshers.core.chess.*
import app.cheshers.core.data.repository.BoardTheme
import app.cheshers.core.data.repository.PieceSet
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.roundToInt

private data class BoardPalette(val light: Color, val dark: Color, val highlight: Color, val legal: Color)

@Composable
fun ChessBoard(
    snapshot: PositionSnapshot,
    flipped: Boolean,
    boardTheme: BoardTheme,
    pieceSet: PieceSet,
    colorBlind: Boolean,
    showCoordinates: Boolean,
    showLegalMoves: Boolean,
    autoPromote: Boolean,
    confirmMove: Boolean,
    animationSpeed: Float,
    enabled: Boolean,
    hintMove: ChessMove?,
    onMove: (ChessMove) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var promotionChoices by remember { mutableStateOf<List<ChessMove>>(emptyList()) }
    var confirmation by remember { mutableStateOf<ChessMove?>(null) }
    var dragSquare by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(1f) }
    var animatingMove by remember { mutableStateOf<ChessMove?>(null) }

    LaunchedEffect(snapshot.fen) {
        selected = null
        dragSquare = null
        dragOffset = Offset.Zero
        val move = snapshot.lastMove
        if (move != null && animationSpeed > 0.1f) {
            animatingMove = move
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = (320 / animationSpeed).toInt().coerceIn(80, 650)))
        } else {
            animatingMove = null
            progress.snapTo(1f)
        }
    }

    val palette = paletteFor(boardTheme, colorBlind)
    fun submit(move: ChessMove) {
        if (confirmMove) confirmation = move else onMove(move)
    }
    fun submit(from: String, to: String) {
        val candidates = snapshot.legalMoves.filter { it.from == from && it.to == to }
        if (candidates.isEmpty()) return
        when {
            candidates.size == 1 -> submit(candidates.single())
            autoPromote -> submit(candidates.firstOrNull { it.promotion == PieceType.QUEEN } ?: candidates.first())
            else -> promotionChoices = candidates
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .semantics { contentDescription = "Chess board, ${snapshot.turn.name.lowercase()} to move" },
    ) {
        val squareSize = maxWidth / 8
        val density = LocalDensity.current
        val squarePx = with(density) { squareSize.toPx() }
        val squares = orderedSquares(flipped)
        val pieceBySquare = snapshot.pieces.associateBy { it.square }

        fun screenPosition(square: String): Pair<Int, Int> {
            val fileIndex = square[0] - 'a'
            val rankIndex = square[1] - '1'
            val column = if (flipped) 7 - fileIndex else fileIndex
            val row = if (flipped) rankIndex else 7 - rankIndex
            return column to row
        }
        fun squareAt(position: Offset): String? {
            val column = floor(position.x / squarePx).toInt()
            val row = floor(position.y / squarePx).toInt()
            if (column !in 0..7 || row !in 0..7) return null
            val fileIndex = if (flipped) 7 - column else column
            val rankIndex = if (flipped) row else 7 - row
            return "${'a' + fileIndex}${'1' + rankIndex}"
        }
        fun tap(square: String) {
            if (!enabled) return
            val current = selected
            val piece = pieceBySquare[square]
            when {
                current == null -> if (piece?.color == snapshot.turn) selected = square
                current == square -> selected = null
                snapshot.legalMoves.any { it.from == current && it.to == square } -> submit(current, square)
                piece?.color == snapshot.turn -> selected = square
                else -> selected = null
            }
        }

        Column(Modifier.fillMaxSize()) {
            squares.chunked(8).forEach { row ->
                Row(Modifier.weight(1f)) {
                    row.forEach { square ->
                        val piece = pieceBySquare[square]
                        val (column, rowIndex) = screenPosition(square)
                        val isDark = (column + rowIndex) % 2 == 0
                        val isLast = snapshot.lastMove?.let { it.from == square || it.to == square } == true
                        val isSelected = selected == square
                        val isChecked = snapshot.checkedKing == square
                        val legalTargets = selected?.let { origin ->
                            snapshot.legalMoves.filter { it.from == origin && it.to == square }
                        }.orEmpty()
                        val hideForAnimation = animatingMove?.to == square && progress.value < 1f
                        val hideForDrag = dragSquare == square && dragOffset != Offset.Zero
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (isDark) palette.dark else palette.light)
                                .semantics {
                                    contentDescription = buildString {
                                        append(square)
                                        append(piece?.let { ", ${it.color.name.lowercase()} ${it.type.name.lowercase()}" } ?: ", empty")
                                        if (isSelected) append(", selected")
                                        if (legalTargets.isNotEmpty()) append(", legal destination")
                                        if (isChecked) append(", king in check")
                                    }
                                    if (enabled) onClick("Select or move to $square") { tap(square); true }
                                }
                                .pointerInput(square, enabled, snapshot.fen) {
                                    detectTapGestures { tap(square) }
                                }
                                .pointerInput(square, enabled, snapshot.fen, piece) {
                                    if (enabled && piece?.color == snapshot.turn) {
                                        detectDragGestures(
                                            onDragStart = {
                                                selected = square
                                                dragSquare = square
                                                dragOffset = Offset.Zero
                                            },
                                            onDragEnd = {
                                                val origin = dragSquare
                                                if (origin != null) {
                                                    val (c, r) = screenPosition(origin)
                                                    val center = Offset((c + 0.5f) * squarePx, (r + 0.5f) * squarePx)
                                                    squareAt(center + dragOffset)?.let { target -> submit(origin, target) }
                                                }
                                                dragSquare = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = { dragSquare = null; dragOffset = Offset.Zero },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                            },
                                        )
                                    }
                                },
                        ) {
                            if (isLast) Box(Modifier.matchParentSize().background(palette.highlight))
                            if (isSelected) Box(Modifier.matchParentSize().background(Color(0x55FFFFFF)))
                            if (isChecked) Canvas(Modifier.matchParentSize()) {
                                drawCircle(Color(0xBCE5484D), radius = size.minDimension * 0.46f)
                            }
                            if (colorBlind && isDark) Canvas(Modifier.matchParentSize()) {
                                val step = size.minDimension / 5
                                var x = -size.height
                                while (x < size.width) {
                                    drawLine(Color(0x18FFFFFF), Offset(x, 0f), Offset(x + size.height, size.height), 1.5f)
                                    x += step
                                }
                            }
                            if (showLegalMoves && legalTargets.isNotEmpty()) Canvas(Modifier.matchParentSize()) {
                                if (piece == null) drawCircle(palette.legal, radius = size.minDimension * 0.12f)
                                else drawCircle(palette.legal, radius = size.minDimension * 0.43f, style = androidx.compose.ui.graphics.drawscope.Stroke(size.minDimension * 0.07f))
                            }
                            if (piece != null && !hideForAnimation && !hideForDrag) {
                                PieceGlyph(piece, squareSize, pieceSet, Modifier.align(Alignment.Center))
                            }
                            if (showCoordinates) {
                                if (rowIndex == 7) Text(
                                    square[0].toString(),
                                    color = if (isDark) palette.light else palette.dark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                                )
                                if (column == 0) Text(
                                    square[1].toString(),
                                    color = if (isDark) palette.light else palette.dark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        hintMove?.let { hint ->
            Canvas(Modifier.matchParentSize()) {
                val (fromColumn, fromRow) = screenPosition(hint.from)
                val (toColumn, toRow) = screenPosition(hint.to)
                val start = Offset((fromColumn + 0.5f) * squarePx, (fromRow + 0.5f) * squarePx)
                val end = Offset((toColumn + 0.5f) * squarePx, (toRow + 0.5f) * squarePx)
                val color = Color(0xDD3B82F6)
                drawLine(color, start, end, strokeWidth = squarePx * 0.09f)
                val angle = atan2(end.y - start.y, end.x - start.x)
                val head = squarePx * 0.25f
                val path = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo(end.x - head * cos(angle - 0.55f), end.y - head * sin(angle - 0.55f))
                    lineTo(end.x - head * cos(angle + 0.55f), end.y - head * sin(angle + 0.55f))
                    close()
                }
                drawPath(path, color)
            }
        }

        val moving = animatingMove
        if (moving != null && progress.value < 1f) {
            pieceBySquare[moving.to]?.let { piece ->
                val (fromC, fromR) = screenPosition(moving.from)
                val (toC, toR) = screenPosition(moving.to)
                val x = (fromC + (toC - fromC) * progress.value) * squarePx
                val y = (fromR + (toR - fromR) * progress.value) * squarePx
                PieceGlyph(piece, squareSize, pieceSet, Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) })
            }
        }
        val dragging = dragSquare
        if (dragging != null && dragOffset != Offset.Zero) {
            pieceBySquare[dragging]?.let { piece ->
                val (column, rowIndex) = screenPosition(dragging)
                PieceGlyph(
                    piece, squareSize, pieceSet,
                    Modifier.offset {
                        IntOffset(
                            (column * squarePx + dragOffset.x).roundToInt(),
                            (rowIndex * squarePx + dragOffset.y).roundToInt(),
                        )
                    },
                )
            }
        }
    }

    if (promotionChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { promotionChoices = emptyList() },
            title = { Text("Choose promotion") },
            text = { Text("Promote your pawn to:") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { type ->
                        TextButton(onClick = {
                            promotionChoices.firstOrNull { it.promotion == type }?.let { submit(it) }
                            promotionChoices = emptyList()
                        }) { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { promotionChoices = emptyList() }) { Text("Cancel") } },
        )
    }
    confirmation?.let { move ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("Confirm move") },
            text = { Text("Play ${move.from} → ${move.to}?") },
            confirmButton = { TextButton(onClick = { onMove(move); confirmation = null }) { Text("Play move") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PieceGlyph(piece: BoardPiece, size: Dp, set: PieceSet, modifier: Modifier = Modifier) {
    val glyph = when (set) {
        PieceSet.CLASSIC -> when (piece.color to piece.type) {
            ChessColor.WHITE to PieceType.KING -> "♔"; ChessColor.WHITE to PieceType.QUEEN -> "♕"
            ChessColor.WHITE to PieceType.ROOK -> "♖"; ChessColor.WHITE to PieceType.BISHOP -> "♗"
            ChessColor.WHITE to PieceType.KNIGHT -> "♘"; ChessColor.WHITE to PieceType.PAWN -> "♙"
            ChessColor.BLACK to PieceType.KING -> "♚"; ChessColor.BLACK to PieceType.QUEEN -> "♛"
            ChessColor.BLACK to PieceType.ROOK -> "♜"; ChessColor.BLACK to PieceType.BISHOP -> "♝"
            ChessColor.BLACK to PieceType.KNIGHT -> "♞"; else -> "♟"
        }
        else -> when (piece.type) {
            PieceType.KING -> "♚"; PieceType.QUEEN -> "♛"; PieceType.ROOK -> "♜"
            PieceType.BISHOP -> "♝"; PieceType.KNIGHT -> "♞"; PieceType.PAWN -> "♟"
        }
    }
    val font = when (set) {
        PieceSet.CLASSIC -> FontFamily.Serif
        PieceSet.NEO -> FontFamily.SansSerif
        PieceSet.ALPHA -> FontFamily.Monospace
    }
    val scale = if (set == PieceSet.ALPHA) 0.68f else 0.78f
    Text(
        glyph,
        modifier = modifier.size(size).semantics {
            contentDescription = "${piece.color.name.lowercase()} ${piece.type.name.lowercase()} on ${piece.square}"
        },
        color = if (piece.color == ChessColor.WHITE) Color(0xFFF8F6F0) else Color(0xFF15191E),
        fontFamily = font,
        fontWeight = if (set == PieceSet.NEO) FontWeight.Bold else FontWeight.Normal,
        fontSize = (size.value * scale).sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = androidx.compose.ui.text.TextStyle(
            shadow = Shadow(if (piece.color == ChessColor.WHITE) Color(0xAA000000) else Color(0x77FFFFFF), blurRadius = 2f),
        ),
    )
}

private fun orderedSquares(flipped: Boolean): List<String> = buildList {
    val ranks = if (flipped) 1..8 else 8 downTo 1
    val files = if (flipped) 'h' downTo 'a' else 'a'..'h'
    ranks.forEach { rank -> files.forEach { file -> add("$file$rank") } }
}

private fun paletteFor(theme: BoardTheme, colorBlind: Boolean): BoardPalette {
    if (colorBlind) return BoardPalette(Color(0xFFF3E5AB), Color(0xFF3973AC), Color(0x66F0C419), Color(0xAA17202A))
    return when (theme) {
        BoardTheme.LIGHT -> BoardPalette(Color(0xFFF0D9B5), Color(0xFFB58863), Color(0x66F6D55C), Color(0x88444444))
        BoardTheme.DARK -> BoardPalette(Color(0xFF8A94A6), Color(0xFF353B49), Color(0x66B69CFF), Color(0xAAE5E7EB))
        BoardTheme.WOOD -> BoardPalette(Color(0xFFE3C395), Color(0xFF8C5A3C), Color(0x66F6D55C), Color(0x88442222))
        BoardTheme.GREEN -> BoardPalette(Color(0xFFE9EDCC), Color(0xFF739552), Color(0x66F6D55C), Color(0x88444444))
        BoardTheme.BLUE -> BoardPalette(Color(0xFFDEE3E6), Color(0xFF8CA2AD), Color(0x664FC3F7), Color(0x88344444))
    }
}
