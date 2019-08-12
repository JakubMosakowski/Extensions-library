package com.mosjak.extensionslibrary

import java.util.*

fun Date.isOlderThan(minutes: Int, nowFn: () -> Date = { Date() }): Boolean {
    return nowFn().time - 1000 * 60 * minutes > time
}

fun Date.isTheSameDay(nowFn: () -> Date = { Date() }): Boolean {
    val c1 = Calendar.getInstance()
    val c2 = Calendar.getInstance()

    c1.time = this
    c2.time = nowFn()

    val isTheSameYear = c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
    val isTheSameMonth = c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
    val isTheSameDayOfMonth = c1.get(Calendar.DAY_OF_MONTH) == c2.get(Calendar.DAY_OF_MONTH)

    return isTheSameYear && isTheSameMonth && isTheSameDayOfMonth
}
