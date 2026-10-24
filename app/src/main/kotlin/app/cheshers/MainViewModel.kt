package app.cheshers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cheshers.core.data.repository.CheshersSettings
import app.cheshers.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<CheshersSettings?> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun completeOnboarding() {
        val current = settings.value ?: return
        viewModelScope.launch { settingsRepository.save(current.copy(onboardingComplete = true)) }
    }
}
