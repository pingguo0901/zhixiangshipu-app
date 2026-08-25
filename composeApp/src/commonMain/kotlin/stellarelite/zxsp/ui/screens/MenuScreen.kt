package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.ui.theme.DiningColors

data class MenuItem(
    val name: String,
    val price: Double,
    val category: String,
    val emoji: String
)

@Composable
fun MenuScreen() {
    val categories = listOf("招牌菜", "热菜", "凉菜", "汤品", "主食", "饮品")
    val menuItems = listOf(
        MenuItem("红烧排骨", 38.00, "热菜", "🍖"),
        MenuItem("清蒸鲈鱼", 58.00, "热菜", "🐟"),
        MenuItem("宫保鸡丁", 32.00, "热菜", "🍗"),
        MenuItem("蒜蓉西兰花", 22.00, "热菜", "🥦"),
        MenuItem("拍黄瓜", 12.00, "凉菜", "🥒"),
        MenuItem("皮蛋豆腐", 15.00, "凉菜", "🥚"),
        MenuItem("紫菜蛋花汤", 18.00, "汤品", "🍲"),
        MenuItem("酸辣汤", 20.00, "汤品", "🥣"),
        MenuItem("蛋炒饭", 16.00, "主食", "🍚"),
        MenuItem("手工水饺", 25.00, "主食", "🥟"),
        MenuItem("冰红茶", 8.00, "饮品", "🧊"),
        MenuItem("酸梅汤", 10.00, "饮品", "🍹"),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "📋 菜单管理",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DiningColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        categories.forEach { category ->
            item {
                Text(
                    category,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DiningColors.Primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            val items = menuItems.filter { it.category == category }
            if (items.isEmpty()) {
                item {
                    Text(
                        "暂无菜品",
                        color = DiningColors.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(items) { item ->
                    MenuItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun MenuItemCard(item: MenuItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.emoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        item.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = DiningColors.TextPrimary
                    )
                    Text(
                        item.category,
                        fontSize = 12.sp,
                        color = DiningColors.TextMuted
                    )
                }
            }
            Text(
                "¥%.2f".format(item.price),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DiningColors.Primary
            )
        }
    }
}
