package com.ebchat.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ebchat.R
import com.ebchat.chat.ChatActivity
import com.ebchat.home.MainActivity
import com.ebchat.utils.Constants
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.PrefsManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val prefs by lazy { PrefsManager.getInstance() }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save FCM token to Firebase
        val userId = FirebaseUtils.currentUserId ?: return
        FirebaseUtils.fcmTokensRef().child(userId).setValue(token)
        FirebaseUtils.usersRef().child(userId).child("fcmToken").setValue(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Check if notifications are enabled
        if (!prefs.isNotificationsEnabled) return

        // Check if sender is muted
        val senderId = remoteMessage.data["senderId"] ?: ""
        val currentUserId = FirebaseUtils.currentUserId ?: return

        // Check mute status
        FirebaseUtils.mutedUsersRef()
            .child(currentUserId)
            .child(senderId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists() || snapshot.value != true) {
                    showNotification(remoteMessage)
                }
            }
    }

    private fun showNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"] ?: "New Message"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"] ?: ""
        val senderId = remoteMessage.data["senderId"] ?: ""
        val senderName = remoteMessage.data["senderName"] ?: ""
        val chatRoomId = remoteMessage.data["chatRoomId"] ?: ""

        // Create intent to open chat
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(Constants.EXTRA_USER_ID, senderId)
            putExtra(Constants.EXTRA_USER_NAME, senderName)
            putExtra(Constants.EXTRA_CHAT_ROOM_ID, chatRoomId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, pendingIntentFlags
        )

        // Sound
        val defaultSoundUri = if (prefs.isSoundEnabled) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            null
        }

        // Vibration pattern
        val vibratePattern = if (prefs.isVibrationEnabled) {
            longArrayOf(0, 300, 200, 300)
        } else {
            longArrayOf(0)
        }

        val notificationBuilder = NotificationCompat.Builder(
            this,
            getString(R.string.default_notification_channel_id)
        ).apply {
            setSmallIcon(R.drawable.ic_notification)
            setContentTitle(title)
            setContentText(body)
            setAutoCancel(true)
            setContentIntent(pendingIntent)
            priority = NotificationCompat.PRIORITY_HIGH
            setCategory(NotificationCompat.CATEGORY_MESSAGE)
            setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

            // Style for longer messages
            setStyle(NotificationCompat.BigTextStyle().bigText(body))

            // Sound
            defaultSoundUri?.let { setSound(it) }

            // Vibration
            setVibrate(vibratePattern)

            // LED
            setLights(getColor(R.color.pink_primary), 300, 2000)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
