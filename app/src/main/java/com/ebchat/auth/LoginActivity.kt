package com.ebchat.auth

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ebchat.R
import com.ebchat.databinding.ActivityLoginBinding
import com.ebchat.home.MainActivity
import com.ebchat.model.User
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.PrefsManager
import com.ebchat.utils.ValidationUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private val prefs = PrefsManager.getInstance()
    private var progressDialog: ProgressDialog? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                showError("Google sign in failed: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupUI()
    }

    private fun setupUI() {
        // Animate views
        animateViews()

        binding.loginButton.setOnClickListener {
            attemptLogin()
        }

        binding.googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }

        binding.signupLink.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.forgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun animateViews() {
        val views = listOf(
            binding.logoContainer,
            binding.welcomeText,
            binding.emailInput,
            binding.passwordInput,
            binding.loginButton,
            binding.orText,
            binding.googleSignInButton,
            binding.signupLink
        )

        views.forEachIndexed { index, view ->
            view.apply {
                alpha = 0f
                translationY = 30f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setStartDelay((index * 100).toLong())
                    .start()
            }
        }
    }

    private fun attemptLogin() {
        val email = binding.emailEdit.text.toString().trim()
        val password = binding.passwordEdit.text.toString().trim()

        // Validation
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

        showProgress("Logging in...")

        lifecycleScope.launch {
            try {
                FirebaseUtils.auth.signInWithEmailAndPassword(email, password).await()
                withContext(Dispatchers.Main) {
                    hideProgress()
                    prefs.userId = FirebaseUtils.currentUserId
                    navigateToMain()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    showError("Login failed: ${e.message}")
                }
            }
        }
    }

    private fun signInWithGoogle() {
        showProgress("Signing in with Google...")
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        lifecycleScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                val result = FirebaseUtils.auth.signInWithCredential(credential).await()

                // Create/update user in database
                result.user?.let { firebaseUser ->
                    val userRef = FirebaseUtils.usersRef().child(firebaseUser.uid)
                    val snapshot = userRef.get().await()

                    if (!snapshot.exists()) {
                        // New user - create profile
                        val user = User(
                            uid = firebaseUser.uid,
                            name = firebaseUser.displayName ?: "User",
                            email = firebaseUser.email ?: "",
                            profileImage = firebaseUser.photoUrl?.toString() ?: "",
                            online = true
                        )
                        userRef.setValue(user.toMap()).await()
                    } else {
                        // Update online status
                        userRef.updateChildren(
                            mapOf(
                                "online" to true,
                                "lastSeen" to System.currentTimeMillis()
                            )
                        ).await()
                    }

                    prefs.userId = firebaseUser.uid
                }

                withContext(Dispatchers.Main) {
                    hideProgress()
                    navigateToMain()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    showError("Google auth failed: ${e.message}")
                }
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val email = binding.emailEdit.text.toString().trim()
        if (!ValidationUtils.isValidEmail(email)) {
            binding.emailInput.error = "Enter your email first"
            return
        }
        binding.emailInput.error = null

        lifecycleScope.launch {
            try {
                FirebaseUtils.auth.sendPasswordResetEmail(email).await()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Password reset email sent!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Failed: ${e.message}")
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
        val intent = if (prefs.isFirstLaunch) {
            Intent(this, PermissionActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
