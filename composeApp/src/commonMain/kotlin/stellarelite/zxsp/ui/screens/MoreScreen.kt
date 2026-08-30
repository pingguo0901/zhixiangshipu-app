package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.network.MenuItem
import stellarelite.zxsp.network.Staff
import stellarelite.zxsp.network.Supplier
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.TableList
import stellarelite.zxsp.ui.theme.DiningColors
import stellarelite.zxsp.util.decodeJwtSub

private sealed class MoreNav {
    object Menu : MoreNav()
    object MenuManage : MoreNav()
    object Tables : MoreNav()
    object Suppliers : MoreNav()
    object Staffs : MoreNav()
}

@Composable
fun MoreScreen() {
    var nav by remember { mutableStateOf<MoreNav>(MoreNav.Menu) }
    // 进入页面时刷新角色，修复旧会话 role 缓存导致 Admin 按钮消失
    LaunchedEffect(Unit) {
        val uid = SessionManager.authUid ?: decodeJwtSub(SessionManager.accessToken ?: "")
        val staff = uid?.let { runCatching { SupabaseClient.fetchMyStaff(it) }.getOrNull() }
        if (staff != null && staff.is_active) {
            SessionManager.setSession(SessionManager.accessToken, staff.id, staff.staff_name, staff.role, uid)
        }
    }
    when (val n = nav) {
        is MoreNav.Menu -> MoreMenuView(
            onMenu = { nav = MoreNav.MenuManage },
            onTables = { nav = MoreNav.Tables },
            onSuppliers = { nav = MoreNav.Suppliers },
            onStaffs = { nav = MoreNav.Staffs }
        )
        is MoreNav.MenuManage -> MenuManageScreen(onBack = { nav = MoreNav.Menu })
        is MoreNav.Tables -> TableManageScreen(onBack = { nav = MoreNav.Menu })
        is MoreNav.Suppliers -> SupplierManageScreen(onBack = { nav = MoreNav.Menu })
        is MoreNav.Staffs -> StaffManageScreen(onBack = { nav = MoreNav.Menu })
    }
}

@Composable
private fun MoreMenuView(onMenu: () -> Unit, onTables: () -> Unit, onSuppliers: () -> Unit, onStaffs: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = null, tint = DiningColors.TextPrimary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("更多", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
        }
        Text("你好，${SessionManager.staffName}（${if (SessionManager.isAdmin) "老板" else "员工"}）", fontSize = 14.sp, color = DiningColors.TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))

        // 菜单管理（仅老板，维护菜品价格上下架）
        if (SessionManager.isAdmin) {
            MenuEntry(Icons.Outlined.RestaurantMenu, "菜品管理", "维护菜品、价格、上下架") { onMenu() }
            MenuEntry(Icons.Outlined.TableRestaurant, "桌台管理", "新增/编辑桌台、修改状态") { onTables() }
            MenuEntry(Icons.Outlined.LocalShipping, "供应商管理", "批发商档案、BRN、TIN") { onSuppliers() }
            MenuEntry(Icons.Outlined.Group, "员工管理", "新增/停用员工账号") { onStaffs() }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 退出登录
        Button(
            onClick = { SessionManager.clear() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Error.copy(alpha = 0.1f))
        ) {
            Text("退出登录", fontSize = 16.sp, color = DiningColors.Error)
        }
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = DiningColors.Primary, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                Text(desc, fontSize = 12.sp, color = DiningColors.TextMuted)
            }
            Text("›", fontSize = 20.sp, color = DiningColors.TextMuted)
        }
    }
}

