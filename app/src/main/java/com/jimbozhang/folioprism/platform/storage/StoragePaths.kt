package com.jimbozhang.folioprism.platform.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

object StoragePaths {
    private const val DIR_NAME = "folio-prism"

    fun dataDir(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        DIR_NAME,
    )

    fun dataFile(): File = File(dataDir(), "folio-prism.json")

    fun priceCacheFile(): File = File(dataDir(), "price-cache.json")

    fun backupDir(): File = File(dataDir(), "backups")

    fun logDir(): File = File(dataDir(), "logs")

    fun ensureDirs() {
        dataDir().mkdirs()
        backupDir().mkdirs()
        logDir().mkdirs()
    }

    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * API 30+: open the system "all files access" page for this app.
     * API 26-29: callers should request WRITE_EXTERNAL_STORAGE through ActivityResult.
     */
    fun openStorageSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { context.startActivity(appIntent) }
                .onFailure {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
        }
    }
}
