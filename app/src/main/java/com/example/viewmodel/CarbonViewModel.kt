package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.util.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

sealed interface AuthState {
    object Unauthenticated : AuthState
    object Loading : AuthState
    data class Authenticated(val user: User) : AuthState
    data class Error(val message: String) : AuthState
}

class CarbonViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("carbon_settings", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(application)
    private val repository = CarbonRepository(database.carbonDao(), database.userDao())

    // --- Dynamic Theme Support ---
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("is_dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        sharedPrefs.edit().putBoolean("is_dark_mode", newValue).apply()
    }

    // --- Voice Assistant TTS Engine ---
    private var tts: TextToSpeech? = null
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private fun initTTS() {
        try {
            tts = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                } else {
                    Log.e("CarbonViewModel", "TTS Initialization failed")
                }
            }
        } catch (e: Exception) {
            Log.e("CarbonViewModel", "TTS initialization exception", e)
        }
    }

    fun speak(text: String) {
        if (text.isEmpty()) return
        if (tts == null) {
            initTTS()
        }
        viewModelScope.launch {
            // Introduce a short delay to ensure TTS engine is ready if dynamically initialized
            if (tts == null) {
                kotlinx.coroutines.delay(200)
            }
            tts?.let {
                if (_isSpeaking.value) {
                    it.stop()
                }
                _isSpeaking.value = true
                // Speak with listener for stop event
                it.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }
                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
                it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CarbonTrace_TTS")
            }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    override fun onCleared() {
        super.onCleared()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("CarbonViewModel", "Error cleaning up TTS", e)
        }
    }

    // --- Auth States ---
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(getSavedLoggedInUserEmail())
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    // --- State variables ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allActivities: StateFlow<List<CarbonActivity>> = _currentUserEmail
        .flatMapLatest { email ->
            if (email.isNullOrEmpty()) {
                flowOf(emptyList())
            } else {
                repository.getActivitiesForUser(email)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _dailyTarget = MutableStateFlow(getSavedDailyTarget())
    val dailyTarget: StateFlow<Double> = _dailyTarget.asStateFlow()

    private val _userName = MutableStateFlow(getSavedUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()

    // --- AI Insight States ---
    private val _aiRecommendation = MutableStateFlow<String>("")
    val aiRecommendation: StateFlow<String> = _aiRecommendation.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    // --- Chat Room States (AI Advisor) ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("advisor", "Hello! I am your AI Eco-Advisor. Tell me about your lifestyle or ask me for tips to lower your carbon footprint! You can also click 'Generate Personalized Plan' to analyze your logged activities.")
    ))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    init {
        // Load session on start
        loadUserSession()
    }

    private fun getSavedLoggedInUserEmail(): String? {
        return sharedPrefs.getString("logged_in_user_email", null)
    }

    private fun loadUserSession() {
        val email = getSavedLoggedInUserEmail()
        if (email != null) {
            _authState.value = AuthState.Loading
            viewModelScope.launch {
                val user = repository.getUserByEmail(email)
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user)
                    _currentUserEmail.value = user.email
                    _userName.value = user.name
                    // Automatically fetch initial plan when they resume
                    generateFeedbackPlan(silent = true)
                } else {
                    logout()
                }
            }
        } else {
            _authState.value = AuthState.Unauthenticated
            _currentUserEmail.value = null
        }
    }

    // --- User Authorization logic ---
    fun signUp(email: String, name: String, passwordText: String, onResult: (Boolean, String) -> Unit) {
        val trimmedEmail = email.trim().lowercase()
        val trimmedName = name.trim()
        if (trimmedEmail.isEmpty() || trimmedName.isEmpty() || passwordText.isEmpty()) {
            onResult(false, "All fields are required")
            return
        }

        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val existing = repository.getUserByEmail(trimmedEmail)
                if (existing != null) {
                    _authState.value = AuthState.Unauthenticated
                    onResult(false, "An account with this email already exists")
                    return@launch
                }

                val salt = HashUtils.generateSalt()
                val hash = HashUtils.hashPassword(passwordText, salt)

                val user = User(
                    email = trimmedEmail,
                    name = trimmedName,
                    passwordHash = hash,
                    salt = salt
                )
                repository.registerUser(user)
                loginAfterAuth(user)
                onResult(true, "Registration successful")
            } catch (e: Exception) {
                Log.e("CarbonViewModel", "SignUp Error", e)
                _authState.value = AuthState.Unauthenticated
                onResult(false, "Registration failed: ${e.message}")
            }
        }
    }

    fun login(email: String, passwordText: String, onResult: (Boolean, String) -> Unit) {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty() || passwordText.isEmpty()) {
            onResult(false, "Email and password are required")
            return
        }

        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val user = repository.getUserByEmail(trimmedEmail)
                if (user == null) {
                    _authState.value = AuthState.Unauthenticated
                    onResult(false, "No account found with this email")
                    return@launch
                }

                val targetHash = HashUtils.hashPassword(passwordText, user.salt)
                if (targetHash == user.passwordHash) {
                    val updatedUser = user.copy(lastLoginTime = System.currentTimeMillis())
                    repository.updateUser(updatedUser)
                    loginAfterAuth(updatedUser)
                    onResult(true, "Authentication successful")
                } else {
                    _authState.value = AuthState.Unauthenticated
                    onResult(false, "Incorrect password")
                }
            } catch (e: Exception) {
                Log.e("CarbonViewModel", "Login Error", e)
                _authState.value = AuthState.Unauthenticated
                onResult(false, "Login failed: ${e.message}")
            }
        }
    }

    private fun loginAfterAuth(user: User) {
        sharedPrefs.edit()
            .putString("logged_in_user_email", user.email)
            .putString("user_name", user.name)
            .apply()

        _authState.value = AuthState.Authenticated(user)
        _currentUserEmail.value = user.email
        _userName.value = user.name
        generateFeedbackPlan(silent = true)
    }

    fun logout() {
        sharedPrefs.edit()
            .remove("logged_in_user_email")
            .apply()
        _authState.value = AuthState.Unauthenticated
        _currentUserEmail.value = null
        _userName.value = "Eco Champion"
        _aiRecommendation.value = ""
        _chatMessages.value = listOf(
            ChatMessage("advisor", "You've successfully signed out. Log in to access your secure personalized eco statistics!")
        )
    }

    // --- Database Operations ---
    fun logActivity(category: String, subCategory: String, value: Double, unit: String, co2Emitted: Double, notes: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val activity = CarbonActivity(
                category = category,
                subCategory = subCategory,
                value = value,
                unit = unit,
                co2Emitted = co2Emitted,
                notes = notes,
                timestamp = System.currentTimeMillis(),
                userEmail = _currentUserEmail.value ?: ""
            )
            repository.insertActivity(activity)
            // Trigger background feedback update
            generateFeedbackPlan(silent = true)
        }
    }

    fun deleteActivity(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteActivityById(id)
            generateFeedbackPlan(silent = true)
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            val email = _currentUserEmail.value
            if (email != null) {
                repository.clearAllForUser(email)
            } else {
                repository.clearAll()
            }
            _aiRecommendation.value = ""
            _chatMessages.value = listOf(
                ChatMessage("advisor", "I've reset your logging footprint. Log new activities described in Transport, Diet, or Energy tabs and I'll generate personalized plans!")
            )
        }
    }

    // --- Preferences Operations ---
    private fun getSavedDailyTarget(): Double {
        return sharedPrefs.getFloat("daily_target", 15.0f).toDouble()
    }

    fun updateDailyTarget(target: Double) {
        _dailyTarget.value = target
        sharedPrefs.edit().putFloat("daily_target", target.toFloat()).apply()
    }

    private fun getSavedUserName(): String {
        return sharedPrefs.getString("user_name", "Eco Champion") ?: "Eco Champion"
    }

    fun updateUserName(name: String) {
        _userName.value = name
        sharedPrefs.edit().putString("user_name", name).apply()
    }

    // --- Footprint Calculator/Tracker Preferences ---
    private val _baselineTransportMiles = MutableStateFlow(sharedPrefs.getFloat("baseline_transport_miles", 60.0f).toDouble())
    val baselineTransportMiles: StateFlow<Double> = _baselineTransportMiles.asStateFlow()

    private val _baselineDietMeatMeals = MutableStateFlow(sharedPrefs.getFloat("baseline_diet_meat_meals", 7.0f).toDouble())
    val baselineDietMeatMeals: StateFlow<Double> = _baselineDietMeatMeals.asStateFlow()

    private val _baselineEnergyKwh = MutableStateFlow(sharedPrefs.getFloat("baseline_energy_kwh", 40.0f).toDouble())
    val baselineEnergyKwh: StateFlow<Double> = _baselineEnergyKwh.asStateFlow()

    private val _reductionGoalPercent = MutableStateFlow(sharedPrefs.getFloat("reduction_goal_percent", 20.0f).toDouble())
    val reductionGoalPercent: StateFlow<Double> = _reductionGoalPercent.asStateFlow()

    fun updateBaselineTransportMiles(value: Double) {
        _baselineTransportMiles.value = value
        sharedPrefs.edit().putFloat("baseline_transport_miles", value.toFloat()).apply()
    }

    fun updateBaselineDietMeatMeals(value: Double) {
        _baselineDietMeatMeals.value = value
        sharedPrefs.edit().putFloat("baseline_diet_meat_meals", value.toFloat()).apply()
    }

    fun updateBaselineEnergyKwh(value: Double) {
        _baselineEnergyKwh.value = value
        sharedPrefs.edit().putFloat("baseline_energy_kwh", value.toFloat()).apply()
    }

    fun updateReductionGoalPercent(value: Double) {
        _reductionGoalPercent.value = value
        sharedPrefs.edit().putFloat("reduction_goal_percent", value.toFloat()).apply()
    }

    // --- Gemini Interactive AI Calculations & Tips ---
    fun generateFeedbackPlan(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _isAiLoading.value = true
                _aiError.value = null
            }
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    if (!silent) {
                        _aiRecommendation.value = "To receive personalized Gemini AI suggestions, please add your real GEMINI_API_KEY in the AI Studio Secrets panel."
                    }
                    return@launch
                }

                val logsList = allActivities.value.take(25) // Analyze latest 25 logs
                val summaryPrompt = if (logsList.isEmpty()) {
                    "Hello! State your name to the user (${_userName.value}) as a friendly AI Eco Assistant. " +
                    "Since the user has no logged activities yet, explain 3 simple everyday actions a beginner can take to save carbon in daily transport, meal choices, and home electricity usage. Keep the guidance visual, concise (3 sentences max per category), and encouraging!"
                } else {
                    val logsText = logsList.joinToString("\n") { log ->
                        "- ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(log.timestamp))}: " +
                        "${log.category} (${log.subCategory}) log: ${log.value} ${log.unit} causing ${String.format("%.2f", log.co2Emitted)} kg CO2e"
                    }
                    "As an expert Carbon Footprint Counselor, analyze these recent logs for ${_userName.value}:\n\n$logsText\n\n" +
                    "1. Summarize their total impact and highlight which category (Transport, Diet, Energy, Consumption etc.) is their largest source of emissions.\n" +
                    "2. Provide 3 specific, practical, highly actionable recommendations they can take right now to reduce this footprint, including estimated carbon savings (e.g. 'Use cold wash to save ~0.8kg CO2 per load').\n" +
                    "3. Format your advice elegantly with bold titles and neat spacing, keeping the tone supportive, direct, and conversational."
                }

                val systemPrompt = "You are a friendly, expert Sustainability Consultant giving concise, constructive advice on reducing personal carbon footprint. Keep summaries short, practical, and highly scannable."

                val responseText = withContext(Dispatchers.IO) {
                    val requestBody = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = summaryPrompt)))),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                        generationConfig = GeminiGenerationConfig(temperature = 0.5f)
                    )
                    val response = RetrofitClient.service.generateContent(apiKey, requestBody)
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: "No insights could be generated right now. Please try again!"
                }

                _aiRecommendation.value = responseText

            } catch (e: Exception) {
                Log.e("CarbonViewModel", "Error fetching Gemini insights", e)
                if (!silent) {
                    _aiError.value = "Failed to connect to AI Advisor: ${e.message}"
                }
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun sendChatMessage(userText: String) {
        if (userText.trim().isEmpty()) return

        val userMsg = ChatMessage("user", userText)
        _chatMessages.value = _chatMessages.value + userMsg
        _isChatLoading.value = true

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _chatMessages.value = _chatMessages.value + ChatMessage("advisor", "I want to help you, but I need a valid Gemini API Key! Please configure the GEMINI_API_KEY in the Secrets panel in AI Studio to permit live conversation.")
                    _isChatLoading.value = false
                    return@launch
                }

                val logsList = allActivities.value.take(10)
                val logsContext = if (logsList.isEmpty()) {
                    "No logged activities yet."
                } else {
                    logsList.joinToString(", ") { "${it.category} (${it.subCategory}) ${it.co2Emitted} kg" }
                }

                val chatPrompt = "User logs context (recently tracked): $logsContext\n\n" +
                        "Conversation history:\n" +
                        _chatMessages.value.filter { it.sender != "system" }.takeLast(8).joinToString("\n") { "${it.sender}: ${it.text}" } +
                        "\n\nuser: $userText\n" +
                        "advisor (give a short, helpful, conversational response in 2-3 sentences. No long preamble, focus on helping them reduce carbon):"

                val systemPrompt = "You are a professional, helpful, and concise household carbon footprint reduction advisor. Answer questions directly, giving scientifically backed everyday eco advice with a pleasant attitude."

                val responseText = withContext(Dispatchers.IO) {
                    val requestBody = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = chatPrompt)))),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                        generationConfig = GeminiGenerationConfig(temperature = 0.6f)
                    )
                    val response = RetrofitClient.service.generateContent(apiKey, requestBody)
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: "I'm having a brief connection hitch. What else can I guide you with?"
                }

                _chatMessages.value = _chatMessages.value + ChatMessage("advisor", responseText)

            } catch (e: Exception) {
                Log.e("CarbonViewModel", "Chat API error", e)
                _chatMessages.value = _chatMessages.value + ChatMessage("advisor", "I ran into an issue finding that answer. Please verify your internet connection. (Error: ${e.message})")
            } finally {
                _isChatLoading.value = false
            }
        }
    }
}

data class ChatMessage(
    val sender: String, // "user" or "advisor"
    val text: String,
    val time: Long = System.currentTimeMillis()
)
