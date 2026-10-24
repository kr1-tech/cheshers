package app.cheshers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cheshers.R
import app.cheshers.core.data.local.entity.GameEntity
import app.cheshers.core.data.local.entity.ProfileEntity
import app.cheshers.core.data.repository.GameRepository
import app.cheshers.core.data.repository.ProgressionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(games: GameRepository, progression: ProgressionRepository) : ViewModel() {
    val active: StateFlow<GameEntity?> = games.observeLatestActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val profile: StateFlow<ProfileEntity?> = progression.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    init { viewModelScope.launch { runCatching { progression.ensureInitialized() } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onContinue: (GameEntity) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Cheshers", fontWeight = FontWeight.Bold) },
            actions = { IconButton(onClick = { onNavigate("settings") }) { Icon(Icons.Default.Settings, "Settings") } },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Chess, free forever", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("No ads. No subscriptions. No paywalls. Ever.", Modifier.padding(top = 5.dp))
                            profile?.let { Text("Level ${it.level} · ${it.xp} XP · ${it.coins} coins", Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold) }
                        }
                        Text("♞", style = MaterialTheme.typography.displayLarge)
                    }
                }
            }
            active?.let { game ->
                item {
                    Card(onClick = { onContinue(game) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Continue your game", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${game.mode.lowercase().replace('_', ' ')} · ${game.timeControlId} · ${game.result}")
                        }
                    }
                }
            }
            item { HomeCard("♟", "Play offline", "OpenRouter bot or pass-and-play, clocks, hints and undo") { onNavigate("offline-setup") } }
            item { HomeCard("♜", "Play online", "Rated matchmaking, friend invites, reconnect and chat") { onNavigate("online") } }
            item { HomeCard("★", "Puzzles", "Curated tactics and a daily challenge with streaks") { onNavigate("puzzles") } }
            item { HomeCard("◆", "Daily challenge", "Solve today's puzzle and keep your streak alive") { onNavigate("daily-puzzle") } }
            item { HomeCard("±", "Analysis board", "Free play, OpenRouter evaluation and coach review") { onNavigate("analysis") } }
            item { HomeCard("☑", "Quests & rewards", "Daily and weekly quests, achievements and cosmetics") { onNavigate("quests") } }
            item { HomeCard("♔", "Profile & history", "Ratings, stats, leaderboards, replay and PGN export") { onNavigate("profile") } }
            item {
                Text(
                    "AI moves, hints, and reviews use OpenRouter. Every proposed move is checked by chesslib. Offline bot fallback is random legal play, never a bundled engine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeCard(glyph: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(glyph, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.width(42.dp))
            Column { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
