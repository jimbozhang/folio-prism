package com.jimbozhang.folioprism.data.store

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BackupManager(
    private val backupDir: File,
) {
    private val stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss-SSS")

    fun backupBeforeWrite(file: File, retainCount: Int) {
        if (!file.exists()) return
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            error("Unable to create backup directory ${backupDir.path}")
        }
        val stamp = LocalDateTime.now().format(stampFormat)
        val target = File(backupDir, "${file.nameWithoutExtension}-$stamp.${file.extension.ifBlank { "json" }}")
        file.copyTo(target, overwrite = false)
        prune(file.nameWithoutExtension, retainCount)
    }

    private fun prune(baseName: String, retainCount: Int) {
        val keep = retainCount.coerceAtLeast(1)
        val backups = backupDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$baseName-") && it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        backups.drop(keep).forEach { it.delete() }
    }
}
