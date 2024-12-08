package com.capstone.injureal

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.nio.ByteOrder

class HomeFragment : Fragment() {

    private val IMAGE_PICK_CODE = 1000
    private val CAMERA_REQUEST_CODE = 1001
    private val PERMISSION_REQUEST_CODE = 1002

    private lateinit var previewImageView: ImageView
    private lateinit var progressIndicator: LinearProgressIndicator
    private var tflite: Interpreter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Load TensorFlow Lite model
        tflite = loadModelFile()

        // Request permissions if needed
        if (!hasPermissions()) {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CODE
            )
        }

        // Initialize Views
        previewImageView = view.findViewById(R.id.previewImageView)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        // Gallery Button
        val galleryButton: Button = view.findViewById(R.id.galleryButton)
        galleryButton.setOnClickListener {
            openGallery()
        }

        // Camera Button
        val cameraButton: Button = view.findViewById(R.id.cameraButton)
        cameraButton.setOnClickListener {
            openCamera()
        }

        // Analyze Button
        val analyzeButton: Button = view.findViewById(R.id.analyzeButton)
        analyzeButton.setOnClickListener {
            analyzeImage()
        }

        return view
    }

    private fun loadModelFile(): Interpreter? {
        try {
            val fileDescriptor = requireContext().assets.openFd("injury_model.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return Interpreter(fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Model load failed: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                // Inform the user that permission is denied
                Toast.makeText(context, "Permissions are required for this feature", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Open gallery to pick an image
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    // Open camera to take a picture
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, CAMERA_REQUEST_CODE)
    }

    // Analyze the image with TensorFlow Lite
    private fun analyzeImage() {
        // Show progress indicator
        progressIndicator.visibility = View.VISIBLE
        // Get the bitmap from the previewImageView
        val bitmap = (previewImageView.drawable as? BitmapDrawable)?.bitmap
        bitmap?.let {
            val result = classifyImage(it)
            progressIndicator.visibility = View.GONE
            Toast.makeText(context, "Injury Type: $result", Toast.LENGTH_LONG).show()
        }
    }

    private fun classifyImage(bitmap: Bitmap): String {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)

        // Assuming your model outputs 3 categories: abrasions, bruises, and burn
        val output = Array(1) { FloatArray(3) } // 3 output classes

        // Run the model inference
        tflite?.run(byteBuffer, output)

        // Interpret the output (simple logic to pick the highest score)
        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        return when (maxIndex) {
            0 -> "Abrasions"
            1 -> "Bruises"
            2 -> "Burn"
            else -> "Unknown"
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(224 * 224)

        bitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

        var pixelIndex = 0
        for (i in intValues.indices) {
            val color = intValues[i]
            byteBuffer.putFloat(((color shr 16) and 0xFF) / 255.0f)
            byteBuffer.putFloat(((color shr 8) and 0xFF) / 255.0f)
            byteBuffer.putFloat((color and 0xFF) / 255.0f)
        }

        return byteBuffer
    }

    // Handle the result from gallery or camera
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == IMAGE_PICK_CODE) {
                // If image is picked from gallery
                val imageUri: Uri? = data?.data
                previewImageView.setImageURI(imageUri)
            } else if (requestCode == CAMERA_REQUEST_CODE) {
                // If image is taken from camera
                val imageBitmap = data?.extras?.get("data") as? Bitmap
                previewImageView.setImageBitmap(imageBitmap)
            }
        }
    }
}
