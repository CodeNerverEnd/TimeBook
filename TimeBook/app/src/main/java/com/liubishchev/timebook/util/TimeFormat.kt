package com.liubishchev.timebook.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 所有时间格式化的唯一出口，避免各处口径不一致。 */
object TimeFormat {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 计时器表盘：01:25:03 */
    fun clock(millis: Long): String {
        val total = (millis / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    /** 人类可读时长：1小时25分 / 25分 / 40秒 */
    fun human(millis: Long): String {
        val total = millis.coerceAtLeast(0L)
        if (total < 1000L) return "0分"
        val minutes = total / 60_000L
        if (minutes < 1L) return "${total / 1000L}秒"
        val h = minutes / 60L
        val m = minutes % 60L
        return when {
            h > 0L && m > 0L -> "${h}小时${m}分"
            h > 0L -> "${h}小时"
            else -> "${m}分"
        }
    }

    /** 08:05 */
    fun hm(epochMillis: Long): String =
        hourMinute.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** 12:30（分:秒），用于「还剩 / 已超出」倒计时 */
    fun countdown(millis: Long): String = clock(millis).substring(3)

    /** 2026年11月25日 周三 */
    fun dateLabel(date: LocalDate): String {
        val dow = when (date.dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
        return "${date.year}年${date.monthValue}月${date.dayOfMonth}日 $dow"
    }

    /** 11月25日 */
    fun shortDate(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun toLocalTime(epochMillis: Long): LocalTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()

    fun atStartOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun withTime(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}
