package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.dao.TaskDao
import com.example.data.entity.TaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val time: Long = System.currentTimeMillis()
)

class DashboardViewModel(private val taskDao: TaskDao) : ViewModel() {

    // Local DB tasks
    val tasks: StateFlow<List<TaskItem>> = taskDao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chat history
    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(isUser = false, text = "Hello! I am your Gemini AI Assistant. How can I help you organize your daily tasks or brainstorm ideas today?")
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _parsedSuggestions = MutableStateFlow<List<String>>(emptyList())
    val parsedSuggestions: StateFlow<List<String>> = _parsedSuggestions.asStateFlow()

    // Key Validation
    fun isApiKeyAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && !key.contains("MY_GEMINI_API_KEY") && !key.contains("PLACEHOLDER")
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        // Add User Message
        val updatedMessages = _messages.value + ChatMessage(isUser = true, text = text)
        _messages.value = updatedMessages
        _isLoading.value = true
        _parsedSuggestions.value = emptyList()

        viewModelScope.launch {
            try {
                if (!isApiKeyAvailable()) {
                    _messages.value = updatedMessages + ChatMessage(
                        isUser = false,
                        text = "⚠️ Gemini API key is missing or is using the default placeholder. Please open the 'Secrets' panel in AI Studio, find the GEMINI_API_KEY field, add your real API key, and re-run your build configuration."
                    )
                    _isLoading.value = false
                    return@launch
                }

                // Build full context (last 6 turns for conversational accuracy)
                val conversationHistory = updatedMessages.takeLast(6).map { msg ->
                    Content(parts = listOf(Part(text = msg.text)))
                }

                val request = GenerateContentRequest(
                    contents = conversationHistory,
                    systemInstruction = Content(parts = listOf(Part(
                        text = "You are a highly analytical, empowering personal task copilot and brain-scaffolder. " +
                                "Provide structural, clear, concise answers. If you recommend actions or tasks, " +
                                "phrase them as simple, atomic-action bullet points starting with standard bullets (e.g., '- Buy weekly groceries' or '- Complete design draft') " +
                                "so the app can parse and save them to the planner."
                    )))
                )

                val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                val aiText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "I apologize, but I received an empty response. Could you try rephrasing your question?"

                _messages.value = _messages.value + ChatMessage(isUser = false, text = aiText)
                
                // Parse potential tasks from response
                parseTasksFromAiResponse(aiText)

            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    isUser = false,
                    text = "Sorry, I ran into an error communicating with the server: ${e.localizedMessage ?: "Unknown network error"}."
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseTasksFromAiResponse(text: String) {
        val lines = text.lines()
        val suggestions = mutableListOf<String>()
        val bulletPrefixes = listOf("- [ ] ", "- ", "* [ ] ", "* ", "• ")
        
        for (line in lines) {
            val trimmedLine = line.trim()
            for (prefix in bulletPrefixes) {
                if (trimmedLine.startsWith(prefix) && trimmedLine.length > prefix.length) {
                    val taskName = trimmedLine.substring(prefix.length).trim()
                    if (taskName.isNotEmpty() && taskName.length < 100) {
                        suggestions.add(taskName)
                        break
                    }
                }
            }
        }
        _parsedSuggestions.value = suggestions
    }

    fun addTask(title: String, description: String = "", category: String = "General") {
        viewModelScope.launch {
            taskDao.insertTask(TaskItem(title = title, description = description, category = category))
        }
    }

    fun toggleTaskCompletion(task: TaskItem) {
        viewModelScope.launch {
            taskDao.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun removeTask(task: TaskItem) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            taskDao.deleteCompletedTasks()
        }
    }

    fun clearChatHistory() {
        _messages.value = listOf(
            ChatMessage(isUser = false, text = "Hello! Chat history is cleared. What task or dynamic plan should we design next?")
        )
        _parsedSuggestions.value = emptyList()
    }
}

class DashboardViewModelFactory(private val taskDao: TaskDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(taskDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
