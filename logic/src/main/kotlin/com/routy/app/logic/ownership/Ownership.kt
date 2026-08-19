package com.routy.app.logic.ownership

/** Mirrors src/lib/ownership.ts — admins edit anything; users edit only what they created. */
fun canEdit(currentUserId: Int, isAdmin: Boolean, ownerId: Int?): Boolean =
    isAdmin || (ownerId != null && ownerId == currentUserId)
