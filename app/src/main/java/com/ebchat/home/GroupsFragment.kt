package com.ebchat.home

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.ebchat.chat.GroupChatActivity
import com.ebchat.databinding.FragmentGroupsBinding
import com.ebchat.databinding.ItemGroupBinding
import com.ebchat.model.Group
import com.ebchat.model.GroupMember
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

class GroupsFragment : Fragment() {

    private var _binding: FragmentGroupsBinding? = null
    private val binding get() = _binding!!

    private val groups = mutableListOf<Group>()
    private lateinit var adapter: GroupsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = GroupsAdapter(groups) { group ->
            openGroupChat(group)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.createGroupFab.setOnClickListener {
            showCreateGroupDialog()
        }

        loadGroups()
    }

    private fun loadGroups() {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        FirebaseUtils.groupsRef()
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    groups.clear()
                    for (groupSnapshot in snapshot.children) {
                        val group = Group.fromMap(
                            groupSnapshot.value as Map<String, Any?>,
                            groupSnapshot.key ?: ""
                        )
                        // Only show groups where user is a member
                        if (group.members.containsKey(currentUserId)) {
                            groups.add(group)
                        }
                    }
                    groups.sortByDescending { it.lastMessageTime }

                    if (groups.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                    }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showCreateGroupDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(android.R.layout.simple_list_item_1, null)
        val editText = EditText(requireContext()).apply {
            hint = "Group Name"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create New Group")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createGroup(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createGroup(name: String) {
        val currentUserId = FirebaseUtils.currentUserId ?: return
        val groupId = FirebaseUtils.generateGroupId()

        lifecycleScope.launch {
            try {
                // Get current user info
                val userSnapshot = FirebaseUtils.usersRef().child(currentUserId).get().await()
                val userName = userSnapshot.child("name").value as? String ?: "User"
                val userImage = userSnapshot.child("profileImage").value as? String ?: ""

                val group = Group(
                    groupId = groupId,
                    name = name,
                    createdBy = currentUserId,
                    members = mapOf(
                        currentUserId to GroupMember(
                            userId = currentUserId,
                            name = userName,
                            image = userImage,
                            role = com.ebchat.model.MemberRole.ADMIN.name
                        )
                    ),
                    admins = listOf(currentUserId)
                )

                FirebaseUtils.groupsRef().child(groupId).setValue(group.toMap()).await()

                // Create chat room for group
                val roomId = FirebaseUtils.generateRoomId()
                val roomMap = mapOf(
                    "roomId" to roomId,
                    "participants" to mapOf(currentUserId to true),
                    "isGroup" to true,
                    "groupId" to groupId,
                    "lastMessage" to "Group created",
                    "lastMessageTime" to System.currentTimeMillis()
                )
                FirebaseUtils.chatRoomsRef().child(roomId).setValue(roomMap).await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Group created!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openGroupChat(group: Group) {
        // Find the chat room for this group
        FirebaseUtils.chatRoomsRef()
            .orderByChild("groupId")
            .equalTo(group.groupId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val roomSnapshot = snapshot.children.firstOrNull()
                    val roomId = roomSnapshot?.key ?: ""

                    val intent = Intent(requireContext(), GroupChatActivity::class.java).apply {
                        putExtra(Constants.EXTRA_GROUP_ID, group.groupId)
                        putExtra(Constants.EXTRA_GROUP_NAME, group.name)
                        putExtra(Constants.EXTRA_CHAT_ROOM_ID, roomId)
                    }
                    startActivity(intent)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class GroupsAdapter(
        private val groups: List<Group>,
        private val onClick: (Group) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

        inner class GroupViewHolder(val binding: ItemGroupBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
            val binding = ItemGroupBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return GroupViewHolder(binding)
        }

        override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
            val group = groups[position]
            holder.binding.apply {
                groupName.text = group.name
                memberCount.text = "${group.members.size} members"
                lastMessage.text = group.lastMessage
                timeText.text = TimeUtils.formatDate(group.lastMessageTime)

                if (group.image.isNotEmpty()) {
                    Glide.with(root).load(group.image).circleCrop().into(groupImage)
                }

                root.setOnClickListener { onClick(group) }
            }
        }

        override fun getItemCount() = groups.size
    }
}
