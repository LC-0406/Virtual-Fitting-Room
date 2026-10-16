package com.example.appuidemo.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File

/**
 * 解码图片并按 EXIF 方向摆正。
 *
 * 相机拍摄的照片常把旋转信息写在 EXIF 而不是像素里。展示时 Coil 会自己处理，
 * 但我们会把导入的图重编码为 JPEG（丢掉 EXIF），所以必须在落盘前先摆正，
 * 否则后续 Canvas 合成出来的结果图会躺倒。
 */
fun decodeBitmapOriented(file: File, maxSize: Int = 1600): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

    return bitmap.rotatedBy(readExifRotation(file.absolutePath))
}

/** 同上，但输入是内存里的字节（导入时用） */
fun decodeBytesOriented(bytes: ByteArray, maxSize: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // 按需降采样，避免把一张 4000×3000 的原图整块读进内存导致 OOM
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

    val rotation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ).toRotation()
    }.getOrDefault(0f)

    return bitmap.rotatedBy(rotation)
}

private fun Bitmap.rotatedBy(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated != this) recycle()
    return rotated
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= maxSize || height / (sample * 2) >= maxSize) {
        sample *= 2
    }
    return sample
}

private fun readExifRotation(path: String): Float = runCatching {
    ExifInterface(path)
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        .toRotation()
}.getOrDefault(0f)

private fun Int.toRotation(): Float = when (this) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
    else -> 0f
}
