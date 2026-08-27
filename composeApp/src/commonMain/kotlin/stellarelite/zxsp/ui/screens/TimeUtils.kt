package stellarelite.zxsp.ui.screens

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// 当前时间 ISO 字符串（Asia/Kuala_Lumpur）
fun currentIso(): String {
    val tz = TimeZone.of("Asia/Kuala_Lumpur")
    val now = Clock.System.now().toLocalDateTime(tz)
    return "%04d-%02d-%02dT%02d:%02d:%02d".format(
        now.year, now.monthNumber, now.dayOfMonth,
        now.hour, now.minute, now.second
    )
}

// 今天日期 YYYY-MM-DD
fun todayDate(): String {
    val tz = TimeZone.of("Asia/Kuala_Lumpur")
    val now = Clock.System.now().toLocalDateTime(tz)
    return "%04d-%02d-%02d".format(now.year, now.monthNumber, now.dayOfMonth)
}

// 时间戳转 YYYY-MM-DD
fun millisToDate(millis: Long): String {
    val tz = TimeZone.of("Asia/Kuala_Lumpur")
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    return "%04d-%02d-%02d".format(dt.year, dt.monthNumber, dt.dayOfMonth)
}
