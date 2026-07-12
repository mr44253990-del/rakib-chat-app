package com.ebchat.search

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.ebchat.chat.ChatActivity
import com.ebchat.databinding.ActivitySearchBinding
import com.ebchat.databinding.ItemSearchResultBinding
import com.ebchat.model.User
import com.ebchat.utils.Constants
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.TimeUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val allUsers = mutableListOf<User>()
    private val filteredUsers = mutableListOf<User>()
    private lateinit var adapter: SearchResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        adapter = SearchResultsAdapter(filteredUsers) { user ->
            openChat(user)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.searchEdit.doAfterTextChanged { text ->
            filterUsers(text?.toString() ?: "")
        }

        loadAllUsers()
    }

    private fun loadAllUsers() {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        FirebaseUtils.usersRef()
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    allUsers.clear()
                    for (userSnapshot in snapshot.children) {
                        val user = User.fromMap(
                            userSnapshot.value as Map<String, Any?>,
                            userSnapshot.key ?: ""
                        )
                        if (user.uid != currentUserId && user.searchable) {
                            allUsers.add(user)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SearchActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun filterUsers(query: String) {
        filteredUsers.clear()
        if (query.length >= 2) {
            val lowerQuery = query.lowercase()
            filteredUsers.addAll(allUsers.filter { user ->
                user.name.lowercase().contains(lowerQuery) ||
                        user.userId.lowercase().contains(lowerQuery) ||
                        user.email.lowercase().contains(lowerQuery)
            })
        }

        if (filteredUsers.isEmpty() && query.length >= 2) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }

        adapter.notifyDataSetChanged()
    }

    private fun openChat(user: User) {
        val roomId = FirebaseUtils.getChatRoomId(
            FirebaseUtils.currentUserId ?: "", user.uid
        )
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(Constants.EXTRA_USER_ID, user.uid)
            putExtra(Constants.EXTRA_USER_NAME, user.name)
            putExtra(Constants.EXTRA_USER_IMAGE, user.profileImage)
            putExtra(Constants.EXTRA_CHAT_ROOM_ID, roomId)
        }
        startActivity(intent)
        finish()
    }

    inner class SearchResultsAdapter(
        private val users: List<User>,
        private val onClick: (User) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemSearchResultBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSearchResultBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]
            holder.binding.apply {
                userName.text = user.name
                userId.text = "@${user.userId}"
                userStatus.text = if (user.online) "online" else TimeUtils.formatLastSeen(user.lastSeen)
                onlineIndicator.visibility = if (user.online) View.VISIBLE else View.GONE

                if (user.profileImage.isNotEmpty()) {
                    Glide.with(root).load(user.profileImage).circleCrop().into(userImage)
                }

                root.setOnClickListener { onClick(user) }
            }
        }

        override fun getItemCount() = users.size
    }
}
