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
                        storyProgress = (doc.get("storyProgress") as? Map<*, *>)?.entries
                            ?.filter { it.key is String && it.value is Number }
                            ?.associate { (it.key as String) to (it.value as Number).toInt().coerceAtLeast(0) }
                            ?: emptyMap()
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
        val document = db.collection("users").document(uid)
            .collection("privateData").document("reading")
        db.runTransaction { transaction ->
            val snapshot = transaction.get(document)
            val progress = mutableMapOf<String, Int>()
            (snapshot.get("storyProgress") as? Map<*, *>)?.forEach { (key, value) ->
                if (key is String && value is Number) {
                    progress[key] = value.toInt().coerceAtLeast(0)
                }
            }
            // Keep the original title as the map key; transaction safely handles
            // punctuation and concurrent progress updates from another device.
            progress[title.take(120)] = position.coerceAtLeast(0)
            transaction.set(document, mapOf(
                "storyProgress" to progress,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ), SetOptions.merge())
            null
        }.addOnFailureListener { error ->
            onError(error.localizedMessage ?: "Could not sync reading progress.")
        }
    }
}
