package com.juliensalinas.scrollwatcher.ui

fun formatMillisAsMinutesSeconds(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

fun formatMillisAsPretty(millis: Long): String {
    val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
    val seconds = ((millis % 60_000L) / 1000L)
    return if (totalMinutes >= 60) {
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        "${hours}h ${mins}m"
    } else {
        "${totalMinutes}m ${seconds}s"
    }
}
