package com.cydoniancitizen.bingee.domain.model

data class ViewingDurationLabels(val day: String, val hour: String, val minute: String)

fun formatViewingDuration(totalMinutes: Long, labels: ViewingDurationLabels): String {
    require(totalMinutes >= 0) { "Viewing duration must not be negative" }
    val days = totalMinutes / 1_440
    val hours = totalMinutes % 1_440 / 60
    val minutes = totalMinutes % 60
    return buildList {
        if (days > 0) add("$days${labels.day}")
        if (hours > 0) add("$hours${labels.hour}")
        if (minutes > 0 || isEmpty()) add("$minutes${labels.minute}")
    }.joinToString(" ")
}
