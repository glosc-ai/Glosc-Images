package com.glosc.images.data.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.glosc.images.domain.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class StoredImage(
    val path: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)

class ImageFileStorage(private val context: Context) {
    suspend fun saveBase64Image(base64: String, sourceType: SourceType): StoredImage =
        withContext(Dispatchers.IO) {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val datePath = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
            val dir = File(context.filesDir, "images/${sourceType.name.lowercase(Locale.US)}/$datePath")
            dir.mkdirs()
            val file = File(dir, "${UUID.randomUUID()}.png")
            file.writeBytes(bytes)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            StoredImage(
                path = file.absolutePath,
                width = bounds.outWidth.takeIf { it > 0 } ?: 1024,
                height = bounds.outHeight.takeIf { it > 0 } ?: 1024,
                sizeBytes = file.length()
            )
        }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        if (path.isNotBlank()) {
            runCatching { File(path).delete() }
        }
    }
}
