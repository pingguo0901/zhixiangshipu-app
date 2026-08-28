package stellarelite.zxsp.platform

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager

actual fun printReceiptText(text: String) {
    val context = AppContext.context
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = "炙巷食铺-收据"

    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            val pdf = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(384, 900, 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas
                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                }
                var y = 16f
                for (line in text.split("\n")) {
                    canvas.drawText(line, 8f, y, paint)
                    y += 13f
                }
                pdf.finishPage(page)
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { os ->
                    pdf.writeTo(os)
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                pdf.close()
            }
        }
    }

    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
}