// ============ 菜品管理（仅老板） ============
@Composable
private fun MenuManageScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MenuItem?>(null) }

    fun load() {
        scope.launch {
            loading = true
            runCatching { SupabaseClient.fetchMenuItems() }.onSuccess { menuItems = it }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Text("‹ 返回", color = DiningColors.Primary) }
            Text("菜品管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary, modifier = Modifier.align(Alignment.Center))
            Button(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.CenterEnd), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)) {
                Text("＋ 新增", color = DiningColors.Surface)
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DiningColors.Primary) }
        } else if (menuItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无菜品，点右上角新增", color = DiningColors.TextMuted, fontSize = 14.sp) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(menuItems, key = { it.id }) { m ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).clickable { editing = m },
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(m.item_name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
                                Text("${m.category} · ${m.unit}", fontSize = 12.sp, color = DiningColors.TextMuted)
                                Text("RM%.2f".format(m.sell_price_myr), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.Primary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = m.is_active,
                                onClick = {
                                    scope.launch {
                                        val ok = SupabaseClient.updateMenuItem(m.id, m.copy(is_active = !m.is_active))
                                        if (ok) {
                                            menuItems = menuItems.map { if (it.id == m.id) it.copy(is_active = !m.is_active) else it }
                                        }
                                    }
                                },
                                label = { Text(if (m.is_active) "下架" else "上架", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        MenuItemDialog(item = null, onDismiss = { showAdd = false }, onDone = { showAdd = false; load() })
    }
    editing?.let { item ->
        MenuItemDialog(item = item, onDismiss = { editing = null }, onDone = { editing = null; load() })
    }
}

// 菜品规格选项（可多选）
private val SPEC_OPTIONS = listOf("不辣", "香辣", "加辣", "外带")

@Composable
private fun MenuItemDialog(item: MenuItem?, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(item?.item_name ?: "") }
    var nameEn by remember { mutableStateOf(item?.name_en ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "") }
    var unit by remember { mutableStateOf(item?.unit ?: "") }
    var price by remember { mutableStateOf(if (item != null && item.sell_price_myr > 0) item.sell_price_myr.toString() else "") }
    // 规格多选（不辣/香辣/加辣/外带），存 notes 字段（逗号分隔）
    var specs by remember { mutableStateOf(item?.notes?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isEdit = item != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text(if (isEdit) "编辑菜品" else "新增菜品", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("菜品名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text("英文名（收据第二行，可空）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("分类（如：烧烤、主食）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("单位（如：串、份、瓶）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("售价 (RM)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                // 规格多选
                Text("规格（可多选）", fontSize = 13.sp, color = DiningColors.TextSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SPEC_OPTIONS.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            specs = if (s in specs) specs - s else specs + s
                        }) {
                            Checkbox(checked = s in specs, onCheckedChange = null)
                            Text(s, fontSize = 14.sp, color = DiningColors.TextPrimary)
                        }
                    }
                }
                if (error != null) Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !saving, onClick = {
                scope.launch {
                    saving = true; error = null
                    val p = price.trim().toDoubleOrNull()
                    if (p == null) {
                        saving = false; error = "售价格式不对"
                        return@launch
                    }
                    val ok = if (isEdit) {
                        SupabaseClient.updateMenuItem(item!!.id, item.copy(
                            item_name = name.trim(), name_en = nameEn.trim().ifBlank { null }, category = category.trim(), unit = unit.trim(),
                            sell_price_myr = p, notes = specs.joinToString(",").ifBlank { null }
                        ))
                    } else {
                        SupabaseClient.insertMenuItem(MenuItem(
                            item_name = name.trim(), name_en = nameEn.trim().ifBlank { null }, category = category.trim(), unit = unit.trim(),
                            sell_price_myr = p, notes = specs.joinToString(",").ifBlank { null }, is_active = true
                        )) != null
                    }
                    saving = false
                    if (ok) onDone() else error = "保存失败"
                }
            }) { Text("保存", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) } }
    )
}

// ============ 桌台管理（仅老板） ============
@Composable
private fun TableManageScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tables by remember { mutableStateOf<List<TableList>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            runCatching { SupabaseClient.fetchTables() }.onSuccess { tables = it }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Text("‹ 返回", color = DiningColors.Primary) }
            Text("桌台管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary, modifier = Modifier.align(Alignment.Center))
            Button(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.CenterEnd), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)) {
                Text("＋ 新增", color = DiningColors.Surface)
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DiningColors.Primary) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tables, key = { it.id }) { t ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(t.table_no, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
                                t.notes?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = DiningColors.TextMuted) }
                            }
                            // 状态切换
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("free" to "空闲", "occupied" to "占用", "cleaning" to "清理").forEach { (v, l) ->
                                    FilterChip(
                                        selected = t.table_status == v,
                                        onClick = {
                                            scope.launch {
                                                SupabaseClient.updateTable(t.id, t.copy(table_status = v))
                                                load()
                                            }
                                        },
                                        label = { Text(l, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        TableAddDialog(onDismiss = { showAdd = false }, onDone = { showAdd = false; load() })
    }
}

@Composable
private fun TableAddDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tableNo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("新增桌台", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = tableNo, onValueChange = { tableNo = it }, label = { Text("桌台号（如 T05、BAR-01）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注（大桌、户外桌）") }, modifier = Modifier.fillMaxWidth())
                if (error != null) Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = tableNo.isNotBlank() && !saving, onClick = {
                scope.launch {
                    saving = true; error = null
                    val r = SupabaseClient.insertTable(TableList(table_no = tableNo.trim(), notes = notes.trim().ifBlank { null }))
                    saving = false
                    if (r != null) onDone() else error = "保存失败（桌台号可能重复）"
                }
            }) { Text("保存", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) } }
    )
}

