package stellarelite.zxsp

import android.app.Application
import com.stripe.stripeterminal.TerminalApplicationDelegate

/**
 * 炙巷食铺 Application：初始化 Stripe Terminal（Tap to Pay on Android）
 */
class ZxspApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TerminalApplicationDelegate.onCreate(this)
    }
}
