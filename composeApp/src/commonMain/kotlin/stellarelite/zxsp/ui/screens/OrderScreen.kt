package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.data.MenuItem
import stellarelite.zxsp.data.MenuRepository
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun OrderScreen(onBack: () -> Unit) {
    var selectedTableNo by remember { mutableStateOf(1) }
    val quantities = remember { mutableStateMapOf<Int, Int>() }
    var showSuccess by remember { mutableStateOf(false) }

    val menuItems = MenuRepository.items
    val totalItems = quantities.values.sum()
    val totalAmount = menuItems.sumOf { it.price * (quantities[it.id] ?: 0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiningColors.Background)
    ) {
        // 顶部标题 + 返回
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ 返回", color = DiningColors.Primary, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "🛒 开台点餐",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DiningColors.TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 选择桌台
            Text(
                "选择桌台",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = DiningColors.TextPrimary
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (col in 0 until 4) {
                            val tableNo = row * 4 + col + 1
                            OrderTableCell(
                                tableNo = tableNo,
                                selected = tableNo == selectedTableNo,
                                onClick = { selectedTableNo = tableNo },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 菜单
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "菜单 · 烧烤",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DiningColors.TextPrimary
                )
                Text(
                    "共 $totalItems 件 · RM %.2f".format(totalAmount),
                    fontSize = 13.sp,
                    color = DiningColors.Primary
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(menuItems, key = { it.id }) { item ->
                    OrderMenuItemRow(
                        item = item,
                        quantity = quantities[item.id] ?: 0,
                        onMinus = {
                            val q = quantities[item.id] ?: 0
                            if (q > 1) quantities[item.id] = q - 1 else quantities.remove(item.id)
                        },
                        onPlus = { quantities[item.id] = (quantities[item.id] ?: 0) + 1 }
                    )
                }
            }

            // 底部按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消", color = DiningColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { showSuccess = true },
                    enabled = totalItems > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DiningColors.Primary,
                        disabledContainerColor = DiningColors.TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    Text("确认下单", color = DiningColors.Surface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // 下单成功弹窗
    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { showSuccess = false },
            containerColor = DiningColors.Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("✅ 下单成功", color = DiningColors.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("桌台 $selectedTableNo", fontSize = 14.sp, color = DiningColors.TextPrimary)
                    Text(
                        "共 $totalItems 件 · RM %.2f".format(totalAmount),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DiningColors.Primary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccess = false
                        onBack()
                    }
                ) {
                    Text("确定", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun OrderTableCell(
    tableNo: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1.3f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DiningColors.Primary else DiningColors.Surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "桌 $tableNo",
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) DiningColors.Surface else DiningColors.TextPrimary
        )
    }
}

@Composable
private fun OrderMenuItemRow(
    item: MenuItem,
    quantity: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DishImage(item.image, item.emoji, 44.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = DiningColors.TextPrimary
                )
                Text(
                    "RM%.2f".format(item.price),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DiningColors.Primary
                )
            }
            // 数量加减
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StepperButton("−", enabled = quantity > 0, onClick = onMinus)
                Text(
                    "$quantity",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DiningColors.TextPrimary
                )
                StepperButton("+", enabled = true, onClick = onPlus)
            }
        }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) DiningColors.SurfaceVariant else DiningColors.SurfaceVariant.copy(alpha = 0.5f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) DiningColors.Primary else DiningColors.TextMuted
        )
    }
}

@Composable
private fun DishImage(image: ImageBitmap?, emoji: String, size: Dp) {
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(DiningColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = if (size.value >= 44f) 22.sp else 18.sp)
        }
    }
}
