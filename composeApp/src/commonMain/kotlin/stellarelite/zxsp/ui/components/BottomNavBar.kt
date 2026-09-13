package stellarelite.zxsp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.data.t
import stellarelite.zxsp.ui.theme.DiningColors

enum class DiningTab(val label: String, val icon: ImageVector) {
    Home("工作台", Icons.Outlined.Home),
    Takeaway("外卖工作台", Icons.Outlined.DeliveryDining),
    Orders("订单", Icons.Outlined.ReceiptLong),
    Warehouse("仓库", Icons.Outlined.Inventory2),
    Finance("记账", Icons.Outlined.AccountBalanceWallet),
    More("更多", Icons.Outlined.MoreHoriz)
}

// 底部标签显示文案（跟随全局语言）
internal fun tabLabel(tab: DiningTab): String = when (tab) {
    DiningTab.Home -> t("工作台", "Dashboard")
    DiningTab.Takeaway -> t("外卖工作台", "Delivery")
    DiningTab.Orders -> t("订单", "Orders")
    DiningTab.Warehouse -> t("仓库", "Warehouse")
    DiningTab.Finance -> t("记账", "Finance")
    DiningTab.More -> t("更多", "More")
}

@Composable
fun BottomNavBar(
    currentTab: DiningTab,
    onTabSelected: (DiningTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DiningColors.NavBar)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DiningTab.entries.forEach { tab ->
                NavTabItem(
                    tab = tab,
                    isSelected = currentTab == tab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavTabItem(
    tab: DiningTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = tabLabel(tab),
            tint = if (isSelected) DiningColors.Primary else DiningColors.TextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            tabLabel(tab),
            color = if (isSelected) DiningColors.Primary else DiningColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// 桌面版左侧竖排快捷栏（F1~F6 切换）
@Composable
fun SideNavBar(
    currentTab: DiningTab,
    onTabSelected: (DiningTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(DiningColors.NavBar)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DiningTab.entries.forEach { tab ->
            SideNavItem(
                tab = tab,
                isSelected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SideNavItem(
    tab: DiningTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) DiningColors.Primary else Color.Transparent
    val fg = if (isSelected) DiningColors.Surface else DiningColors.TextPrimary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = tabLabel(tab),
            tint = fg,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            tabLabel(tab),
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
