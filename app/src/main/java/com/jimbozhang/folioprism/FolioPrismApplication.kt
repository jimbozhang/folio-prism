package com.jimbozhang.folioprism

import android.app.Application
import com.jimbozhang.folioprism.data.store.PortfolioRepository
import com.jimbozhang.folioprism.platform.storage.StoragePaths

class FolioPrismApplication : Application() {
    /**
     * The single data repository for the whole process. The UI and any
     * in-process service (such as the LAN data browser) must observe and
     * write through the same in-memory state so one of them can never
     * overwrite the other with stale data.
     */
    val portfolioRepository: PortfolioRepository by lazy {
        PortfolioRepository(
            dataFile = StoragePaths.dataFile(),
            backupDir = StoragePaths.backupDir(),
        )
    }
}
