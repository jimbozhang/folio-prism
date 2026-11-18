package com.jimbozhang.folioprism.data.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jimbozhang.folioprism.FolioPrismApplication
import com.jimbozhang.folioprism.data.price.OkHttpFetcher
import com.jimbozhang.folioprism.platform.storage.StoragePaths
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface WebDataUiState {
    data object Idle : WebDataUiState
    data object Starting : WebDataUiState
    data class Running(val url: String) : WebDataUiState
    data class Failed(val message: String) : WebDataUiState
}

/**
 * Foreground service hosting [WebDataServer] on the LAN so a computer on the
 * same network can view the data file in a browser. Read-only in this first
 * version. The service owns the server instance; the UI toggles it through
 * [start]/[stop] and observes [state].
 */
class WebDataService : Service() {

    private var server: WebDataServer? = null
    private var quoteStore: WebQuoteStore? = null
    private var historyStore: WebHistoryStore? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_STOP, false) == true) {
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }
        startServer()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun startServer() {
        if (server != null) return
        val fetcher = OkHttpFetcher()
        val quotes = WebQuoteStore(fetcher)
        val history = WebHistoryStore(fetcher, StoragePaths.priceCacheFile())
        val repository = (application as FolioPrismApplication).portfolioRepository
        val api = WebPortfolioApi(
            repository = repository,
            dataFile = StoragePaths.dataFile(),
            quotes = quotes,
            history = history,
        )
        val webAssets = AndroidWebAssets(applicationContext.assets)
        for (port in BASE_PORT..BASE_PORT + PORT_SPAN) {
            val candidate = WebDataServer(port, webAssets, api)
            if (runCatching { candidate.start() }.isSuccess) {
                server = candidate
                quoteStore = quotes
                historyStore = history
                updateState(
                    WebDataUiState.Running(
                        "http://${localIpv4() ?: "127.0.0.1"}:$port",
                    ),
                )
                startInForeground(port)
                return
            }
            candidate.stop()
        }
        quotes.clear()
        history.clear()
        updateState(WebDataUiState.Failed("端口 $BASE_PORT 被占用，无法启动服务"))
        stopSelf()
    }

    private fun startInForeground(port: Int) {
        val notification = buildNotification(port)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(port: Int): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "局域网数据服务",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val stopAction = PendingIntent.getService(
            this,
            0,
            Intent(this, WebDataService::class.java).putExtra(EXTRA_STOP, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("局域网数据服务运行中 · 端口 $port")
            .setContentText("同一局域网内的电脑可用浏览器查看数据文件。")
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(0, "停止", stopAction)
            .build()
    }

    private fun stopServer() {
        server?.stop()
        server = null
        quoteStore?.clear()
        quoteStore = null
        historyStore?.clear()
        historyStore = null
        updateState(WebDataUiState.Idle)
    }

    private fun localIpv4(): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
        interfaces.asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filter { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
            .firstOrNull()
            ?.hostAddress
    }.getOrNull()

    companion object {
        private const val BASE_PORT = 8420
        private const val PORT_SPAN = 10
        private const val EXTRA_STOP = "extra_stop"
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "web_data_service"

        private val _state = MutableStateFlow<WebDataUiState>(WebDataUiState.Idle)
        val state: StateFlow<WebDataUiState> = _state.asStateFlow()

        fun updateState(newState: WebDataUiState) {
            _state.value = newState
        }

        fun start(context: Context) {
            updateState(WebDataUiState.Starting)
            context.startForegroundService(Intent(context, WebDataService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WebDataService::class.java).putExtra(EXTRA_STOP, true),
            )
        }
    }
}
