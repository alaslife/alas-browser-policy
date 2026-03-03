package com.sun.alasbrowser.web

import android.net.Uri
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

data class AuthModeDecision(
    val active: Boolean,
    val reason: String = "",
    val relaxPopupBlocking: Boolean = false,
    val relaxRedirectBlocking: Boolean = false,
    val disableCosmeticJs: Boolean = false,
    val allowThirdPartyCookies: Boolean = false,
    val allowStoragerelay: Boolean = false,
)

object AuthCompatibilityEngine {
    private const val TAG = "AuthCompat"

    private val tabAuthState = ConcurrentHashMap<String, AuthTabState>()
    private const val AUTH_MODE_TIMEOUT_MS = 45_000L

    private data class AuthTabState(
        val reason: String,
        val enteredAt: Long = System.currentTimeMillis(),
        val url: String
    )

    private val oauthQueryParams = setOf(
        "client_id", "redirect_uri", "response_type", "scope",
        "code_challenge", "code_challenge_method", "state", "nonce",
        "id_token", "access_token", "grant_type", "code",
        "approval_prompt", "access_type", "login_hint",
        "prompt", "acr_values", "claims", "request_uri"
    )

    private val authPathPatterns = listOf(
        "/authorize", "/oauth2/authorize", "/oauth2/auth",
        "/o/oauth2/", "/connect/authorize", "/openid/connect",
        "/.well-known/openid-configuration",
        "/login", "/signin", "/sign-in", "/sign_in",
        "/oauth", "/oauth2", "/auth", "/sso", "/saml",
        "/accounts/login", "/accounts/signin",
        "/gsi/", "/v2/authorize", "/v1/authorize",
        "/api/oauth", "/api/auth", "/token",
        "/callback", "/oauth/callback", "/auth/callback",
        "/consent", "/permissions", "/grant"
    )

    private val authHostPatterns = setOf(
        "accounts.google.", "signin.google.", "oauth.google.",
        "login.yahoo.", "login.live.", "login.microsoftonline.",
        "auth0.com", "okta.com", "cognito-idp.",
        "id.realme.", "id.oppo.", "account.oppo.", "account.oneplus.",
        "account.samsung.", "id.mi.", "account.xiaomi.",
        "auth.opera.", "accounts.firefox.",
        "login.salesforce.", "login.oracle.",
        "sso.", "auth.", "identity.", "idp.",
        "passport.", "signin.", "login."
    )

    private val knownAuthHosts = setOf(
        "accounts.google.com", "accounts.google.co.in", "accounts.google.co.uk",
        "accounts.google.co.jp", "accounts.youtube.com",
        "login.microsoftonline.com", "login.live.com", "login.yahoo.com",
        "id.realme.com", "c.realme.com", "id.oppo.com", "account.oppo.com",
        "account.oneplus.com", "account.oneplus.in",
        "appleid.apple.com", "www.facebook.com", "m.facebook.com",
        "auth.opera.com", "discord.com", "github.com",
        "signin.aws.amazon.com", "sso.godaddy.com",
        "account.samsung.com", "id.mi.com", "account.xiaomi.com",
        "passport.bilibili.com", "account.proton.me",
        "login.yahoo.co.jp", "api.twitter.com", "twitter.com",
        "accounts.spotify.com", "accounts.nintendo.com",
        "steamcommunity.com", "store.steampowered.com",
        "login.skype.com", "login.windows.net",
        "myaccount.google.com", "consent.google.com",
        "oauth.reddit.com", "www.reddit.com",
        "www.paypal.com", "www.sandbox.paypal.com",
        "secure.avangate.com", "checkout.stripe.com",
        "connect.stripe.com"
    )

    fun evaluate(url: String): AuthModeDecision {
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return AuthModeDecision(active = false)
        }

        val uri = runCatching { Uri.parse(url) }.getOrNull()
            ?: return AuthModeDecision(active = false)
        val host = (uri.host ?: "").lowercase()
        val path = (uri.path ?: "").lowercase()

        if (host in knownAuthHosts) {
            return AuthModeDecision(
                active = true,
                reason = "known_auth_host:$host",
                relaxPopupBlocking = true,
                relaxRedirectBlocking = true,
                disableCosmeticJs = true,
                allowThirdPartyCookies = true,
                allowStoragerelay = true
            )
        }

        if (authHostPatterns.any { host.startsWith(it) || host.contains(it) }) {
            return AuthModeDecision(
                active = true,
                reason = "auth_host_pattern:$host",
                relaxPopupBlocking = true,
                relaxRedirectBlocking = true,
                disableCosmeticJs = true,
                allowThirdPartyCookies = true,
                allowStoragerelay = true
            )
        }

        val hasAuthPath = authPathPatterns.any { path.contains(it) }

        val queryParams = if (uri.isHierarchical) uri.queryParameterNames else emptySet()
        val oauthParamCount = queryParams.count { it in oauthQueryParams }
        val hasOAuthParams = oauthParamCount >= 2

