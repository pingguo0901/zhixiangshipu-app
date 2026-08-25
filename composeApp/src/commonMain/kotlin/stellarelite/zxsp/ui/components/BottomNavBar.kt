package stellarelite.zxsp.ui.components

import androidx.compose.animation.*
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
    Menu("菜单", "📋"),
    Cashier("收银台", "💰"),
    Home("主页", "🍽️"),
    Warehouse("仓库", "📦"),
    Settings("设置", "⚙️")
}

@Composable
fun BottomNavBar(
    currentTab: DiningTab,
    onTabSelected: (DiningTab) -> Unit
) {
    val tabs = DiningTab.entries
    val centerIndex = tabs.indexOf(DiningTab.Home)
    val leftTabs = tabs.take(centerIndex)
    val rightTabs = tabs.drop(centerIndex + 1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DiningColors.NavBar)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f)) {
                leftTabs.forEach { tab ->
                    NavTabItem(
                        tab = tab,
                        isSelected = currentTab == tab,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(72.dp))

            Row(modifier = Modifier.weight(1f)) {
                rightTabs.forEach { tab ->
                    NavTabItem(
                        tab = tab,
                        isSelected = currentTab == tab,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 主页突出按钮 - 中间悬浮
        val isCenterActive = currentTab == DiningTab.Home
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (isCenterActive)
                            listOf(DiningColors.Primary, DiningColors.PrimaryDim)
                        else
                            listOf(DiningColors.PrimaryLight, DiningColors.Primary)
                    )
                )
                .clickable { onTabSelected(DiningTab.Home) },
            contentAlignment = Alignment.Center
        ) {
            Text(DiningTab.Home.emoji, fontSize = 26.sp)
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
        Text(tab.emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            tab.label,
            color = if (isSelected) DiningColors.Primary else DiningColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
