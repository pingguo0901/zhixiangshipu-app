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
import stellarelite.zxsp.ui.components.BottomNavBar
import stellarelite.zxsp.ui.components.DiningTab
import stellarelite.zxsp.ui.screens.*
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun App(
    onCheckUpdate: (suspend () -> VersionInfo?)? = null,
    onRequestUpdate: ((VersionInfo) -> Unit)? = null
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
    }

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
                .padding(horizontal = 0.dp)
        ) {
            AnimatedContent(targetState = currentTab) { tab ->
                when (tab) {
                    DiningTab.Menu -> MenuScreen()
                    DiningTab.Cashier -> CashierScreen()
                    DiningTab.Home -> HomeScreen()
                    DiningTab.Warehouse -> WarehouseScreen()
                    DiningTab.Settings -> SettingsScreen()
                }
            }
        }
        BottomNavBar(currentTab = currentTab, onTabSelected = { currentTab = it })
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
