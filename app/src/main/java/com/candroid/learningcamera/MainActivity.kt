package com.candroid.learningcamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.candroid.learningcamera.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@ExperimentalGetImage
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    //private var listOfRect = mutableListOf<Rect>()
    private var widthOfDevice: Int? = null
    private var heightOfDevice: Int? = null


    private var cameraPermission = false
    private var writeExternalStoragePermission = false

    private var permissionList = mutableListOf<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        widthOfDevice = displayMetrics.widthPixels
        heightOfDevice = displayMetrics.heightPixels

        cameraPermission = cameraPermissionGranted()
        writeExternalStoragePermission = writeExternalStoragePermissionGranted()

        permissionList.add(cameraPermission)
        permissionList.add(writeExternalStoragePermission)

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        val permissionsGranted = permissionList.all {
            it
        }

        if (!permissionsGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                0
            )
        } else {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }


    private fun cameraPermissionGranted() = ContextCompat.checkSelfPermission(
        baseContext,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun writeExternalStoragePermissionGranted() = ContextCompat.checkSelfPermission(
        baseContext,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackgroundExecutor(cameraExecutor)
                //.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(Surface.ROTATION_0)
                .setTargetResolution(Size(widthOfDevice!!, heightOfDevice!!))
               .build()
                .also {
                    lifecycleScope.launch(Dispatchers.Default) {
                        it.setAnalyzer(cameraExecutor, FrameAnalyzer(binding.overlayView))
                    }
                }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalysis
                )

            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    inner class FrameAnalyzer (var overlayView: OverlayView): ImageAnalysis.Analyzer {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var textBoxes= mutableListOf<Rect>()

        override fun analyze(image: ImageProxy) {
            //textBoxes.clear()
            GlobalScope.launch(Dispatchers.IO) {
                val mediaImage = image.image
                if (mediaImage != null) {
                    recognizer.process(
                        InputImage.fromMediaImage(
                            mediaImage,
                            image.imageInfo.rotationDegrees
                        )
                    )
                        .addOnSuccessListener { visionText ->
                            //Log.d("ML_Kit_VisionText", "${visionText.text}")
                            for (block in visionText.textBlocks) {
                                //Log.d("ML_Kit_Blocks", "${block.text}")
                                for (line in block.lines) {

                                    val boundingBox = line.boundingBox
                                    if ((boundingBox != null) && (boundingBox.left >= 0) && (boundingBox.top >= 0) && (boundingBox.right <= image.width) && (boundingBox.bottom <= image.height)) {
                                        textBoxes.add(boundingBox)
                                        overlayView.setBoxes(textBoxes)
                                    }
                                }
                            }

                        }
                        .addOnFailureListener { e ->
                            Log.i("ML_Kit_Error", e.message.toString())
                        }
                        .addOnCompleteListener { image.close() }

                    textBoxes.clear()
                }
            }
        }
    }
/*
    private fun saveImage(image: ImageProxy) {
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                "yy-MM-dd-HH-mm-ss-SSS",
                Locale.getDefault()
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        val bitmap = image.toBitmap()
        val fileOutputStream = FileOutputStream(photoFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
        fileOutputStream.flush();
        fileOutputStream.close()
    }

*/

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//    }
}