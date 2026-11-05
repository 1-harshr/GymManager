package com.harsh.gymmanager.ai

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.harsh.gymmanager.domain.insights.OnDeviceModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gemini Nano through ML Kit, on phones whose AICore has it (recent Pixels and Galaxy flagships).
 * Everywhere else it answers null and the app uses its own wording.
 */
class GeminiNanoModel : OnDeviceModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val model by lazy { Generation.getClient() }
    private var downloadStarted = false

    override fun generate(instructions: String, prompt: String, onResult: (String?) -> Unit) {
        scope.launch {
            val reply = try {
                when (model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> model.generateContent(
                        generateContentRequest(TextPart(prompt)) {
                            systemInstruction = SystemInstruction(instructions)
                            temperature = 0.3f
                            maxOutputTokens = 160
                        }
                    ).candidates.firstOrNull()?.text
                    FeatureStatus.DOWNLOADABLE -> {
                        // The system fetches the model once; later briefs can use it.
                        if (!downloadStarted) {
                            downloadStarted = true
                            scope.launch { runCatching { model.download().collect {} } }
                        }
                        null
                    }
                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "On-device model unavailable", e)
                null
            }
            onResult(reply)
        }
    }

    private companion object {
        const val TAG = "GeminiNano"
    }
}
