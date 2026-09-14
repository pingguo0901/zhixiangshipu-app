package stellarelite.zxsp

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.ui.components.BackHandlerOwner
import stellarelite.zxsp.ui.components.BottomNavBar
import stellarelite.zxsp.ui.components.DesktopTopBar
import stellarelite.zxsp.ui.components.SideNavBar
import stellarelite.zxsp.util.decodeJwtExp
import stellarelite.zxsp.util.decodeJwtSub
import stellarelite.zxsp.ui.components.DiningTab
import stellarelite.zxsp.ui.screens.*
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun App(
    onCheckUpdate: (suspend () -> VersionInfo?)? = null,
    onApplyUpdate: (suspend (VersionInfo, (Long, Long) -> Unit) -> String?)? = null,
    useDesktopLayout: Boolean = false,
    onExit: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(DiningTab.Home) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<VersionInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0f) }
    var updateError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

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
                } else if (!useDesktopLayout) {
                    // 桌面端保持登录，不自动登出（保证后台自动出单永远在线）
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
                    SessionManager.setSession(SessionManager.accessToken, staff.id, staff.staff_name, staff.role, uid, canPrintDaily = staff.can_print_daily, canPrintQr = staff.can_print_qr)
                } else if (!useDesktopLayout) {
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

    // 运行期自动续期：access_token 有效期仅 1 小时，快过期（剩 5 分钟内）就提前用 refresh_token 换新，
    // 避免手机一直开着、不重启导致 token 过期掉线自动退出
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val exp = SessionManager.accessToken?.let { decodeJwtExp(it) }
                val nowSec = Clock.System.now().toEpochMilliseconds() / 1000
                if (exp != null && exp - nowSec < 300) {
                    val rt = SessionManager.refreshToken
                    val ns = rt?.let { SupabaseClient.refreshSession(it).getOrNull() }
                    if (ns != null) {
                        SessionManager.updateTokens(ns.access_token, ns.refresh_token)
                    }
                }
            } catch (_: Exception) { }
            delay(60_000)
        }
    }

    // 自动监听新订单打印厨房单（网页下单 → 店内手机自动出单）
    LaunchedEffect(Unit) {
        KitchenAutoPrinter.initBaseline()
        while (true) {
            try { KitchenAutoPrinter.pollOnce() } catch (_: Exception) { }
            delay(3000)
        }
    }

    // 让根布局持有焦点，确保 F1~F6 快捷键能收到键盘事件（否则无焦点时按键无效）
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiningColors.Background)
            .statusBarsPadding()
            .focusable()
            .focusRequester(focusRequester)
            .then(
                if (useDesktopLayout) Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.F1 -> { currentTab = DiningTab.Home; true }
                            Key.F2 -> { currentTab = DiningTab.Takeaway; true }
                            Key.F3 -> { currentTab = DiningTab.Orders; true }
                            Key.F4 -> { currentTab = DiningTab.Warehouse; true }
                            Key.F5 -> { currentTab = DiningTab.Finance; true }
                            Key.F6 -> { currentTab = DiningTab.More; true }
                            Key.F12 -> { onExit(); true }
                            Key.Escape -> { BackHandlerOwner.dispatchBack() }
                            else -> false
                        }
                    } else false
                } else Modifier
            )
    ) {
        DesktopTopBar()
        if (useDesktopLayout) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SideNavBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    modifier = Modifier.width(200.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TabContent(currentTab, useDesktopLayout)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                TabContent(currentTab, useDesktopLayout)
            }
            BottomNavBar(currentTab = currentTab, onTabSelected = { currentTab = it })
        }
    }

    // Update Dialog
    if (showUpdateDialog && updateInfo != null) {
        if (updating) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = DiningColors.Surface,
                title = {
                    Text(
                        "正在更新 v${updateInfo!!.versionName}",
                        color = DiningColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("正在下载新版本，请稍候…", color = DiningColors.TextSecondary, fontSize = 14.sp)
                        LinearProgressIndicator(
                            progress = { updateProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${(updateProgress * 100).toInt()}%", color = DiningColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                confirmButton = {}
            )
        } else {
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
                    Column {
                        Text(
                            updateInfo!!.changelog.replace("- ", "• "),
                            color = DiningColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                        if (updateError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("⚠️ $updateError", color = DiningColors.Error, fontSize = 13.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            updateError = null
                            scope.launch {
                                val fn = onApplyUpdate
                                if (fn == null) {
                                    updateError = "更新功能不可用"
                                } else {
                                    updating = true
                                    updateProgress = 0f
                                    val err = fn.invoke(updateInfo!!) { done, total ->
                                        if (total > 0) updateProgress = done.toFloat() / total.toFloat()
                                    }
                                    if (err != null) {
                                        updating = false
                                        updateError = err
                                    } else {
                                        // 成功：桌面端在 onApplyUpdate 内部退出；手机端交给系统下载后关闭弹窗
                                        showUpdateDialog = false
                                        updating = false
                                    }
                                }
                            }
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
}

@Composable
private fun TabContent(currentTab: DiningTab, useDesktopLayout: Boolean) {
    AnimatedContent(targetState = currentTab) { tab ->
        when (tab) {
            DiningTab.Home -> if (useDesktopLayout) DesktopDashboardScreen() else DashboardScreen()
            DiningTab.Takeaway -> if (useDesktopLayout) TakeawayDashboardScreen() else PhoneTakeawayScreen()
            DiningTab.Orders -> if (useDesktopLayout) DesktopOrdersScreen() else OrdersScreen()
            DiningTab.Warehouse -> WarehouseScreen()
            DiningTab.Finance -> FinanceScreen()
            DiningTab.More -> MoreScreen()
        }
    }
}
