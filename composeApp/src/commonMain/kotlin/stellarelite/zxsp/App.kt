package stellarelite.zxsp

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.ui.components.BottomNavBar
import stellarelite.zxsp.ui.components.SideNavBar
import stellarelite.zxsp.util.decodeJwtExp
import stellarelite.zxsp.util.decodeJwtSub
import stellarelite.zxsp.ui.components.DiningTab
import stellarelite.zxsp.ui.screens.*
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun App(
    onCheckUpdate: (suspend () -> VersionInfo?)? = null,
    onRequestUpdate: ((VersionInfo) -> Unit)? = null,
    useSideNav: Boolean = false
) {
    var currentTab by remember { mutableStateOf(DiningTab.Home) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<VersionInfo?>(null) }

    // Check for updates on launch
    LaunchedEffect(Unit) {
        onCheckUpdate?.let { checkFn ->
            try {
                val info = checkFn()
                if (info != null) {
                    updateInfo = info
                    showUpdateDialog = true
                }
            } catch (_: Exception) { }
        }

        // 会话缺 staffId（下单会被 RLS 拦截），尝试补全，否则强制重新登录
        if (SessionManager.isLoggedIn) {
            // access token 过期自动用 refresh_token 刷新，避免退出后重开又掉线
            val exp = SessionManager.accessToken?.let { decodeJwtExp(it) }
            val nowSec = Clock.System.now().toEpochMilliseconds() / 1000
            if (exp == null || exp <= nowSec) {
                val rt = SessionManager.refreshToken
                val ns = rt?.let { SupabaseClient.refreshSession(it).getOrNull() }
                if (ns != null) {
                    SessionManager.updateTokens(ns.access_token, ns.refresh_token)
                } else {
                    SessionManager.clear()
                }
            }
        }

        // 每次启动都从数据库刷新角色（修复旧会话 role 不更新导致权限判断错误）
        if (SessionManager.isLoggedIn) {
            val uid = SessionManager.authUid ?: decodeJwtSub(SessionManager.accessToken ?: "")
            val staff = if (uid != null) runCatching { SupabaseClient.fetchMyStaff(uid) }.getOrNull() else null
            if (staff != null) {
                if (staff.is_active) {
                    SessionManager.setSession(SessionManager.accessToken, staff.id, staff.staff_name, staff.role, uid)
                } else {
                    SessionManager.clear()
                }
            }
            // staff == null（网络异常）：保留现有会话，避免误踢
        }
    }

    if (!SessionManager.isLoggedIn) {
        LoginScreen()
        return
    }

    // 自动监听新订单打印厨房单（网页下单 → 店内手机自动出单）
    LaunchedEffect(Unit) {
        KitchenAutoPrinter.initBaseline()
        while (true) {
            try { KitchenAutoPrinter.pollOnce() } catch (_: Exception) { }
            delay(3000)
        }
    }

    if (useSideNav) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DiningColors.Background)
        ) {
            SideNavBar(currentTab = currentTab, onTabSelected = { currentTab = it })
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                AnimatedContent(targetState = currentTab) { tab ->
                    when (tab) {
                        DiningTab.Home -> DashboardScreen()
                        DiningTab.Orders -> OrdersScreen()
                        DiningTab.Warehouse -> WarehouseScreen()
                        DiningTab.Finance -> FinanceScreen()
                        DiningTab.More -> MoreScreen()
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DiningColors.Background)
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(targetState = currentTab) { tab ->
                    when (tab) {
                        DiningTab.Home -> DashboardScreen()
                        DiningTab.Orders -> OrdersScreen()
                        DiningTab.Warehouse -> WarehouseScreen()
                        DiningTab.Finance -> FinanceScreen()
                        DiningTab.More -> MoreScreen()
                    }
                }
            }
            BottomNavBar(currentTab = currentTab, onTabSelected = { currentTab = it })
        }
    }

    // Update Dialog
    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = DiningColors.Surface,
            title = {
                Text(
                    "发现新版本 v${updateInfo!!.versionName}",
                    color = DiningColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    updateInfo!!.changelog.replace("- ", "• "),
                    color = DiningColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateDialog = false
                        onRequestUpdate?.invoke(updateInfo!!)
                    }
                ) {
                    Text("立即更新", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("稍后", color = DiningColors.TextMuted)
                }
            }
        )
    }
}
