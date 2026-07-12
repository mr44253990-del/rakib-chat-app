package com.ebchat.profile

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.ebchat.R
import com.ebchat.databinding.ActivityProfileBinding
import com.ebchat.model.User
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.PrefsManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val prefs = PrefsManager.getInstance()
    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            Glide.with(this).load(it).circleCrop().into(binding.profileImage)
            uploadProfileImage(it)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            binding.profileImage.setImageBitmap(bitmap)
            // Convert to URI and upload
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        loadUserProfile()
        setupClickListeners()
    }

    private fun loadUserProfile() {
        val uid = FirebaseUtils.currentUserId ?: return

        FirebaseUtils.usersRef().child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = User.fromMap(
                        snapshot.value as Map<String, Any?>,
                        snapshot.key ?: ""
                    )

                    binding.nameEdit.setText(user.name)
                    binding.emailEdit.setText(user.email)
                    binding.phoneEdit.setText(user.phone)
                    binding.bioEdit.setText(user.bio)
                    binding.userIdText.text = "@${user.userId}"

                    if (user.profileImage.isNotEmpty()) {
                        Glide.with(this@ProfileActivity)
                            .load(user.profileImage)
                            .circleCrop()
                            .placeholder(R.drawable.ic_person)
                            .into(binding.profileImage)
                    }

                    prefs.putString("user_name", user.name)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupClickListeners() {
        binding.changePhotoButton.setOnClickListener {
            showImagePickerOptions()
        }

        binding.profileImage.setOnClickListener {
            showImagePickerOptions()
        }

        binding.recordVoiceButton.setOnClickListener {
            Toast.makeText(this, "Voice note - Coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.saveButton.setOnClickListener {
            saveProfile()
        }
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cameraLauncher.launch(null)
                    1 -> imagePickerLauncher.launch("image/*")
                    2 -> removeProfilePhoto()
                }
            }
            .show()
    }

    private fun uploadProfileImage(uri: Uri) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Uploading...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val uid = FirebaseUtils.currentUserId ?: return@launch
                val ref = FirebaseUtils.profileImagesRef().child("$uid.jpg")
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                FirebaseUtils.usersRef().child(uid)
                    .child("profileImage")
                    .setValue(downloadUrl)
                    .await()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@ProfileActivity, "Photo updated!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@ProfileActivity, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun removeProfilePhoto() {
        lifecycleScope.launch {
            try {
                val uid = FirebaseUtils.currentUserId ?: return@launch
                FirebaseUtils.usersRef().child(uid)
                    .child("profileImage")
                    .setValue("")
                    .await()
                withContext(Dispatchers.Main) {
                    binding.profileImage.setImageResource(R.drawable.ic_person)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveProfile() {
        val name = binding.nameEdit.text.toString().trim()
        val phone = binding.phoneEdit.text.toString().trim()
        val bio = binding.bioEdit.text.toString().trim()

        if (name.isEmpty()) {
            binding.nameEdit.error = "Name is required"
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Saving...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val uid = FirebaseUtils.currentUserId ?: return@launch
                val updates = mapOf(
                    "name" to name,
                    "phone" to phone,
                    "bio" to bio
                )
                FirebaseUtils.usersRef().child(uid).updateChildren(updates).await()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@ProfileActivity, "Profile saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@ProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
