package com.routy.app.logic.update

/** Kotlin port of src/lib/updateCheck.ts — dotted integers; strips trailing `a`/`s` on each segment. */
private fun parseVersion(v: String): List<Int> =
    v.removePrefix("v")
        .split(".")
        .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

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
