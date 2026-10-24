package app.cheshers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cheshers.core.ai.OpenRouterModel
import app.cheshers.core.ai.OpenRouterService
import app.cheshers.core.data.local.entity.CosmeticEntity
import app.cheshers.core.data.repository.CheshersSettings
import app.cheshers.core.data.repository.ProgressionRepository
import app.cheshers.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: CheshersSettings = CheshersSettings(),
    val models: List<OpenRouterModel> = OpenRouterModel.defaults,
    val cosmetics: List<CosmeticEntity> = emptyList(),
    val refreshingModels: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val progression: ProgressionRepository,
    private val openRouter: OpenRouterService,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    init {
        settingsRepository.settings.onEach { value -> _state.update { it.copy(settings = value) } }.launchIn(viewModelScope)
        progression.observeCosmetics().onEach { value -> _state.update { it.copy(cosmetics = value) } }.launchIn(viewModelScope)
        viewModelScope.launch { runCatching { progression.ensureInitialized() } }
    }

    fun preview(value: CheshersSettings) = _state.update { it.copy(settings = value) }
    fun save(value: CheshersSettings) {
        _state.update { it.copy(settings = value) }
        viewModelScope.launch {
            runCatching { settingsRepository.save(value) }.onFailure { _state.update { it.copy(message = "Settings could not be saved") } }
        }
    }

    fun refreshModels() {
        val key = _state.value.settings.openRouterApiKey
        if (key.isBlank()) { _state.update { it.copy(message = "Add an OpenRouter API key first") }; return }
        viewModelScope.launch {
            _state.update { it.copy(refreshingModels = true) }
            try {
                val remote = openRouter.models("Bearer $key").data.mapNotNull { model ->
                    model.id.takeIf { it.isNotBlank() }?.let { OpenRouterModel(it, model.name.ifBlank { it }) }
                }.sortedBy { it.label.lowercase() }
                _state.update { it.copy(models = (OpenRouterModel.defaults + remote).distinctBy(OpenRouterModel::id), refreshingModels = false) }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _state.update { it.copy(refreshingModels = false, message = "Could not refresh models. The saved model and custom model ID still work.") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
