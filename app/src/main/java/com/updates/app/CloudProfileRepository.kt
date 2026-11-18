package com.updates.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object CloudProfileRepository {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun load(uid: String, onSuccess: (String, String) -> Unit, onError: (String) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                onSuccess(document.getString("displayName") ?: "", document.getString("bio") ?: "")
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Check your connection and Firestore rules.")
            }
    }

    fun save(uid: String, email: String, displayName: String, bio: String,
             onSuccess: () -> Unit, onError: (String) -> Unit) {
        val profile = hashMapOf(
            "uid" to uid,
            "email" to email,
            "displayName" to displayName,
            "bio" to bio,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(profile, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Check your connection and Firestore rules.")
            }
    }
}
