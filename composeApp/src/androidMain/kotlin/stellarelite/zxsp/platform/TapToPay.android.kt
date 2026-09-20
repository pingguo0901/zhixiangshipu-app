package stellarelite.zxsp.platform

import android.util.Log
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.callable.TapToPayReaderListener
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.ConnectionConfiguration
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import com.stripe.stripeterminal.external.models.ConfirmPaymentIntentConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.LocaleConfig
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentIntentParameters
import com.stripe.stripeterminal.external.models.PaymentMethodType
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TapUseCase
import com.stripe.stripeterminal.external.models.TerminalException
import com.stripe.stripeterminal.log.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 后端 Terminal 接口基础地址（Vercel，与线上支付同一后端）
 */
private const val TERMINAL_BASE = "https://zhixiangfoodenterprise.online.stellarelite-xingyuzhenlv.com"

/**
 * 调用后端 Terminal 接口，返回 JSON 文本
 */
private fun terminalHttp(path: String, body: String? = null): String {
    val url = URL(TERMINAL_BASE + path)
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.connectTimeout = 15000
    conn.readTimeout = 15000
    conn.doOutput = body != null
    if (body != null) {
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
    conn.disconnect()
    if (code !in 200..299) throw ConnectionTokenException("HTTP $code: $text")
    return text
}

/**
 * 从后端获取 Connection Token（Terminal 初始化用）
 */
private class TerminalTokenProvider : ConnectionTokenProvider {
    override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
        try {
            val json = terminalHttp("/api/terminal-connection-token")
            val secret = extractJsonString(json, "secret")
            if (secret == null) {
                callback.onFailure(ConnectionTokenException("No secret in response"))
            } else {
                callback.onSuccess(secret)
            }
        } catch (e: Exception) {
            callback.onFailure(ConnectionTokenException(e.message ?: "connection token failed"))
        }
    }
}

private fun extractJsonString(json: String, key: String): String? {
    val marker = "\"$key\""
    val i = json.indexOf(marker)
    if (i < 0) return null
    val colon = json.indexOf(':', i)
    if (colon < 0) return null
    val start = json.indexOf('"', colon)
    if (start < 0) return null
    val end = json.indexOf('"', start + 1)
    if (end < 0) return null
    return json.substring(start + 1, end)
}

/**
 * Terminal 事件监听（记录状态）
 */
private object TerminalEventListener : TerminalListener {
    override fun onConnectionStatusChange(status: ConnectionStatus) {
        Log.i("Terminal", "connection status: $status")
    }

    override fun onPaymentStatusChange(status: PaymentStatus) {
        Log.i("Terminal", "payment status: $status")
    }
}

/**
 * Tap to Pay 读卡器监听（5.8.1 所有方法均有默认实现，这里留空即可）
 */
private object TerminalTapToPayListener : TapToPayReaderListener

/**
 * 拍卡收款 Android 实现
 */
