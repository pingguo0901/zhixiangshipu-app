package stellarelite.zxsp.platform

/**
 * iOS 暂不支持拍卡收款
 */
actual object TapToPayController {
    actual val isSupported: Boolean = false

    actual suspend fun collectPayment(amountCents: Long, onStatus: (String) -> Unit): TapToPayResult =
        TapToPayResult(false, "iOS 暂不支持拍卡收款", null)
}
