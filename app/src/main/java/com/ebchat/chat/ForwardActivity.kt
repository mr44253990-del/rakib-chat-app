package com.ebchat.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.ebchat.R
import com.ebchat.databinding.ActivityForwardBinding
import com.ebchat.databinding.ItemForwardContactBinding
import com.ebchat.model.Message
import com.ebchat.model.User
import com.ebchat.utils.Constants
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.TimeUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ForwardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForwardBinding
    private val contacts = mutableListOf<User>()
    private lateinit var adapter: ForwardContactsAdapter

    private var messageId = ""
    private var messageContent = ""
    private var messageType = ""
    private var fromChatRoom = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForwardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        messageId = intent.getStringExtra("message_id") ?: ""
        messageContent = intent.getStringExtra("message_content") ?: ""
        messageType = intent.getStringExtra("message_type") ?: "TEXT"
        fromChatRoom = intent.getStringExtra("from_chat_room") ?: ""

        binding.toolbarTitle.text = getString(R.string.forward_to)
        binding.backButton.setOnClickListener { finish() }

        adapter = ForwardContactsAdapter(contacts) { user ->
            forwardToUser(user)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadContacts()
    }

    private fun loadContacts() {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        FirebaseUtils.usersRef()
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    contacts.clear()
                    for (userSnapshot in snapshot.children) {
                        val user = User.fromMap(
                            userSnapshot.value as Map<String, Any?>,
                            userSnapshot.key ?: ""
                        )
                        if (user.uid != currentUserId) {
                            contacts.add(user)
                        }
                    }
                    contacts.sortBy { it.name }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ForwardActivity, "Error loading contacts", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun forwardToUser(user: User) {
        lifecycleScope.launch {
            try {
                val currentUserId = FirebaseUtils.currentUserId ?: return@launch
                val newRoomId = FirebaseUtils.getChatRoomId(currentUserId, user.uid)

                // Ensure chat room exists
                val roomSnapshot = FirebaseUtils.chatRoomsRef().child(newRoomId).get().await()
                if (!roomSnapshot.exists()) {
                    val roomMap = mapOf(
                        "roomId" to newRoomId,
                        "participants" to mapOf(
                            currentUserId to true,
                            user.uid to true
                        ),
                        "lastMessage" to "",
                        "lastMessageTime" to System.currentTimeMillis()
                    )
                    FirebaseUtils.chatRoomsRef().child(newRoomId).setValue(roomMap).await()
                }

                // Create forwarded message
                val newMessageId = FirebaseUtils.generateMessageId(newRoomId)
                val currentUserSnapshot = FirebaseUtils.usersRef().child(currentUserId).get().await()
                val senderName = currentUserSnapshot.child("name").value as? String ?: "User"

                val message = Message(
                    messageId = newMessageId,
                    senderId = currentUserId,
                    senderName = senderName,
                    receiverId = user.uid,
                    content = messageContent,
                    type = messageType,
                    forwarded = true,
                    chatRoomId = newRoomId
                )

                FirebaseUtils.messagesRef().child(newRoomId).child(newMessageId)
                    .setValue(message.toMap()).await()

                // Update room
                FirebaseUtils.chatRoomsRef().child(newRoomId).updateChildren(
                    mapOf(
                        "lastMessage" to (if (messageType == MessageType.TEXT.name) messageContent else "Forwarded $messageType"),
                        "lastMessageTime" to System.currentTimeMillis(),
                        "lastMessageSenderId" to currentUserId,
                        "lastMessageType" to messageType
                    )
                ).await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ForwardActivity, "Forwarded to ${user.name}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ForwardActivity, "Failed to forward: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class ForwardContactsAdapter(
        private val contacts: List<User>,
        private val onClick: (User) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ForwardContactsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemForwardContactBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemForwardContactBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = contacts[position]
            holder.binding.apply {
                userName.text = user.name
                userStatus.text = if (user.online) "online" else TimeUtils.formatLastSeen(user.lastSeen)
                if (user.profileImage.isNotEmpty()) {
                    Glide.with(root).load(user.profileImage).circleCrop().into(userImage)
                }
                root.setOnClickListener { onClick(user) }
            }
        }

        override fun getItemCount() = contacts.size
    }
}
