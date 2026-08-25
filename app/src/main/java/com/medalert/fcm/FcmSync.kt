package com.medalert.fcm

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID

/**
 * Pushes ONLY anonymized data to Firestore — a numeric medication id (the local Room
 * primary key, meaningless outside this app) and future trigger timestamps. No medication
 * name, dosage, or notes ever leaves the device. This backs the FCM last-resort push
 * fallback: a GitHub Action polls these timestamps and sends a data-only push at the
 * right time; the app maps the id back to the real medication name locally on receipt.
 *
 * Firestore security rules require an authenticated request (see project setup notes) —
 * we sign in anonymously (no email/password, no personal data) so writes are accepted
 * while still blocking unauthenticated internet clients from reading/writing the DB.
 */
object FcmSync {

    private const val TAG = "FcmSync"
    private const val PREFS_NAME = "fcm_sync_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    private val db by lazy { FirebaseFirestore.getInstance() }

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    private fun ensureAuth(onReady: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) { onReady(); return }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady() }
            .addOnFailureListener { e -> Log.w(TAG, "anonymous sign-in failed", e) }
    }

    /** Registers the current FCM token under this device's Firestore doc. Call on app start and on token refresh. */
    fun syncToken(context: Context) {
        val deviceId = getDeviceId(context)
        ensureAuth {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                db.collection("users").document(deviceId)
                    .set(mapOf("fcmToken" to token, "updatedAt" to System.currentTimeMillis()))
                    .addOnFailureListener { e -> Log.w(TAG, "token sync failed", e) }
            }.addOnFailureListener { e -> Log.w(TAG, "getToken failed", e) }
        }
    }

    /**
     * Uploads the anonymized future-occurrence list for one medication.
     * Fire-and-forget — this is a best-effort backup channel; failures here must never
     * block or fail the local AlarmManager scheduling that already happened.
     */
    fun pushOccurrences(context: Context, medicationId: Long, occurrences: List<Long>) {
        val deviceId = getDeviceId(context)
        ensureAuth {
            db.collection("users").document(deviceId)
                .collection("medications").document(medicationId.toString())
                .set(mapOf(
                    "active" to true,
                    "occurrences" to occurrences,
                    "updatedAt" to System.currentTimeMillis()
                ))
                .addOnFailureListener { e -> Log.w(TAG, "pushOccurrences failed for med $medicationId", e) }
        }
    }

    /** Marks a medication inactive in Firestore (deactivated/deleted locally) so the backend stops polling it. */
    fun markInactive(context: Context, medicationId: Long) {
        val deviceId = getDeviceId(context)
        ensureAuth {
            db.collection("users").document(deviceId)
                .collection("medications").document(medicationId.toString())
                .set(mapOf("active" to false, "updatedAt" to System.currentTimeMillis()))
                .addOnFailureListener { e -> Log.w(TAG, "markInactive failed for med $medicationId", e) }
        }
    }
}
