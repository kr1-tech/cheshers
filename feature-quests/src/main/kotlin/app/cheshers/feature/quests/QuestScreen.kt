package app.cheshers.feature.quests

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheshers.core.data.local.entity.CosmeticEntity
import app.cheshers.core.data.repository.ProgressionRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestScreen(onBack: () -> Unit, viewModel: QuestViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Progression") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.profile?.let { profile ->
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text("Level ${profile.level}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${profile.coins} coins", fontWeight = FontWeight.SemiBold)
                        }
                        LinearProgressIndicator(progress = { ProgressionRules.levelProgress(profile.xp, profile.level) }, modifier = Modifier.fillMaxWidth())
                        Text("${profile.xp} XP · next level at ${ProgressionRules.nextLevelXp(profile.level)} XP", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Quests") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Achievements") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Shop") })
            }
            when (tab) {
                0 -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.quests, key = { it.id }) { quest ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (quest.completedAt != null) Icons.Default.CheckCircle else Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(quest.title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    AssistChip(onClick = {}, label = { Text(if (quest.period == "DAILY") "Daily" else "Weekly") })
                                }
                                Text(quest.description)
                                LinearProgressIndicator(progress = { (quest.progress.toFloat() / quest.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("${quest.progress}/${quest.target} · +${quest.rewardXp} XP · +${quest.rewardCoins} coins")
                                if (quest.completedAt != null) Text("Completed — rewards collected", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                1 -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.achievements, key = { it.id }) { achievement ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (achievement.unlockedAt != null) Icons.Default.CheckCircle else Icons.Default.Lock, null)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(achievement.title, fontWeight = FontWeight.Bold)
                                    Text(achievement.description)
                                    LinearProgressIndicator(progress = { (achievement.progress.toFloat() / achievement.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                                    Text("${achievement.progress}/${achievement.target}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                else -> ShopList(state.cosmetics, state.settings.boardTheme.name, state.settings.pieceSet.name, state.profile?.avatarId, viewModel::purchase, viewModel::equip)
            }
        }
    }
}

@Composable
private fun ShopList(
    items: List<CosmeticEntity>,
    board: String,
    pieces: String,
    avatar: String?,
    onPurchase: (CosmeticEntity) -> Unit,
    onEquip: (CosmeticEntity) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { item ->
            val equipped = when (item.type) {
                "BOARD" -> item.id == "board_${board.lowercase()}"
                "PIECES" -> item.id == "pieces_${pieces.lowercase()}"
                "AVATAR" -> item.id == avatar
                else -> false
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text("${item.type.lowercase().replaceFirstChar { it.uppercase() }} · ${if (item.cost == 0) "Free" else "${item.cost} coins"}")
                    }
                    when {
                        equipped -> Text("Equipped", color = MaterialTheme.colorScheme.primary)
                        item.unlocked -> TextButton(onClick = { onEquip(item) }) { Text("Equip") }
                        else -> Button(onClick = { onPurchase(item) }) { Text("Unlock") }
                    }
                }
            }
        }
        item { Text("Cosmetics are earned only with in-game coins. There are no real-money purchases.", style = MaterialTheme.typography.bodySmall) }
    }
}
