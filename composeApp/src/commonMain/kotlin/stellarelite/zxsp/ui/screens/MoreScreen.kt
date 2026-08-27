package stellarelite.zxsp.ui.screens

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

private sealed class MoreNav {
    object Menu : MoreNav()
    object Tables : MoreNav()
    object Suppliers : MoreNav()
    object Staffs : MoreNav()
}

@Composable
fun MoreScreen() {
    var nav by remember { mutableStateOf<MoreNav>(MoreNav.Menu) }
    when (val n = nav) {
        is MoreNav.Menu -> MoreMenuView(
            onTables = { nav = MoreNav.Tables },
            onSuppliers = { nav = MoreNav.Suppliers },
            onStaffs = { nav = MoreNav.Staffs }
        )
        is MoreNav.Tables -> TableManageScreen(onBack = { nav = MoreNav.Menu })
        is MoreNav.Suppliers -> SupplierManageScreen(onBack = { nav = MoreNav.Menu })
        is MoreNav.Staffs -> StaffManageScreen(onBack = { nav = MoreNav.Menu })
    }
}

@Composable
private fun MoreMenuView(onTables: () -> Unit, onSuppliers: () -> Unit, onStaffs: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("⚙️ 更多", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
        Text("你好，${SessionManager.staffName}（${if (SessionManager.isAdmin) "老板" else "员工"}）", fontSize = 14.sp, color = DiningColors.TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))

        // 菜单管理（老板维护菜品价格上下架）
        MenuEntry("📋", "菜品管理", "维护菜品、价格、上下架") { /* nav to menu */ }
        MenuEntry("🪑", "桌台管理", "新增/编辑桌台、修改状态") { onTables() }
        MenuEntry("📦", "供应商管理", "批发商档案、BRN、TIN") { onSuppliers() }
        MenuEntry("👷", "员工管理", "新增/停用员工账号") { onStaffs() }

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
private fun MenuEntry(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                Text(desc, fontSize = 12.sp, color = DiningColors.TextMuted)
            }
            Text("›", fontSize = 20.sp, color = DiningColors.TextMuted)
        }
    }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = DiningColors.Primary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("🪑 桌台管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Button(onClick = { showAdd = true }, shape = RoundedCornerShape(10.dp),
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = DiningColors.Primary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("📦 供应商管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Button(onClick = { showAdd = true }, shape = RoundedCornerShape(10.dp),
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
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(s.supplier_name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
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
}

@Composable
private fun SupplierAddDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var brn by remember { mutableStateOf("") }
    var tin by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("新增供应商", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
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
                    val r = SupabaseClient.insertSupplier(Supplier(
                        supplier_name = name.trim(), contact_person = contact.trim().ifBlank { null },
                        phone = phone.trim().ifBlank { null }, supplier_brn = brn.trim().ifBlank { null },
                        supplier_tin = tin.trim().ifBlank { null }, notes = notes.trim().ifBlank { null }
                    ))
                    saving = false
                    if (r != null) onDone()
                }
            }) { Text("保存", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) } }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = DiningColors.Primary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("👷 员工管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
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
                                        SupabaseClient.updateStaff(s.id, s.copy(is_active = !s.is_active))
                                        load()
                                    }
                                },
                                label = { Text(if (s.is_active) "在职" else "已停用", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}
