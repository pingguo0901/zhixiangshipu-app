package stellarelite.zxsp.platform

/**
 * 拍卡收款（Tap to Pay）结果
 */
data class TapToPayResult(
    val success: Boolean,
    val message: String,
    val paymentIntentId: String? = null,
)

/**
 * 拍卡收款控制器（跨平台 expect/actual）
 * 仅 Android 支持（Stripe Terminal SDK），桌面/iOS 返回不支持。
 */
expect object TapToPayController {
    /** 当前平台是否支持拍卡收款 */
    val isSupported: Boolean

    /**
     * 发起拍卡收款，金额单位：分。
     * 内部流程：初始化 Terminal → 连接本地读卡器 → 创建 PaymentIntent → 收集+确认。
     * 挂起直到收款成功 / 失败 / 取消。
     * [onStatus] 用于回传过程状态（如"请拍卡"），可空实现。
     */
    suspend fun collectPayment(amountCents: Long, onStatus: (String) -> Unit = {}): TapToPayResult
}
