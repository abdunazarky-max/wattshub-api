package com.hyzin.whtsappclone.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.File

object ImageUtils {
    fun downloadImage(context: Context, imageUrl: String, scope: CoroutineScope) {
        scope.launch {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .build()

                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val saved = saveBitmapToGallery(context, bitmap)
                        withContext(Dispatchers.Main) {
                            if (saved) {
                                Toast.makeText(context, "Image Saved to Gallery", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
        val filename = "WattsHub_${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null
        val contentResolver = context.contentResolver

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WattsHub")
                }
                val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { contentResolver.openOutputStream(it) }
            } else {
                val imagesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WattsHub")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                val imageFile = File(imagesDir, filename)
                fos = java.io.FileOutputStream(imageFile)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun rescaleAndCompressImage(context: Context, uri: Uri, maxWidth: Int = 1024, maxHeight: Int = 1024): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            val width = originalBitmap.width
            val height = originalBitmap.height
            val ratio = width.toFloat() / height.toFloat()

            var newWidth = width
            var newHeight = height

            if (width > maxWidth || height > maxHeight) {
                if (ratio > 1) {
                    newWidth = maxWidth
                    newHeight = (maxWidth / ratio).toInt()
                } else {
                    newHeight = maxHeight
                    newWidth = (maxHeight * ratio).toInt()
                }
            }

            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            
            if (originalBitmap != scaledBitmap) originalBitmap.recycle()
            
            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
