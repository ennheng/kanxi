package com.kanxi.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CoverImageStore {
    private const val MAX_COVER_BYTES = 10L * 1024L * 1024L

    suspend fun import(
        context: Context,
        uri: Uri,
        previousPath: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val mimeType = context.contentResolver.getType(uri)
            require(mimeType == null || mimeType in allowedMimeTypes) {
                "Only JPEG, PNG, or WebP images are supported"
            }

            val coversDirectory = File(context.filesDir, "covers").apply { mkdirs() }
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val destination = File(coversDirectory, "${UUID.randomUUID()}.$extension")

            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "The selected image cannot be opened" }
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > MAX_COVER_BYTES) {
                                throw IOException("The selected image is larger than 10 MB")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }

            previousPath
                ?.let(::File)
                ?.takeIf { it.isFile && it.parentFile == coversDirectory }
                ?.delete()
            destination.absolutePath
        }
    }

    fun deleteOwnedCover(context: Context, path: String?) {
        if (path == null) return
        val coversDirectory = File(context.filesDir, "covers")
        File(path).takeIf { it.isFile && it.parentFile == coversDirectory }?.delete()
    }

    private val allowedMimeTypes = setOf("image/jpeg", "image/png", "image/webp")
}

