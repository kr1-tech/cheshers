package app.cheshers.feature.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cheshers.core.chess.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameSetupScreen(
    onBack: () -> Unit,
    onStart: (GameMode, Difficulty, SideChoice, TimeControl, Personality) -> Unit,
) {
    var mode by remember { mutableStateOf(GameMode.BOT) }
    var difficulty by remember { mutableStateOf(Difficulty.INTERMEDIATE) }
    var side by remember { mutableStateOf(SideChoice.WHITE) }
    var timeControl by remember { mutableStateOf(TimeControl.BLITZ_5_0) }
    var personality by remember { mutableStateOf(Personality.BALANCED) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("New offline game") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Choose your game", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == GameMode.BOT, onClick = { mode = GameMode.BOT }, label = { Text("Vs OpenRouter bot") })
                FilterChip(selected = mode == GameMode.PASS_AND_PLAY, onClick = { mode = GameMode.PASS_AND_PLAY }, label = { Text("Pass & play") })
            }
            if (mode == GameMode.BOT) {
                SetupSection("Difficulty") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Difficulty.entries.forEach { item ->
                            FilterChip(selected = difficulty == item, onClick = { difficulty = item }, label = { Text(item.displayName) })
                        }
                    }
                }
                SetupSection("Bot personality") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Personality.entries.forEach { item ->
                            FilterChip(selected = personality == item, onClick = { personality = item }, label = { Text(item.displayName) })
                        }
                    }
                }
                SetupSection("Your side") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SideChoice.entries.forEach { item ->
                            FilterChip(selected = side == item, onClick = { side = item }, label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
            }
            SetupSection("Time control") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TimeControl.entries.forEach { item ->
                        FilterChip(selected = timeControl == item, onClick = { timeControl = item }, label = { Text(item.label) })
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Free forever", fontWeight = FontWeight.Bold)
                    Text("No ads, subscriptions, premium locks, or pay-to-win items. AI requests use your configured OpenRouter account.")
                }
            }
            Button(
                onClick = { onStart(mode, difficulty, side, timeControl, personality) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Start game") }
        }
    }
}

@Composable
private fun SetupSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}
