package com.rapo.haloai.data.model

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.rapo.haloai.data.database.entities.ModelEntity
import com.rapo.haloai.data.database.entities.ModelFormat
import com.rapo.haloai.data.tokenizer.ONNXTokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.nio.IntBuffer
import javax.inject.Inject

class ONNXModelRuntime @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelRuntime {

    private var session: OrtSession? = null
    private var tokenizer: ONNXTokenizer? = null
    private var environment: OrtEnvironment? = null
    private var isModelLoaded = false

    override suspend fun initializeModel(model: ModelEntity): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (model.format != ModelFormat.ONNX) {
                    return@withContext Result.failure(IllegalArgumentException("Model format is not ONNX"))
                }

                environment = OrtEnvironment.getEnvironment()
                val sessionOptions = OrtSession.SessionOptions()
                session = environment!!.createSession(model.path, sessionOptions)

                val vocabPath = model.path.replaceAfterLast("/", "vocab.json")
                tokenizer = ONNXTokenizer(vocabPath)

                isModelLoaded = true
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun generateResponse(prompt: String, maxTokens: Int): Flow<String> {
        return flow {
            if (!isModelLoaded || session == null || tokenizer == null || environment == null) {
                throw IllegalStateException("Model not initialized")
            }

            val fullPrompt = "<|user|>\n$prompt<|end|>\n<|assistant|>"
            val inputIds = tokenizer!!.encode(fullPrompt)

            var generatedTokens = 0
            var currentInputIds = inputIds

            while (generatedTokens < maxTokens) {
                val inputName = session!!.inputNames.first()
                val shape = longArrayOf(1, currentInputIds.size.toLong())
                val buffer = IntBuffer.wrap(currentInputIds)
                val inputTensor = OnnxTensor.createTensor(environment!!, buffer, shape)
                var shouldBreak = false

                inputTensor.use { tensor ->
                    session!!.run(mapOf(inputName to tensor)).use { result ->
                        val outputTensor = result.first().value as Array<Array<FloatArray>>
                        val logits = outputTensor[0][0]
                        val nextTokenId = logits.indices.maxByOrNull { logits[it] } ?: -1

                        if (nextTokenId == tokenizer!!.eosTokenId) {
                            shouldBreak = true
                        } else {
                            val decodedToken = tokenizer!!.decode(intArrayOf(nextTokenId))
                            emit(decodedToken)

                            currentInputIds += nextTokenId
                            generatedTokens++
                        }
                    }
                }
                if (shouldBreak) break
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun stopGeneration() { /* No-op */ }

    override suspend fun release() {
        withContext(Dispatchers.IO) {
            session?.close()
            environment?.close()
            session = null
            environment = null
            isModelLoaded = false
        }
    }

    override fun getPerformanceMetrics(): PerformanceMetrics {
        return PerformanceMetrics(0f, 0L, 0L, "ONNX")
    }

    override fun isReady(): Boolean = isModelLoaded
}
