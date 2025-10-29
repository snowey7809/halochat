package com.rapo.haloai.presentation.viewmodel

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapo.haloai.data.database.entities.ModelEntity
import com.rapo.haloai.data.database.entities.ModelFormat
import com.rapo.haloai.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class GreetingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository
) : ViewModel() {
    
    private val _isGreetingPlayed = MutableStateFlow(false)
    val isGreetingPlayed = _isGreetingPlayed.asStateFlow()
    
    private val _greetingText = MutableStateFlow("Welcome to Halo AI powered by SCIT")
    val greetingText = _greetingText.asStateFlow()
    
    private var tts: TextToSpeech? = null
    
    init {
        // Initialize TTS
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }
    
    fun playGreeting() {
        viewModelScope.launch {
            if (!_isGreetingPlayed.value) {
                val greeting = "Welcome to Halo AI powered by S C I T"
                tts?.speak(greeting, TextToSpeech.QUEUE_FLUSH, null, null)
                _isGreetingPlayed.value = true
            }
        }
    }
    
    fun stopGreeting() {
        tts?.stop()
    }
    
    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}