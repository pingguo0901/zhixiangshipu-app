package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.data.MenuItem
import stellarelite.zxsp.data.MenuRepository
import stellarelite.zxsp.platform.rememberImagePicker
import stellarelite.zxsp.ui.theme.DiningColors

private enum class MenuDialog { None, Action, Add, Modify, Delete }

@Composable
fun MenuScreen() {
    val categories = MenuRepository.categories
    val items = MenuRepository.items

    var dialog by remember { mutableStateOf(MenuDialog.None) }
    var modifyInitial by remember { mutableStateOf<MenuItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题栏 + 编辑按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📋 菜单管理",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DiningColors.TextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { dialog = MenuDialog.Action },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("✏️ 编辑", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            categories.forEach { category ->
                item(key = "header_$category") {
                    Text(
                        category,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DiningColors.Primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                val catItems = items.filter { it.category == category }
                if (catItems.isEmpty()) {
                    item(key = "empty_$category") {
                        Text(
                            "暂无菜品",
                            color = DiningColors.TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(catItems, key = { it.id }) { item ->
                        MenuItemCard(item)
                    }
                }
            }
        }
    }

    when (dialog) {
        MenuDialog.Action -> EditActionDialog(
            onDismiss = { dialog = MenuDialog.None },
            onAdd = { dialog = MenuDialog.Add },
            onModify = { modifyInitial = null; dialog = MenuDialog.Modify },
            onDelete = { dialog = MenuDialog.Delete }
        )
        MenuDialog.Add -> AddDishDialog(onDismiss = { dialog = MenuDialog.None })
        MenuDialog.Modify -> ModifyDishDialog(
            initialItem = modifyInitial,
            onDismiss = { dialog = MenuDialog.None }
        )
        MenuDialog.Delete -> DeleteDishDialog(
            onDismiss = { dialog = MenuDialog.None },
            onModifyItem = { item ->
                modifyInitial = item
                dialog = MenuDialog.Modify
            }
        )
        MenuDialog.None -> {}
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
                DishImage(item.image, item.emoji, 48.dp)
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
                "RM%.2f".format(item.price),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DiningColors.Primary
            )
        }
    }
}

// ===== 编辑动作选择弹窗 =====
@Composable
private fun EditActionDialog(
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onModify: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("编辑菜单", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionRow("➕", "添加菜品") { onDismiss(); onAdd() }
                ActionRow("✏️", "修改菜品") { onDismiss(); onModify() }
                ActionRow("🗑️", "删除菜品") { onDismiss(); onDelete() }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) }
        }
    )
}

@Composable
private fun ActionRow(emoji: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
    }
}

// ===== 添加菜品弹窗 =====
@Composable
private fun AddDishDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val pickImage = rememberImagePicker { image = it }

    val price = priceText.toDoubleOrNull()
    val canSave = name.isNotBlank() && price != null && price > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("添加菜品", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PhotoPickerBox(image, onClick = pickImage)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("菜品名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("单价 (RM)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    MenuRepository.addItem(
                        name.trim(),
                        price!!,
                        MenuRepository.categories.firstOrNull() ?: "烧烤",
                        image
                    )
                    onDismiss()
                }
            ) {
                Text(
                    "保存",
                    color = if (canSave) DiningColors.Primary else DiningColors.TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) }
        }
    )
}

// ===== 修改菜品弹窗 =====
@Composable
private fun ModifyDishDialog(initialItem: MenuItem?, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(initialItem) }

    if (selected == null) {
        // 阶段1：选择要修改的菜品
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = DiningColors.Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("‹ 返回", color = DiningColors.Primary) }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("修改菜品", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                }
            },
            text = {
                DishPickerList { selected = it }
            },
            confirmButton = {},
            dismissButton = {}
        )
    } else {
        // 阶段2：编辑选中菜品
        val item = selected!!
        var name by remember(item.id) { mutableStateOf(item.name) }
        var priceText by remember(item.id) { mutableStateOf(item.price.toString()) }
        var image by remember(item.id) { mutableStateOf(item.image) }
        val pickImage = rememberImagePicker { image = it }

        val price = priceText.toDoubleOrNull()
        val canSave = name.isNotBlank() && price != null && price > 0

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = DiningColors.Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = null }) { Text("‹ 返回", color = DiningColors.Primary) }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("修改菜品", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PhotoPickerBox(image, onClick = pickImage)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("菜品名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("单价 (RM)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canSave,
                    onClick = {
                        MenuRepository.updateItem(item.id, name.trim(), price!!, item.category, image)
                        onDismiss()
                    }
                ) {
                    Text(
                        "保存",
                        color = if (canSave) DiningColors.Primary else DiningColors.TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) }
            }
        )
    }
}

// ===== 删除菜品弹窗 =====
@Composable
private fun DeleteDishDialog(
    onDismiss: () -> Unit,
    onModifyItem: (MenuItem) -> Unit
) {
    var selected by remember { mutableStateOf<MenuItem?>(null) }

    if (selected == null) {
        // 阶段1：选择要删除的菜品
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = DiningColors.Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("‹ 返回", color = DiningColors.Primary) }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("删除菜品", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                }
            },
            text = {
                DishPickerList { selected = it }
            },
            confirmButton = {},
            dismissButton = {}
        )
    } else {
        // 阶段2：确认删除
        val item = selected!!
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = DiningColors.Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = null }) { Text("‹ 返回", color = DiningColors.Primary) }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("删除菜品", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DishImage(item.image, item.emoji, 72.dp)
                    Text(
                        "确定删除「${item.name}」吗？",
                        fontSize = 15.sp,
                        color = DiningColors.TextPrimary
                    )
                    Text(
                        "RM%.2f".format(item.price),
                        fontSize = 14.sp,
                        color = DiningColors.TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        MenuRepository.removeItem(item.id)
                        onDismiss()
                    }
                ) {
                    Text("删除", color = DiningColors.Error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss(); onModifyItem(item) }) {
                    Text("修改", color = DiningColors.Primary)
                }
            }
        )
    }
}

// ===== 菜品选择列表 =====
@Composable
private fun DishPickerList(onSelect: (MenuItem) -> Unit) {
    val items = MenuRepository.items
    if (items.isEmpty()) {
        Text(
            "暂无菜品",
            color = DiningColors.TextMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(item) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DishImage(item.image, item.emoji, 40.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        item.name,
                        fontSize = 15.sp,
                        color = DiningColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "RM%.2f".format(item.price),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = DiningColors.Primary
                    )
                }
            }
        }
    }
}

// ===== 通用菜品图片 =====
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
            Text(emoji, fontSize = if (size.value >= 48f) 22.sp else 18.sp)
        }
    }
}

// ===== 照片上传框 =====
@Composable
private fun PhotoPickerBox(image: ImageBitmap?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DiningColors.SurfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📷", fontSize = 28.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("上传菜品照片", fontSize = 12.sp, color = DiningColors.TextSecondary)
            }
        }
    }
}
