package com.routy.app.logic.update

/** Kotlin port of src/lib/updateCheck.ts's parseVersion/isNewer — same dotted-integer comparison, ignoring a leading "v". */
private fun parseVersion(v: String): List<Int> =
    v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

fun isNewerVersion(latest: String, current: String): Boolean {
    val a = parseVersion(latest)
    val b = parseVersion(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x > y) return true
        if (x < y) return false
    }
    return false
}
