package com.jimbozhang.folioprism.data.store

import com.jimbozhang.folioprism.data.json.DefaultData
import com.jimbozhang.folioprism.data.json.PortfolioJson
import com.jimbozhang.folioprism.domain.model.PortfolioData
import java.io.File
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class PortfolioRepository(
    private val dataFile: File,
    private val backupDir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _data = MutableStateFlow<PortfolioData?>(null)
    val data: StateFlow<PortfolioData?> = _data.asStateFlow()

    suspend fun load(): PortfolioData = withContext(dispatcher) {
        dataFile.parentFile?.mkdirs()
        val loaded = if (dataFile.exists()) {
            PortfolioJson.load(dataFile)
        } else {
            DefaultData.empty().also { PortfolioJson.saveAtomic(dataFile, it) }
        }
        _data.value = loaded
        loaded
    }

    suspend fun save(data: PortfolioData): PortfolioData = withContext(dispatcher) {
        val updated = data.copy(
            meta = data.meta.copy(updatedAt = OffsetDateTime.now().toString()),
        )
        BackupManager(backupDir).backupBeforeWrite(
            file = dataFile,
            retainCount = updated.settings.backup.retainCount,
        )
        PortfolioJson.saveAtomic(dataFile, updated)
        _data.value = updated
        updated
    }

    suspend fun update(transform: (PortfolioData) -> PortfolioData): PortfolioData {
        val current = _data.value ?: load()
        return save(transform(current))
    }
}
