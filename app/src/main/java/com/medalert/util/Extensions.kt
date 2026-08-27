package com.medalert.util

import com.medalert.data.db.entity.Schedule
import com.medalert.data.db.entity.ScheduleType
import org.json.JSONArray
import java.util.Calendar

/** endDate == 0 means "no end". */
private fun withinEndDate(endDate: Long, t: Long) = endDate == 0L || t <= endDate

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
                    if (cal.timeInMillis > now && withinEndDate(endDate, cal.timeInMillis)) results.add(cal.timeInMillis)
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
                        if (cal.timeInMillis > now && withinEndDate(endDate, cal.timeInMillis)) results.add(cal.timeInMillis)
                        if (results.size >= count) break
                    }
                }
                dayOffset++
                if (dayOffset > 400) break
            }
        }
        ScheduleType.EVERY_X_DAYS -> {
            // Calendar.add(DAY_OF_YEAR, ...) — not raw millisecond arithmetic — so a DST
            // transition doesn't shift the dose onto the wrong calendar day.
            val safeInterval = if (intervalDays > 0) intervalDays else 1
            var dayOffset = 0
            while (results.size < count) {
                cal.timeInMillis = startDate
                cal.add(Calendar.DAY_OF_YEAR, dayOffset)
                for (time in times) {
                    cal.set(Calendar.HOUR_OF_DAY, time.first)
                    cal.set(Calendar.MINUTE, time.second)
                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis > now && withinEndDate(endDate, cal.timeInMillis)) results.add(cal.timeInMillis)
                    if (results.size >= count) break
                }
                dayOffset += safeInterval
                if (dayOffset > 400) break
            }
        }
        ScheduleType.SINGLE_DATE -> {
            for (time in times) {
                cal.timeInMillis = startDate
                cal.set(Calendar.HOUR_OF_DAY, time.first)
                cal.set(Calendar.MINUTE, time.second)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis > now && withinEndDate(endDate, cal.timeInMillis)) results.add(cal.timeInMillis)
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
                        if (cal.timeInMillis > now && withinEndDate(endDate, cal.timeInMillis)) results.add(cal.timeInMillis)
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
 * for medications that didn't exist yet on those dates. Never returns occurrences after
 * schedule.endDate (0 = no end).
 */
fun occurrencesInRange(schedule: Schedule, from: Long, to: Long): List<Long> {
    val results = mutableListOf<Long>()
    val times = parseTimes(schedule.timesOfDay)
    if (times.isEmpty()) return results
    val cal = Calendar.getInstance()
    val effectiveFrom = maxOf(from, schedule.startDate)  // never before medication was created
    val effectiveTo = if (schedule.endDate != 0L) minOf(to, schedule.endDate) else to
    if (effectiveTo < effectiveFrom) return results

    when (schedule.type) {
        ScheduleType.DAILY, ScheduleType.MULTIPLE_TIMES_DAILY -> {
            var cur = Calendar.getInstance().apply { timeInMillis = effectiveFrom }
            cur.set(Calendar.HOUR_OF_DAY, 0); cur.set(Calendar.MINUTE, 0); cur.set(Calendar.SECOND, 0)
            while (cur.timeInMillis <= effectiveTo) {
                for (time in times) {
                    cal.timeInMillis = cur.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, time.first)
                    cal.set(Calendar.MINUTE, time.second)
                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis in effectiveFrom..effectiveTo) results.add(cal.timeInMillis)
                }
                cur.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        ScheduleType.SPECIFIC_WEEKDAYS -> {
            val days = parseDays(schedule.daysOfWeek)
            var cur = Calendar.getInstance().apply { timeInMillis = effectiveFrom }
            cur.set(Calendar.HOUR_OF_DAY, 0); cur.set(Calendar.MINUTE, 0); cur.set(Calendar.SECOND, 0)
            while (cur.timeInMillis <= effectiveTo) {
                if (cur.get(Calendar.DAY_OF_WEEK) in days) {
                    for (time in times) {
                        cal.timeInMillis = cur.timeInMillis
                        cal.set(Calendar.HOUR_OF_DAY, time.first)
                        cal.set(Calendar.MINUTE, time.second)
                        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        if (cal.timeInMillis in effectiveFrom..effectiveTo) results.add(cal.timeInMillis)
                    }
                }
                cur.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        ScheduleType.EVERY_X_DAYS -> {
            // Calendar.add(DAY_OF_YEAR, ...) — matches nextOccurrences — so this function can't
            // disagree with it across a DST boundary the way raw millisecond stepping would.
            val safeInterval = if (schedule.intervalDays > 0) schedule.intervalDays else 1
            val cursor = Calendar.getInstance().apply { timeInMillis = schedule.startDate }
            while (cursor.timeInMillis <= effectiveTo) {
                for (time in times) {
                    cal.timeInMillis = cursor.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, time.first)
                    cal.set(Calendar.MINUTE, time.second)
                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis in effectiveFrom..effectiveTo) results.add(cal.timeInMillis)
                }
                cursor.add(Calendar.DAY_OF_YEAR, safeInterval)
            }
        }
        ScheduleType.SINGLE_DATE -> {
            for (time in times) {
                cal.timeInMillis = schedule.startDate
                cal.set(Calendar.HOUR_OF_DAY, time.first)
                cal.set(Calendar.MINUTE, time.second)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis in effectiveFrom..effectiveTo) results.add(cal.timeInMillis)
            }
        }
        ScheduleType.COMPLEX_CYCLE -> {
            if (schedule.cycleDaysOn <= 0) return results
            val cycleLen = schedule.cycleDaysOn + schedule.cycleDaysOff
            var dayOffset = 0
            val cursor = Calendar.getInstance().apply { timeInMillis = schedule.startDate }
            while (cursor.timeInMillis <= effectiveTo) {
                val dayInCycle = dayOffset % cycleLen
                if (dayInCycle < schedule.cycleDaysOn) {
                    for (time in times) {
                        cal.timeInMillis = cursor.timeInMillis
                        cal.set(Calendar.HOUR_OF_DAY, time.first)
                        cal.set(Calendar.MINUTE, time.second)
                        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        if (cal.timeInMillis in effectiveFrom..effectiveTo) results.add(cal.timeInMillis)
                    }
                }
                cursor.add(Calendar.DAY_OF_YEAR, 1)
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
    } catch (e: Exception) {
        android.util.Log.e("ScheduleParse", "Malformed timesOfDay JSON: $json", e)
        emptyList()
    }
}

private fun parseDays(json: String): Set<Int> {
    if (json.isEmpty()) return emptySet()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getInt(it) }.toSet()
    } catch (e: Exception) {
        android.util.Log.e("ScheduleParse", "Malformed daysOfWeek JSON: $json", e)
        emptySet()
    }
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
