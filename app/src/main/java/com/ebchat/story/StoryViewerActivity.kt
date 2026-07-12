package com.ebchat.story

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.ebchat.databinding.ActivityStoryViewerBinding
import com.ebchat.utils.FirebaseUtils

class StoryViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoryViewerBinding
    private var storyId = ""
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoryViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storyId = intent.getStringExtra("story_id") ?: ""
        val storyUrl = intent.getStringExtra("story_url") ?: ""
        val storyUser = intent.getStringExtra("story_user") ?: ""
        val storyUserImage = intent.getStringExtra("story_user_image") ?: ""
        val storyCaption = intent.getStringExtra("story_caption") ?: ""

        binding.userName.text = storyUser
        if (storyUserImage.isNotEmpty()) {
            Glide.with(this).load(storyUserImage).circleCrop().into(binding.userImage)
        }
        Glide.with(this).load(storyUrl).into(binding.storyImage)
        binding.captionText.text = storyCaption
        binding.captionText.visibility = if (storyCaption.isNotEmpty()) View.VISIBLE else View.GONE

        // Progress bar animation (12 hours = auto delete, but viewer shows for 5 seconds)
        startProgressTimer()

        binding.closeButton.setOnClickListener { finish() }

        binding.deleteButton.setOnClickListener {
            showDeleteDialog()
        }

        binding.storyImage.setOnClickListener {
            finish()
        }

        // Mark as viewed
        markStoryAsViewed()
    }

    private fun startProgressTimer() {
        binding.progressBar.max = 5000
        binding.progressBar.progress = 0

        countdownTimer = object : CountDownTimer(5000, 50) {
            override fun onTick(millisUntilFinished: Long) {
                binding.progressBar.progress = (5000 - millisUntilFinished).toInt()
            }

            override fun onFinish() {
                finish()
            }
        }.start()
    }

    private fun markStoryAsViewed() {
        val currentUserId = FirebaseUtils.currentUserId ?: return
        FirebaseUtils.storiesRef().child(storyId)
            .child("viewers")
            .child(currentUserId)
            .setValue(true)
    }

    private fun showDeleteDialog() {
        val currentUserId = FirebaseUtils.currentUserId ?: return

        // Check if this is user's own story
        FirebaseUtils.storiesRef().child(storyId).get().addOnSuccessListener { snapshot ->
            val userId = snapshot.child("userId").value as? String ?: ""
            if (userId == currentUserId) {
                AlertDialog.Builder(this)
                    .setTitle("Delete Story")
                    .setMessage("Delete this story?")
                    .setPositiveButton("Delete") { _, _ ->
                        deleteStory()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(this, "You can only delete your own stories", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteStory() {
        FirebaseUtils.storiesRef().child(storyId)
            .child("deleted")
            .setValue(true)
            .addOnSuccessListener {
                Toast.makeText(this, "Story deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}
