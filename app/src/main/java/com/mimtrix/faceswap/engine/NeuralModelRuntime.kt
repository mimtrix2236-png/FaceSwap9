package com.mimtrix.faceswap.engine

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File

/**
 * Owns the ONNX sessions used by the neural swap pipeline.
 * Image tensor preparation and face compositing stay in the app's Kotlin code.
 */
class NeuralModelRuntime private constructor(
    private val environment: OrtEnvironment,
    val swapper: OrtSession,
    val embeddingConverter: OrtSession,
    val faceRecognizer: OrtSession
) : Closeable {

    override fun close() {
        faceRecognizer.close()
        embeddingConverter.close()
        swapper.close()
    }

    companion object {
        fun open(models: Map<String, File>): NeuralModelRuntime {
            val required = listOf(
                "ghost_1_256.onnx",
                "crossface_ghost.onnx",
                "arcface_w600k_r50.onnx"
            )
            required.forEach { name ->
                require(models[name]?.isFile == true) { "Falta el modelo neuronal: $name" }
            }

            val environment = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
            }
            return try {
                val swapper = environment.createSession(models.getValue(required[0]).absolutePath, options)
                val converter = environment.createSession(models.getValue(required[1]).absolutePath, options)
                val recognizer = environment.createSession(models.getValue(required[2]).absolutePath, options)
                NeuralModelRuntime(environment, swapper, converter, recognizer)
            } catch (error: Exception) {
                options.close()
                throw error
            }
        }
    }
}