// ============ 供应商管理（仅老板） ============
@Composable
private fun SupplierManageScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            runCatching { SupabaseClient.fetchSuppliers() }.onSuccess { suppliers = it }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    var editing by remember { mutableStateOf<Supplier?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Text("‹ 返回", color = DiningColors.Primary) }
            Text("供应商管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary, modifier = Modifier.align(Alignment.Center))
            Button(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.CenterEnd), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)) {
                Text("＋ 新增", color = DiningColors.Surface)
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DiningColors.Primary) }
        } else if (suppliers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无供应商", color = DiningColors.TextMuted, fontSize = 14.sp) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(suppliers, key = { it.id }) { s ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { editing = s },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.supplier_name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = DiningColors.TextMuted, modifier = Modifier.size(16.dp))
                            }
                            s.contact_person?.takeIf { it.isNotBlank() }?.let { Text("对接人：$it", fontSize = 12.sp, color = DiningColors.TextSecondary) }
                            s.phone?.takeIf { it.isNotBlank() }?.let { Text("电话：$it", fontSize = 12.sp, color = DiningColors.TextSecondary) }
                            s.supplier_brn?.takeIf { it.isNotBlank() }?.let { Text("BRN：$it", fontSize = 12.sp, color = DiningColors.TextMuted) }
                            s.supplier_tin?.takeIf { it.isNotBlank() }?.let { Text("TIN：$it", fontSize = 12.sp, color = DiningColors.TextMuted) }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        SupplierAddDialog(onDismiss = { showAdd = false }, onDone = { showAdd = false; load() })
    }
    editing?.let { s ->
        SupplierAddDialog(initial = s, onDismiss = { editing = null }, onDone = { editing = null; load() })
    }
}

@Composable
private fun SupplierAddDialog(onDismiss: () -> Unit, onDone: () -> Unit, initial: Supplier? = null) {
    val scope = rememberCoroutineScope()
    var name by remember(initial) { mutableStateOf(initial?.supplier_name ?: "") }
    var contact by remember(initial) { mutableStateOf(initial?.contact_person ?: "") }
    var phone by remember(initial) { mutableStateOf(initial?.phone ?: "") }
    var brn by remember(initial) { mutableStateOf(initial?.supplier_brn ?: "") }
    var tin by remember(initial) { mutableStateOf(initial?.supplier_tin ?: "") }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text(if (initial == null) "新增供应商" else "编辑供应商", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("批发商名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("对接人") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("联系电话") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = brn, onValueChange = { brn = it }, label = { Text("BRN/SSM 号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tin, onValueChange = { tin = it }, label = { Text("TIN 税号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注（猪肉、调料供应商）") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !saving, onClick = {
                scope.launch {
                    saving = true
                    val ok = if (initial == null) {
                        SupabaseClient.insertSupplier(Supplier(
                            supplier_name = name.trim(), contact_person = contact.trim().ifBlank { null },
                            phone = phone.trim().ifBlank { null }, supplier_brn = brn.trim().ifBlank { null },
                            supplier_tin = tin.trim().ifBlank { null }, notes = notes.trim().ifBlank { null }
                        )) != null
                    } else {
                        SupabaseClient.updateSupplier(initial.id, Supplier(
                            id = initial.id, supplier_name = name.trim(), contact_person = contact.trim().ifBlank { null },
                            phone = phone.trim().ifBlank { null }, supplier_brn = brn.trim().ifBlank { null },
                            supplier_tin = tin.trim().ifBlank { null }, notes = notes.trim().ifBlank { null }
                        ))
                    }
                    saving = false
                    if (ok) onDone()
                }
            }) { Text("保存", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (initial != null) {
                    TextButton(onClick = {
                        scope.launch {
                            val ok = SupabaseClient.deleteSupplier(initial.id)
                            if (ok) onDone()
                        }
                    }) { Text("删除", color = DiningColors.Error) }
                }
                TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) }
            }
        }
    )
}

// ============ 员工管理（仅老板） ============
@Composable
private fun StaffManageScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var staffs by remember { mutableStateOf<List<Staff>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun load() {
        scope.launch {
            loading = true
            runCatching { SupabaseClient.fetchStaffs() }.onSuccess { staffs = it }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Text("‹ 返回", color = DiningColors.Primary) }
            Text("员工管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary, modifier = Modifier.align(Alignment.Center))
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DiningColors.Primary) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(staffs, key = { it.id }) { s ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(s.staff_name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                                Text(if (s.role == "admin") "老板" else "员工", fontSize = 12.sp, color = if (s.role == "admin") DiningColors.Primary else DiningColors.TextMuted)
                            }
                            // 停用/启用切换
                            FilterChip(
                                selected = s.is_active,
                                onClick = {
                                    scope.launch {
                                        val ok = SupabaseClient.updateStaff(s.id, s.copy(is_active = !s.is_active))
                                        if (ok) {
                                            staffs = staffs.map { if (it.id == s.id) it.copy(is_active = !s.is_active) else it }
                                        }
                                    }
                                },
                                label = { Text(if (s.is_active) "停用" else "启用", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}