        if (hasAuthPath && hasOAuthParams) {
            return AuthModeDecision(
                active = true,
                reason = "oauth_flow:path+params($oauthParamCount)",
                relaxPopupBlocking = true,
                relaxRedirectBlocking = true,
                disableCosmeticJs = true,
                allowThirdPartyCookies = true,
                allowStoragerelay = true
            )
        }

        if (queryParams.contains("client_id") && queryParams.contains("redirect_uri")) {
            return AuthModeDecision(
                active = true,
                reason = "oauth_params:client_id+redirect_uri",
                relaxPopupBlocking = true,
                relaxRedirectBlocking = true,
                disableCosmeticJs = true,
                allowThirdPartyCookies = true,
                allowStoragerelay = false
            )
        }

        if (hasAuthPath) {
            return AuthModeDecision(
                active = true,
                reason = "auth_path:$path",
                relaxPopupBlocking = false,
                relaxRedirectBlocking = true,
                disableCosmeticJs = true,
                allowThirdPartyCookies = true,
                allowStoragerelay = false
            )
        }

        if (LoginCompatibilityEngine.isLoginUrl(url)) {
            return AuthModeDecision(
                active = true,
                reason = "legacy_login_compat",
                relaxPopupBlocking = true,
                relaxRedirectBlocking = true,
                disableCosmeticJs = true,
                allowThirdPartyCookies = true,
                allowStoragerelay = true
            )
        }

        return AuthModeDecision(active = false)
    }

    fun onNavigation(tabId: String, url: String): Boolean {
        val decision = evaluate(url)

        if (decision.active) {
            val wasActive = isTabInAuthMode(tabId)
            tabAuthState[tabId] = AuthTabState(
                reason = decision.reason,
                url = url
            )
            Log.d(TAG, "🔓 Auth mode ACTIVE for tab $tabId: ${decision.reason}")

            LoginCompatibilityEngine.onNavigation(tabId, url)
            return !wasActive
        } else {
            val state = tabAuthState[tabId]
            if (state != null) {
                val elapsed = System.currentTimeMillis() - state.enteredAt
                if (elapsed > AUTH_MODE_TIMEOUT_MS) {
                    tabAuthState.remove(tabId)
                    LoginCompatibilityEngine.onNavigation(tabId, url)
                    Log.d(TAG, "🔒 Auth mode DEACTIVATED for tab $tabId (timeout)")
                    return true
                }
            }
            LoginCompatibilityEngine.onNavigation(tabId, url)
        }
        return false
    }

    fun isTabInAuthMode(tabId: String): Boolean {
        val state = tabAuthState[tabId] ?: return false
        val elapsed = System.currentTimeMillis() - state.enteredAt
        if (elapsed > AUTH_MODE_TIMEOUT_MS) {
            tabAuthState.remove(tabId)
            return false
        }
        return true
    }

    fun isAnyAuthActive(): Boolean = tabAuthState.any { (tabId, _) -> isTabInAuthMode(tabId) }

    fun getDecisionForTab(tabId: String): AuthModeDecision {
        if (!isTabInAuthMode(tabId)) return AuthModeDecision(active = false)
        val state = tabAuthState[tabId] ?: return AuthModeDecision(active = false)
        return evaluate(state.url)
    }

    fun clearTab(tabId: String) {
        tabAuthState.remove(tabId)
        LoginCompatibilityEngine.clearTab(tabId)
    }

    fun looksLikeOAuth(url: String): Boolean {
        val decision = evaluate(url)
        return decision.active && decision.reason.contains("oauth")
    }

    @Suppress("MaxLineLength")
    fun getStoragerelayShimScript(): String = """
        (function() {
            if (window.__alasStoragerelayPatch) return;
            window.__alasStoragerelayPatch = true;
            try {
                var loc = window.location;
                var origAssign = (typeof loc.assign === 'function') ? loc.assign.bind(loc) : null;
                var origReplace = (typeof loc.replace === 'function') ? loc.replace.bind(loc) : null;

                if (origAssign) {
                    try {
                        loc.assign = function(u) {
                            try { if (String(u).startsWith('storagerelay://')) return; } catch(e){}
                            return origAssign(u);
                        };
                    } catch(e) {}
                }

                if (origReplace) {
                    try {
                        loc.replace = function(u) {
                            try { if (String(u).startsWith('storagerelay://')) return; } catch(e){}
                            return origReplace(u);
                        };
                    } catch(e) {}
                }
            } catch(e) {}
        })();
    """.trimIndent()

    fun getAuthHeaders(previousUrl: String?, nextUrl: String): Map<String, String> {
        if (previousUrl == null) return emptyMap()
        if (!looksLikeOAuth(nextUrl)) return emptyMap()
        return mapOf("Referer" to previousUrl)
    }
}
