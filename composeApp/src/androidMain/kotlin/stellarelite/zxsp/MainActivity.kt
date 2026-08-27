package stellarelite.zxsp

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
