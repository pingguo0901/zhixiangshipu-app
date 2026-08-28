package stellarelite.zxsp.platform

// 调用系统打印收据（Android 用 PrintManager，iOS 暂未实现）
expect fun printReceiptText(text: String)
