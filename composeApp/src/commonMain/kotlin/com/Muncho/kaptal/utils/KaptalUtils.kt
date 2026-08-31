package com.muncho.kaptal.utils

import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.Instant
import kotlin.math.round

fun Double.roundTo(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}

fun Instant.toTimestamp(): Timestamp = Timestamp(this.epochSeconds, this.nanosecondsOfSecond)

fun Timestamp.toInstant(): Instant = Instant.fromEpochSeconds(this.seconds, this.nanoseconds)

fun Timestamp.toEpochMilliseconds(): Long = (seconds * 1000) + (nanoseconds / 1000000)
