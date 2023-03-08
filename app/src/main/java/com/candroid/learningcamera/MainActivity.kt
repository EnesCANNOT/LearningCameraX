package com.candroid.learningcamera

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.candroid.learningcamera.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private var cameraPermission = false
    private var writeExternalStoragePermission = false

    private var permissionList = mutableListOf<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                0
            )
        } else{
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun cameraPermissionGranted() = ContextCompat.checkSelfPermission(
        baseContext,
        android.Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun writeExternalStoragePermissionGranted() = ContextCompat.checkSelfPermission(
        baseContext,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
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
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FrameAnalyzer())
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

    private inner class FrameAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            // Save every frame taken as soon as the PreviewView is opened
            saveImage(image)

            image.close()
        }
    }

    private fun saveImage(image: ImageProxy) {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val file = File(
            outputDirectory,
            "IMG_${
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss_SSS",
                    Locale.getDefault()
                ).format(System.currentTimeMillis())
            }.jpg"
        )

        file.writeBytes(bytes)
    }


    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }
}