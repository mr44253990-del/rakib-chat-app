package com.ebchat.auth

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ebchat.R
import com.ebchat.databinding.ActivitySignupBinding
import com.ebchat.model.User
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.PrefsManager
import com.ebchat.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val prefs = PrefsManager.getInstance()
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        animateViews()

        binding.signupButton.setOnClickListener {
            attemptSignup()
        }

        binding.loginLink.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // Real-time password strength indicator
        binding.passwordEdit.setOnFocusChangeListener { _, hasFocus ->
            binding.passwordStrength.visibility = if (hasFocus) View.VISIBLE else View.GONE
        }
    }

    private fun animateViews() {
        val views = listOf(
            binding.logoContainer,
            binding.createAccountText,
            binding.nameInput,
            binding.emailInput,
            binding.passwordInput,
            binding.confirmPasswordInput,
            binding.signupButton,
            binding.loginLink
        )

        views.forEachIndexed { index, view ->
            view.apply {
                alpha = 0f
                translationY = 30f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setStartDelay((index * 80).toLong())
                    .start()
            }
        }
    }

    private fun attemptSignup() {
        val name = binding.nameEdit.text.toString().trim()
        val email = binding.emailEdit.text.toString().trim()
        val password = binding.passwordEdit.text.toString()
        val confirmPassword = binding.confirmPasswordEdit.text.toString()

        // Validation
        if (!ValidationUtils.isValidName(name)) {
            binding.nameInput.error = getString(R.string.error_name_required)
            return
        }
        binding.nameInput.error = null

        if (!ValidationUtils.isValidEmail(email)) {
            binding.emailInput.error = getString(R.string.error_email_invalid)
            return
        }
        binding.emailInput.error = null

        if (!ValidationUtils.isValidPassword(password)) {
            binding.passwordInput.error = getString(R.string.error_password_short)
            return
        }
        binding.passwordInput.error = null

        if (!ValidationUtils.passwordsMatch(password, confirmPassword)) {
            binding.confirmPasswordInput.error = getString(R.string.error_password_match)
            return
        }
        binding.confirmPasswordInput.error = null

        showProgress("Creating your account...")

        lifecycleScope.launch {
            try {
                // Create Firebase Auth user
                val result = FirebaseUtils.auth.createUserWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid ?: throw Exception("Failed to get user ID")

                // Create user in Realtime Database
                val user = User(
                    uid = uid,
                    name = name,
                    email = email,
                    online = true,
                    userId = generateUserId(name)
                )

                FirebaseUtils.usersRef().child(uid).setValue(user.toMap()).await()

                // Send email verification
                result.user?.sendEmailVerification()?.await()

                prefs.userId = uid

                withContext(Dispatchers.Main) {
                    hideProgress()
                    Toast.makeText(
                        this@SignupActivity,
                        "Account created! Welcome, $name!",
                        Toast.LENGTH_LONG
                    ).show()
                    navigateToMain()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    showError("Signup failed: ${e.message}")
                }
            }
        }
    }

    private fun generateUserId(name: String): String {
        val base = name.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
        val random = (1000..9999).random()
        return "${base}_$random"
    }

    private fun updatePasswordStrength(password: String) {
        val strength = ValidationUtils.getPasswordStrength(password)
        binding.passwordStrength.apply {
            when (strength) {
                ValidationUtils.PasswordStrength.WEAK -> {
                    text = "Weak"
                    setTextColor(getColor(android.R.color.holo_red_light))
                }
                ValidationUtils.PasswordStrength.MEDIUM -> {
                    text = "Medium"
                    setTextColor(getColor(android.R.color.holo_orange_light))
                }
                ValidationUtils.PasswordStrength.STRONG -> {
                    text = "Strong"
                    setTextColor(getColor(android.R.color.holo_green_light))
                }
            }
        }
    }

    private fun showProgress(message: String) {
        progressDialog = ProgressDialog(this).apply {
            setMessage(message)
            setCancelable(false)
            show()
        }
    }

    private fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, PermissionActivity::class.java)
        startActivity(intent)
        finishAffinity()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
