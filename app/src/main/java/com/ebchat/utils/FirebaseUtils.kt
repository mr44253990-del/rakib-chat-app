package com.ebchat.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await

object FirebaseUtils {

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUserId: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    // Database References
    fun usersRef(): DatabaseReference = database.getReference(Constants.USERS_REF)
    fun messagesRef(): DatabaseReference = database.getReference(Constants.MESSAGES_REF)
    fun chatRoomsRef(): DatabaseReference = database.getReference(Constants.CHAT_ROOMS_REF)
    fun groupsRef(): DatabaseReference = database.getReference(Constants.GROUPS_REF)
    fun storiesRef(): DatabaseReference = database.getReference(Constants.STORIES_REF)
    fun typingRef(): DatabaseReference = database.getReference(Constants.TYPING_REF)
    fun mutedUsersRef(): DatabaseReference = database.getReference(Constants.MUTED_USERS_REF)
    fun fcmTokensRef(): DatabaseReference = database.getReference(Constants.FCM_TOKENS_REF)

    // Storage References
    fun profileImagesRef(): StorageReference = storage.reference.child("profile_images")
    fun storyMediaRef(): StorageReference = storage.reference.child("story_media")
    fun voiceNotesRef(): StorageReference = storage.reference.child("voice_notes")
    fun chatImagesRef(): StorageReference = storage.reference.child("chat_images")

    // Generate unique IDs
    fun generateMessageId(chatRoomId: String): String =
        database.getReference(Constants.MESSAGES_REF).child(chatRoomId).push().key ?: ""

    fun generateRoomId(): String =
        database.getReference(Constants.CHAT_ROOMS_REF).push().key ?: ""

    fun generateGroupId(): String =
        database.getReference(Constants.GROUPS_REF).push().key ?: ""

    fun generateStoryId(): String =
        database.getReference(Constants.STORIES_REF).push().key ?: ""

    // Chat Room ID from two user IDs
    fun getChatRoomId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_$userId2" else "${userId2}_$userId1"
    }

    // Update online status
    suspend fun updateOnlineStatus(online: Boolean) {
        val uid = currentUserId ?: return
        try {
            usersRef().child(uid).updateChildren(
                mapOf(
                    "online" to online,
                    "lastSeen" to System.currentTimeMillis()
                )
            ).await()
        } catch (_: Exception) {
            // Silently fail - user might have logged out
        }
    }

    // Sign out and clean up
    suspend fun signOut() {
        updateOnlineStatus(false)
        auth.signOut()
    }
}
