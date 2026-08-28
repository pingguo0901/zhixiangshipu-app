package stellarelite.zxsp

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.platform.AppContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppContext.init(applicationContext)
        SessionManager.load()
        // 蓝牙打印权限（Android 12+）
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN
                ),
                1001
            )
        }
        UpdateManager.setCurrentVersion(packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt())
        setContent {
            App(
                onCheckUpdate = { UpdateManager.checkForUpdate() },
                onRequestUpdate = { info ->
                    UpdateManager.downloadAndInstall(this, info.apkUrl)
                }
            )
        }
    }
}
