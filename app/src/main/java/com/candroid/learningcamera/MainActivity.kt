package com.candroid.learningcamera


import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.candroid.learningcamera.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val preview: Preview = Preview.Builder().build()
        val imageCapture: ImageCapture = ImageCapture.Builder().build()
        val imageAnalyzer: ImageAnalysis = ImageAnalysis.Builder().build()
        val cameraProvider: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(this)

        cameraProvider.addListener(Runnable {
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            val camera = cameraProvider.get().bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        }, ContextCompat.getMainExecutor(this))

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // The captured image is available here
                    super.onCaptureSuccess(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    // Handle the capture error here
                    super.onError(exception)
                }
            }
        )


    }

    private fun requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_SERVICE)) {
            // Explain to the user why we need to read the contacts
            Toast.makeText(this, "We need permission to access the camera", Toast.LENGTH_SHORT).show()
        }
        ActivityCompat.requestPermissions(this, arrayOf(CAMERA_SERVICE), 1)
    }

}