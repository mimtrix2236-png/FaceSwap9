package com.mimtrix.faceswap.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the neural model bundle into private app storage.
 * Model weights are never bundled into the APK or uploaded with user photos.
 */
class NeuralModelStore(context: Context) {
    private val modelDir = File(context.filesDir, "neural-models").apply { mkdirs() }

    data class Model(
        val fileName: String,
        val modelUrl: String,
        val checksumUrl: String,
        val license: String
    )

    companion object {
        // Ghost is Apache-2.0; ArcFace and FaceFusion CrossFace weights have
        // separate non-commercial / OpenRAIL-M terms. See MODEL_LICENSES.md.
        val REQUIRED_MODELS = listOf(
            Model(
                "ghost_1_256.onnx",
                "https://github.com/facefusion/facefusion-assets/releases/download/models-3.0.0/ghost_1_256.onnx",
                "https://github.com/facefusion/facefusion-assets/releases/download/models-3.0.0/ghost_1_256.hash",
                "Apache-2.0"
            ),
            Model(
                "crossface_ghost.onnx",
                "https://github.com/facefusion/facefusion-assets/releases/download/models-3.4.0/crossface_ghost.onnx",
                "https://github.com/facefusion/facefusion-assets/releases/download/models-3.4.0/crossface_ghost.hash",
                "OpenRAIL-M"
            ),
            Model(
                "arcface_w600k_r50.onnx",
                "https://github.com/facefusion/facefusion-assets/releases/download/models-3.0.0/arcface_w600k_r50.onnx",
                "https://github.com/facefusion/facefusion-assets/releases/download/models-3.0.0/arcface_w600k_r50.hash",
                "Non-Commercial"
            )
        )
    }

    suspend fun prepareAll(onProgress: (String, Long, Long) -> Unit = { _, _, _ -> }): Map<String, File> =
        withContext(Dispatchers.IO) {
            REQUIRED_MODELS.associate { model ->
                model.fileName to ensureModel(model, onProgress)
            }
        }

    private fun ensureModel(
        model: Model,
        onProgress: (String, Long, Long) -> Unit
    ): File {
        val destination = File(modelDir, model.fileName)
        val expected = readText(model.checksumUrl).trim().lowercase()
            .substringBefore(' ').substringBefore('\n')
            .filter { it in "0123456789abcdef" }
        require(expected.length == 32 || expected.length == 64) {
            "No se pudo validar la firma del modelo ${model.fileName}."
        }

        if (destination.exists() && digest(destination, expected.length) == expected) {
            return destination
        }

        val partial = File(modelDir, "${model.fileName}.part")
        partial.delete()
        val connection = (URL(model.modelUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FaceSwap9-Android")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "No se pudo descargar ${model.fileName} (HTTP ${connection.responseCode})."
            }
            val total = connection.contentLengthLong.coerceAtLeast(-1L)
            BufferedInputStream(connection.inputStream).use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(model.fileName, downloaded, total)
                    }
                }
            }
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }

        check(digest(partial, expected.length) == expected) {
            partial.delete()
            "La verificación del modelo ${model.fileName} falló. Intenta descargarlo nuevamente."
        }
        if (destination.exists()) destination.delete()
        check(partial.renameTo(destination)) { "No se pudo guardar ${model.fileName}." }
        return destination
    }

    private fun readText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun digest(file: File, expectedLength: Int): String {
        val algorithm = if (expectedLength == 32) "MD5" else "SHA-256"
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
