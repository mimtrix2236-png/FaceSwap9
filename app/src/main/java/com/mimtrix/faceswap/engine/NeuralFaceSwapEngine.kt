package com.mimtrix.faceswap.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * On-device GHOST 1 neural face replacement.
 * ArcFace creates the source identity embedding, CrossFace maps it to GHOST's
 * embedding space, and GHOST synthesizes the target-aligned face crop.
 * FaceFusion model preprocessing conventions are followed.
 */
class NeuralFaceSwapEngine(private val context: Context) {
    companion object {
        private const val LANDMARK_URL =
            "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
        private const val LANDMARK_FILE = "face_landmarker.task"
        private const val FACE_SIZE = 256
        private val ANCHORS = intArrayOf(33, 263, 1, 61, 291)
        private val OVAL = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
            397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
            172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
        )
        private val ARC_TEMPLATE_112 = arrayOf(
            doubleArrayOf(39.729999, 51.137999),
            doubleArrayOf(72.269999, 51.137999),
            doubleArrayOf(56.0, 68.493000),
            doubleArrayOf(42.462999, 87.010000),
            doubleArrayOf(69.537001, 87.010000)
        )
    }

    suspend fun swap(source: Bitmap, target: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        require(!source.isRecycled && !target.isRecycled) { "Las imágenes ya no están disponibles." }
        check(OpenCVLoader.initLocal()) { "No se pudo inicializar OpenCV." }

        val landmarkFile = ensureLandmarkModel()
        val detectorOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(landmarkFile.absolutePath).build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .build()
        val landmarker = FaceLandmarker.createFromOptions(context, detectorOptions)
        try {
            val sourceFace = landmarker.detect(BitmapImageBuilder(source).build())
                .faceLandmarks().firstOrNull() ?: error("No se detectó un rostro en la foto de referencia.")
            val targetFace = landmarker.detect(BitmapImageBuilder(target).build())
                .faceLandmarks().firstOrNull() ?: error("No se detectó un rostro en la foto de destino.")

            val srcRgba = Mat()
            val dstRgba = Mat()
            val srcBgr = Mat()
            val dstBgr = Mat()
            val source112 = Mat()
            val target256 = Mat()
            val srcPoints = points(sourceFace, source.width, source.height, 112)
            val dstPoints112 = points(targetFace, target.width, target.height, 112)
            val dstPoints256 = points(targetFace, target.width, target.height, FACE_SIZE)
            val srcTransform = affine(srcPoints, template(112))
            val dstTransform = affine(dstPoints256, template(FACE_SIZE))

            try {
                Utils.bitmapToMat(source, srcRgba)
                Utils.bitmapToMat(target, dstRgba)
                Imgproc.cvtColor(srcRgba, srcBgr, Imgproc.COLOR_RGBA2BGR)
                Imgproc.cvtColor(dstRgba, dstBgr, Imgproc.COLOR_RGBA2BGR)
                Imgproc.warpAffine(srcBgr, source112, srcTransform, Size(112.0, 112.0),
                    Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0, 0.0, 0.0))
                Imgproc.warpAffine(dstBgr, target256, dstTransform, Size(FACE_SIZE.toDouble(), FACE_SIZE.toDouble()),
                    Imgproc.INTER_LINEAR, Core.BORDER_REFLECT)

                val models = NeuralModelStore(context).prepareAll()
                NeuralModelRuntime.open(models).use { runtime ->
                    val env = OrtEnvironment.getEnvironment()
                    val identity = runVector(runtime.faceRecognizer, env, "input",
                        imageTensor(source112, 112, normalizeArcFace = true), longArrayOf(1, 3, 112, 112))
                    val identityNorm = l2Normalize(identity)
                    val converted = runVector(runtime.embeddingConverter, env, "input",
                        identityNorm, longArrayOf(1, 512))
                    val ghostInput = imageTensor(target256, FACE_SIZE, normalizeArcFace = false)
                    val generated = runVector(runtime.swapper, env, mapOf(
                        "source" to tensor(env, converted, longArrayOf(1, converted.size.toLong())),
                        "target" to tensor(env, ghostInput, longArrayOf(1, 3, FACE_SIZE.toLong(), FACE_SIZE.toLong()))
                    ))
                    val generatedMat = outputToBgr(generated, FACE_SIZE)
                    val mask = createFaceMask(targetFace, dstTransform, target.width, target.height)
                    val resultMat = Mat()
                    try {
                        val center = Point(
                            targetFace[1].x().toDouble() * target.width,
                            targetFace[1].y().toDouble() * target.height
                        )
                        Photo.seamlessClone(generatedMat, dstBgr, mask, center, resultMat, Photo.NORMAL_CLONE)
                        val rgba = Mat()
                        try {
                            Imgproc.cvtColor(resultMat, rgba, Imgproc.COLOR_BGR2RGBA)
                            val bitmap = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
                            Utils.matToBitmap(rgba, bitmap)
                            bitmap
                        } finally {
                            rgba.release()
                        }
                    } finally {
                        generatedMat.release()
                        mask.release()
                        resultMat.release()
                    }
                }
            } finally {
                srcRgba.release(); dstRgba.release(); srcBgr.release(); dstBgr.release()
                source112.release(); target256.release()
                srcPoints.release(); dstPoints112.release(); dstPoints256.release()
                srcTransform.release(); dstTransform.release()
            }
        } finally {
            landmarker.close()
        }
    }

    private fun points(face: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
                       width: Int, height: Int, size: Int): MatOfPoint2f =
        MatOfPoint2f(*ANCHORS.map { index ->
            Point(face[index].x().toDouble() * width * size / width,
                  face[index].y().toDouble() * height * size / height)
        }.toTypedArray())

    private fun template(size: Int): MatOfPoint2f =
        MatOfPoint2f(*ARC_TEMPLATE_112.map { Point(it[0] * size / 112.0, it[1] * size / 112.0) }.toTypedArray())

    private fun affine(from: MatOfPoint2f, to: MatOfPoint2f): Mat {
        val matrix = Calib3d.estimateAffinePartial2D(from, to)
        check(!matrix.empty()) { "No se pudo alinear el rostro para el modelo neuronal." }
        return matrix
    }

    private fun imageTensor(imageBgr: Mat, size: Int, normalizeArcFace: Boolean): FloatArray {
        val rgb = Mat()
        val resized = Mat()
        try {
            Imgproc.cvtColor(imageBgr, rgb, Imgproc.COLOR_BGR2RGB)
            if (rgb.cols() != size || rgb.rows() != size) {
                Imgproc.resize(rgb, resized, Size(size.toDouble(), size.toDouble()))
            } else {
                rgb.copyTo(resized)
            }
            val pixels = ByteArray(size * size * 3)
            resized.get(0, 0, pixels)
            val tensor = FloatArray(3 * size * size)
            val plane = size * size
            for (y in 0 until size) for (x in 0 until size) {
                val pixel = (y * size + x) * 3
                for (channel in 0..2) {
                    val value = (pixels[pixel + channel].toInt() and 0xff) / 255f
                    tensor[channel * plane + y * size + x] =
                        if (normalizeArcFace) value * 2f - 1f else value * 2f - 1f
                }
            }
            return tensor
        } finally {
            rgb.release(); resized.release()
        }
    }

    private fun tensor(env: OrtEnvironment, values: FloatArray, shape: LongArray): OnnxTensor {
        val direct = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        direct.put(values).rewind()
        return OnnxTensor.createTensor(env, direct, shape)
    }

    private fun runVector(session: ai.onnxruntime.OrtSession, env: OrtEnvironment,
                          inputName: String, values: FloatArray, shape: LongArray): FloatArray =
        runVector(session, env, mapOf(inputName to tensor(env, values, shape)))

    private fun runVector(session: ai.onnxruntime.OrtSession, env: OrtEnvironment,
                          inputs: Map<String, OnnxTensor>): FloatArray {
        inputs.values.forEach { /* tensors are closed below */ }
        try {
            session.run(inputs).use { result ->
                val output = result.get(0).value as OnnxTensor
                val buffer = output.floatBuffer
                val values = FloatArray(buffer.remaining())
                buffer.get(values)
                return values
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun l2Normalize(values: FloatArray): FloatArray {
        val norm = sqrt(values.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        require(norm > 0f) { "ArcFace produjo una identidad vacía." }
        return FloatArray(values.size) { values[it] / norm }
    }

    private fun outputToBgr(output: FloatArray, size: Int): Mat {
        val plane = size * size
        require(output.size >= plane * 3) { "GHOST devolvió una salida ONNX inesperada." }
        val rgb = ByteArray(plane * 3)
        for (i in 0 until plane) for (c in 0..2) {
            val normalized = output[c * plane + i].coerceIn(-1f, 1f)
            rgb[i * 3 + c] = (((normalized + 1f) * 127.5f).toInt()).toByte()
        }
        val rgbMat = Mat(size, size, CvType.CV_8UC3)
        rgbMat.put(0, 0, rgb)
        val bgr = Mat()
        Imgproc.cvtColor(rgbMat, bgr, Imgproc.COLOR_RGB2BGR)
        rgbMat.release()
        return bgr
    }

    private fun createFaceMask(
        face: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        transform: Mat,
        width: Int,
        height: Int
    ): Mat {
        val values = DoubleArray(6)
        transform.get(0, 0, values)
        val polygon = MatOfPoint(*OVAL.map { index ->
            val x = face[index].x().toDouble() * width
            val y = face[index].y().toDouble() * height
            Point(values[0] * x + values[1] * y + values[2],
                  values[3] * x + values[4] * y + values[5])
        }.toTypedArray())
        val mask = Mat.zeros(FACE_SIZE, FACE_SIZE, CvType.CV_8UC1)
        Imgproc.fillConvexPoly(mask, polygon, Scalar(255.0))
        Imgproc.GaussianBlur(mask, mask, Size(17.0, 17.0), 0.0)
        polygon.release()
        return mask
    }

    private suspend fun ensureLandmarkModel(): File = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, LANDMARK_FILE)
        if (file.exists() && file.length() > 0L) return@withContext file
        val temp = File(context.filesDir, "$LANDMARK_FILE.part")
        val connection = (URL(LANDMARK_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 90_000
            instanceFollowRedirects = true
        }
        try {
            connection.inputStream.use { input -> temp.outputStream().use(input::copyTo) }
            check(temp.length() > 0L) { "No se pudo descargar el detector facial." }
            if (file.exists()) file.delete()
            check(temp.renameTo(file)) { "No se pudo guardar el detector facial." }
        } catch (error: Exception) {
            temp.delete()
            throw error
        } finally {
            connection.disconnect()
        }
        file
    }
}
