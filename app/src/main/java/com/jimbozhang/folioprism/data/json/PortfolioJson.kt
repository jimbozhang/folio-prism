package com.jimbozhang.folioprism.data.json

import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.withoutLegacyCreatedAt
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PortfolioJson {

    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = false
        ignoreUnknownKeys = false
        classDiscriminator = "type"
    }

    fun encode(data: PortfolioData): String = json.encodeToString(data)

    /**
     * Decoding also drops the legacy per-event recording timestamp, so files
     * written by older versions lose it on their next save instead of keeping
     * it until every event happens to be edited.
     */
    fun decode(text: String): PortfolioData =
        json.decodeFromString<PortfolioData>(text).let { data ->
            data.copy(events = data.events.map { it.withoutLegacyCreatedAt() })
        }

    fun load(file: File): PortfolioData =
        decode(file.readText(Charsets.UTF_8))

    /**
     * Writes the file via a temporary file and verifies it before replacing the target.
     * Backup rotation is handled by a higher-level repository.
     */
    fun saveAtomic(file: File, data: PortfolioData) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        val text = encode(data)
        tmp.writeText(text, Charsets.UTF_8)

        // Verify the serialized payload before touching the real file.
        decode(tmp.readText(Charsets.UTF_8))

        if (file.exists() && !file.delete()) {
            error("Unable to replace ${file.path}")
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }
}
