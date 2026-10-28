package com.routy.app.logic.route

/**
 * M4: Decision for "Go" on a route that the server already owns (blank token —
 * favorite / share / bootstrap / already-accepted). When the client has saved
 * progress (routeProgressStore / walk track / trackedGeometry), Go must RESUME the
 * run instead of clearing the persisted progress and restarting from index -1.
 */
data class AcceptResumeDecision(
    /** true: clear stored progress/walk-track (fresh start). false: keep them (resume). */
    val clearStores: Boolean,
    /** true: start tracking with an already-loaded progress; keep completedWaypointIndex. */
    val keepIndex: Boolean,
)

fun acceptResumeDecision(hasSavedProgress: Boolean): AcceptResumeDecision =
    if (hasSavedProgress) {
        AcceptResumeDecision(clearStores = false, keepIndex = true)
    } else {
        AcceptResumeDecision(clearStores = true, keepIndex = false)
    }
