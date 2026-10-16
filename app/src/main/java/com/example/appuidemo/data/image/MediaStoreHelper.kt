package com.example.appuidemo.data.image

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把应用私有目录里的图片复制一份到系统相册。
 * 相册是公共区域，所以导出后的副本在卸载 App 后仍然保留。
 */
object MediaStoreHelper {

    suspend fun saveToAlbum(context: Context, source: File, displayName: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!source.exists()) return@runCatching false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveViaMediaStore(context, source, displayName)
                } else {
                    saveLegacy(source, displayName)
                }
            }.getOrDefault(false)
        }

    private fun saveViaMediaStore(context: Context, source: File, displayName: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + "VirtualFittingRoom"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false

        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: error("无法打开相册输出流")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(source: File, displayName: String): Boolean {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "VirtualFittingRoom"
        )
        if (!dir.exists() && !dir.mkdirs()) return false
        val target = File(dir, displayName)
        return runCatching {
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }.getOrDefault(false)
    }
}
