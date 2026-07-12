package com.ebchat.model

data class Story(
    val storyId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userImage: String = "",
    val mediaUrl: String = "",
    val type: String = StoryType.IMAGE.name,
    val caption: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (12 * 60 * 60 * 1000),
    val viewers: Map<String, Boolean> = emptyMap(),
    val deleted: Boolean = false
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt

    fun toMap(): Map<String, Any?> = mapOf(
        "storyId" to storyId,
        "userId" to userId,
        "userName" to userName,
        "userImage" to userImage,
        "mediaUrl" to mediaUrl,
        "type" to type,
        "caption" to caption,
        "timestamp" to timestamp,
        "expiresAt" to expiresAt,
        "viewers" to viewers,
        "deleted" to deleted
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, storyId: String): Story = Story(
            storyId = storyId,
            userId = map["userId"] as? String ?: "",
            userName = map["userName"] as? String ?: "",
            userImage = map["userImage"] as? String ?: "",
            mediaUrl = map["mediaUrl"] as? String ?: "",
            type = map["type"] as? String ?: StoryType.IMAGE.name,
            caption = map["caption"] as? String ?: "",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            expiresAt = (map["expiresAt"] as? Number)?.toLong()
                ?: (System.currentTimeMillis() + (12 * 60 * 60 * 1000)),
            viewers = (map["viewers"] as? Map<String, Boolean>) ?: emptyMap(),
            deleted = map["deleted"] as? Boolean ?: false
        )
    }
}

enum class StoryType {
    IMAGE, VIDEO, TEXT
}
