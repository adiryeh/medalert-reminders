package com.medalert.util

import com.medalert.data.db.entity.Schedule
import com.medalert.data.db.entity.ScheduleType
import org.json.JSONArray
import java.util.Calendar

fun Schedule.nextOccurrences(count: Int): List<Long> {
    val results = mutableListOf<Long>()
    val times = parseTimes(timesOfDay)
    if (times.isEmpty()) return results

    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()

    when (type) {
        ScheduleType.DAILY, ScheduleType.MULTIPLE_TIMES_DAILY -> {
            var dayOffset = 0
            while (results.size < count) {
                for (time in times) {
                    cal.timeInMillis = now
                    cal.add(Calendar.DAY_OF_YEAR, dayOffset)
                    cal.set(Calendar.HOUR_OF_DAY, time.first)
                    cal.set(Calendar.MINUTE, time.second)
                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis > now) results.add(cal.timeInMillis)
                    if (results.size >= count) break
                }
                dayOffset++
                if (dayOffset > 400) break
            }
        }
        ScheduleType.SPECIFIC_WEEKDAYS -> {
            val days = parseDays(daysOfWeek)
            var dayOffset = 0
            while (results.size < count) {
                cal.timeInMillis = now
                cal.add(Calendar.DAY_OF_YEAR, dayOffset)
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                if (dow in days) {
                    for (time in times) {
                        cal.set(Calendar.HOUR_OF_DAY, time.first)
                        cal.set(Calendar.MINUTE, time.second)
                        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        if (cal.timeInMillis > now) results.add(cal.timeInMillis)
                        if (results.size >= count) break
                    }
                }
                dayOffset++
                if (dayOffset > 400) break
            }
        }
        ScheduleType.EVERY_X_DAYS -> {
            var base = startDate
            while (results.size < count) {
                for (time in times) {
                    cal.timeInMillis = base
                    cal.set(Calendar.HOUR_OF_DAY, time.first)
                    cal.set(Calendar.MINUTE, time.second)
                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis > now) results.add(cal.timeInMillis)
                    if (results.size >= count) break
                }
                base += intervalDays * 86_400_000L
                if (base > now + 365 * 86_400_000L) break
            }
        }
        ScheduleType.SINGLE_DATE -> {
            for (time in times) {
                cal.timeInMillis = startDate
                cal.set(Calendar.HOUR_OF_DAY, time.first)
                cal.set(Calendar.MINUTE, time.second)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis > now) results.add(cal.timeInMillis)
            }
        }
        ScheduleType.COMPLEX_CYCLE -> {
            if (cycleDaysOn <= 0) return results
            val cycleLen = cycleDaysOn + cycleDaysOff
            var dayOffset = 0
            while (results.size < count) {
                cal.timeInMillis = startDate
                cal.add(Calendar.DAY_OF_YEAR, dayOffset)
                val dayInCycle = dayOffset % cycleLen
                if (dayInCycle < cycleDaysOn) {
                    for (time in times) {
                        cal.set(Calendar.HOUR_OF_DAY, time.first)
                        cal.set(Calendar.MINUTE, time.second)
                        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        if (cal.timeInMillis > now) results.add(cal.timeInMillis)
                        if (results.size >= count) break
                    }
                }
                dayOffset++
                if (dayOffset > 400) break
            }
        }
    }
    return results.sorted().take(count)
}

/**
 * Returns all scheduled occurrences within [from, to] (inclusive), including past times.
 * Never returns occurrences before schedule.startDate — prevents phantom "overdue" entries
 * for medications that didn't exist yet on those dates.
 */
