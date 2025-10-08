package com.spam.buzzergame

import android.content.Context
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
//import com.google.firebase.firestore.ktx.firestore
//import com.google.firebase.firestore.ktx.toObject
//import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Date
import androidx.core.content.edit



enum class ValidationStatus {
    IDLE,
    LOADING,
    SUCCESS,
    FAILURE
}

class BuzzerViewModel : ViewModel() {

    // The connection to our Firestore database
    private val db = Firebase.firestore

    // StateFlow to hold the real-time state of the current room
    private val _roomState = MutableStateFlow(RoomState())
    val roomState: StateFlow<RoomState> = _roomState

    // StateFlow to manage the UI state of the room code validation
    private val _validationStatus = MutableStateFlow(ValidationStatus.IDLE)
    val validationStatus: StateFlow<ValidationStatus> = _validationStatus

    /**
     * Creates a new room in Firestore.
     * This function also performs a cleanup of the last room created by this device.
     */
    fun createRoom(context: Context, newRoomCode: String) {
        // First, clean up the last room created by THIS teacher to prevent orphans
        cleanupPreviousRoom(context)

        // Calculate an expiration time for future use with TTL policies
        val twentyFourHoursInMillis = 24 * 60 * 60 * 1000
        val expirationDate = Date(System.currentTimeMillis() + twentyFourHoursInMillis)

        // Create the new RoomState object with the expiration date
        val newRoom = RoomState(
            roomCode = newRoomCode,
            isBuzzerActive = true,
            expiresAt = expirationDate
        )

        db.collection("rooms").document(newRoomCode).set(newRoom).addOnSuccessListener {
            // After successfully creating the room, save its code locally for future cleanup
            saveMyRoomCode(context, newRoomCode)
            listenToRoomUpdates(newRoomCode)
        }
    }

    /**
     * Called by a student to start listening to an existing room's updates.
     */
    fun joinRoom(roomCode: String) {
        listenToRoomUpdates(roomCode)
    }

    /**
     * Attaches a real-time snapshot listener to a room document in Firestore.
     * Any changes to the room will be automatically pushed to the _roomState Flow.
     */
    private fun listenToRoomUpdates(roomCode: String) {
        db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { return@addSnapshotListener }
                if (snapshot != null && snapshot.exists()) {
                    _roomState.value = snapshot.toObject<RoomState>()!!
                }
            }
    }

    /**
     * Allows a team to buzz in. Uses a transaction to ensure only the first buzz is registered.
     */
    fun buzzIn(roomCode: String, team: Team) {
        val roomRef = db.collection("rooms").document(roomCode)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(roomRef)
            val currentState = snapshot.toObject<RoomState>()!!
            // This check is critical: only proceed if the buzzer is currently active
            if (currentState.isBuzzerActive) {
                transaction.update(
                    roomRef,
                    mapOf(
                        "isBuzzerActive" to false,
                        "buzzedInTeamId" to team.id,
                        "buzzedInTeamName" to team.name,
                        "buzzedTimestamp" to System.currentTimeMillis()
                    )
                )
            }
            null
        }
    }

    /**
     * Resets the buzzer for the next question. Called by the teacher.
     */
    fun resetBuzzer(roomCode: String) {
        db.collection("rooms").document(roomCode)
            .update(
                mapOf(
                    "isBuzzerActive" to true,
                    "buzzedInTeamId" to null,
                    "buzzedInTeamName" to null
                )
            )
    }

    /**
     * Checks if a room code exists in Firestore before allowing a student to join.
     */
    fun validateRoomCode(roomCode: String) {
        _validationStatus.value = ValidationStatus.LOADING
        db.collection("rooms").document(roomCode).get()
            .addOnSuccessListener { document ->
                _validationStatus.value = if (document != null && document.exists()) {
                    ValidationStatus.SUCCESS
                } else {
                    ValidationStatus.FAILURE
                }
            }
            .addOnFailureListener {
                _validationStatus.value = ValidationStatus.FAILURE
            }
    }

    /**
     * Resets the validation status, used to clear error messages in the UI.
     */
    fun resetValidationStatus() {
        _validationStatus.value = ValidationStatus.IDLE
    }

    // --- Local Storage (SharedPreferences) Functions for Manual Cleanup ---

    private fun getPrefs(context: Context) =
        context.getSharedPreferences("teacher_prefs", Context.MODE_PRIVATE)

    private fun saveMyRoomCode(context: Context, roomCode: String) {
        getPrefs(context).edit { putString("my_last_room", roomCode) }
    }

    private fun cleanupPreviousRoom(context: Context) {
        val prefs = getPrefs(context)
        val lastRoomCode = prefs.getString("my_last_room", null)
        if (lastRoomCode != null) {
            // Delete the previously saved room from Firestore
            db.collection("rooms").document(lastRoomCode).delete()
            // Clear the record from local storage
            prefs.edit { remove("my_last_room") }
        }
    }
}