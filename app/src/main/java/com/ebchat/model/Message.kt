package com.ebchat.model

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderImage: String = "",
    val receiverId: String = "",
    val content: String = "",
    val type: String = MessageType.TEXT.name,
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false,
    val delivered: Boolean = false,
    val replyTo: ReplyInfo? = null,
    val reactions: Map<String, String> = emptyMap(),
    val deleted: Boolean = false,
    val forwarded: Boolean = false,
    val mediaUrl: String = "",
    val voiceDuration: Int = 0,
    val chatRoomId: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "messageId" to messageId,
        "senderId" to senderId,
        "senderName" to senderName,
        "senderImage" to senderImage,
        "receiverId" to receiverId,
        "content" to content,
        "type" to type,
        "timestamp" to timestamp,
        "read" to read,
        "delivered" to delivered,
        "replyTo" to replyTo?.toMap(),
        "reactions" to reactions,
        "deleted" to deleted,
        "forwarded" to forwarded,
        "mediaUrl" to mediaUrl,
        "voiceDuration" to voiceDuration,
        "chatRoomId" to chatRoomId
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, messageId: String): Message = Message(
            messageId = messageId,
            senderId = map["senderId"] as? String ?: "",
            senderName = map["senderName"] as? String ?: "",
            senderImage = map["senderImage"] as? String ?: "",
            receiverId = map["receiverId"] as? String ?: "",
            content = map["content"] as? String ?: "",
            type = map["type"] as? String ?: MessageType.TEXT.name,
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            read = map["read"] as? Boolean ?: false,
            delivered = map["delivered"] as? Boolean ?: false,
            replyTo = (map["replyTo"] as? Map<String, Any?>)?.let { ReplyInfo.fromMap(it) },
            reactions = (map["reactions"] as? Map<String, String>) ?: emptyMap(),
            deleted = map["deleted"] as? Boolean ?: false,
            forwarded = map["forwarded"] as? Boolean ?: false,
            mediaUrl = map["mediaUrl"] as? String ?: "",
            voiceDuration = (map["voiceDuration"] as? Number)?.toInt() ?: 0,
            chatRoomId = map["chatRoomId"] as? String ?: ""
        )
    }
}

data class ReplyInfo(
    val messageId: String = "",
    val senderName: String = "",
    val content: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "messageId" to messageId,
        "senderName" to senderName,
        "content" to content
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ReplyInfo = ReplyInfo(
            messageId = map["messageId"] as? String ?: "",
            senderName = map["senderName"] as? String ?: "",
            content = map["content"] as? String ?: ""
        )
    }
}

enum class MessageType {
    TEXT, IMAGE, VOICE, VIDEO, FILE, LOCATION
}
