package com.ebchat.chat

import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ebchat.R
import com.ebchat.databinding.ActivityChatBinding
import com.ebchat.databinding.ItemMessageReceiverBinding
import com.ebchat.databinding.ItemMessageSenderBinding
import com.ebchat.model.*
import com.ebchat.utils.Constants
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.PrefsManager
import com.ebchat.utils.TimeUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val messages = mutableListOf<Message>()
    private lateinit var adapter: ChatAdapter
    private val prefs = PrefsManager.getInstance()

    private var otherUserId = ""
    private var otherUserName = ""
    private var otherUserImage = ""
    private var chatRoomId = ""
    private var replyingTo: Message? = null

    // Voice recording
    private var mediaRecorder: MediaRecorder? = null
    private var voiceFilePath = ""
    private var isRecording = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        otherUserId = intent.getStringExtra(Constants.EXTRA_USER_ID) ?: ""
        otherUserName = intent.getStringExtra(Constants.EXTRA_USER_NAME) ?: ""
        otherUserImage = intent.getStringExtra(Constants.EXTRA_USER_IMAGE) ?: ""
        chatRoomId = intent.getStringExtra(Constants.EXTRA_CHAT_ROOM_ID) ?: ""

        if (chatRoomId.isEmpty()) {
            chatRoomId = FirebaseUtils.getChatRoomId(
                FirebaseUtils.currentUserId ?: "", otherUserId
            )
        }

        setupUI()
        setupRecyclerView()
        loadMessages()
        listenForTyping()
        listenForOtherUserInfo()
    }

    private fun setupUI() {
        binding.userName.text = otherUserName
        Glide.with(this).load(otherUserImage).placeholder(R.drawable.ic_person)
            .circleCrop().into(binding.userImage)

        binding.backButton.setOnClickListener { finish() }

        binding.sendButton.setOnClickListener {
            sendTextMessage()
        }

        binding.emojiButton.setOnClickListener {
            showEmojiPicker()
        }

        binding.attachButton.setOnClickListener {
            showAttachmentOptions()
        }

        binding.voiceButton.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startVoiceRecording()
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    stopVoiceRecording()
                    true
                }
                else -> false
            }
        }

        binding.cancelReplyButton.setOnClickListener {
            cancelReply()
        }

        // Typing indicator
        binding.messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) setTyping(true)
        }

        // Cancel typing after delay
        binding.messageInput.addTextChangedListener(object : android.text.TextWatcher {
            private var typingRunnable: Runnable? = null
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                setTyping(true)
                typingRunnable?.let { handler.removeCallbacks(it) }
                typingRunnable = Runnable { setTyping(false) }
                handler.postDelayed(typingRunnable!!, Constants.TYPING_TIMEOUT_MS)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.recyclerView.layoutManager = layoutManager

        adapter = ChatAdapter(messages, { message ->
            showMessageOptions(message)
        }, { message ->
            startReply(message)
        })

        binding.recyclerView.adapter = adapter
    }

    private fun loadMessages() {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        FirebaseUtils.messagesRef().child(chatRoomId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    messages.clear()
                    for (msgSnapshot in snapshot.children) {
                        val message = Message.fromMap(
                            msgSnapshot.value as Map<String, Any?>,
                            msgSnapshot.key ?: ""
                        )
                        if (!message.deleted) {
                            messages.add(message)
                        }
                    }
                    messages.sortBy { it.timestamp }
                    adapter.notifyDataSetChanged()
                    if (messages.isNotEmpty()) {
                        binding.recyclerView.scrollToPosition(messages.size - 1)
                    }

                    // Mark messages as read
                    markMessagesAsRead(currentUserId)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun markMessagesAsRead(currentUserId: String) {
        messages.filter { it.receiverId == currentUserId && !it.read }
            .forEach { message ->
                FirebaseUtils.messagesRef()
                    .child(chatRoomId)
                    .child(message.messageId)
                    .child("read")
                    .setValue(true)
            }

        // Update unread count
        FirebaseUtils.chatRoomsRef().child(chatRoomId)
            .child("unreadCount")
            .child(currentUserId)
            .setValue(0)
    }

    private fun listenForTyping() {
        FirebaseUtils.typingRef().child(chatRoomId).child(otherUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isTyping = snapshot.getValue(Boolean::class.java) ?: false
                    binding.typingIndicator.visibility =
                        if (isTyping) View.VISIBLE else View.GONE
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun listenForOtherUserInfo() {
        FirebaseUtils.usersRef().child(otherUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0

                    binding.userStatus.apply {
                        text = if (online) {
                            setTextColor(getColor(R.color.online_green))
                            "online"
                        } else {
                            setTextColor(getColor(R.color.text_secondary))
                            TimeUtils.formatLastSeen(lastSeen)
                        }
                    }
                    binding.onlineIndicator.visibility = if (online) View.VISIBLE else View.GONE
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setTyping(typing: Boolean) {
        val currentUserId = FirebaseUtils.currentUserId ?: return
        FirebaseUtils.typingRef()
            .child(chatRoomId)
            .child(currentUserId)
            .setValue(typing)
    }

    private fun sendTextMessage() {
        val text = binding.messageInput.text.toString().trim()
        if (text.isEmpty()) return

        val currentUserId = FirebaseUtils.currentUserId ?: return
        val messageId = FirebaseUtils.generateMessageId(chatRoomId)

        val message = Message(
            messageId = messageId,
            senderId = currentUserId,
            senderName = prefs.getString("user_name", "User"),
            receiverId = otherUserId,
            content = text,
            type = MessageType.TEXT.name,
            replyTo = replyingTo?.let {
                ReplyInfo(it.messageId, it.senderName, it.content)
            },
            chatRoomId = chatRoomId
        )

        // Save message
        FirebaseUtils.messagesRef().child(chatRoomId).child(messageId)
            .setValue(message.toMap())

        // Update chat room
        updateChatRoom(text, MessageType.TEXT.name)

        binding.messageInput.text?.clear()
        cancelReply()
    }

    private fun updateChatRoom(lastMessage: String, type: String) {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        val roomUpdate = mapOf(
            "lastMessage" to lastMessage,
            "lastMessageTime" to System.currentTimeMillis(),
            "lastMessageSenderId" to currentUserId,
            "lastMessageType" to type,
            "participants/$currentUserId" to true,
            "participants/$otherUserId" to true
        )
        FirebaseUtils.chatRoomsRef().child(chatRoomId).updateChildren(roomUpdate)

        // Increment unread for other user
        FirebaseUtils.chatRoomsRef().child(chatRoomId)
            .child("unreadCount")
            .child(otherUserId)
            .get().addOnSuccessListener { snapshot ->
                val current = snapshot.getValue(Int::class.java) ?: 0
                snapshot.ref.setValue(current + 1)
            }
    }

    private fun startReply(message: Message) {
        replyingTo = message
        binding.replyLayout.visibility = View.VISIBLE
        binding.replyName.text = message.senderName
        binding.replyText.text = message.content
        binding.messageInput.requestFocus()
    }

    private fun cancelReply() {
        replyingTo = null
        binding.replyLayout.visibility = View.GONE
    }

    private fun showMessageOptions(message: Message) {
        val popup = PopupMenu(this, findViewById(R.id.messageOptionsAnchor))
        popup.menuInflater.inflate(R.menu.menu_message_options, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reply -> startReply(message)
                R.id.action_forward -> forwardMessage(message)
                R.id.action_copy -> copyMessage(message)
                R.id.action_delete_me -> deleteMessageForMe(message)
                R.id.action_delete_everyone -> deleteMessageForEveryone(message)
                R.id.action_react -> showEmojiReactions(message)
            }
            true
        }
        popup.show()
    }

    private fun copyMessage(message: Message) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("message", message.content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun deleteMessageForMe(message: Message) {
        FirebaseUtils.messagesRef().child(chatRoomId).child(message.messageId)
            .child("deleted")
            .setValue(true)
    }

    private fun deleteMessageForEveryone(message: Message) {
        val currentUserId = FirebaseUtils.currentUserId ?: return
        if (message.senderId != currentUserId) {
            Toast.makeText(this, "You can only delete your own messages", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Message")
            .setMessage("Delete this message for everyone?")
            .setPositiveButton("Delete") { _, _ ->
                FirebaseUtils.messagesRef().child(chatRoomId).child(message.messageId)
                    .updateChildren(mapOf(
                        "deleted" to true,
                        "content" to getString(R.string.message_deleted)
                    ))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun forwardMessage(message: Message) {
        val intent = Intent(this, ForwardActivity::class.java).apply {
            putExtra("message_id", message.messageId)
            putExtra("message_content", message.content)
            putExtra("message_type", message.type)
            putExtra("from_chat_room", chatRoomId)
        }
        startActivity(intent)
    }

    private fun showEmojiReactions(message: Message) {
        val emojis = arrayOf("❤️", "👍", "😂", "😮", "😢", "🙏", "🔥", "👏")
        AlertDialog.Builder(this)
            .setItems(emojis) { _, which ->
                val currentUserId = FirebaseUtils.currentUserId ?: return@setItems
                val reaction = emojis[which]
                FirebaseUtils.messagesRef().child(chatRoomId).child(message.messageId)
                    .child("reactions")
                    .child(currentUserId)
                    .setValue(reaction)
            }
            .show()
    }

    private fun showEmojiPicker() {
        // Simple emoji picker - in production use a proper emoji library
        val emojis = arrayOf("😀", "😂", "❤️", "👍", "😢", "😡", "🎉", "🔥", "👏", "🙏")
        AlertDialog.Builder(this)
            .setTitle("Pick Emoji")
            .setItems(emojis) { _, which ->
                binding.messageInput.append(emojis[which])
            }
            .show()
    }

    private fun showAttachmentOptions() {
        val options = arrayOf("Image", "Camera", "Location")
        AlertDialog.Builder(this)
            .setTitle("Attach")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImage()
                    1 -> openCamera()
                    2 -> sendLocation()
                }
            }
            .show()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, Constants.RC_IMAGE_PICK)
    }

    private fun openCamera() {
        val intent = android.provider.MediaStore.ACTION_IMAGE_CAPTURE
        startActivityForResult(intent, Constants.RC_CAMERA)
    }

    private fun sendLocation() {
        Toast.makeText(this, "Location sharing - Coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun startVoiceRecording() {
        if (isRecording) return
        isRecording = true

        voiceFilePath = getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath +
                "/voice_${System.currentTimeMillis()}.3gp"

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(voiceFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            try {
                prepare()
                start()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Recording failed", Toast.LENGTH_SHORT).show()
                isRecording = false
            }
        }

        binding.recordingIndicator.visibility = View.VISIBLE
    }

    private fun stopVoiceRecording() {
        if (!isRecording) return
        isRecording = false

        binding.recordingIndicator.visibility = View.GONE

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            uploadVoiceNote()
        } catch (e: Exception) {
            Toast.makeText(this, "Voice recording failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadVoiceNote() {
        val file = File(voiceFilePath)
        if (!file.exists()) return

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Sending voice...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val uri = Uri.fromFile(file)
                val ref = FirebaseUtils.voiceNotesRef()
                    .child("${chatRoomId}_${System.currentTimeMillis()}.3gp")
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                sendVoiceMessage(downloadUrl, (file.length() / 1024).toInt())

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@ChatActivity, "Failed to send voice", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendVoiceMessage(url: String, duration: Int) {
        val currentUserId = FirebaseUtils.currentUserId ?: return
        val messageId = FirebaseUtils.generateMessageId(chatRoomId)

        val message = Message(
            messageId = messageId,
            senderId = currentUserId,
            senderName = prefs.getString("user_name", "User"),
            receiverId = otherUserId,
            content = "Voice message",
            type = MessageType.VOICE.name,
            mediaUrl = url,
            voiceDuration = duration,
            chatRoomId = chatRoomId
        )

        FirebaseUtils.messagesRef().child(chatRoomId).child(messageId)
            .setValue(message.toMap())
        updateChatRoom("Voice message", MessageType.VOICE.name)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                Constants.RC_IMAGE_PICK -> {
                    data?.data?.let { uploadImage(it) }
                }
                Constants.RC_CAMERA -> {
                    // Handle camera result
                }
            }
        }
    }

    private fun uploadImage(uri: Uri) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Sending image...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val ref = FirebaseUtils.chatImagesRef()
                    .child("${chatRoomId}_${System.currentTimeMillis()}.jpg")
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                sendImageMessage(downloadUrl)

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@ChatActivity, "Failed to send image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendImageMessage(url: String) {
        val currentUserId = FirebaseUtils.currentUserId ?: return
        val messageId = FirebaseUtils.generateMessageId(chatRoomId)

        val message = Message(
            messageId = messageId,
            senderId = currentUserId,
            senderName = prefs.getString("user_name", "User"),
            receiverId = otherUserId,
            content = "Image",
            type = MessageType.IMAGE.name,
            mediaUrl = url,
            chatRoomId = chatRoomId
        )

        FirebaseUtils.messagesRef().child(chatRoomId).child(messageId)
            .setValue(message.toMap())
        updateChatRoom("Image", MessageType.IMAGE.name)
    }

    override fun onDestroy() {
        super.onDestroy()
        setTyping(false)
        mediaRecorder?.release()
    }

    // Chat Adapter
    inner class ChatAdapter(
        private val messages: List<Message>,
        private val onLongClick: (Message) -> Unit,
        private val onReplySwipe: (Message) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val VIEW_TYPE_SENT = 1
            const val VIEW_TYPE_RECEIVED = 2
        }

        inner class SentViewHolder(val binding: ItemMessageSenderBinding) :
            RecyclerView.ViewHolder(binding.root)

        inner class ReceivedViewHolder(val binding: ItemMessageReceiverBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun getItemViewType(position: Int): Int {
            val currentUserId = FirebaseUtils.currentUserId ?: ""
            return if (messages[position].senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_SENT) {
                SentViewHolder(ItemMessageSenderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ))
            } else {
                ReceivedViewHolder(ItemMessageReceiverBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val message = messages[position]

            if (holder is SentViewHolder) {
                bindSentMessage(holder.binding, message)
            } else if (holder is ReceivedViewHolder) {
                bindReceivedMessage(holder.binding, message)
            }
        }

        private fun bindSentMessage(binding: ItemMessageSenderBinding, message: Message) {
            binding.apply {
                messageText.text = message.content
                timeText.text = TimeUtils.formatTime(message.timestamp)

                // Reply info
                if (message.replyTo != null) {
                    replyLayout.visibility = View.VISIBLE
                    replyName.text = message.replyTo.senderName
                    replyText.text = message.replyTo.content
                } else {
                    replyLayout.visibility = View.GONE
                }

                // Reactions
                if (message.reactions.isNotEmpty()) {
                    reactionsLayout.visibility = View.VISIBLE
                    reactionsText.text = message.reactions.values.joinToString(" ")
                } else {
                    reactionsLayout.visibility = View.GONE
                }

                // Status indicators
                statusIcon.visibility = View.VISIBLE
                statusIcon.setImageResource(
                    when {
                        message.read -> R.drawable.ic_done_all
                        message.delivered -> R.drawable.ic_done_all
                        else -> R.drawable.ic_done
                    }
                )

                root.setOnLongClickListener {
                    onLongClick(message)
                    true
                }
            }
        }

        private fun bindReceivedMessage(binding: ItemMessageReceiverBinding, message: Message) {
            binding.apply {
                messageText.text = message.content
                timeText.text = TimeUtils.formatTime(message.timestamp)

                // Reply info
                if (message.replyTo != null) {
                    replyLayout.visibility = View.VISIBLE
                    replyName.text = message.replyTo.senderName
                    replyText.text = message.replyTo.content
                } else {
                    replyLayout.visibility = View.GONE
                }

                // Reactions
                if (message.reactions.isNotEmpty()) {
                    reactionsLayout.visibility = View.VISIBLE
                    reactionsText.text = message.reactions.values.joinToString(" ")
                } else {
                    reactionsLayout.visibility = View.GONE
                }

                root.setOnLongClickListener {
                    onLongClick(message)
                    true
                }
            }
        }

        override fun getItemCount() = messages.size
    }
}
