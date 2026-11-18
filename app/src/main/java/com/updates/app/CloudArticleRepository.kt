package com.updates.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object CloudArticleRepository {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun observe(
        onChange: (List<CommunityArticle>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return db.collection("communityArticles")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "Unable to load community articles.")
                    return@addSnapshotListener
                }
                val articles = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val body = doc.getString("body") ?: return@mapNotNull null
                    CommunityArticle(
                        id = doc.id,
                        title = title,
                        category = doc.getString("category") ?: "Other",
                        body = body,
                        publishedAt = doc.getLong("publishedAt") ?: 0L,
                        authorName = doc.getString("authorName") ?: "Reader",
                        authorUid = doc.getString("authorUid") ?: ""
                    )
                }.sortedByDescending { it.publishedAt }
                onChange(articles)
            }
    }

    fun publish(
        article: CommunityArticle,
        userId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (userId.isBlank() || article.title.length !in 5..120 || article.body.length !in 30..20000) {
            onError("Please check your account, title (5–120 characters), and article (30–20,000 characters).")
            return
        }
        val data = hashMapOf(
            "title" to article.title,
            "category" to article.category,
            "body" to article.body,
            "publishedAt" to article.publishedAt,
            "authorName" to article.authorName,
            "authorUid" to userId
        )
        db.collection("communityArticles").document(article.id).set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Article could not be published.")
            }
    }

    fun delete(
        articleId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("communityArticles").document(articleId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Article could not be deleted.")
            }
    }
}
