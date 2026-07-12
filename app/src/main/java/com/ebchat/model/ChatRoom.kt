package com.ebchat.model

data class ChatRoom(
    val roomId: String = "",
    val participants: Map<String, Boolean> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val lastMessageSenderId: String = "",
    val lastMessageType: String = MessageType.TEXT.name,
    val unreadCount: Map<String, Int> = emptyMap(),
    val typing: Map<String, Boolean> = emptyMap(),
    val isGroup: Boolean = false,
    val groupId: String = "",
    val pinned: Boolean = false
) {
    fun getOtherUserId(currentUserId: String): String? {
        return participants.keys.firstOrNull { it != currentUserId }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "roomId" to roomId,
        "participants" to participants,
        "lastMessage" to lastMessage,
        "lastMessageTime" to lastMessageTime,
        "lastMessageSenderId" to lastMessageSenderId,
        "lastMessageType" to lastMessageType,
        "unreadCount" to unreadCount,
        "typing" to typing,
        "isGroup" to isGroup,
        "groupId" to groupId,
        "pinned" to pinned
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, roomId: String): ChatRoom = ChatRoom(
            roomId = roomId,
            participants = (map["participants"] as? Map<String, Boolean>) ?: emptyMap(),
            lastMessage = map["lastMessage"] as? String ?: "",
            lastMessageTime = (map["lastMessageTime"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            lastMessageSenderId = map["lastMessageSenderId"] as? String ?: "",
            lastMessageType = map["lastMessageType"] as? String ?: MessageType.TEXT.name,
            unreadCount = (map["unreadCount"] as? Map<String, Int>) ?: emptyMap(),
            typing = (map["typing"] as? Map<String, Boolean>) ?: emptyMap(),
            isGroup = map["isGroup"] as? Boolean ?: false,
            groupId = map["groupId"] as? String ?: "",
            pinned = map["pinned"] as? Boolean ?: false
        )
    }
}
