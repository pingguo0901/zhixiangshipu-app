package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.ui.theme.DiningColors

data class OrderItem(
    val name: String,
    val price: Double,
    var quantity: Int,
    val emoji: String
)

@Composable
fun CashierScreen() {
    var orderItems by remember {
        mutableStateOf(listOf(
            OrderItem("红烧排骨", 38.0, 2, "🍖"),
            OrderItem("蛋炒饭", 16.0, 1, "🍚"),
            OrderItem("酸梅汤", 10.0, 3, "🍹"),
        ))
    }
    var discount by remember { mutableStateOf(0.0) }

    val subtotal = orderItems.sumOf { it.price * it.quantity }
    val total = subtotal - discount
    val itemCount = orderItems.sumOf { it.quantity }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "💰 收银台",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DiningColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "当前订单 · $itemCount 件商品",
                fontSize = 14.sp,
                color = DiningColors.TextSecondary
            )
        }

        // 订单列表
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("订单明细", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DiningColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    orderItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.emoji, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(item.name, fontSize = 15.sp, color = DiningColors.TextPrimary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("x${item.quantity}", fontSize = 13.sp, color = DiningColors.TextMuted)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "¥%.2f".format(item.price * item.quantity),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DiningColors.TextPrimary
                                )
                            }
                        }
                        if (item != orderItems.last()) {
                            HorizontalDivider(color = DiningColors.SurfaceVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // 金额汇总
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SummaryRow("小计", "¥%.2f".format(subtotal))
                    SummaryRow("优惠", "-¥%.2f".format(discount), DiningColors.Success)
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = DiningColors.SurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("应付金额", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
                        Text(
                            "¥%.2f".format(total),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = DiningColors.Primary
                        )
                    }
                }
            }
        }

        // 结账按钮
        item {
            Button(
                onClick = { /* 结账逻辑 */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)
            ) {
                Text("💳 确认收款 ¥%.2f".format(total), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = DiningColors.TextPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = DiningColors.TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
