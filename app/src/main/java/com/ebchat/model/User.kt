package com.ebchat.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val bio: String = "",
    val profileImage: String = "",
    val voiceNote: String = "",
    val status: String = "Hey, I'm using EB Chat!",
    val online: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val fcmToken: String = "",
    val searchable: Boolean = true,
    val userId: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "name" to name,
        "email" to email,
        "phone" to phone,
        "bio" to bio,
        "profileImage" to profileImage,
        "voiceNote" to voiceNote,
        "status" to status,
        "online" to online,
        "lastSeen" to lastSeen,
        "createdAt" to createdAt,
        "fcmToken" to fcmToken,
        "searchable" to searchable,
        "userId" to userId
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, uid: String): User = User(
            uid = uid,
            name = map["name"] as? String ?: "",
            email = map["email"] as? String ?: "",
            phone = map["phone"] as? String ?: "",
            bio = map["bio"] as? String ?: "",
            profileImage = map["profileImage"] as? String ?: "",
            voiceNote = map["voiceNote"] as? String ?: "",
            status = map["status"] as? String ?: "",
            online = map["online"] as? Boolean ?: false,
            lastSeen = (map["lastSeen"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            fcmToken = map["fcmToken"] as? String ?: "",
            searchable = map["searchable"] as? Boolean ?: true,
            userId = map["userId"] as? String ?: ""
        )
    }
}
