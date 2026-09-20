package stellarelite.zxsp.platform

/**
 * 桌面端不支持拍卡收款
 */
actual object TapToPayController {
    actual val isSupported: Boolean = false

    actual suspend fun collectPayment(amountCents: Long, onStatus: (String) -> Unit): TapToPayResult =
        TapToPayResult(false, "桌面端不支持拍卡收款", null)
}
