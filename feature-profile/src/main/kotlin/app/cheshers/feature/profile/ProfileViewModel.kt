package app.cheshers.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cheshers.core.chess.RatingCategory
import app.cheshers.core.data.local.entity.CosmeticEntity
import app.cheshers.core.data.local.entity.GameEntity
import app.cheshers.core.data.local.entity.ProfileEntity
import app.cheshers.core.data.repository.GameRepository
import app.cheshers.core.data.repository.ProgressionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: ProfileEntity? = null,
    val history: List<GameEntity> = emptyList(),
    val cosmetics: List<CosmeticEntity> = emptyList(),
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val leaderboardLoading: Boolean = false,
    val leaderboardScope: String = "global",
    val leaderboardCategory: RatingCategory = RatingCategory.BLITZ,
    val leaderboardError: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val progression: ProgressionRepository,
    private val games: GameRepository,
    private val leaderboards: LeaderboardRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { runCatching { progression.ensureInitialized() } }
        progression.observeProfile().onEach { value -> _state.update { it.copy(profile = value) } }.launchIn(viewModelScope)
        progression.observeCosmetics().onEach { value -> _state.update { it.copy(cosmetics = value) } }.launchIn(viewModelScope)
        games.observeHistory().onEach { value -> _state.update { it.copy(history = value) } }.launchIn(viewModelScope)
        loadLeaderboard()
    }

    fun updateProfile(username: String, avatarId: String, country: String) {
        viewModelScope.launch {
            val result = progression.updateProfile(username, avatarId, country)
            result.onSuccess { _state.update { it.copy(message = "Profile saved") } }
                .onFailure { error -> _state.update { it.copy(message = error.message ?: "Profile could not be saved") } }
        }
    }

    fun deleteGame(id: String) = viewModelScope.launch { runCatching { games.deleteGame(id) } }

    fun leaderboardFilters(scope: String, category: RatingCategory) {
        _state.update { it.copy(leaderboardScope = scope, leaderboardCategory = category) }
        loadLeaderboard()
    }

    fun loadLeaderboard() {
        val scope = _state.value.leaderboardScope
        val category = _state.value.leaderboardCategory
        viewModelScope.launch {
            _state.update { it.copy(leaderboardLoading = true, leaderboardError = false) }
            leaderboards.load(scope, category).onSuccess { entries ->
                _state.update { it.copy(leaderboard = entries, leaderboardLoading = false) }
            }.onFailure {
                _state.update { it.copy(leaderboard = emptyList(), leaderboardLoading = false, leaderboardError = true) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
