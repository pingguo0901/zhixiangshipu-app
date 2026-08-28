package stellarelite.zxsp.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import stellarelite.zxsp.data.SessionManager

// ============ 请求体 DTO ============
@Serializable
data class LoginRequest(val email: String, val password: String)

object SupabaseClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private const val BASE = SupabaseConfig.BASE_URL

    var lastError: String? = null

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    // 登录后带 access_token；未登录带 anon key
    private fun HttpRequestBuilder.applyAuth() {
        header("apikey", SupabaseConfig.ANON_KEY)
        val token = SessionManager.accessToken
        if (token != null) {
            header("Authorization", "Bearer $token")
        } else {
            header("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
        }
    }

    // ============ Auth 登录 ============
    suspend fun login(email: String, password: String): Result<AuthSession> = runCatching {
        val resp: HttpResponse = client.post("$BASE/auth/v1/token") {
            header("apikey", SupabaseConfig.ANON_KEY)
            url { parameters.append("grant_type", "password") }
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }
        if (!resp.status.isSuccess()) {
            throw Exception("登录失败（${resp.status.value}），请检查账号密码")
        }
        resp.body<AuthSession>()
    }

    // ============ staff：查询当前登录员工 ============
    suspend fun fetchMyStaff(authUid: String): Staff? {
        val resp: HttpResponse = client.get("$BASE/rest/v1/staff") {
            applyAuth()
            url { parameters.append("select", "*") }
            url { parameters.append("auth_uid", "eq.$authUid") }
        }
        return if (resp.status.isSuccess()) {
            runCatching { resp.body<List<Staff>>().firstOrNull() }.getOrNull()
        } else null
    }

    // ============ 通用列表查询 ============
    private suspend inline fun <reified T> getList(path: String, params: Map<String, String> = emptyMap()): List<T> {
        val resp: HttpResponse = client.get("$BASE/rest/v1/$path") {
            applyAuth()
            url {
                parameters.append("select", "*")
                params.forEach { (k, v) -> parameters.append(k, v) }
            }
        }
        return if (resp.status.isSuccess()) {
            runCatching { resp.body<List<T>>() }.getOrElse { emptyList() }
        } else emptyList()
    }

    suspend fun fetchTables(): List<TableList> = getList("table_list", mapOf("order" to "table_no.asc"))
    suspend fun fetchMenuItems(): List<MenuItem> = getList("menu_items", mapOf("order" to "category.asc,id.asc"))
    suspend fun fetchSuppliers(): List<Supplier> = getList("supplier", mapOf("order" to "supplier_name.asc"))
    suspend fun fetchWarehouseItems(): List<WarehouseItem> = getList("warehouse_items", mapOf("order" to "item_name.asc"))
    suspend fun fetchOrders(): List<CustomerOrder> = getList("customer_orders", mapOf("order" to "order_datetime.desc"))
    suspend fun fetchPayments(): List<PaymentRecord> = getList("payment_records", mapOf("order" to "transaction_datetime.desc"))
    suspend fun fetchStockInLogs(): List<StockInLog> = getList("stock_in_log", mapOf("order" to "transaction_datetime.desc"))
    suspend fun fetchFridgeLogs(): List<FridgeLog> = getList("fridge_log", mapOf("order" to "log_time.desc"))
    suspend fun fetchMeatProcessLogs(): List<MeatProcessLog> = getList("meat_process_log", mapOf("order" to "process_time.desc"))
    suspend fun fetchExpenses(): List<ExpenseRecord> = getList("expense_records", mapOf("order" to "transaction_datetime.desc"))
    suspend fun fetchAuditLogs(): List<AuditLog> = getList("audit_log", mapOf("order" to "action_time.desc"))
    suspend fun fetchStaffs(): List<Staff> = getList("staff", mapOf("order" to "id.asc"))

    // 兑底获取当前员工ID（会话丢失 staffId 时自动补，避免下单被 RLS 拦截）
    suspend fun currentStaffId(): Long {
        SessionManager.staffId?.let { return it }
        return fetchStaffs().firstOrNull()?.id ?: 0
    }

    // 按桌台查当前未结账订单（用于桌台看板点击）
    suspend fun fetchActiveOrderByTable(tableId: Long): CustomerOrder? {
        val resp: HttpResponse = client.get("$BASE/rest/v1/customer_orders") {
            applyAuth()
            url {
                parameters.append("select", "*")
                parameters.append("table_id", "eq.$tableId")
                parameters.append("payment_status", "neq.paid")
                parameters.append("order", "id.desc")
                parameters.append("limit", "1")
            }
        }
        return if (resp.status.isSuccess()) {
            runCatching { resp.body<List<CustomerOrder>>().firstOrNull() }.getOrNull()
        } else null
    }

    // 加单：更新订单明细与总金额
    suspend fun updateOrderItems(orderId: Long, orderItems: JsonElement, totalAmount: Double): Boolean {
        val resp: HttpResponse = client.patch("$BASE/rest/v1/customer_orders") {
            applyAuth()
            url { parameters.append("id", "eq.$orderId") }
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("order_items", orderItems)
                put("total_amount_myr", JsonPrimitive(totalAmount))
            })
        }
        return resp.status.isSuccess()
    }

    // 设置桌台状态（结账后释放为空闲等）
    suspend fun setTableStatus(tableId: Long, status: String): Boolean {
        val resp: HttpResponse = client.patch("$BASE/rest/v1/table_list") {
            applyAuth()
            url { parameters.append("id", "eq.$tableId") }
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("table_status", JsonPrimitive(status)) })
        }
        return resp.status.isSuccess()
    }

    // ============ 通用插入 ============
    private suspend inline fun <reified T> insert(path: String, body: Any): T? {
        val resp: HttpResponse = client.post("$BASE/rest/v1/$path") {
            applyAuth()
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return if (resp.status.isSuccess()) {
            runCatching { resp.body<List<T>>().firstOrNull() }.getOrNull()
        } else {
            lastError = runCatching { resp.readRawBytes().decodeToString() }.getOrNull() ?: "HTTP ${resp.status.value}"
            null
        }
    }

    suspend fun insertOrder(order: CustomerOrder): CustomerOrder? = insert("customer_orders", order)
    suspend fun insertPayment(p: PaymentRecord): PaymentRecord? = insert("payment_records", p)
    suspend fun insertStockIn(s: StockInLog): StockInLog? = insert("stock_in_log", s)
    suspend fun insertFridgeLog(f: FridgeLog): FridgeLog? = insert("fridge_log", f)
    suspend fun insertMeatProcessLog(m: MeatProcessLog): MeatProcessLog? = insert("meat_process_log", m)
    suspend fun insertExpense(e: ExpenseRecord): ExpenseRecord? = insert("expense_records", e)
    suspend fun insertTable(t: TableList): TableList? = insert("table_list", t)
    suspend fun insertSupplier(s: Supplier): Supplier? = insert("supplier", s)
    suspend fun insertWarehouseItem(w: WarehouseItem): WarehouseItem? = insert("warehouse_items", w)
    suspend fun insertMenuItem(m: MenuItem): MenuItem? = insert("menu_items", m)
    suspend fun insertStaff(s: Staff): Staff? = insert("staff", s)

    // ============ 通用更新 ============
    private suspend fun update(path: String, id: Long, body: Any): Boolean {
        val resp: HttpResponse = client.patch("$BASE/rest/v1/$path") {
            applyAuth()
            url { parameters.append("id", "eq.$id") }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return resp.status.isSuccess()
    }

    suspend fun updateTable(id: Long, t: TableList): Boolean = update("table_list", id, t)
    suspend fun updateSupplier(id: Long, s: Supplier): Boolean = update("supplier", id, s)
    suspend fun updateWarehouseItem(id: Long, w: WarehouseItem): Boolean = update("warehouse_items", id, w)
    suspend fun updateMenuItem(id: Long, m: MenuItem): Boolean = update("menu_items", id, m)
    suspend fun updateStaff(id: Long, s: Staff): Boolean = update("staff", id, s)
    suspend fun updateStockIn(id: Long, s: StockInLog): Boolean = update("stock_in_log", id, s)
    suspend fun updateExpense(id: Long, e: ExpenseRecord): Boolean = update("expense_records", id, e)
    suspend fun updateFridgeLog(id: Long, f: FridgeLog): Boolean = update("fridge_log", id, f)

    // ============ 通用删除 ============
    private suspend fun delete(path: String, id: Long): Boolean {
        val resp: HttpResponse = client.delete("$BASE/rest/v1/$path") {
            applyAuth()
            url { parameters.append("id", "eq.$id") }
        }
        return resp.status.isSuccess()
    }

    suspend fun deleteTable(id: Long): Boolean = delete("table_list", id)
    suspend fun deleteSupplier(id: Long): Boolean = delete("supplier", id)
    suspend fun deleteWarehouseItem(id: Long): Boolean = delete("warehouse_items", id)
    suspend fun deleteMenuItem(id: Long): Boolean = delete("menu_items", id)
    suspend fun deleteStaff(id: Long): Boolean = delete("staff", id)
    suspend fun deleteExpense(id: Long): Boolean = delete("expense_records", id)

    // ============ 报表视图（仅 admin，走 RPC 函数） ============
    suspend fun fetchDailySales(start: String, end: String): List<DailySales> {
        val resp: HttpResponse = client.post("$BASE/rest/v1/rpc/get_daily_sales") {
            applyAuth()
            contentType(ContentType.Application.Json)
            setBody("""{"p_start":"$start","p_end":"$end"}""")
        }
        return if (resp.status.isSuccess()) {
            runCatching { resp.body<List<DailySales>>() }.getOrElse { emptyList() }
        } else emptyList()
    }

    // ============ Storage 上传 ============
    suspend fun uploadFile(bucket: String, path: String, bytes: ByteArray): String? {
        val resp: HttpResponse = client.post("$BASE/storage/v1/object/$bucket/$path") {
            applyAuth()
            header("x-upsert", "true")
            contentType(ContentType.Image.JPEG)
            setBody(bytes)
        }
        return if (resp.status.isSuccess()) {
            "$BASE/storage/v1/object/$bucket/$path"
        } else null
    }

    suspend fun downloadFile(url: String): ByteArray? {
        val resp: HttpResponse = client.get(url) { applyAuth() }
        return if (resp.status.isSuccess()) resp.readRawBytes() else null
    }
}
