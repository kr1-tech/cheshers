package app.cheshers.feature.game.puzzle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
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
import app.cheshers.core.data.local.entity.ProfileEntity
import app.cheshers.core.data.repository.ProgressionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PuzzleMenuViewModel @Inject constructor(private val progression: ProgressionRepository) : ViewModel() {
    private val solved = MutableStateFlow<Set<String>>(emptySet())
    val profile: StateFlow<ProfileEntity?> = progression.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val solvedIds: StateFlow<Set<String>> = solved.asStateFlow()
    init {
        viewModelScope.launch { runCatching { progression.ensureInitialized() } }
        progression.observeSolvedPuzzles().onEach { solved.value = it.toSet() }.launchIn(viewModelScope)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleMenuScreen(
    onBack: () -> Unit,
    onDaily: () -> Unit,
    onPuzzle: (String) -> Unit,
    viewModel: PuzzleMenuViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val solved by viewModel.solvedIds.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Puzzles") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(onClick = onDaily, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Daily challenge", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("A new puzzle every day · ${profile?.dailyPuzzleStreak ?: 0} day streak")
                        }
                    }
                }
            }
            item { Text("Curated tactics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(PuzzleCatalog.puzzles, key = { it.id }) { puzzle ->
                val baseId = puzzle.id
                val isSolved = solved.any { it == baseId || it.startsWith("$baseId-") }
                Card(onClick = { onPuzzle(puzzle.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(puzzle.title, fontWeight = FontWeight.SemiBold)
                            Text("${puzzle.theme} · ${puzzle.objective}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (isSolved) Icon(Icons.Default.CheckCircle, "Solved", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
