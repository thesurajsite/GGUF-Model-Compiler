package com.suraj.ggufmodelrunner.ui


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suraj.ggufmodelrunner.data.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isModelLoading: Boolean = false,
    val isModelReady: Boolean = false,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

// AndroidViewModel (not plain ViewModel) because ChatRepository needs
// Application Context to reach assets/ and filesDir.
// No custom Factory needed — the default one handles AndroidViewModel.
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadModel()
    }

    private fun loadModel() {
        _uiState.update { it.copy(isModelLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.initModel()
                .onSuccess {
                    _uiState.update { it.copy(isModelLoading = false, isModelReady = true) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isModelLoading = false,
                            isModelReady = false,
                            errorMessage = "Failed to load model: ${e.message}"
                        )
                    }
                }
        }
    }

    fun retryLoadModel() = loadModel()

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        if (!_uiState.value.isModelReady || _uiState.value.isGenerating) return

        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(trimmed, isUser = true),
                isGenerating = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            repository.sendMessage(trimmed, maxTokens = 50)
                .onSuccess { reply ->
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(reply, isUser = false),
                            isGenerating = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            errorMessage = "Generation failed: ${e.message}"
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is cancelled around the same time onCleared() runs,
        // so cleanup uses its own short-lived scope instead — otherwise
        // repository.close() (which frees the native model) may never run.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            repository.close()
        }
    }
}