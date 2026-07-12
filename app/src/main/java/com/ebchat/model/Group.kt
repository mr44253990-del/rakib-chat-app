package com.ebchat.model

data class Group(
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val image: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val members: Map<String, GroupMember> = emptyMap(),
    val admins: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "name" to name,
        "description" to description,
        "image" to image,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "members" to members.mapValues { it.value.toMap() },
        "admins" to admins,
        "lastMessage" to lastMessage,
        "lastMessageTime" to lastMessageTime
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, groupId: String): Group = Group(
            groupId = groupId,
            name = map["name"] as? String ?: "",
            description = map["description"] as? String ?: "",
            image = map["image"] as? String ?: "",
            createdBy = map["createdBy"] as? String ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            members = (map["members"] as? Map<String, Map<String, Any?>>)?.mapValues {
                GroupMember.fromMap(it.value)
            } ?: emptyMap(),
            admins = (map["admins"] as? List<String>) ?: emptyList(),
            lastMessage = map["lastMessage"] as? String ?: "",
            lastMessageTime = (map["lastMessageTime"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}

data class GroupMember(
    val userId: String = "",
    val name: String = "",
    val image: String = "",
    val joinedAt: Long = System.currentTimeMillis(),
    val role: String = MemberRole.MEMBER.name,
    val muted: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "name" to name,
        "image" to image,
        "joinedAt" to joinedAt,
        "role" to role,
        "muted" to muted
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): GroupMember = GroupMember(
            userId = map["userId"] as? String ?: "",
            name = map["name"] as? String ?: "",
            image = map["image"] as? String ?: "",
            joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            role = map["role"] as? String ?: MemberRole.MEMBER.name,
            muted = map["muted"] as? Boolean ?: false
        )
    }
}

enum class MemberRole {
    ADMIN, MEMBER
}
