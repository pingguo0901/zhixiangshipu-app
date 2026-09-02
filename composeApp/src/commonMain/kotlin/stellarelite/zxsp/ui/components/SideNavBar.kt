package stellarelite.zxsp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.data.t
import stellarelite.zxsp.ui.theme.DiningColors

// 桌面端左侧快捷导航栏（对标网页员工版 sidebar）
@Composable
fun SideNavBar(
    currentTab: DiningTab,
    onTabSelected: (DiningTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(DiningColors.NavBar)
            .padding(horizontal = 14.dp, vertical = 20.dp)
    ) {
        // Logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🍢", fontSize = 26.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                t("炙巷食铺", "ZHI XIANG"),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DiningColors.TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // 导航项
        DiningTab.entries.forEach { tab ->
            val selected = currentTab == tab
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) DiningColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tabLabel(tab),
                    tint = if (selected) DiningColors.Primary else DiningColors.TextMuted,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    tabLabel(tab),
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) DiningColors.Primary else DiningColors.TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // 用户信息
        Text(
            SessionManager.staffName.ifBlank { "—" },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DiningColors.TextPrimary
        )
        Text(
            if (SessionManager.isAdmin) t("老板", "Owner") else t("员工", "Staff"),
            fontSize = 12.sp,
            color = DiningColors.TextMuted
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}
