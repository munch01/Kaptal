package com.Muncho.kaptal.utils

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
}
