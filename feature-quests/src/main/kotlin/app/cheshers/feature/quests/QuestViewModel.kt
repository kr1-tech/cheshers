package app.cheshers.feature.quests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cheshers.core.data.local.entity.*
import app.cheshers.core.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestUiState(
    val profile: ProfileEntity? = null,
    val quests: List<QuestEntity> = emptyList(),
    val achievements: List<AchievementEntity> = emptyList(),
    val cosmetics: List<CosmeticEntity> = emptyList(),
    val settings: CheshersSettings = CheshersSettings(),
    val message: String? = null,
)

@HiltViewModel
class QuestViewModel @Inject constructor(
    private val progression: ProgressionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(QuestUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { runCatching { progression.ensureInitialized() } }
        progression.observeProfile().onEach { value -> _state.update { it.copy(profile = value) } }.launchIn(viewModelScope)
        progression.observeQuests().onEach { value -> _state.update { it.copy(quests = value) } }.launchIn(viewModelScope)
        progression.observeAchievements().onEach { value -> _state.update { it.copy(achievements = value) } }.launchIn(viewModelScope)
        progression.observeCosmetics().onEach { value -> _state.update { it.copy(cosmetics = value) } }.launchIn(viewModelScope)
        settingsRepository.settings.onEach { value -> _state.update { it.copy(settings = value) } }.launchIn(viewModelScope)
    }

    fun refresh() = viewModelScope.launch { runCatching { progression.ensureQuests() } }

    fun purchase(item: CosmeticEntity) {
        viewModelScope.launch {
            val result = progression.purchaseCosmetic(item.id)
            result.onSuccess { _state.update { it.copy(message = "${item.title} unlocked") } }
                .onFailure { error -> _state.update { it.copy(message = error.message ?: "Could not unlock item") } }
        }
    }

    fun equip(item: CosmeticEntity) {
        viewModelScope.launch {
            when (CosmeticType.valueOf(item.type)) {
                CosmeticType.BOARD -> {
                    val theme = BoardTheme.valueOf(item.id.removePrefix("board_").uppercase())
                    settingsRepository.save(_state.value.settings.copy(boardTheme = theme))
                }
                CosmeticType.PIECES -> {
                    val set = PieceSet.valueOf(item.id.removePrefix("pieces_").uppercase())
                    settingsRepository.save(_state.value.settings.copy(pieceSet = set))
                }
                CosmeticType.AVATAR -> _state.value.profile?.let { profile ->
                    progression.updateProfile(profile.username, item.id, profile.country)
                }
                CosmeticType.BADGE -> Unit
            }
            _state.update { it.copy(message = "${item.title} equipped") }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
