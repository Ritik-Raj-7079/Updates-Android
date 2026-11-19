package com.updates.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

data class CloudReadingData(
    val savedStories: Set<String> = emptySet(),
    val savedBooks: Set<String> = emptySet(),
    val history: List<String> = emptyList()
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
                        history = (doc.get("history") as? List<*>)?.filterIsInstance<String>()?.take(50) ?: emptyList()
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
}
