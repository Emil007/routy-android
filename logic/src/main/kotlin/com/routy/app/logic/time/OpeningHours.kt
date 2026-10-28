package com.routy.app.logic.time

/**
 * M37 (F45): ONE opening-hours rule across both clients — the web rule from
 * src/lib/nodeOpeningHours.ts:
 *  - both bounds null  → always open
 *  - exactly one bound → closed
 *  - from == until     → always open
 *  - window [from, until) exclusive at the end; overnight: now >= from || now < until
 * Android used an inclusive/`||` variant before which disagreed at the closing
 * minute and treated one-bound nodes as open.
 */
fun isNodeOpenByRule(
    openFromMinutes: Int?,
    openUntilMinutes: Int?,
    nowMinutes: Int,
): Boolean {
    if (openFromMinutes == null && openUntilMinutes == null) return true
    if (openFromMinutes == null || openUntilMinutes == null) return false
    val from = openFromMinutes
    val until = openUntilMinutes
    if (from == until) return true
    return if (from < until) {
        nowMinutes >= from && nowMinutes < until
    } else {
        nowMinutes >= from || nowMinutes < until
    }
}