actual object TapToPayController {
    actual val isSupported: Boolean = true

    @Volatile
    private var initialized = false
    private var locationId: String? = null

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val locale = LocaleConfig.HardcodedLocale.Builder("en-US").build()
            Terminal.init(
                AppContext.context,
                LogLevel.ERROR,
                TerminalTokenProvider(),
                TerminalEventListener,
                null,
                locale,
            )
            initialized = true
        }
    }

    private fun ensureLocation(): String {
        locationId?.let { return it }
        val json = terminalHttp("/api/terminal-ensure-location")
        val id = extractJsonString(json, "location_id")
            ?: throw ConnectionTokenException("No location_id in response")
        locationId = id
        return id
    }

    private suspend fun discoverTapToPayReader(): Reader =
        suspendCancellableCoroutine { cont ->
            var cancelable: Cancelable? = null
            val config = DiscoveryConfiguration.TapToPayDiscoveryConfiguration(false)
            cancelable = Terminal.getInstance().discoverReaders(
                config,
                object : DiscoveryListener {
                    override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                        if (readers.isNotEmpty() && !cont.isCompleted) {
                            cancelable?.cancel(object : Callback {
                                override fun onSuccess() {}
                                override fun onFailure(e: TerminalException) {}
                            })
                            cont.resume(readers.first())
                        }
                    }
                },
                object : Callback {
                    override fun onSuccess() {
                        if (!cont.isCompleted) {
                            cont.resumeWithException(
                                TerminalException(
                                    com.stripe.stripeterminal.external.models.TerminalErrorCode.NOT_CONNECTED_TO_READER,
                                    "未找到可用的 Tap to Pay 读卡器",
                                )
                            )
                        }
                    }

                    override fun onFailure(e: TerminalException) {
                        if (!cont.isCompleted) cont.resumeWithException(e)
                    }
                },
            )
        }

    private suspend fun connectTapToPay(reader: Reader, locationId: String): Reader =
        suspendCancellableCoroutine { cont ->
            val config = ConnectionConfiguration.TapToPayConnectionConfiguration(
                TapUseCase.Pay(locationId),
                true,
                TerminalTapToPayListener,
            )
            Terminal.getInstance().connectReader(reader, config, object : ReaderCallback {
                override fun onSuccess(reader: Reader) {
                    if (cont.isActive) cont.resume(reader)
                }

                override fun onFailure(e: TerminalException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            })
        }

    private suspend fun createPaymentIntent(amountCents: Long): PaymentIntent =
        suspendCancellableCoroutine { cont ->
            val params = PaymentIntentParameters.Builder(listOf(PaymentMethodType.CARD_PRESENT))
                .setAmount(amountCents)
                .setCurrency("myr")
                .build()
            Terminal.getInstance().createPaymentIntent(params, object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    if (cont.isActive) cont.resume(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            })
        }

    private suspend fun collectPaymentMethod(paymentIntent: PaymentIntent): PaymentIntent =
        suspendCancellableCoroutine { cont ->
            val cancelable = Terminal.getInstance().collectPaymentMethod(
                paymentIntent,
                object : PaymentIntentCallback {
                    override fun onSuccess(p: PaymentIntent) {
                        if (cont.isActive) cont.resume(p)
                    }

                    override fun onFailure(e: TerminalException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                },
            )
            cont.invokeOnCancellation {
                cancelable.cancel(object : Callback {
                    override fun onSuccess() {}
                    override fun onFailure(e: TerminalException) {}
                })
            }
        }

    private suspend fun confirmPaymentIntent(paymentIntent: PaymentIntent): PaymentIntent =
        suspendCancellableCoroutine { cont ->
            val cancelable = Terminal.getInstance().confirmPaymentIntent(
                paymentIntent,
                object : PaymentIntentCallback {
                    override fun onSuccess(p: PaymentIntent) {
                        if (cont.isActive) cont.resume(p)
                    }

                    override fun onFailure(e: TerminalException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                },
                ConfirmPaymentIntentConfiguration.Builder().build(),
            )
            cont.invokeOnCancellation {
                cancelable.cancel(object : Callback {
                    override fun onSuccess() {}
                    override fun onFailure(e: TerminalException) {}
                })
            }
        }

    actual suspend fun collectPayment(amountCents: Long, onStatus: (String) -> Unit): TapToPayResult =
        withContext(Dispatchers.IO) {
            try {
                ensureInitialized()
                val locId = ensureLocation()

                onStatus("正在发现读卡器…")
                val reader = discoverTapToPayReader()

                onStatus("正在连接读卡器…")
                connectTapToPay(reader, locId)

                onStatus("正在创建收款单…")
                val created = createPaymentIntent(amountCents)

                onStatus("请拍卡")
                val collected = collectPaymentMethod(created)

                onStatus("正在确认收款…")
                val confirmed = confirmPaymentIntent(collected)

                TapToPayResult(true, "收款成功", confirmed.id)
            } catch (e: TerminalException) {
                TapToPayResult(false, e.errorMessage ?: "收款失败", null)
            } catch (e: Exception) {
                TapToPayResult(false, e.message ?: "收款失败", null)
            }
        }
}
