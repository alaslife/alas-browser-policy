package com.sun.alasbrowser.web

import android.net.Uri
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object LoginCompatibilityEngine {
    private const val TAG = "LoginCompat"
    
    private val loginTabs = ConcurrentHashMap.newKeySet<String>()
    private val lastLoginHitMs = ConcurrentHashMap<String, Long>()
    private const val LOGIN_MODE_HYSTERESIS_MS = 30_000L

    private val knownLoginHosts = setOf(
        "accounts.google.com",
        "accounts.google.co.in",
        "accounts.google.co.uk",
        "accounts.google.co.jp",
        "accounts.youtube.com",
        "login.microsoftonline.com",
        "login.live.com",
        "login.yahoo.com",
        "id.realme.com",
        "c.realme.com",
        "id.oppo.com",
        "account.oppo.com",
        "account.oneplus.com",
        "account.oneplus.in",
        "appleid.apple.com",
        "www.facebook.com",
        "m.facebook.com",
        "auth.opera.com",
        "discord.com",
        "github.com",
        "signin.aws.amazon.com",
        "sso.godaddy.com",
        "account.samsung.com",
        "id.mi.com",
        "account.xiaomi.com",
        "passport.bilibili.com",
        "account.proton.me",
        "login.yahoo.co.jp"
    )

    private val loginPathPatterns = listOf(
        "/login", "/signin", "/sign-in", "/sign_in",
        "/oauth", "/oauth2", "/authorize", "/auth",
        "/sso", "/saml", "/accounts/login", "/accounts/signin",
        "/openid", "/connect/authorize",
        "/gsi/", "/o/oauth2/"
    )

    private val loginHostPatterns = setOf(
        "accounts.google.", "signin.google.", "oauth.google.",
        "login.yahoo.", "login.live.", "login.microsoftonline.",
        "auth0.com", "okta.com", "cognito-idp.",
        "id.realme.", "id.oppo.", "account.oppo.", "account.oneplus.",
        "account.samsung.", "id.mi.", "account.xiaomi."
    )

    fun isLoginUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = (uri.host ?: "").lowercase()
        val path = (uri.path ?: "").lowercase()

        if (host in knownLoginHosts) return true
        if (loginHostPatterns.any { host.startsWith(it) || host.contains(it) }) return true
        if (loginPathPatterns.any { path.contains(it) }) return true

        return false
    }

    fun onNavigation(tabId: String, url: String): Boolean {
        val isLogin = isLoginUrl(url)
        if (isLogin) {
            val wasActive = isAnyLoginActive()
            loginTabs.add(tabId)
            lastLoginHitMs[tabId] = System.currentTimeMillis()
            Log.d(TAG, "🔓 Login mode ACTIVE for tab $tabId: $url")
            return !wasActive
        } else {
            val last = lastLoginHitMs[tabId] ?: 0L
            if (System.currentTimeMillis() - last > LOGIN_MODE_HYSTERESIS_MS) {
                val wasActive = isAnyLoginActive()
                loginTabs.remove(tabId)
                lastLoginHitMs.remove(tabId)
                if (wasActive && !isAnyLoginActive()) {
                    Log.d(TAG, "🔒 Login mode DEACTIVATED (no more login tabs)")
                    return true
                }
            }
        }
        return false
    }

    fun isTabInLoginMode(tabId: String): Boolean = tabId in loginTabs

    fun isAnyLoginActive(): Boolean = loginTabs.isNotEmpty()

    fun clearTab(tabId: String) {
        loginTabs.remove(tabId)
        lastLoginHitMs.remove(tabId)
    }
}
