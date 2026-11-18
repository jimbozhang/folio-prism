package com.jimbozhang.folioprism.data.web

import android.content.res.AssetManager
import java.io.File

/**
 * Reads the packaged web UI (HTML/CSS/JS) so the server can stay free of
 * Android types and remains unit-testable on the JVM.
 */
interface WebAssets {
    /** Returns the bytes of [path] (relative to the web root), or null. */
    fun read(path: String): ByteArray?
}

class AndroidWebAssets(private val assets: AssetManager) : WebAssets {
    override fun read(path: String): ByteArray? = runCatching {
        assets.open("web/$path").use { it.readBytes() }
    }.getOrNull()
}

/** Directory-backed assets for unit tests. */
class DirectoryWebAssets(private val root: File) : WebAssets {
    override fun read(path: String): ByteArray? {
        val file = File(root, path)
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return null
        if (canonical != rootCanonical &&
            !canonical.path.startsWith(rootCanonical.path + File.separator)
        ) {
            return null
        }
        if (!canonical.isFile) return null
        return runCatching { canonical.readBytes() }.getOrNull()
    }
}

fun webContentType(path: String): String =
    when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }
