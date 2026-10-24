package app.cheshers.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheshers.core.chess.RatingCategory
import app.cheshers.core.data.local.entity.GameEntity
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onReplay: (String) -> Unit,
    onSharePgn: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & stats") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit profile") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.profile?.let { profile ->
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(avatarGlyph(profile.avatarId), style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Level ${profile.level} · ${profile.xp} XP ${if (profile.country.isNotBlank()) "· ${profile.country}" else ""}")
                        }
                        Text("${profile.coins} coins", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Overview") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Games") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Leaderboards") })
            }
            when (tab) {
                0 -> Overview(state)
                1 -> History(state.history, onReplay, onSharePgn, viewModel::deleteGame)
                else -> Leaderboards(state, viewModel::leaderboardFilters)
            }
        }
    }
    if (editing) {
        val profile = state.profile
        if (profile != null) EditProfileDialog(profile.username, profile.avatarId, profile.country, state.cosmetics.filter { it.type == "AVATAR" && it.unlocked }, { editing = false }) { name, avatar, country ->
            viewModel.updateProfile(name, avatar, country); editing = false
        }
    }
}

@Composable
private fun Overview(state: ProfileUiState) {
    val profile = state.profile ?: return
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Wins", profile.wins.toString(), Modifier.weight(1f))
                StatCard("Losses", profile.losses.toString(), Modifier.weight(1f))
                StatCard("Draws", profile.draws.toString(), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ratings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    RatingRow("Bullet", profile.bulletRating, profile.bulletGames)
                    RatingRow("Blitz", profile.blitzRating, profile.blitzGames)
                    RatingRow("Rapid", profile.rapidRating, profile.rapidGames)
                    RatingRow("Classical", profile.classicalRating, profile.classicalGames)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Playing record", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Games played: ${profile.gamesPlayed}")
                    Text("Best win streak: ${profile.bestWinStreak}")
                    Text("Puzzles solved: ${profile.puzzlesSolved}")
                    Text("Daily puzzle streak: ${profile.dailyPuzzleStreak}")
                    val accuracy = if (profile.accuracySamples > 0) profile.accuracyTotal / profile.accuracySamples else null
                    Text("Average AI-reviewed accuracy: ${accuracy?.let { "%.1f%%".format(it) } ?: "No reviews yet"}")
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(label)
    } }
}

@Composable
private fun RatingRow(label: String, rating: Int, games: Int) {
    Row { Text(label, Modifier.weight(1f)); Text("$rating", fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp)); Text("$games games", style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun History(games: List<GameEntity>, onReplay: (String) -> Unit, onShare: (String) -> Unit, onDelete: (String) -> Unit) {
    if (games.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Completed games will appear here") }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(games, key = { it.id }) { game ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row {
                        Text(game.result, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(game.updatedAt)), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${game.whiteName} vs ${game.blackName}")
                    Text("${game.resultReason} · ${game.timeControlId}" + if (game.ratingDelta != 0) " · rating ${if (game.ratingDelta > 0) "+" else ""}${game.ratingDelta}" else "")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { onReplay(game.id) }) { Text("Replay & review") }
                        IconButton(onClick = { onShare(game.pgn) }) { Icon(Icons.Default.Share, "Export PGN") }
                        TextButton(onClick = { onDelete(game.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Leaderboards(state: ProfileUiState, onFilters: (String, RatingCategory) -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("global", "friends", "weekly").forEach { scope ->
                FilterChip(state.leaderboardScope == scope, { onFilters(scope, state.leaderboardCategory) }, label = { Text(scope.replaceFirstChar { it.uppercase() }) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(RatingCategory.BULLET, RatingCategory.BLITZ, RatingCategory.RAPID, RatingCategory.CLASSICAL).forEach { category ->
                FilterChip(state.leaderboardCategory == category, { onFilters(state.leaderboardScope, category) }, label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        when {
            state.leaderboardLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.leaderboardError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Leaderboard unavailable. Configure and deploy the backend to enable it.", textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                itemsIndexed(state.leaderboard) { index, entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("#${index + 1}", Modifier.width(42.dp), fontWeight = FontWeight.Bold)
                            Text(entry.username, Modifier.weight(1f)); Text(entry.rating.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialAvatar: String,
    initialCountry: String,
    avatars: List<app.cheshers.core.data.local.entity.CosmeticEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var avatar by remember { mutableStateOf(initialAvatar) }
    var country by remember { mutableStateOf(initialCountry) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(country, { country = it.take(2) }, label = { Text("Country code (optional)") }, singleLine = true)
                Text("Avatar")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    avatars.forEach { item -> FilterChip(avatar == item.id, { avatar = item.id }, label = { Text(avatarGlyph(item.id)) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, avatar, country) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun avatarGlyph(id: String): String = when (id) {
    "avatar_cat" -> "🐈"; "avatar_fox" -> "🦊"; "avatar_rook" -> "♖"; "avatar_crown" -> "♛"; else -> "♔"
}
