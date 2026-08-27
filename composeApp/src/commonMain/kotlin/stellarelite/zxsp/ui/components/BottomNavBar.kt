package stellarelite.zxsp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.ui.theme.DiningColors

enum class DiningTab(val label: String, val emoji: String) {
    Home("工作台", "🏠"),
    Orders("订单", "🧾"),
    Warehouse("仓库", "📦"),
    Finance("记账", "💸"),
    More("更多", "⚙️")
}

@Composable
fun BottomNavBar(
    currentTab: DiningTab,
    onTabSelected: (DiningTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DiningColors.NavBar)
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
        Text(tab.emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            tab.label,
            color = if (isSelected) DiningColors.Primary else DiningColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
