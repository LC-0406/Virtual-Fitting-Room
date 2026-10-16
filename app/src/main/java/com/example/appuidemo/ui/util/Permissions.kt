package com.example.appuidemo.ui.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasCameraPermission(): Boolean = hasPermission(Manifest.permission.CAMERA)

/** Android 9 及以下导出到相册需要写外部存储；10+ 走 MediaStore 不需要 */
fun Context.needsLegacyStoragePermission(): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)

/** 权限被永久拒绝后，只能引导用户去系统设置里手动打开 */
fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}
