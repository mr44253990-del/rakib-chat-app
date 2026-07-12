package com.ebchat.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.ebchat.chat.ChatActivity
import com.ebchat.chat.GroupChatActivity
import com.ebchat.databinding.FragmentChatListBinding
import com.ebchat.databinding.ItemChatRoomBinding
import com.ebchat.model.ChatRoom
import com.ebchat.model.User
import com.ebchat.utils.Constants
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.TimeUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.*

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!

    private val chatRooms = mutableListOf<ChatRoom>()
    private val userCache = mutableMapOf<String, User>()
    private lateinit var adapter: ChatListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ChatListAdapter(chatRooms) { room ->
            openChat(room)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            loadChatRooms()
        }

        loadChatRooms()
    }

    private fun loadChatRooms() {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        FirebaseUtils.chatRoomsRef()
            .orderByChild("participants/$currentUserId")
            .equalTo(true)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    chatRooms.clear()
                    for (roomSnapshot in snapshot.children) {
                        val room = ChatRoom.fromMap(
                            roomSnapshot.value as Map<String, Any?>,
                            roomSnapshot.key ?: ""
                        )
                        chatRooms.add(room)
                    }
                    // Sort by last message time
                    chatRooms.sortByDescending { it.lastMessageTime }

                    if (chatRooms.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        loadUserInfo()
                    }
                    binding.swipeRefresh.isRefreshing = false
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.swipeRefresh.isRefreshing = false
                }
            })
    }

    private fun loadUserInfo() {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        chatRooms.forEach { room ->
            if (room.isGroup) {
                // Load group info
                FirebaseUtils.groupsRef().child(room.groupId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            adapter.notifyDataSetChanged()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            } else {
                val otherUserId = room.getOtherUserId(currentUserId) ?: return
                if (!userCache.containsKey(otherUserId)) {
                    FirebaseUtils.usersRef().child(otherUserId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val user = User.fromMap(
                                    snapshot.value as Map<String, Any?>,
                                    otherUserId
                                )
                                userCache[otherUserId] = user
                                adapter.notifyDataSetChanged()
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
            }
        }
    }

    private fun openChat(room: ChatRoom) {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        if (room.isGroup) {
            val intent = Intent(requireContext(), GroupChatActivity::class.java).apply {
                putExtra(Constants.EXTRA_GROUP_ID, room.groupId)
                putExtra(Constants.EXTRA_CHAT_ROOM_ID, room.roomId)
            }
            startActivity(intent)
        } else {
            val otherUserId = room.getOtherUserId(currentUserId) ?: return
            val user = userCache[otherUserId] ?: return

            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra(Constants.EXTRA_USER_ID, otherUserId)
                putExtra(Constants.EXTRA_USER_NAME, user.name)
                putExtra(Constants.EXTRA_USER_IMAGE, user.profileImage)
                putExtra(Constants.EXTRA_CHAT_ROOM_ID, room.roomId)
            }
            startActivity(intent)
        }
        activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class ChatListAdapter(
        private val rooms: List<ChatRoom>,
        private val onClick: (ChatRoom) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

        inner class ChatViewHolder(val binding: ItemChatRoomBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val binding = ItemChatRoomBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ChatViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val room = rooms[position]
            val currentUserId = FirebaseUtils.currentUserId ?: return

            holder.binding.apply {
                if (room.isGroup) {
                    // Group chat display
                    FirebaseUtils.groupsRef().child(room.groupId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val name = snapshot.child("name").value as? String ?: "Group"
                                val image = snapshot.child("image").value as? String ?: ""
                                userName.text = name
                                Glide.with(root).load(image)
                                    .placeholder(com.ebchat.R.drawable.ic_groups)
                                    .circleCrop().into(userImage)
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                } else {
                    val otherUserId = room.getOtherUserId(currentUserId) ?: return
                    val user = userCache[otherUserId]
                    userName.text = user?.name ?: "Unknown"
                    statusText.text = if (user?.online == true) "online" else
                        TimeUtils.formatLastSeen(user?.lastSeen ?: 0)

                    if (user?.profileImage?.isNotEmpty() == true) {
                        Glide.with(root).load(user.profileImage)
                            .circleCrop().into(userImage)
                    }

                    // Online indicator
                    onlineIndicator.visibility = if (user?.online == true)
                        View.VISIBLE else View.GONE
                }

                lastMessage.text = room.lastMessage
                timeText.text = TimeUtils.formatDate(room.lastMessageTime)

                // Unread count
                val unread = room.unreadCount[currentUserId] ?: 0
                if (unread > 0) {
                    unreadBadge.visibility = View.VISIBLE
                    unreadBadge.text = unread.toString()
                } else {
                    unreadBadge.visibility = View.GONE
                }

                root.setOnClickListener { onClick(room) }
            }
        }

        override fun getItemCount() = rooms.size
    }
}
