package app.cheshers.feature.online

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheshers.core.chess.TimeControl
import app.cheshers.feature.online.protocol.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnlineLobbyScreen(
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit,
    viewModel: OnlineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var timeControl by remember { mutableStateOf(TimeControl.BLITZ_5_0) }
    var challengeCode by remember { mutableStateOf("") }
    var friendName by remember { mutableStateOf("") }
    var openedGame by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    LaunchedEffect(state.game?.id, state.game?.result) {
        val game = state.game
        if (game != null && game.result == "*" && openedGame != game.id) { openedGame = game.id; onOpenGame(game.id) }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Online chess") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.session == null) AuthPanel(state.busy, viewModel::guest, viewModel::login, viewModel::register)
            else {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(state.session!!.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${state.connection.name.lowercase().replaceFirstChar { it.uppercase() }} · ${if (state.session!!.guest) "Guest account" else "Registered account"}")
                        }
                        TextButton(onClick = viewModel::signOut) { Text("Sign out") }
                    }
                }
                if (state.settings.backendUrl.isBlank() || state.settings.backendUrl.contains("example")) Card(Modifier.fillMaxWidth()) {
                    Text("Deploy the included backend and set its HTTPS URL in Settings to play online.", Modifier.padding(14.dp))
                }
                Text("Rated matchmaking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TimeControl.entries.filter { it != TimeControl.UNLIMITED }.forEach { control ->
                        FilterChip(timeControl == control, { timeControl = control }, label = { Text(control.label) })
                    }
                }
                if (state.queue == null) Button(onClick = { viewModel.joinQueue(timeControl.id) }, enabled = state.connection == ConnectionStatus.CONNECTED, modifier = Modifier.fillMaxWidth()) {
                    Text("Find ${timeControl.label} opponent")
                } else Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        CircularProgressIndicator(); Text("Searching · queue position ${state.queue?.position}", Modifier.padding(top = 8.dp))
                        TextButton(onClick = viewModel::leaveQueue) { Text("Cancel search") }
                    }
                }
                HorizontalDivider()
                Text("Play a friend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(friendName, { friendName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Friend's username (optional)") }, singleLine = true)
                Button(onClick = { viewModel.createChallenge(timeControl.id, friendName) }, enabled = state.connection == ConnectionStatus.CONNECTED, modifier = Modifier.fillMaxWidth()) {
                    Text("Create invite code")
                }
                state.challenge?.let { challenge ->
                    val clipboard = LocalClipboardManager.current
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp)) {
                            Column(Modifier.weight(1f)) { Text("Invite code"); Text(challenge.code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(challenge.code)) }) { Text("Copy") }
                        }
                    }
                }
                OutlinedTextField(challengeCode, { challengeCode = it.uppercase().take(12) }, modifier = Modifier.fillMaxWidth(), label = { Text("Enter invite code") }, singleLine = true)
                OutlinedButton(onClick = { viewModel.joinChallenge(challengeCode) }, enabled = challengeCode.length >= 4 && state.connection == ConnectionStatus.CONNECTED, modifier = Modifier.fillMaxWidth()) {
                    Text("Join friend's game")
                }
                Text("Guest accounts are stored on your backend. Register with email and password to keep a named identity.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    state.invite?.let { invite ->
        AlertDialog(
            onDismissRequest = viewModel::dismissInvite,
            title = { Text("Friend challenge") },
            text = { Text("${invite.from.username} invited you to a ${invite.timeControl} game.") },
            confirmButton = { TextButton(onClick = viewModel::acceptInvite) { Text("Accept") } },
            dismissButton = { TextButton(onClick = viewModel::dismissInvite) { Text("Not now") } },
        )
    }
}

@Composable
private fun AuthPanel(
    busy: Boolean,
    onGuest: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var identity by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Welcome to online Cheshers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Guest") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Sign in") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Register") })
            }
            when (tab) {
                0 -> {
                    Text("Play immediately with a guest identity. No email required.")
                    Button(onClick = onGuest, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Continue as guest") }
                }
                1 -> {
                    OutlinedTextField(identity, { identity = it }, label = { Text("Email or username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    PasswordField(password, { password = it })
                    Button(onClick = { onLogin(identity, password) }, enabled = !busy && identity.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
                }
                else -> {
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    PasswordField(password, { password = it })
                    Button(onClick = { onRegister(username, email, password) }, enabled = !busy && username.length >= 3 && email.contains('@') && password.length >= 8, modifier = Modifier.fillMaxWidth()) { Text("Create account") }
                }
            }
            if (busy) CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
}
