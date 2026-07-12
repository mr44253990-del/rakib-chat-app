package com.ebchat.utils

import android.util.Patterns

object ValidationUtils {

    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    fun isValidName(name: String): Boolean {
        return name.trim().length >= 2
    }

    fun isValidPhone(phone: String): Boolean {
        return phone.isBlank() || (phone.length >= 10 && phone.all { it.isDigit() || it == '+' })
    }

    fun isValidUserId(userId: String): Boolean {
        return userId.isNotBlank() && userId.length >= 3
    }

    fun passwordsMatch(password: String, confirm: String): Boolean {
        return password == confirm
    }

    fun getPasswordStrength(password: String): PasswordStrength {
        return when {
            password.length < 6 -> PasswordStrength.WEAK
            password.length < 10 -> PasswordStrength.MEDIUM
            password.any { it.isUpperCase() } &&
                    password.any { it.isLowerCase() } &&
                    password.any { it.isDigit() } -> PasswordStrength.STRONG
            else -> PasswordStrength.MEDIUM
        }
    }
}

enum class PasswordStrength {
    WEAK, MEDIUM, STRONG
}
