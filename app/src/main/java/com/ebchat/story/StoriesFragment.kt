package com.ebchat.story

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.ebchat.databinding.FragmentStoriesBinding
import com.ebchat.databinding.ItemStoryBinding
import com.ebchat.model.Story
import com.ebchat.model.User
import com.ebchat.utils.Constants
import com.ebchat.utils.FirebaseUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class StoriesFragment : Fragment() {

    private var _binding: FragmentStoriesBinding? = null
    private val binding get() = _binding!!

    private val stories = mutableListOf<Story>()
    private lateinit var adapter: StoriesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // My Story button
        binding.myStoryCard.setOnClickListener {
            showAddStoryOptions()
        }

        // Recycler setup
        adapter = StoriesAdapter(stories) { story ->
            openStoryViewer(story)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.HORIZONTAL, false
        )
        binding.recyclerView.adapter = adapter

        // Also load vertical list
        binding.storiesList.layoutManager = LinearLayoutManager(requireContext())
        binding.storiesList.adapter = adapter

        loadStories()
        loadMyStory()
    }

    private fun loadMyStory() {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        FirebaseUtils.storiesRef()
            .orderByChild("userId")
            .equalTo(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var hasActiveStory = false
                    for (storySnapshot in snapshot.children) {
                        val story = Story.fromMap(
                            storySnapshot.value as Map<String, Any?>,
                            storySnapshot.key ?: ""
                        )
                        if (!story.isExpired() && !story.deleted) {
                            hasActiveStory = true
                            binding.myStoryStatus.text = "Tap to view your story"
                        }
                    }
                    if (!hasActiveStory) {
                        binding.myStoryStatus.text = "Tap to add story"
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadStories() {
        FirebaseUtils.storiesRef()
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    stories.clear()
                    val currentUserId = FirebaseUtils.currentUserId ?: ""

                    for (storySnapshot in snapshot.children) {
                        val story = Story.fromMap(
                            storySnapshot.value as Map<String, Any?>,
                            storySnapshot.key ?: ""
                        )
                        // Only show non-expired, non-deleted stories from other users
                        if (!story.isExpired() && !story.deleted && story.userId != currentUserId) {
                            stories.add(story)
                        }
                    }
                    stories.sortByDescending { it.timestamp }

                    if (stories.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.storiesList.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.storiesList.visibility = View.VISIBLE
                    }

                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun showAddStoryOptions() {
        val options = arrayOf("Photo", "Camera", "Text Story")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Story")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageForStory()
                    1 -> Toast.makeText(requireContext(), "Camera - Coming soon", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(requireContext(), "Text Story - Coming soon", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun pickImageForStory() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, 2001)
    }

    private fun openStoryViewer(story: Story) {
        val intent = Intent(requireContext(), StoryViewerActivity::class.java).apply {
            putExtra("story_id", story.storyId)
            putExtra("story_url", story.mediaUrl)
            putExtra("story_user", story.userName)
            putExtra("story_user_image", story.userImage)
            putExtra("story_caption", story.caption)
        }
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == android.app.Activity.RESULT_OK) {
            data?.data?.let { uri ->
                uploadStoryImage(uri)
            }
        }
    }

    private fun uploadStoryImage(uri: android.net.Uri) {
        val progressDialog = android.app.ProgressDialog(requireContext()).apply {
            setMessage("Uploading story...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val currentUserId = FirebaseUtils.currentUserId ?: return@launch
                val storyId = FirebaseUtils.generateStoryId()

                // Get user info
                val userSnapshot = FirebaseUtils.usersRef().child(currentUserId).get().await()
                val userName = userSnapshot.child("name").value as? String ?: "User"
                val userImage = userSnapshot.child("profileImage").value as? String ?: ""

                // Upload image to Firebase Storage
                val ref = FirebaseUtils.storyMediaRef()
                    .child("${currentUserId}_${System.currentTimeMillis()}.jpg")
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                // Create story
                val story = Story(
                    storyId = storyId,
                    userId = currentUserId,
                    userName = userName,
                    userImage = userImage,
                    mediaUrl = downloadUrl,
                    type = com.ebchat.model.StoryType.IMAGE.name,
                    expiresAt = System.currentTimeMillis() + Constants.STORY_EXPIRY_MS
                )

                FirebaseUtils.storiesRef().child(storyId).setValue(story.toMap()).await()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Story added!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class StoriesAdapter(
        private val stories: List<Story>,
        private val onClick: (Story) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<StoriesAdapter.StoryViewHolder>() {

        inner class StoryViewHolder(val binding: ItemStoryBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
            val binding = ItemStoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return StoryViewHolder(binding)
        }

        override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
            val story = stories[position]
            holder.binding.apply {
                userName.text = story.userName
                if (story.userImage.isNotEmpty()) {
                    Glide.with(root).load(story.userImage).circleCrop().into(userImage)
                }
                if (story.mediaUrl.isNotEmpty()) {
                    Glide.with(root).load(story.mediaUrl).into(storyPreview)
                }
                root.setOnClickListener { onClick(story) }
            }
        }

        override fun getItemCount() = stories.size
    }
}
