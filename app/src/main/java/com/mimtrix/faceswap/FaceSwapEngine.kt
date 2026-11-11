package com.mimtrix.faceswap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * On-device starter engine: detects one face in each photo and blends an aligned
 * face crop into the target. This is a local geometric compositor, not a neural
 * identity-transfer model; it deliberately reports that limitation to the UI.
 */
class FaceSwapEngine(private val context: Context) {

    suspend fun swap(targetUri: Uri, sourceUri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val target = decode(targetUri)
        val source = decode(sourceUri)

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        val detector = FaceDetection.getClient(options)

        try {
            val targetFaces = Tasks.await(
                detector.process(InputImage.fromBitmap(target, 0)),
                25, TimeUnit.SECONDS
            )
            val sourceFaces = Tasks.await(
                detector.process(InputImage.fromBitmap(source, 0)),
                25, TimeUnit.SECONDS
            )

            if (targetFaces.isEmpty()) {
                throw FaceSwapException("No se detectó un rostro en la foto de destino. Prueba con una cara de frente y bien iluminada.")
            }
            if (sourceFaces.isEmpty()) {
                throw FaceSwapException("No se detectó un rostro en la foto de referencia. Prueba con una cara visible y bien iluminada.")
            }

            val targetFace = targetFaces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
            val sourceFace = sourceFaces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!

            val sourceRect = paddedRect(sourceFace.boundingBox, source.width, source.height, 0.18f, 0.16f)
            val targetRect = paddedRect(targetFace.boundingBox, target.width, target.height, 0.10f, 0.08f)
            val faceCrop = Bitmap.createBitmap(
                source, sourceRect.left, sourceRect.top, sourceRect.width(), sourceRect.height()
            )

            val output = target.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(output)
            val bounds = RectF(targetRect)
            val path = Path().apply { addOval(bounds, Path.Direction.CW) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

            val save = canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(faceCrop, null, bounds, paint)
            canvas.restoreToCount(save)

            if (faceCrop !== source) faceCrop.recycle()
            if (source !== target) source.recycle()
            output
        } catch (e: FaceSwapException) {
            target.recycle()
            source.recycle()
            throw e
        } catch (e: Exception) {
            target.recycle()
            source.recycle()
            val message = when {
                e.message?.contains("Timed out", ignoreCase = true) == true ->
                    "El análisis facial tardó demasiado. Inténtalo con fotografías más pequeñas."
                else -> "No se pudo procesar las fotografías: ${e.localizedMessage ?: "error desconocido"}"
            }
            throw FaceSwapException(message, e)
        } finally {
            detector.close()
        }
    }

    private fun decode(uri: Uri): Bitmap {
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
        return bitmap ?: throw FaceSwapException("No se pudo abrir una de las fotografías.")
    }

    private fun paddedRect(
        rect: Rect,
        imageWidth: Int,
        imageHeight: Int,
        horizontalPadding: Float,
        verticalPadding: Float
    ): Rect {
        val dx = (rect.width() * horizontalPadding).toInt()
        val dy = (rect.height() * verticalPadding).toInt()
        val left = (rect.left - dx).coerceAtLeast(0)
        val top = (rect.top - dy).coerceAtLeast(0)
        val right = (rect.right + dx).coerceAtMost(imageWidth)
        val bottom = (rect.bottom + dy).coerceAtMost(imageHeight)
        return Rect(left, top, right, bottom)
    }
}

class FaceSwapException(message: String, cause: Throwable? = null) : Exception(message, cause)
