package com.mds.player.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val RU = Locale.forLanguageTag("ru")

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

fun formatUpdatedAt(timestamp: Long): String {
    if (timestamp <= 0L) return "каталог ещё не загружен"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val time = SimpleDateFormat("HH:mm", RU).format(Date(timestamp))
    return if (sameDay) {
        "обновлено сегодня в $time"
    } else {
        "обновлено " + SimpleDateFormat("dd.MM.yyyy", RU).format(Date(timestamp))
    }
}

fun formatCount(value: Int): String =
    NumberFormat.getIntegerInstance(RU).format(value.toLong())

fun recordsLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "записей"
        mod10 == 1 -> "запись"
        mod10 in 2..4 -> "записи"
        else -> "записей"
    }
}

fun displayDate(date: String): String =
    if (date.isBlank() || date == "-") "дата неизвестна" else date

fun displayStation(station: String): String =
    station.ifBlank { "радиостанция не указана" }
