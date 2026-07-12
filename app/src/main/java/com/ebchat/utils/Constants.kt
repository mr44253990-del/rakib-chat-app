package com.ebchat.utils

object Constants {
    // Firebase Paths
    const val USERS_REF = "users"
    const val MESSAGES_REF = "messages"
    const val CHAT_ROOMS_REF = "chatRooms"
    const val GROUPS_REF = "groups"
    const val STORIES_REF = "stories"
    const val TYPING_REF = "typing"
    const val MUTED_USERS_REF = "mutedUsers"
    const val FCM_TOKENS_REF = "fcmTokens"

    // Preferences
    const val PREFS_NAME = "ebchat_prefs"
    const val PREF_FIRST_LAUNCH = "first_launch"
    const val PREF_USER_ID = "user_id"
    const val PREF_DARK_MODE = "dark_mode"
    const val PREF_NOTIFICATIONS = "notifications_enabled"
    const val PREF_SOUND = "sound_enabled"
    const val PREF_VIBRATION = "vibration_enabled"
    const val PREF_SHOW_ONLINE = "show_online_status"
    const val PREF_READ_RECEIPTS = "read_receipts"

    // Intent Extras
    const val EXTRA_USER_ID = "user_id"
    const val EXTRA_USER_NAME = "user_name"
    const val EXTRA_USER_IMAGE = "user_image"
    const val EXTRA_GROUP_ID = "group_id"
    const val EXTRA_GROUP_NAME = "group_name"
    const val EXTRA_CHAT_ROOM_ID = "chat_room_id"
    const val EXTRA_MESSAGE_TO_FORWARD = "message_to_forward"

    // Request Codes
    const val RC_GOOGLE_SIGN_IN = 1001
    const val RC_IMAGE_PICK = 1002
    const val RC_CAMERA = 1003
    const val RC_PERMISSIONS = 1004
    const val RC_VOICE_RECORD = 1005

    // Story
    const val STORY_EXPIRY_HOURS = 12L
    const val STORY_EXPIRY_MS = STORY_EXPIRY_HOURS * 60 * 60 * 1000

    // Typing
    const val TYPING_TIMEOUT_MS = 3000L

    // Supabase
    const val SUPABASE_URL = "https://srfztgcdejfaesrvkarg.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_BcH2xwywnUCVG48LYjPOLQ_8-y2InGA"
}
