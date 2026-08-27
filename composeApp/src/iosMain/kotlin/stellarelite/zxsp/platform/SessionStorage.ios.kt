package stellarelite.zxsp.platform

import platform.Foundation.NSUserDefaults

actual object SessionStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun get(key: String): String? = defaults.stringForKey(key)
    actual fun put(key: String, value: String) { defaults.setObject(value, forKey = key) }
    actual fun remove(key: String) { defaults.removeObjectForKey(key) }
}
