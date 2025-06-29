package com.bma.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ActivityQrScannerBinding
import com.bma.android.models.PairingData
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    
    // DEBUG: Add comprehensive logging for crash investigation
    private fun debugLog(message: String) {
        println("🔍 [QRScannerActivity] $message")
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        debugLog("Camera permission result: $isGranted")
        if (isGranted) {
            debugLog("Permission granted, starting camera...")
            startCamera()
        } else {
            debugLog("Permission denied, finishing activity")
            showError("Camera permission is required to scan QR codes")
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        debugLog("QRScannerActivity onCreate() started")
        
        try {
            super.onCreate(savedInstanceState)
            debugLog("super.onCreate() completed")
            
            // Initialize binding with error handling
            try {
                binding = ActivityQrScannerBinding.inflate(layoutInflater)
                debugLog("View binding inflated successfully")
                setContentView(binding.root)
                debugLog("Content view set successfully")
            } catch (e: Exception) {
                debugLog("ERROR: Failed to inflate binding or set content view: ${e.message}")
                e.printStackTrace()
                throw e
            }
            
            // Setup UI with error handling
            try {
                setupUI()
                debugLog("UI setup completed")
            } catch (e: Exception) {
                debugLog("ERROR: Failed to setup UI: ${e.message}")
                e.printStackTrace()
                throw e
            }
            
            // FIXED: Initialize camera resources BEFORE checking permissions
            
            // Initialize camera executor with error handling
            try {
                cameraExecutor = Executors.newSingleThreadExecutor()
                debugLog("Camera executor created successfully")
            } catch (e: Exception) {
                debugLog("ERROR: Failed to create camera executor: ${e.message}")
                e.printStackTrace()
                throw e
            }
            
            // Initialize camera provider future with error handling
            try {
                cameraProviderFuture = ProcessCameraProvider.getInstance(this)
                debugLog("Camera provider future initialized successfully")
            } catch (e: Exception) {
                debugLog("ERROR: Failed to initialize camera provider: ${e.message}")
                e.printStackTrace()
                throw e
            }
            
            // NOW check camera permission and start camera (after initialization)
            try {
                checkCameraPermission()
                debugLog("Camera permission check completed")
            } catch (e: Exception) {
                debugLog("ERROR: Failed during camera permission check: ${e.message}")
                e.printStackTrace()
                throw e
            }
            
            debugLog("QRScannerActivity onCreate() completed successfully")
            
        } catch (e: Exception) {
            debugLog("CRITICAL ERROR in onCreate(): ${e.message}")
            e.printStackTrace()
            
            // Show error to user and finish gracefully
            Toast.makeText(this, "Failed to initialize QR scanner: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupUI() {
        debugLog("Setting up UI components...")
        
        try {
            // Ensure binding is accessible
            if (!::binding.isInitialized) {
                debugLog("ERROR: Binding not initialized in setupUI")
                throw IllegalStateException("Binding not initialized")
            }
            
            binding.cancelButton.setOnClickListener {
                debugLog("Cancel button clicked")
                finish()
            }
            debugLog("Cancel button listener set successfully")
            
        } catch (e: Exception) {
            debugLog("ERROR in setupUI(): ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    private fun checkCameraPermission() {
        debugLog("Checking camera permission...")
        
        try {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    debugLog("Camera permission already granted")
                    startCamera()
                }
                else -> {
                    debugLog("Requesting camera permission...")
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } catch (e: Exception) {
            debugLog("ERROR in checkCameraPermission(): ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    private fun startCamera() {
        debugLog("Starting camera...")
        
        try {
            // Ensure camera provider future is initialized
            if (!::cameraProviderFuture.isInitialized) {
                debugLog("ERROR: Camera provider future not initialized")
                throw IllegalStateException("Camera provider future not initialized")
            }
            
            cameraProviderFuture.addListener({
                debugLog("Camera provider listener triggered")
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    debugLog("Camera provider obtained successfully")
                    bindPreview(cameraProvider)
                } catch (e: Exception) {
                    debugLog("ERROR in camera provider listener: ${e.message}")
                    e.printStackTrace()
                    showError("Failed to get camera provider: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(this))
            
            debugLog("Camera provider listener added successfully")
            
        } catch (e: Exception) {
            debugLog("ERROR in startCamera(): ${e.message}")
            e.printStackTrace()
            showError("Failed to start camera: ${e.message}")
        }
    }
    
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        debugLog("Binding camera preview...")
        
        try {
            // Ensure binding is accessible
            if (!::binding.isInitialized) {
                debugLog("ERROR: Binding not initialized in bindPreview")
                throw IllegalStateException("Binding not initialized")
            }
            
            val preview = Preview.Builder().build()
            debugLog("Preview builder created")
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            debugLog("Camera selector created")
            
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)
            debugLog("Surface provider set")
            
            // Ensure camera executor is initialized
            if (!::cameraExecutor.isInitialized) {
                debugLog("ERROR: Camera executor not initialized")
                throw IllegalStateException("Camera executor not initialized")
            }
            
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode ->
                        debugLog("QR code detected: ${qrCode.take(50)}...")
                        processQRCode(qrCode)
                    })
                }
            debugLog("Image analyzer created and configured")
            
            try {
                cameraProvider.unbindAll()
                debugLog("Camera provider unbound all previous use cases")
                
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                debugLog("Camera bound to lifecycle successfully")
                
            } catch (exc: Exception) {
                debugLog("ERROR binding camera to lifecycle: ${exc.message}")
                exc.printStackTrace()
                showError("Failed to bind camera: ${exc.message}")
            }
            
        } catch (e: Exception) {
            debugLog("ERROR in bindPreview(): ${e.message}")
            e.printStackTrace()
            showError("Failed to setup camera preview: ${e.message}")
        }
    }
    
    private fun processQRCode(qrCodeData: String) {
        debugLog("Processing QR code data...")
        
        runOnUiThread {
            try {
                debugLog("Parsing QR code JSON...")
                val gson = Gson()
                val pairingData = gson.fromJson(qrCodeData, PairingData::class.java)
                debugLog("QR code parsed successfully: serverUrl=${pairingData.serverUrl}")
                
                showStatus("QR Code detected! Connecting...")
                connectWithPairingData(pairingData)
                
            } catch (e: Exception) {
                debugLog("ERROR processing QR code: ${e.message}")
                e.printStackTrace()
                showError("Invalid QR code format: ${e.message}")
            }
        }
    }
    
    private fun connectWithPairingData(pairingData: PairingData) {
        debugLog("Connecting with pairing data...")
        
        try {
            // Stop camera scanning immediately after QR detection
            debugLog("Stopping camera and cleaning up resources after QR detection...")
            cleanupCameraResources()
            
            // Update API client with new server URL and token
            debugLog("Setting server URL: ${pairingData.serverUrl}")
            ApiClient.setServerUrl(pairingData.serverUrl)
            
            debugLog("Setting auth token: ${pairingData.token.take(8)}...")
            ApiClient.setAuthToken(pairingData.token)
            
            // Save to preferences
            debugLog("Saving connection details to preferences...")
            getSharedPreferences("BMA", MODE_PRIVATE).edit()
                .putString("server_url", pairingData.serverUrl)
                .putString("auth_token", pairingData.token)
                .putString("token_expires_at", pairingData.expiresAt)
                .apply()
            debugLog("Connection details saved successfully")
            
            showStatus("Connected successfully!")
            
            
            // Return to main activity
            debugLog("Setting result OK and finishing activity")
            setResult(RESULT_OK)
            finish()
            
        } catch (e: Exception) {
            debugLog("ERROR in connectWithPairingData(): ${e.message}")
            e.printStackTrace()
            showError("Failed to connect: ${e.message}")
        }
    }
    
    private fun showStatus(message: String) {
        debugLog("Showing status: $message")
        
        try {
            // Ensure binding is accessible
            if (!::binding.isInitialized) {
                debugLog("ERROR: Binding not initialized in showStatus")
                return
            }
            
            binding.statusText.text = message
            binding.statusText.visibility = android.view.View.VISIBLE
            
        } catch (e: Exception) {
            debugLog("ERROR in showStatus(): ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun showError(message: String) {
        debugLog("Showing error: $message")
        
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            debugLog("ERROR showing toast: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        debugLog("QRScannerActivity onDestroy() started")
        
        try {
            // Clean up camera resources first
            cleanupCameraResources()
            
            super.onDestroy()
            debugLog("super.onDestroy() completed")
            
        } catch (e: Exception) {
            debugLog("ERROR in onDestroy(): ${e.message}")
            e.printStackTrace()
        }
        
        debugLog("QRScannerActivity onDestroy() completed")
    }
    
    override fun onPause() {
        debugLog("QRScannerActivity onPause() - releasing camera resources")
        super.onPause()
        
        // Release camera when activity is paused to prevent resource locking
        try {
            if (::cameraProviderFuture.isInitialized) {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                debugLog("Camera unbound during onPause()")
            }
        } catch (e: Exception) {
            debugLog("ERROR releasing camera in onPause(): ${e.message}")
        }
    }
    
    override fun onResume() {
        debugLog("QRScannerActivity onResume() - reacquiring camera")
        super.onResume()
        
        // Restart camera when activity resumes
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                startCamera()
                debugLog("Camera restarted during onResume()")
            } catch (e: Exception) {
                debugLog("ERROR restarting camera in onResume(): ${e.message}")
            }
        }
    }
    
    /**
     * NEW: Centralized camera resource cleanup
     */
    private fun cleanupCameraResources() {
        debugLog("Cleaning up camera resources...")
        
        try {
            // Stop image analyzer
            imageAnalyzer?.clearAnalyzer()
            debugLog("Image analyzer cleared")
            
            // Unbind all camera use cases
            if (::cameraProviderFuture.isInitialized) {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                debugLog("Camera provider unbound all use cases")
            }
            
            // Safely shutdown camera executor
            if (::cameraExecutor.isInitialized) {
                cameraExecutor.shutdown()
                debugLog("Camera executor shutdown completed")
            } else {
                debugLog("Camera executor was not initialized, skipping shutdown")
            }
            
        } catch (e: Exception) {
            debugLog("ERROR during camera cleanup: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private inner class QRCodeAnalyzer(
        private val onQRCodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {
        
        private val scanner = BarcodeScanning.getClient()
        
        override fun analyze(imageProxy: ImageProxy) {
            try {
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            try {
                                for (barcode in barcodes) {
                                    when (barcode.valueType) {
                                        Barcode.TYPE_TEXT -> {
                                            barcode.displayValue?.let { qrData ->
                                                debugLog("QR barcode detected, calling callback...")
                                                onQRCodeDetected(qrData)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                debugLog("ERROR processing barcode results: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                        .addOnFailureListener { e ->
                            debugLog("Barcode scanning failed: ${e.message}")
                        }
                        .addOnCompleteListener {
                            try {
                                imageProxy.close()
                            } catch (e: Exception) {
                                debugLog("ERROR closing image proxy: ${e.message}")
                            }
                        }
                } else {
                    debugLog("Media image is null, closing proxy")
                    imageProxy.close()
                }
            } catch (e: Exception) {
                debugLog("ERROR in analyze(): ${e.message}")
                e.printStackTrace()
                try {
                    imageProxy.close()
                } catch (closeError: Exception) {
                    debugLog("ERROR closing image proxy after analyze error: ${closeError.message}")
                }
            }
        }
    }
} 