package stellarelite.zxsp.platform

import android.content.Context
import android.content.SharedPreferences

object AppContext {
    lateinit var context: Context
    fun init(ctx: Context) { context = ctx.applicationContext }
}

actual object SessionStorage {
    private val prefs: SharedPreferences
        get() = AppContext.context.getSharedPreferences("zxsp_session", Context.MODE_PRIVATE)

    actual fun get(key: String): String? = prefs.getString(key, null)
    actual fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    actual fun remove(key: String) { prefs.edit().remove(key).apply() }
}
