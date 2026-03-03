package com.sun.alasbrowser.utils

import android.content.Context
import android.webkit.JavascriptInterface
import android.util.Log
import com.sun.alasbrowser.data.BrowserDatabase
import com.sun.alasbrowser.data.CredentialEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri

class AutofillManager(private val context: Context) {
    private val database = BrowserDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Callback to show UI dialog
    var onCredentialsDetected: ((url: String, username: String, password: String) -> Unit)? = null
    
    // Callback for text extraction (summarization)
    var onPageTextExtracted: ((text: String) -> Unit)? = null

    @JavascriptInterface
    fun onFormSubmitted(url: String, formDataJson: String) {
        Log.d("AutofillManager", "Form submitted on $url: $formDataJson")
        
        try {
            // Parse JSON manually or use regex to avoid dependency overhead if possible, 
            // but for robustness we should use standard JSON parsing if available.
            // Since we are adding logic, we'll do manual parsing for simple JSON object
            
            // Expected format: {"username": "...", "password": "..."}
            val username = extractJsonValue(formDataJson, "username")
            val password = extractJsonValue(formDataJson, "password")
            
            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                // Check if we already have this credential
                scope.launch {
                    val domain = getDomain(url)
                    val existing = database.credentialDao().getCredential(domain, username)
                    
                    if (existing == null) {
                        withContext(Dispatchers.Main) {
                            onCredentialsDetected?.invoke(url, username, password)
                        }
                    } else {
                        // Optional: Update password if changed
                        val currentDecrypted = SecurityUtils.decrypt(existing.passwordEncrypted)
                        if (currentDecrypted != password) {
                             withContext(Dispatchers.Main) {
                                onCredentialsDetected?.invoke(url, username, password)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AutofillManager", "Error parsing form data", e)
        }
    }

    @JavascriptInterface
    fun onTextExtracted(text: String) {
        scope.launch {
            withContext(Dispatchers.Main) {
                onPageTextExtracted?.invoke(text)
            }
        }
    }
    
    fun saveCredential(url: String, username: String, password: String) {
        scope.launch {
            val encryptedPassword = SecurityUtils.encrypt(password)
            if (encryptedPassword != null) {
                val credential = CredentialEntity(
                    url = url,
                    username = username,
                    passwordEncrypted = encryptedPassword
                )
                database.credentialDao().insert(credential)
            }
        }
    }
    
    suspend fun getCredentialsForUrl(url: String): List<Pair<String, String>> {
        val domain = getDomain(url)
        return database.credentialDao().getCredentialsForDomain(domain).mapNotNull { entity ->
            val password = SecurityUtils.decrypt(entity.passwordEncrypted)
            if (password != null) {
                entity.username to password
            } else {
                null
            }
        }
    }
    
    fun checkAndAutofill(webView: android.webkit.WebView, url: String) {
        scope.launch {
            val creds = getCredentialsForUrl(url)
            if (creds.isNotEmpty()) {
                val script = ScriptManager.getAutofillScript(creds)
                if (script.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        try {
                            webView.evaluateJavascript(script, null)
                        } catch (e: Exception) {
                            Log.e("AutofillManager", "Failed to inject autofill script", e)
                        }
                    }
                }
            }
        }
    }

    private fun getDomain(url: String): String {
        return try {
            Uri.parse(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }
    
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }
}
