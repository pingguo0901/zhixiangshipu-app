package stellarelite.zxsp.platform

// 调用系统打印收据（Android 用蓝牙 ESC/POS，iOS 暂未实现）
expect fun printReceiptText(text: String)

// 打印桌台下单二维码贴纸（含硬件 QR 指令字节流，Android 蓝牙直接下发）
expect fun printTableQrSticker(tableNo: String, qrUrl: String)
