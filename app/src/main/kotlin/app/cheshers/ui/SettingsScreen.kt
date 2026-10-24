package app.cheshers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheshers.core.data.repository.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onReplayOnboarding: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val snackbar = remember { SnackbarHostState() }
    var apiKey by remember(settings.openRouterApiKey) { mutableStateOf(settings.openRouterApiKey) }
    var backendUrl by remember(settings.backendUrl) { mutableStateOf(settings.backendUrl) }
    var showKey by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSection("Appearance & accessibility") {
                Text("Color mode", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ThemeMode.entries.forEach { mode -> FilterChip(settings.themeMode == mode, { viewModel.save(settings.copy(themeMode = mode)) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                Text("Board theme", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    BoardTheme.entries.filter { theme -> state.cosmetics.isEmpty() || state.cosmetics.any { it.id == "board_${theme.name.lowercase()}" && it.unlocked } }.forEach { theme ->
                        FilterChip(settings.boardTheme == theme, { viewModel.save(settings.copy(boardTheme = theme)) }, label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                Text("Piece set", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PieceSet.entries.filter { set -> state.cosmetics.isEmpty() || state.cosmetics.any { it.id == "pieces_${set.name.lowercase()}" && it.unlocked } }.forEach { set ->
                        FilterChip(settings.pieceSet == set, { viewModel.save(settings.copy(pieceSet = set)) }, label = { Text(set.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                SwitchSetting("Colorblind-friendly board", "High-contrast blue palette with dark-square patterns", settings.colorBlind) { viewModel.save(settings.copy(colorBlind = it)) }
                SwitchSetting("Show coordinates", null, settings.showCoordinates) { viewModel.save(settings.copy(showCoordinates = it)) }
                SwitchSetting("Show legal-move dots", null, settings.showLegalMoves) { viewModel.save(settings.copy(showLegalMoves = it)) }
            }
            SettingsSection("Gameplay") {
                SwitchSetting("Sound effects", null, settings.sound) { viewModel.save(settings.copy(sound = it)) }
                SwitchSetting("Haptic feedback", null, settings.haptics) { viewModel.save(settings.copy(haptics = it)) }
                SwitchSetting("Auto-promote to queen", "Turn off to choose queen, rook, bishop, or knight", settings.autoPromote) { viewModel.save(settings.copy(autoPromote = it)) }
                SwitchSetting("Confirm each move", null, settings.confirmMove) { viewModel.save(settings.copy(confirmMove = it)) }
                SwitchSetting("Flip pass-and-play board", "Keep the side to move at the bottom", settings.flipPassAndPlay) { viewModel.save(settings.copy(flipPassAndPlay = it)) }
                Text("Animation speed · ${"%.1f".format(settings.animationSpeed)}×")
                Slider(settings.animationSpeed, { viewModel.preview(settings.copy(animationSpeed = it)) }, onValueChangeFinished = { viewModel.save(state.settings) }, valueRange = 0.5f..2f)
                SwitchSetting("Quest notifications", null, settings.notifications) { viewModel.save(settings.copy(notifications = it)) }
            }
            SettingsSection("OpenRouter AI") {
                Text("Model", fontWeight = FontWeight.SemiBold)
                ModelPicker(state, settings.openRouterModel) { viewModel.save(settings.copy(openRouterModel = it)) }
                OutlinedTextField(apiKey, { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("OpenRouter API key") }, singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } })
                OutlinedTextField(settings.openRouterModel, { viewModel.save(settings.copy(openRouterModel = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("Custom model ID") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.save(settings.copy(openRouterApiKey = apiKey.trim())) }) { Text("Save key") }
                    OutlinedButton(onClick = viewModel::refreshModels, enabled = !state.refreshingModels) { Text(if (state.refreshingModels) "Refreshing…" else "Refresh models") }
                }
                if (!settings.secureStorageAvailable) Text("Encrypted storage is unavailable. Credentials will remain in memory only for this process.", color = MaterialTheme.colorScheme.error)
                Text("Security note: a key compiled into any Android client can be extracted. Use a restricted, revocable key and do not publish a credential-bearing APK. local.properties and secrets are never committed.", style = MaterialTheme.typography.bodySmall)
            }
            SettingsSection("Self-hosted online backend") {
                OutlinedTextField(backendUrl, { backendUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Backend HTTPS URL") }, singleLine = true, placeholder = { Text("https://your-server.example") })
                Button(onClick = { viewModel.save(settings.copy(backendUrl = backendUrl.trim().trimEnd('/'))) }) { Text("Save backend URL") }
                Text("Release builds allow HTTPS/WSS only. Emulator development can use http://10.0.2.2:3000 in a debug build.", style = MaterialTheme.typography.bodySmall)
            }
            SettingsSection("Privacy & help") {
                Text("Cheshers contains no ads, analytics trackers, in-app purchases, or subscriptions. Games, progression, and cached AI responses are stored locally. Online account and game data is stored only on your configured backend.")
                TextButton(onClick = { viewModel.save(settings.copy(onboardingComplete = false)); onReplayOnboarding() }) { Text("Replay onboarding tutorial") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(state: SettingsUiState, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val models = (state.models + app.cheshers.core.ai.OpenRouterModel(selectedId, selectedId)).distinctBy { it.id }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            models.firstOrNull { it.id == selectedId }?.label ?: selectedId, {}, readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(), label = { Text("Available models") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            models.forEach { model -> DropdownMenuItem(text = { Text("${model.label}\n${model.id}") }, onClick = { onSelect(model.id); expanded = false }) }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
        Switch(checked, onChange)
    }
}