fun occurrencesInRange(schedule: Schedule, from: Long, to: Long): List<Long> {
    val results = mutableListOf<Long>()
    val times = parseTimes(schedule.timesOfDay)
    if (times.isEmpty()) return results
    val cal = Calendar.getInstance()
    val effectiveFrom = maxOf(from, schedule.startDate)  // never before medication was created

    when (schedule.type) {
        ScheduleType.DAILY, ScheduleType.MULTIPLE_TIMES_DAILY -> {
            var cur = Calendar.getInstance().apply { timeInMillis = effectiveFrom }
            cur.set(Calendar.HOUR_OF_DAY, 0); cur.set(Calendar.MINUTE, 0); cur.set(Calendar.SECOND, 0)
            while (cur.timeInMillis <= to) {
                for (time in times) {
                    cal.timeInMillis = cur.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, time.first)
                    cal.set(Calendar.MINUTE, time.second)
                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis in effectiveFrom..to) results.add(cal.timeInMillis)
                }
                cur.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        ScheduleType.SPECIFIC_WEEKDAYS -> {
            val days = parseDays(schedule.daysOfWeek)
            var cur = Calendar.getInstance().apply { timeInMillis = effectiveFrom }
            cur.set(Calendar.HOUR_OF_DAY, 0); cur.set(Calendar.MINUTE, 0); cur.set(Calendar.SECOND, 0)
            while (cur.timeInMillis <= to) {
                if (cur.get(Calendar.DAY_OF_WEEK) in days) {
                    for (time in times) {
                        cal.timeInMillis = cur.timeInMillis
                        cal.set(Calendar.HOUR_OF_DAY, time.first)
                        cal.set(Calendar.MINUTE, time.second)
                        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        if (cal.timeInMillis in effectiveFrom..to) results.add(cal.timeInMillis)
                    }
                }
                cur.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        ScheduleType.EVERY_X_DAYS -> {
            var base = schedule.startDate
            while (base <= to) {
                for (time in times) {
                    cal.timeInMillis = base
                    cal.set(Calendar.HOUR_OF_DAY, time.first)
                    cal.set(Calendar.MINUTE, time.second)
                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis in effectiveFrom..to) results.add(cal.timeInMillis)
                }
                base += schedule.intervalDays * 86_400_000L
            }
        }
        ScheduleType.SINGLE_DATE -> {
            for (time in times) {
                cal.timeInMillis = schedule.startDate
                cal.set(Calendar.HOUR_OF_DAY, time.first)
                cal.set(Calendar.MINUTE, time.second)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis in effectiveFrom..to) results.add(cal.timeInMillis)
            }
        }
        ScheduleType.COMPLEX_CYCLE -> {
            if (schedule.cycleDaysOn <= 0) return results
            val cycleLen = schedule.cycleDaysOn + schedule.cycleDaysOff
            var dayOffset = 0
            var base = schedule.startDate
            while (base <= to) {
                val dayInCycle = dayOffset % cycleLen
                if (dayInCycle < schedule.cycleDaysOn) {
                    for (time in times) {
                        cal.timeInMillis = base
                        cal.set(Calendar.HOUR_OF_DAY, time.first)
                        cal.set(Calendar.MINUTE, time.second)
                        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        if (cal.timeInMillis in effectiveFrom..to) results.add(cal.timeInMillis)
                    }
                }
                base += 86_400_000L
                dayOffset++
            }
        }
    }
    return results.sorted()
}

private fun parseTimes(json: String): List<Pair<Int, Int>> {
    if (json.isEmpty()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val parts = arr.getString(it).split(":")
            Pair(parts[0].toInt(), parts[1].toInt())
        }
    } catch (e: Exception) { emptyList() }
}

private fun parseDays(json: String): Set<Int> {
    if (json.isEmpty()) return emptySet()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getInt(it) }.toSet()
    } catch (e: Exception) { emptySet() }
}

fun Long.toDisplayTime(): String {
    val cal = Calendar.getInstance().apply { timeInMillis = this@toDisplayTime }
    return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

fun Long.toDisplayDate(): String {
    val cal = Calendar.getInstance().apply { timeInMillis = this@toDisplayDate }
    return String.format("%02d/%02d/%04d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
}

fun Long.toDayOfWeekHebrew(): String {
    val cal = Calendar.getInstance().apply { timeInMillis = this@toDayOfWeekHebrew }
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    return when {
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "היום"
        cal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) -> "מחר"
        else -> {
            val dayName = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "ראשון"
                Calendar.MONDAY -> "שני"
                Calendar.TUESDAY -> "שלישי"
                Calendar.WEDNESDAY -> "רביעי"
                Calendar.THURSDAY -> "חמישי"
                Calendar.FRIDAY -> "שישי"
                Calendar.SATURDAY -> "שבת"
                else -> ""
            }
            "יום $dayName ${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}"
        }
    }
}
