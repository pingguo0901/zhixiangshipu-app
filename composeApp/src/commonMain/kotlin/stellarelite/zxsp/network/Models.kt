package stellarelite.zxsp.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

// ============ Auth ============
@Serializable
data class AuthUser(
    val id: String = "",
    val email: String = ""
)

@Serializable
data class AuthSession(
    val access_token: String = "",
    val refresh_token: String = "",
    val user: AuthUser = AuthUser()
)

// ============ staff ============
@Serializable
data class Staff(
    val id: Long = 0,
    val auth_uid: String = "",
    val staff_name: String = "",
    val role: String = "",
    val phone: String? = null,
    val is_active: Boolean = true,
    val created_at: String? = null
)

// ============ table_list ============
@Serializable
data class TableList(
    val id: Long = 0,
    val table_no: String = "",
    val table_status: String = "free",
    val notes: String? = null,
    val created_at: String? = null
)

// ============ menu_items ============
@Serializable
data class MenuItem(
    val id: Long = 0,
    val item_name: String = "",
    val name_en: String? = null,
    val category: String = "",
    val unit: String = "",
    val sell_price_myr: Double = 0.0,
    val is_active: Boolean = true,
    val notes: String? = null,
    val created_at: String? = null
)

// ============ customer_orders ============
@Serializable
data class CustomerOrder(
    val id: Long = 0,
    val order_no: String = "",
    val table_id: Long? = null,
    val customer_name: String? = null,
    val customer_phone: String? = null,
    val order_items: JsonElement = JsonArray(emptyList()),
    val total_amount_myr: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val payment_status: String = "unpaid",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val receipt_no: String = "",
    val notes: String? = null,
    val created_by_staff_id: Long = 0,
    val order_datetime: String? = null,
    val created_at: String? = null
)

// ============ payment_records ============
@Serializable
data class PaymentRecord(
    val id: Long = 0,
    val order_id: Long = 0,
    val pay_amount_myr: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val pay_currency: String = "MYR",
    val exchange_rate: Double? = null,
    val pay_method: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val transaction_ref: String = "",
    val receipt_attachment_url: String? = null,
    val received_by_staff_id: Long = 0,
    val transaction_datetime: String? = null,
    val notes: String? = null,
    val created_at: String? = null
)

// ============ receipt_master ============
@Serializable
data class ReceiptMaster(
    val receipt_id: Long = 0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val receipt_no: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val ssm_brand: String = "ZHI XIANG FOOD ENTERPRISE",
    val ssm_brn: String? = null,
    val trans_datetime: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val sub_total: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val discount: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val total_amount: Double = 0.0,
    val payment_mode: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val amount_received: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val change_given: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val operator: String = "",
    val create_time: String? = null,
    val remark: String? = null
)

// ============ receipt_item ============
@Serializable
data class ReceiptItem(
    val item_id: Long = 0,
    val receipt_no: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val item_name: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val qty: Int = 1,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val unit_price: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val item_amount: Double = 0.0
)

// ============ supplier ============
@Serializable
data class Supplier(
    val id: Long = 0,
    val supplier_name: String = "",
    val contact_person: String? = null,
    val phone: String? = null,
    val supplier_brn: String? = null,
    val supplier_tin: String? = null,
    val notes: String? = null,
    val created_at: String? = null
)

// ============ stock_in_log ============
@Serializable
data class StockInLog(
    val id: Long = 0,
    val stock_in_no: String = "",
    val supplier_id: Long = 0,
    val in_items: JsonElement = JsonArray(emptyList()),
    val total_cost_myr: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val cost_currency: String = "MYR",
    val exchange_rate: Double? = null,
    val pay_method: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val transaction_ref: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val payment_status: String = "unpaid",
    val supplier_invoice_no: String? = null,
    val supplier_invoice_attachment_url: String? = null,
    val operate_staff_id: Long = 0,
    val transaction_datetime: String? = null,
    val created_at: String? = null
)

// ============ warehouse_items ============
@Serializable
data class WarehouseItem(
    val id: Long = 0,
    val item_name: String = "",
    val unit: String = "",
    val stock_qty: Double = 0.0,
    val warning_qty: Double = 0.0,
    val supplier_id: Long? = null,
    val notes: String? = null,
    val created_at: String? = null
)

// ============ fridge_log ============
@Serializable
data class FridgeLog(
    val id: Long = 0,
    val warehouse_item_id: Long = 0,
    val take_qty: Double = 0.0,
    val return_qty: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val used_qty: Double = 0.0,
    val operate_staff_id: Long = 0,
    val log_time: String? = null,
    val created_at: String? = null
)

// ============ meat_process_log ============
@Serializable
data class MeatProcessLog(
    val id: Long = 0,
    val warehouse_item_id: Long = 0,
    val process_status: String = "",
    val process_qty: Double = 0.0,
    val operate_staff_id: Long = 0,
    val process_time: String? = null,
    val created_at: String? = null
)

// ============ expense_records ============
@Serializable
data class ExpenseRecord(
    val id: Long = 0,
    val expense_title: String = "",
    val expense_type: String = "",
    val amount_myr: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val expense_currency: String = "MYR",
    val exchange_rate: Double? = null,
    val pay_method: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val transaction_ref: String = "",
    val receipt_invoice_no: String? = null,
    val attachment_url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val is_personal: Boolean = false,
    val operate_staff_id: Long = 0,
    val transaction_datetime: String? = null,
    val notes: String? = null,
    val created_at: String? = null
)

// ============ audit_log ============
@Serializable
data class AuditLog(
    val id: Long = 0,
    val table_name: String = "",
    val record_id: Long = 0,
    val action: String = "",
    val old_data: String? = null,
    val new_data: String? = null,
    val operate_staff_id: Long = 0,
    val action_time: String? = null
)

// ============ daily_sales_view ============
@Serializable
data class DailySales(
    val period_date: String = "",
    val total_sales_myr: Double = 0.0,
    val total_stock_cost_myr: Double = 0.0,
    val total_expense_myr: Double = 0.0,
    val gross_profit_myr: Double = 0.0
)
