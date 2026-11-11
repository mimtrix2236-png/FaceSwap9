package com.mimtrix.faceswap.engine

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File

/**
 * Owns ONNX sessions used by the neural swap pipeline.
 * Tensor preparation, face alignment and compositing remain app-owned Kotlin code.
 */
class NeuralModelRuntime private constructor(
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
            val options = OrtSession.SessionOptions()
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            val opened = mutableListOf<OrtSession>()
            try {
                val swapper = environment.createSession(models.getValue(required[0]).absolutePath, options)
                    .also(opened::add)
                val converter = environment.createSession(models.getValue(required[1]).absolutePath, options)
                    .also(opened::add)
                val recognizer = environment.createSession(models.getValue(required[2]).absolutePath, options)
                    .also(opened::add)
                return NeuralModelRuntime(swapper, converter, recognizer)
            } catch (error: Exception) {
                opened.asReversed().forEach { runCatching { it.close() } }
                throw error
            } finally {
                options.close()
            }
        }
    }
}
