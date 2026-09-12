package stellarelite.zxsp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.data.t
import stellarelite.zxsp.ui.theme.DiningColors

// 桌面版全局顶栏：左侧员工名称（职位），右侧日期 + 时间（AM/PM，吉隆坡时区）
@Composable
fun DesktopTopBar() {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Clock.System.now()
            delay(30_000)
        }
    }

    val tz = TimeZone.of("Asia/Kuala_Lumpur")
    val dt = now.toLocalDateTime(tz)
    val hour = dt.hour
    val amPm = if (hour < 12) "AM" else "PM"
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val timeStr = "%02d:%02d %s".format(hour12, dt.minute, amPm)
    val dateStr = "%02d/%02d/%04d".format(dt.dayOfMonth, dt.monthNumber, dt.year)
    val role = if (SessionManager.isAdmin) t("老板", "Owner") else t("员工", "Staff")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DiningColors.NavBar)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${SessionManager.staffName}（$role）",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DiningColors.TextPrimary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "$dateStr   $timeStr",
            fontSize = 14.sp,
            color = DiningColors.TextSecondary
        )
    }
}
