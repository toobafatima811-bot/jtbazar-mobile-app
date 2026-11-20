package com.example.util

/**
 * Shared JTBazar password policy for registration, reset and password changes.
 * Minimum 8 characters with uppercase, lowercase, number and a non-space symbol.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 8

    fun isStrong(password: String): Boolean =
        password.length >= MIN_LENGTH &&
            password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { it.isDigit() } &&
            password.any { !it.isLetterOrDigit() && !it.isWhitespace() }

    fun message(): String =
        "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number and a special symbol."
}
