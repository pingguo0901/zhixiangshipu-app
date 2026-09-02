package stellarelite.zxsp.platform

import java.util.prefs.Preferences

// 桌面端本地键值存储（Windows 注册表 Preferences）
actual object SessionStorage {
    private val prefs: Preferences = Preferences.userRoot().node("zxsp_session")

    actual fun get(key: String): String? = prefs.get(key, null)
    actual fun put(key: String, value: String) { prefs.put(key, value) }
    actual fun remove(key: String) { prefs.remove(key) }
}
