package com.routy.app.logic.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Minutes from midnight (0–1439) in the given IANA zone at the given instant. */
fun localMinutesInZone(instantMs: Long, timezone: String): Int {
    val zone = runCatching { ZoneId.of(timezone) }.getOrNull() ?: return localMinutesDevice(instantMs)
    val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(instantMs), zone)
    return zdt.hour * 60 + zdt.minute
}

/** Fallback to the device clock when no valid zone is supplied (legacy behavior). */
fun localMinutesDevice(instantMs: Long): Int {
    val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(instantMs), ZoneId.systemDefault())
    return zdt.hour * 60 + zdt.minute
}
