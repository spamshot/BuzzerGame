package com.spam.buzzergame

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// This data class represents the state of a room in Firestore
// The @PropertyName annotation creates an explicit link to the database fields.
// This data class represents the state of a room in Firestore
data class RoomState(
    @get:PropertyName("roomCode")
    val roomCode: String = "",

    @get:PropertyName("isBuzzerActive")
    val isBuzzerActive: Boolean = true,

    @get:PropertyName("buzzedInTeamId")
    val buzzedInTeamId: String? = null,

    @get:PropertyName("buzzedInTeamName")
    val buzzedInTeamName: String? = null,

    @get:PropertyName("buzzedTimestamp")
    val buzzedTimestamp: Long = 0,

    @get:PropertyName("expiresAt")
    val expiresAt: Date? = null
) {
    // A no-argument constructor is required for Firestore's toObject() method
    constructor() : this("", true, null, null, 0,null)
}

    // --- ADD THIS PROPERTY ---
    // This field will hold the timestamp for when the document should be deleted.
//    @get:PropertyName("expiresAt")
//    val expiresAt: Date? = null
