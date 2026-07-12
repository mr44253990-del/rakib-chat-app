package com.ebchat.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ebchat.R
import com.ebchat.chat.ChatListFragment
import com.ebchat.databinding.ActivityMainBinding
import com.ebchat.search.SearchActivity
import com.ebchat.story.StoriesFragment
import com.ebchat.utils.FirebaseUtils
import com.google.android.material.badge.BadgeDrawable
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var unreadBadge: BadgeDrawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupSearchButton()

        // Default fragment
        if (savedInstanceState == null) {
            loadFragment(ChatListFragment())
        }

        // Update online status
        updateOnlineStatus(true)
        listenForUnreadMessages()
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> ChatListFragment()
                R.id.nav_groups -> GroupsFragment()
                R.id.nav_stories -> StoriesFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> ChatListFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out
            )
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun setupSearchButton() {
        binding.searchButton.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun listenForUnreadMessages() {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        FirebaseUtils.chatRoomsRef()
            .orderByChild("participants/$currentUserId")
            .equalTo(true)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var totalUnread = 0
                    for (roomSnapshot in snapshot.children) {
                        val unreadMap = roomSnapshot.child("unreadCount").value as? Map<String, Long>
                        totalUnread += unreadMap?.get(currentUserId)?.toInt() ?: 0
                    }
                    updateBadge(totalUnread)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateBadge(count: Int) {
        if (count > 0) {
            unreadBadge = binding.bottomNav.getOrCreateBadge(R.id.nav_home)
            unreadBadge?.number = count
            unreadBadge?.isVisible = true
            unreadBadge?.backgroundColor = getColor(R.color.pink_primary)
        } else {
            binding.bottomNav.removeBadge(R.id.nav_home)
        }
    }

    override fun onResume() {
        super.onResume()
        updateOnlineStatus(true)
    }

    override fun onPause() {
        super.onPause()
        // Keep user online even when app is backgrounded
        // Only set offline when explicitly logging out
    }

    private fun updateOnlineStatus(online: Boolean) {
        val uid = FirebaseUtils.currentUserId ?: return
        FirebaseUtils.usersRef().child(uid).updateChildren(
            mapOf(
                "online" to online,
                "lastSeen" to System.currentTimeMillis()
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't set offline here - user should appear online
        // as long as they're connected to internet
    }
}
