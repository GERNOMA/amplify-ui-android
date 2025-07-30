/*
 * Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amplifyframework.ui.liveness.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.amplifyframework.core.Amplify
import com.amplifyframework.ui.liveness.ml.FaceDetector
import com.amplifyframework.ui.liveness.ml.FaceOval
import com.amplifyframework.ui.liveness.state.LivenessState
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

import org.json.JSONObject
import org.json.JSONArray

import android.util.Base64
import java.io.ByteArrayOutputStream
import android.provider.Settings

import android.graphics.Matrix

internal class FrameAnalyzer(
    context: Context,
    private val livenessState: LivenessState,
    private val sessionId: String
) : ImageAnalysis.Analyzer {

    private val tfLite = FaceDetector.loadModel(context)
    private val tfImageBuffer = TensorImage(DataType.UINT8)
    private var tfImageProcessor: ImageProcessor? = null

    private var cachedBitmap: Bitmap? = null
    private var faceDetector = FaceDetector(livenessState)

    private val logger = Amplify.Logging.forNamespace("Liveness")

    private var imageNumber = 0;
    private val httpClient by lazy { OkHttpClient() }

    private val androidId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }

    override fun analyze(image: ImageProxy) {
        try {

            attemptAnalyze(image)
        } catch (e: Exception) {
            // We've seen a few instances of exceptions thrown by copyPixelsFromBuffer.
            // This indicates the image received may have been in an unexpected format.
            // We discard this frame, in hopes that the next frame is readable.
            logger.error("Failed to analyze frame", e)
        }
    }

    private var lastRotationDegrees: Int = 0

    private fun attemptAnalyze(image: ImageProxy) {
        if (cachedBitmap == null) {
            cachedBitmap = Bitmap.createBitmap(
                image.width,
                image.height,
                Bitmap.Config.ARGB_8888
            )
        }

        image.use {

            lastRotationDegrees = it.imageInfo.rotationDegrees 

            cachedBitmap?.let { bitmap ->


                if(imageNumber == 0){
                    val url = "http://desarrollo.datamatic.com.uy:96/mcc/index.php?r=/ws/dispositivo/geteventbysession" // TODO: replace with your endpoint
                    //val json = """{"a":""" + sessionId + """}"""

                    val payload = JSONObject()
                        .put("sessionId", sessionId)
                        .put("token", "faBAeiCKfcAWbdaS66G5")
                        .toString()


                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = payload.toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url(url)
                        .post(body)
                        .build()

                    httpClient.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            logger.warn("POST failed", e)
                        }
                        override fun onResponse(call: Call, response: Response) {
                            response.use { resp ->
                                val code = resp.code
                                val raw = resp.body?.string().orEmpty() // read once

                                if (!resp.isSuccessful) {
                                    logger.warn("POST non-2xx (code=$code), body=$raw")
                                    return
                                }

                                try {
                                    val trimmed = raw.trim()
                                    val json: Any = if (trimmed.startsWith("[")) {
                                        JSONArray(trimmed)           // handle JSON array responses
                                    } else {
                                        JSONObject(trimmed)          // handle JSON object responses
                                    }

                                    val eventoValue: String? = when (json) {
                                        is JSONObject -> json.optString("evento", null)
                                        is JSONArray -> json.optJSONObject(0)?.optString("evento", null)
                                        else -> null
                                    }

                                    // Do something with the JSON (here we just log it)
                                    logger.info("""POST success (code=$code); parsed JSON=${eventoValue}""")

                                    // If you need to hand it off to the UI thread, for example:
                                    // Handler(Looper.getMainLooper()).post {
                                    //     // use `json` here on the main thread
                                    // }

                                    try {
                                        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                        val baos = ByteArrayOutputStream()

                                        val m = Matrix().apply { postRotate(lastRotationDegrees.toFloat()) }

                                        val rotated = Bitmap.createBitmap(copy, 0, 0, copy.width, copy.height, m, true)


                                        rotated.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                                        val img64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                                        val url = "http://desarrollo.datamatic.com.uy:96/mcc/index.php?r=/ws/dispositivo/init"

                                        val payload2 = JSONObject()
                                        .put("evento", eventoValue)
                                        .put("binary", img64)
                                        .put("token", "faBAeiCKfcAWbdaS66G5")
                                        .put("codigo", androidId)
                                        .toString()

                                        
                                        val mediaType2 = "application/json; charset=utf-8".toMediaType()
                                        val body = payload2.toRequestBody(mediaType2)

                                        val request2 = Request.Builder()
                                            .url(url)
                                            .post(body)
                                            .build()

                                        httpClient.newCall(request2).enqueue(object : Callback {
                                            override fun onFailure(call: Call, e: IOException) {
                                                logger.warn("POST failed", e)
                                            }
                                            override fun onResponse(call: Call, response: Response) {
                                                response.use { resp ->
                                                    val code = resp.code
                                                    val raw = resp.body?.string().orEmpty() // read once

                                                    if (!resp.isSuccessful) {
                                                        logger.warn("POST non-2xx (code=$code), body=$raw")
                                                        return
                                                    }

                                                    try {
                                                        val trimmed = raw.trim()
                                                        val json: Any = if (trimmed.startsWith("[")) {
                                                            JSONArray(trimmed)           // handle JSON array responses
                                                        } else {
                                                            JSONObject(trimmed)          // handle JSON object responses
                                                        }

                                                        val eventoValue: String? = when (json) {
                                                            is JSONObject -> json.optString("evento", null)
                                                            is JSONArray -> json.optJSONObject(0)?.optString("evento", null)
                                                            else -> null
                                                        }

                                                        // Do something with the JSON (here we just log it)
                                                        logger.info("""La caraaaaaaaaa ${raw}""")

                                                    } catch (e: Exception) {
                                                        logger.warn("Response body was not valid JSON: $raw", e)
                                                    }
                                                }
                                            }
                                        })

                                    } catch (e: Exception) {
                                        logger.error("Failed to encode frame to base64", e)
                                        null
                                    }

                                } catch (e: Exception) {
                                    logger.warn("Response body was not valid JSON: $raw", e)
                                }
                            }
                        }
                    })
                }

                imageNumber++;
            
                bitmap.copyPixelsFromBuffer(it.planes[0].buffer)
                if (livenessState.onFrameAvailable()) {
                    val outputLocations = arrayOf(
                        Array(FaceDetector.NUM_BOXES) {
                            FloatArray(FaceDetector.NUM_COORDS)
                        }
                    )
                    val outputScores = arrayOf(Array(FaceDetector.NUM_BOXES) { FloatArray(1) })
                    val outputMap = mapOf(0 to outputLocations, 1 to outputScores)
                    val tensorImage = tfImageBuffer.apply { load(cachedBitmap) }
                    val tfImage = getImageProcessor(it.imageInfo.rotationDegrees)
                        .process(tensorImage)
                    tfLite.runForMultipleInputsOutputs(arrayOf(tfImage.buffer), outputMap)

                    val facesFound = faceDetector.getBoundingBoxes(outputLocations, outputScores)
                    livenessState.onFrameFaceCountUpdate(facesFound.size)

                    if (facesFound.size > 1) return

                    facesFound.firstOrNull()?.let { detectedFace ->
                        val mirrorRectangle = FaceOval.convertMirroredRectangle(
                            detectedFace.location,
                            LivenessCoordinator.TARGET_WIDTH
                        )
                        val mirroredLeftEye = FaceOval.convertMirroredLandmark(
                            detectedFace.leftEye,
                            LivenessCoordinator.TARGET_WIDTH
                        )
                        val mirroredRightEye = FaceOval.convertMirroredLandmark(
                            detectedFace.rightEye,
                            LivenessCoordinator.TARGET_WIDTH
                        )
                        val mirroredMouth = FaceOval.convertMirroredLandmark(
                            detectedFace.mouth,
                            LivenessCoordinator.TARGET_WIDTH
                        )

                        livenessState.onFrameFaceUpdate(
                            mirrorRectangle,
                            mirroredLeftEye,
                            mirroredRightEye,
                            mirroredMouth
                        )
                    }
                }
            }
        }
    }

    private fun getImageProcessor(imageRotationDegrees: Int): ImageProcessor {
        val existingImageProcessor = tfImageProcessor
        if (existingImageProcessor != null) return existingImageProcessor

        val tfInputSize = tfLite.getInputTensor(0).shape().let {
            Size(it[2], it[1])
        }

        val imageProcessor = ImageProcessor.Builder()
            .add(
                ResizeOp(
                    tfInputSize.height,
                    tfInputSize.width,
                    ResizeOp.ResizeMethod.NEAREST_NEIGHBOR
                )
            )
            .add(Rot90Op(-imageRotationDegrees / 90))
            .add(NormalizeOp(0f, 255f)) // transform RGB values from [-255, 255] to [-1, 1]
            .build()

        this.tfImageProcessor = imageProcessor
        return imageProcessor
    }
}
