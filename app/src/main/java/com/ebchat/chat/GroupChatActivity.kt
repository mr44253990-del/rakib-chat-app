package com.ebchat.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.ebchat.R
import com.ebchat.databinding.ActivityGroupChatBinding
import com.ebchat.databinding.ItemGroupMessageBinding
import com.ebchat.model.Group
import com.ebchat.model.MemberRole
import com.ebchat.model.Message
import com.ebchat.model.MessageType
import com.ebchat.utils.Constants
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.TimeUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class GroupChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupChatBinding
    private val messages = mutableListOf<Message>()
    private lateinit var adapter: GroupChatAdapter

    private var groupId = ""
    private var groupName = ""
    private var chatRoomId = ""
    private val memberNames = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra(Constants.EXTRA_GROUP_ID) ?: ""
        groupName = intent.getStringExtra(Constants.EXTRA_GROUP_NAME) ?: ""
        chatRoomId = intent.getStringExtra(Constants.EXTRA_CHAT_ROOM_ID) ?: ""

        if (chatRoomId.isEmpty()) {
            chatRoomId = "group_$groupId"
        }

        setupUI()
        setupRecyclerView()
        loadGroupInfo()
        loadMessages()
    }

    private fun setupUI() {
        binding.groupName.text = groupName
        binding.backButton.setOnClickListener { finish() }

        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        binding.groupInfoButton.setOnClickListener {
            showGroupInfo()
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.recyclerView.layoutManager = layoutManager

        adapter = GroupChatAdapter(messages, memberNames)
        binding.recyclerView.adapter = adapter
    }

    private fun loadGroupInfo() {
        FirebaseUtils.groupsRef().child(groupId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val group = Group.fromMap(
                        snapshot.value as Map<String, Any?>,
                        snapshot.key ?: ""
                    )
                    groupName = group.name
                    binding.groupName.text = groupName
                    binding.memberCount.text = "${group.members.size} members"

                    // Cache member names
                    group.members.forEach { (uid, member) ->
                        memberNames[uid] = member.name
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadMessages() {
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
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun sendMessage() {
        val text = binding.messageInput.text.toString().trim()
        if (text.isEmpty()) return

        val currentUserId = FirebaseUtils.currentUserId ?: return
        val messageId = FirebaseUtils.generateMessageId(chatRoomId)

        val message = Message(
            messageId = messageId,
            senderId = currentUserId,
            senderName = memberNames[currentUserId] ?: "User",
            receiverId = groupId,
            content = text,
            type = MessageType.TEXT.name,
            chatRoomId = chatRoomId
        )

        FirebaseUtils.messagesRef().child(chatRoomId).child(messageId)
            .setValue(message.toMap())

        // Update group last message
        FirebaseUtils.groupsRef().child(groupId).updateChildren(
            mapOf(
                "lastMessage" to text,
                "lastMessageTime" to System.currentTimeMillis()
            )
        )

        binding.messageInput.text?.clear()
    }

    private fun showGroupInfo() {
        // Show group info dialog
        val membersList = memberNames.entries.joinToString("\n") { "• ${it.value}" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(groupName)
            .setMessage("Members:\n$membersList")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    inner class GroupChatAdapter(
        private val messages: List<Message>,
        private val memberNames: Map<String, String>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<GroupChatAdapter.MessageViewHolder>() {

        inner class MessageViewHolder(val binding: ItemGroupMessageBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val binding = ItemGroupMessageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return MessageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messages[position]
            val currentUserId = FirebaseUtils.currentUserId ?: ""

            holder.binding.apply {
                senderName.text = memberNames[message.senderId] ?: "Unknown"
                senderName.visibility = View.VISIBLE

                messageText.text = message.content
                timeText.text = TimeUtils.formatTime(message.timestamp)

                // Different background for own messages
                if (message.senderId == currentUserId) {
                    messageBubble.setBackgroundResource(R.drawable.bg_chat_sender)
                    messageText.setTextColor(getColor(android.R.color.white))
                } else {
                    messageBubble.setBackgroundResource(R.drawable.bg_chat_receiver)
                    messageText.setTextColor(getColor(R.color.text_primary))
                }
            }
        }

        override fun getItemCount() = messages.size
    }
}
