package com.mimtrix.faceswap.engine

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

/**
 * Offline-first still-image face replacement pipeline.
 *
 * MediaPipe supplies facial landmarks; OpenCV aligns the source face and blends
 * it into the target. The landmark model is fetched once and cached locally.
 * Only process images for which the people depicted have given permission.
 */
class FaceSwapEngine(private val context: Context) {
    companion object {
        private const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
        private const val MODEL_FILE = "face_landmarker.task"
        private val ANCHORS = intArrayOf(33, 263, 1, 61, 291)
        private val FACE_OVAL = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
            397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
            172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
        )
    }

    suspend fun swap(source: Bitmap, target: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        require(!source.isRecycled && !target.isRecycled) { "Las imágenes ya no están disponibles." }
        check(OpenCVLoader.initLocal()) { "No se pudo inicializar OpenCV." }

        val model = loadModel()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(ByteBuffer.wrap(model)).build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .build()

        val landmarker = FaceLandmarker.createFromOptions(context, options)
        try {
            val sourceResult = landmarker.detect(BitmapImageBuilder(source).build())
            val targetResult = landmarker.detect(BitmapImageBuilder(target).build())
            val sourceFace = sourceResult.faceLandmarks().firstOrNull()
                ?: error("No se detectó un rostro en la foto de referencia.")
            val targetFace = targetResult.faceLandmarks().firstOrNull()
                ?: error("No se detectó un rostro en la foto de destino.")

            val src = Mat()
            val dst = Mat()
            Utils.bitmapToMat(source, src)
            Utils.bitmapToMat(target, dst)

            val from = MatOfPoint2f(*ANCHORS.map { i ->
                Point(sourceFace[i].x().toDouble() * src.cols(), sourceFace[i].y().toDouble() * src.rows())
            }.toTypedArray())
            val to = MatOfPoint2f(*ANCHORS.map { i ->
                Point(targetFace[i].x().toDouble() * dst.cols(), targetFace[i].y().toDouble() * dst.rows())
            }.toTypedArray())

            val transform = Calib3d.estimateAffinePartial2D(from, to)
            check(!transform.empty()) { "No se pudo alinear ambos rostros." }

            val warped = Mat()
            Imgproc.warpAffine(src, warped, transform, dst.size(), Imgproc.INTER_LINEAR, Core.BORDER_REFLECT)

            val oval = MatOfPoint(*FACE_OVAL.map { i ->
                Point(targetFace[i].x().toDouble() * dst.cols(), targetFace[i].y().toDouble() * dst.rows())
            }.toTypedArray())
            val mask = Mat.zeros(dst.rows(), dst.cols(), CvType.CV_8UC1)
            Imgproc.fillConvexPoly(mask, oval, Scalar(255.0))
            Imgproc.GaussianBlur(mask, mask, Size( nine(), nine()), 0.0)

            val output = Mat()
            val center = Point(
                targetFace[1].x().toDouble() * dst.cols(),
                targetFace[1].y().toDouble() * dst.rows()
            )
            PhotoBridge.clone(warped, dst, mask, center, output)

            val result = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(output, result)
            src.release(); dst.release(); warped.release(); mask.release()
            oval.release(); from.release(); to.release(); transform.release(); output.release()
            result
        } finally {
            landmarker.close()
        }
    }

    private fun nine() =  nineSize

    private val nineSize =  nineValue

    private suspend fun loadModel(): ByteArray = withContext(Dispatchers.IO) {
        val cached = java.io.File(context.filesDir, MODEL_FILE)
        if (!cached.exists() || cached.length() == 0L) {
            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
            }
            try {
                connection.inputStream.use { input -> cached.outputStream().use(input::copyTo) }
            } finally {
                connection.disconnect()
            }
        }
        cached.readBytes()
    }
}

/** Isolated wrapper keeps OpenCV's photo module use in one place. */
private object PhotoBridge {
    fun clone(source: Mat, target: Mat, mask: Mat, center: Point, output: Mat) {
        org.opencv.photo.Photo.seamlessClone(
            source, target, mask, center, output, org.opencv.photo.Photo.NORMAL_CLONE
        )
    }
}
