package com.jimbozhang.folioprism.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.ui.theme.FolioGold
import com.jimbozhang.folioprism.ui.theme.FolioGoldSoft
import com.jimbozhang.folioprism.ui.theme.FolioMuted
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.charts.ChartsScreen
import com.jimbozhang.folioprism.ui.debug.QuoteFailureScreen
import com.jimbozhang.folioprism.ui.home.HomeScreen
import com.jimbozhang.folioprism.ui.settings.SettingsScreen
import com.jimbozhang.folioprism.ui.transactions.TransactionsScreen

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    Tab("home", "首页", Icons.Filled.Home),
    Tab("charts", "图表", Icons.Filled.BarChart),
    Tab("transactions", "交易", Icons.AutoMirrored.Filled.ReceiptLong),
    Tab("settings", "设置", Icons.Filled.Settings),
)

@Composable
fun FolioPrismApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val historyState by viewModel.historyState.collectAsState()
    val navController = rememberNavController()

    when (val current = state) {
        is AppUiState.Loading -> LoadingScreen("正在加载数据…")
        is AppUiState.LoadingQuotes -> LoadingScreen("正在获取行情…")
        is AppUiState.Error -> ErrorScreen(current.message, onRetry = viewModel::load)
        is AppUiState.QuoteFailed -> QuoteFailureScreen(
            data = current.data,
            failures = current.failures,
            logs = current.logs,
            onRefresh = viewModel::refreshQuotes,
            onSaveLog = viewModel::saveLog,
            onSetManualPrice = viewModel::setManualPrice,
        )
        is AppUiState.Ready -> MainScaffold(
            navController = navController,
            data = current.data,
            quotes = current.quotes,
            isRefreshing = current.refreshing,
            historyState = historyState,
            onLoadHistory = viewModel::loadHistory,
            onRefresh = viewModel::refreshQuotes,
            onUpdateData = viewModel::updateData,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    navController: NavHostController,
    data: PortfolioData,
    quotes: QuoteSnapshot,
    isRefreshing: Boolean,
    historyState: HistoryUiState,
    onLoadHistory: (java.time.LocalDate, java.time.LocalDate) -> Unit,
    onRefresh: () -> Unit,
    onUpdateData: ((PortfolioData) -> PortfolioData) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FolioGold,
                            selectedTextColor = FolioGold,
                            indicatorColor = FolioGoldSoft,
                            unselectedIconColor = FolioMuted,
                            unselectedTextColor = FolioMuted,
                        ),
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                HomeRoute(
                    data = data,
                    quotes = quotes,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                )
            }
            composable("charts") {
                ChartsScreen(
                    data = data,
                    quotes = quotes,
                    historyState = historyState,
                    onLoadHistory = onLoadHistory,
                    contentPadding = PaddingValues(0.dp),
                )
            }
            composable("transactions") {
                TransactionsScreen(
                    data = data,
                    quotes = quotes,
                    onUpdateData = onUpdateData,
                    contentPadding = PaddingValues(0.dp),
                )
            }
            composable("settings") {
                SettingsScreen(
                    data = data,
                    onUpdateData = onUpdateData,
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRoute(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        HomeScreen(
            data = data,
            quotes = quotes,
            contentPadding = PaddingValues(0.dp),
        )
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "加载失败",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}
