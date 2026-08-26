package com.muncho.kaptal.utils

import kotlinx.datetime.*

object DateTimeUtils {
    fun now(): Instant = Clock.System.now()
    
    fun getYear(instant: Instant): Int {
        return instant.toLocalDateTime(TimeZone.currentSystemDefault()).year
    }
    
    fun getMonth(instant: Instant): Int {
        return instant.toLocalDateTime(TimeZone.currentSystemDefault()).monthNumber - 1 // 0-indexed like Calendar
    }
    
    fun getDayOfMonth(instant: Instant): Int {
        return instant.toLocalDateTime(TimeZone.currentSystemDefault()).dayOfMonth
    }
    
    fun addMonths(instant: Instant, months: Int): Instant {
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val newDateTime = dateTime.toInstant(TimeZone.currentSystemDefault())
            .plus(months, DateTimeUnit.MONTH, TimeZone.currentSystemDefault())
        return newDateTime
    }
    
    fun addDays(instant: Instant, days: Int): Instant {
        return instant.plus(days, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
    }
    
    fun startOfYear(year: Int): Instant {
        return LocalDateTime(year, 1, 1, 0, 0).toInstant(TimeZone.currentSystemDefault())
    }
    
    fun endOfMonth(year: Int, month: Int): Instant {
        val firstDay = LocalDateTime(year, month + 1, 1, 0, 0)
        val nextMonth = firstDay.toInstant(TimeZone.currentSystemDefault()).plus(1, DateTimeUnit.MONTH, TimeZone.currentSystemDefault())
        return nextMonth.minus(1, DateTimeUnit.SECOND, TimeZone.currentSystemDefault())
    }
    
    fun isBefore(instant1: Instant, instant2: Instant): Boolean {
        return instant1 < instant2
    }
    
    fun toEpochMilliseconds(instant: Instant): Long {
        return instant.toEpochMilliseconds()
    }

    fun formatDate(instant: Instant, pattern: String): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return when (pattern) {
            "dd/MM/yyyy" -> "${dt.dayOfMonth.toString().padStart(2, '0')}/${dt.monthNumber.toString().padStart(2, '0')}/${dt.year}"
            "MMM yyyy" -> "${getMonthName(dt.monthNumber)} ${dt.year}"
            else -> dt.toString()
        }
    }

    fun startOfMonth(instant: Instant): Instant {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return LocalDateTime(dt.year, dt.monthNumber, 1, 0, 0).toInstant(TimeZone.currentSystemDefault())
    }

    private fun getMonthName(month: Int): String {
        return when (month) {
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
    }
}
