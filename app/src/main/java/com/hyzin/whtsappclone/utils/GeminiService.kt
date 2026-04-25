package com.hyzin.whtsappclone.utils

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiService {
    // Note: Replace with your actual Gemini API Key or use a secure way to load it
    private const val API_KEY = "YOUR_GEMINI_API_KEY"
    
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = API_KEY,
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 1024
        }
    )

    suspend fun correctGrammar(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = "Act as a professional writer. Correct the grammar and spelling of the following sentence while keeping its meaning intact. Only return the corrected sentence: \"$text\""
            val response = model.generateContent(prompt)
            response.text?.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun rewriteSentence(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = "Act as a professional writer. Rewrite the following sentence to make it more engaging and professional. Only return the rewritten sentence: \"$text\""
            val response = model.generateContent(prompt)
            response.text?.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createSentence(topic: String): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = "Create a short, friendly chat message about: \"$topic\". Only return the message text."
            val response = model.generateContent(prompt)
            response.text?.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getQuickReplySuggestion(currentText: String): String? = withContext(Dispatchers.IO) {
        try {
            if (API_KEY == "YOUR_GEMINI_API_KEY") return@withContext "Set API Key✨"
            
            val prompt = "Based on this partial message: \"$currentText\", suggest a short, natural completion or a quick reply. Return ONLY the suggested completion text (max 5 words)."
            val response = model.generateContent(prompt)
            response.text?.trim()?.removePrefix(currentText)?.trim()
        } catch (e: Exception) {
            null
        }
    }
}
