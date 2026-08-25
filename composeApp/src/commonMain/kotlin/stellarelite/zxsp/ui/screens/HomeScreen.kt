package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun HomeScreen() {
    var todayRevenue by remember { mutableStateOf(2856.50) }
    var orderCount by remember { mutableStateOf(47) }
    var tableCount by remember { mutableStateOf(12) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部标题
        Text(
            "🍽️ 炙巷食谱",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DiningColors.TextPrimary
        )
        Text(
            "今日营业概览",
            fontSize = 14.sp,
            color = DiningColors.TextSecondary
        )

        // 今日数据卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DiningColors.Primary)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("今日营业额", fontSize = 14.sp, color = DiningColors.Surface.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "¥%.2f".format(todayRevenue),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = DiningColors.Surface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("📋", "$orderCount", "订单数")
                    StatItem("🪑", "$tableCount", "桌台数")
                    StatItem("⭐", "4.8", "评分")
                }
            }
        }

        // 快捷入口
        Text("快捷操作", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard("🛒", "开台点餐", Modifier.weight(1f))
            QuickActionCard("📊", "营业报表", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard("📋", "菜品管理", Modifier.weight(1f))
            QuickActionCard("👥", "员工排班", Modifier.weight(1f))
        }

        // 最近订单
        Text("最近订单", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                RecentOrderItem("05", "¥238.00", "已完成", DiningColors.Success)
                RecentOrderItem("04", "¥156.50", "进行中", DiningColors.Warning)
                RecentOrderItem("03", "¥89.00", "已完成", DiningColors.Success)
            }
        }
    }
}

@Composable
private fun StatItem(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.Surface)
        Text(label, fontSize = 11.sp, color = DiningColors.Surface.copy(alpha = 0.7f))
    }
}

@Composable
private fun QuickActionCard(emoji: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .clickable { /* 导航 */ }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DiningColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 18.sp)
            }
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
        }
    }
}

@Composable
private fun RecentOrderItem(orderNo: String, amount: String, status: String, statusColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("桌台 $orderNo", fontSize = 14.sp, color = DiningColors.TextPrimary)
        Text(amount, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
        Text(status, fontSize = 12.sp, color = statusColor)
    }
}
