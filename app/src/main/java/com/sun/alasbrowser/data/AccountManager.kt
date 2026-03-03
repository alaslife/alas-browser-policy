package com.sun.alasbrowser.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

data class UserAccount(
    val email: String,
    val displayName: String,
    val avatarUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isGoogleAccount: Boolean = false,
    val googleIdToken: String? = null
)

class AccountManager private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
    private val credentialManager = CredentialManager.create(context)

    private var _isLoggedIn by mutableStateOf(loadIsLoggedIn())
    val isLoggedIn: Boolean
        get() = _isLoggedIn

    private var _currentUser by mutableStateOf(loadCurrentUser())
    val currentUser: UserAccount?
        get() = _currentUser

    private fun loadIsLoggedIn(): Boolean {
        return prefs.getBoolean("is_logged_in", false)
    }

    private fun loadCurrentUser(): UserAccount? {
        if (!prefs.getBoolean("is_logged_in", false)) return null
        val email = prefs.getString("email", null) ?: return null
        val displayName = prefs.getString("display_name", "") ?: ""
        val avatarUri = prefs.getString("avatar_uri", null)
        val createdAt = prefs.getLong("created_at", System.currentTimeMillis())
        val isGoogle = prefs.getBoolean("is_google_account", false)
        return UserAccount(email, displayName, avatarUri, createdAt, isGoogle)
    }

    fun buildGoogleSignInRequest(): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .build()

        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    fun handleGoogleSignInResult(result: GetCredentialResponse): Result<UserAccount> {
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val email = googleIdTokenCredential.id
            val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")
            val avatarUri = googleIdTokenCredential.profilePictureUri?.toString()
            val createdAt = System.currentTimeMillis()

            prefs.edit {
                putString("email", email)
                putString("display_name", displayName)
                putString("avatar_uri", avatarUri)
                putLong("created_at", createdAt)
                putBoolean("is_logged_in", true)
                putBoolean("is_google_account", true)
            }

            val account = UserAccount(email, displayName, avatarUri, createdAt, isGoogleAccount = true)
            _currentUser = account
            _isLoggedIn = true
            return Result.success(account)
        }
        return Result.failure(IllegalStateException("Invalid credential type"))
    }

    fun signInWithEmail(email: String, displayName: String): Result<UserAccount> {
        if (email.isBlank() || !email.contains("@")) {
            return Result.failure(IllegalArgumentException("Invalid email address"))
        }
        val name = displayName.ifBlank { email.substringBefore("@") }
        val createdAt = System.currentTimeMillis()

        prefs.edit {
            putString("email", email.trim())
            putString("display_name", name.trim())
            remove("avatar_uri")
            putLong("created_at", createdAt)
            putBoolean("is_logged_in", true)
            putBoolean("is_google_account", false)
        }

        val account = UserAccount(email.trim(), name.trim(), null, createdAt, isGoogleAccount = false)
        _currentUser = account
        _isLoggedIn = true
        return Result.success(account)
    }

    fun signOut() {
        prefs.edit {
            putBoolean("is_logged_in", false)
            remove("is_google_account")
        }
        _currentUser = null
        _isLoggedIn = false
    }

    fun updateDisplayName(displayName: String) {
        prefs.edit { putString("display_name", displayName) }
        _currentUser = _currentUser?.copy(displayName = displayName)
    }

    fun updateAvatarUri(uri: String?) {
        prefs.edit { putString("avatar_uri", uri) }
        _currentUser = _currentUser?.copy(avatarUri = uri)
    }

    companion object {
        private const val TAG = "AccountManager"

        // TODO: Replace with your actual web client ID from Google Cloud Console
        // Get it from: https://console.cloud.google.com/apis/credentials
        // Create OAuth 2.0 Client ID → Web application type
        const val WEB_CLIENT_ID = "820980529709-4jqbqjkr0ch3ujpl3ldgjdklqpic8533.apps.googleusercontent.com"

        @Volatile
        private var INSTANCE: AccountManager? = null

        fun getInstance(context: Context): AccountManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AccountManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
