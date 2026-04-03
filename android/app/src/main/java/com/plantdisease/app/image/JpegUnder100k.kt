package com.plantdisease.app.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TARGET_MAX_BYTES = 100_000
private const val INITIAL_MAX_SIDE = 768
private const val MIN_SIDE = 240

/**
 * Decode from [uri], downscale, then JPEG-encode until ≤ [maxBytes] when possible.
 * Best-effort: if still above [maxBytes] at minimum quality and size, returns smallest achieved.
 */
fun compressPlantPhotoToJpeg(resolver: ContentResolver, uri: Uri, maxBytes: Int = TARGET_MAX_BYTES): ByteArray {
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Could not read image")
    return compressJpegFromEncodedBytes(bytes, maxBytes)
}

fun compressPlantPhotoFromFile(path: java.io.File, maxBytes: Int = TARGET_MAX_BYTES): ByteArray {
    val bytes = path.readBytes()
    return compressJpegFromEncodedBytes(bytes, maxBytes)
}

fun compressJpegFromEncodedBytes(encoded: ByteArray, maxBytes: Int): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
    val sample = computeInSampleSize(bounds, INITIAL_MAX_SIDE * 2)
    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, decodeOpts)
        ?: error("Could not decode image")
    return try {
        compressBitmapToJpegMaxBytes(decoded, maxBytes)
    } finally {
        decoded.recycle()
    }
}

fun compressBitmapToJpegMaxBytes(src: Bitmap, maxBytes: Int): ByteArray {
    var maxSide = min(INITIAL_MAX_SIDE, max(src.width, src.height))
    var working = scaleToMaxSide(src, maxSide)
    var ownsWorking = working !== src
    var quality = 85
    try {
        while (true) {
            val stream = ByteArrayOutputStream()
            working.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val out = stream.toByteArray()
            if (out.size <= maxBytes) return out
            if (quality > 30) {
                quality -= 5
                continue
            }
            if (maxSide <= MIN_SIDE) return out
            maxSide = max(MIN_SIDE, (maxSide * 0.82f).roundToInt())
            val next = scaleToMaxSide(src, maxSide)
            if (ownsWorking) working.recycle()
            working = next
            ownsWorking = working !== src
            quality = 85
        }
    } finally {
        if (ownsWorking) working.recycle()
    }
}

private fun scaleToMaxSide(src: Bitmap, maxSide: Int): Bitmap {
    val w = src.width
    val h = src.height
    val long = max(w, h)
    if (long <= maxSide) return src
    val scale = maxSide.toFloat() / long
    val nw = max(1, (w * scale).roundToInt())
    val nh = max(1, (h * scale).roundToInt())
    return Bitmap.createScaledBitmap(src, nw, nh, true)
}

private fun computeInSampleSize(options: BitmapFactory.Options, maxSide: Int): Int {
    val h = options.outHeight
    val w = options.outWidth
    var inSampleSize = 1
    if (h > maxSide || w > maxSide) {
        var halfH = h / 2
        var halfW = w / 2
        while (halfH / inSampleSize >= maxSide && halfW / inSampleSize >= maxSide) {
            inSampleSize *= 2
        }
    }
    return max(1, inSampleSize)
}
