package com.updates.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

data class CloudReadingData(
    val savedStories: Set<String> = emptySet(),
    val savedBooks: Set<String> = emptySet(),
    val history: List<String> = emptyList(),
    val storyProgress: Map<String, Int> = emptyMap()
)

object CloudReadingRepository {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun load(uid: String, onSuccess: (CloudReadingData) -> Unit, onError: (String) -> Unit) {
        if (uid.isBlank()) {
            onError("Sign in to sync your library.")
            return
        }
        db.collection("users").document(uid).collection("privateData").document("reading")
            .get()
            .addOnSuccessListener { doc ->
                onSuccess(
                    CloudReadingData(
                        savedStories = (doc.get("savedStories") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet(),
                        savedBooks = (doc.get("savedBooks") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet(),
                        history = (doc.get("history") as? List<*>)?.filterIsInstance<String>()?.take(50) ?: emptyList(),
                        storyProgress = (doc.get("storyProgress") as? Map<*, *>)?.mapNotNull { (key, value) ->
                            if (key is String && value is Number) key to value.toInt().coerceAtLeast(0) else null
                        }?.toMap() ?: emptyMap()
                    )
                )
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "Could not load your cloud library.") }
    }

    fun save(uid: String, data: CloudReadingData, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (uid.isBlank()) {
            onError("Sign in to sync your library.")
            return
        }
        val payload = hashMapOf(
            "savedStories" to data.savedStories.toList(),
            "savedBooks" to data.savedBooks.toList(),
            "history" to data.history.take(50),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).collection("privateData").document("reading")
            .set(payload, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Could not sync your library.") }
    }
    fun saveProgress(uid: String, title: String, position: Int, onError: (String) -> Unit = {}) {
        if (uid.isBlank() || title.isBlank()) {
            onError("Sign in to sync reading progress.")
            return
        }
        val safeKey = title.take(120)
        db.collection("users").document(uid).collection("privateData").document("reading")
            .update("storyProgress.$safeKey", position.coerceAtLeast(0),
                "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
            .addOnFailureListener { error ->
                // A missing reading document is created with the progress map.
                val payload = hashMapOf(
                    "storyProgress" to mapOf(safeKey to position.coerceAtLeast(0)),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                db.collection("users").document(uid).collection("privateData").document("reading")
                    .set(payload, SetOptions.merge())
                    .addOnFailureListener { retryError ->
                        onError(retryError.localizedMessage ?: error.localizedMessage ?: "Could not sync reading progress.")
                    }
            }
    }

}
