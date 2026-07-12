package com.ebchat.home

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.ebchat.auth.LoginActivity
import com.ebchat.databinding.FragmentSettingsBinding
import com.ebchat.profile.ProfileActivity
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.PrefsManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val prefs = PrefsManager.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserProfile()
        setupSwitches()
        setupClickListeners()
    }

    private fun loadUserProfile() {
        val uid = FirebaseUtils.currentUserId ?: return

        FirebaseUtils.usersRef().child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.child("name").value as? String ?: "User"
                    val email = snapshot.child("email").value as? String ?: ""
                    val image = snapshot.child("profileImage").value as? String ?: ""
                    val status = snapshot.child("status").value as? String ?: ""

                    binding.userName.text = name
                    binding.userEmail.text = email
                    binding.userStatus.text = status

                    if (image.isNotEmpty()) {
                        Glide.with(this@SettingsFragment)
                            .load(image)
                            .circleCrop()
                            .into(binding.profileImage)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupSwitches() {
        binding.notificationsSwitch.isChecked = prefs.isNotificationsEnabled
        binding.soundSwitch.isChecked = prefs.isSoundEnabled
        binding.vibrationSwitch.isChecked = prefs.isVibrationEnabled
        binding.onlineStatusSwitch.isChecked = prefs.showOnlineStatus
        binding.readReceiptsSwitch.isChecked = prefs.readReceipts

        binding.notificationsSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.isNotificationsEnabled = checked
        }
        binding.soundSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.isSoundEnabled = checked
        }
        binding.vibrationSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.isVibrationEnabled = checked
        }
        binding.onlineStatusSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.showOnlineStatus = checked
            // Update Firebase
            FirebaseUtils.usersRef().child(FirebaseUtils.currentUserId ?: "")
                .child("searchable").setValue(checked)
        }
        binding.readReceiptsSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.readReceipts = checked
        }
    }

    private fun setupClickListeners() {
        binding.editProfileCard.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        binding.changePasswordCard.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.blockedUsersCard.setOnClickListener {
            Toast.makeText(requireContext(), "Blocked Users - Coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.dataUsageCard.setOnClickListener {
            Toast.makeText(requireContext(), "Data Usage - Coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.helpCard.setOnClickListener {
            showHelpDialog()
        }

        binding.aboutCard.setOnClickListener {
            showAboutDialog()
        }

        binding.logoutButton.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showChangePasswordDialog() {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "New Password"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Change Password")
            .setView(editText)
            .setPositiveButton("Change") { _, _ ->
                val newPassword = editText.text.toString()
                if (newPassword.length >= 6) {
                    lifecycleScope.launch {
                        try {
                            FirebaseUtils.auth.currentUser?.updatePassword(newPassword)?.await()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Password updated!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Help & Support")
            .setMessage("For support, contact us at:\nsupport@ebchat.com\n\nFAQ:\n\nQ: How do I change my profile picture?\nA: Go to Settings > Edit Profile\n\nQ: How do I create a group?\nA: Go to Groups tab and tap the + button\n\nQ: How long do stories last?\nA: Stories automatically expire after 12 hours")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("About EB Chat")
            .setMessage("EB Chat v1.0.0\n\nA modern messaging platform designed for seamless communication.\n\nDeveloper: Rakibul\n\nFeatures:\n- Real-time messaging\n- Voice messages\n- Stories (12h expiry)\n- Group chats\n- End-to-end encryption ready")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                lifecycleScope.launch {
                    FirebaseUtils.signOut()
                    prefs.clear()
                    withContext(Dispatchers.Main) {
                        val intent = Intent(requireContext(), LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
