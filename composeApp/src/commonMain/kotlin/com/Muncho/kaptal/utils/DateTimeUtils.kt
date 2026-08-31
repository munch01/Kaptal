package com.muncho.kaptal.utils

import kotlinx.datetime.*

object DateTimeUtils {
    fun now(): Instant = Clock.System.now()
    
    fun getYear(instant: Instant): Int {
        return toSafeInstant(instant).toLocalDateTime(TimeZone.UTC).year
    }
    
    fun getMonth(instant: Instant): Int {
        return toSafeInstant(instant).toLocalDateTime(TimeZone.UTC).monthNumber - 1 // 0-indexed like Calendar
    }
    
    fun getDayOfMonth(instant: Instant): Int {
        return toSafeInstant(instant).toLocalDateTime(TimeZone.UTC).dayOfMonth
    }
    
    fun addMonths(instant: Instant, months: Int): Instant {
        val tz = TimeZone.UTC
        val dateTime = instant.toLocalDateTime(tz)
        val period = DatePeriod(months = months)
        val newDate = dateTime.date.plus(period)
        return LocalDateTime(newDate.year, newDate.monthNumber, newDate.dayOfMonth, dateTime.hour, dateTime.minute, dateTime.second).toInstant(tz)
    }
    
    fun toSafeInstant(instant: Instant): Instant {
        val tz = TimeZone.UTC
        // Add 6 hours to push late-night UTC shifts (10 PM, 11 PM) into the intended day
        val shifted = instant.plus(6, DateTimeUnit.HOUR)
        val dt = shifted.toLocalDateTime(tz)
        return LocalDateTime(dt.year, dt.monthNumber, dt.dayOfMonth, 12, 0, 0).toInstant(tz)
    }

    fun addDays(instant: Instant, days: Int): Instant {
        return instant.plus(days, DateTimeUnit.DAY, TimeZone.UTC)
    }
    
    fun startOfYear(year: Int): Instant {
        return LocalDateTime(year, 1, 1, 12, 0, 0).toInstant(TimeZone.UTC)
    }
    
    fun endOfMonth(year: Int, month: Int): Instant {
        val firstDay = LocalDateTime(year, month + 1, 1, 12, 0, 0)
        val nextMonth = firstDay.toInstant(TimeZone.UTC).plus(1, DateTimeUnit.MONTH, TimeZone.UTC)
        return nextMonth.minus(1, DateTimeUnit.SECOND, TimeZone.UTC)
    }
    
    fun isBefore(instant1: Instant, instant2: Instant): Boolean {
        return instant1 < instant2
    }
    
    fun toEpochMilliseconds(instant: Instant): Long {
        return instant.toEpochMilliseconds()
    }

    fun getDaysInMonth(year: Int, month: Int): Int {
        val nextMonth = if (month == 11) 1 else month + 2
        val nextYear = if (month == 11) year + 1 else year
        val firstOfNextMonth = LocalDateTime(nextYear, nextMonth, 1, 12, 0, 0).toInstant(TimeZone.UTC)
        val lastDay = firstOfNextMonth.minus(1, DateTimeUnit.DAY, TimeZone.UTC)
        return lastDay.toLocalDateTime(TimeZone.UTC).dayOfMonth
    }

    fun formatDate(instant: Instant, pattern: String): String {
        val dt = instant.toLocalDateTime(TimeZone.UTC)
        return when (pattern) {
            "dd" -> dt.dayOfMonth.toString().padStart(2, '0')
            "HH:mm" -> "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
            "dd/MM/yyyy" -> "${dt.dayOfMonth.toString().padStart(2, '0')}/${dt.monthNumber.toString().padStart(2, '0')}/${dt.year}"
            "MMM yyyy" -> "${getMonthName(dt.monthNumber, short = true)} ${dt.year}"
            "MMMM yyyy" -> "${getMonthName(dt.monthNumber, short = false)} ${dt.year}"
            else -> dt.toString()
        }
    }

    fun startOfMonth(instant: Instant): Instant {
        val dt = instant.toLocalDateTime(TimeZone.UTC)
        return LocalDateTime(dt.year, dt.monthNumber, 1, 12, 0, 0).toInstant(TimeZone.UTC)
    }

    private fun getMonthName(month: Int, short: Boolean = true): String {
        return if (short) {
            when (month) {
                1 -> "Janv."
                2 -> "Févr."
                3 -> "Mars"
                4 -> "Avr."
                5 -> "Mai"
                6 -> "Juin"
                7 -> "Juil."
                8 -> "Août"
                9 -> "Sept."
                10 -> "Oct."
                11 -> "Nov."
                12 -> "Déc."
                else -> ""
            }
        } else {
            when (month) {
                1 -> "Janvier"
                2 -> "Février"
                3 -> "Mars"
                4 -> "Avril"
                5 -> "Mai"
                6 -> "Juin"
                7 -> "Juillet"
                8 -> "Août"
                9 -> "Septembre"
                10 -> "Octobre"
                11 -> "Novembre"
                12 -> "Décembre"
                else -> ""
            }
        }
    }
}
