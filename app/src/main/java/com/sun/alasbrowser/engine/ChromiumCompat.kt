package com.sun.alasbrowser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Single source of truth for Chromium compatibility.
 * 
 * Centralizes:
 *  - UA string generation (clean, no WebView markers)
 *  - Anti-detection JS (injected before any page script runs)
 *  - WebView settings that make it behave like real Chrome
 *  - Google allowlist for challenge/verification scripts
 */
object ChromiumCompat {

    /** Current Chrome version to mimic */
    private const val CHROME_VERSION = "137.0.7151.104"
    private const val CHROME_MAJOR = "137"

    // ═══════════════════════════════════════════════════════════════
    // USER AGENT
    // ═══════════════════════════════════════════════════════════════

    /** Desktop Chrome UA */
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_VERSION Safari/537.36"

    /** Build a clean mobile UA from the system default, stripping all WebView markers */
    fun cleanMobileUA(context: Context): String {
        return WebSettings.getDefaultUserAgent(context)
            .replace("; wv", "")                       // primary marker
            .replace(Regex(",\\s*wv"), "")              // alternate position
            .replace(Regex("Version/\\d+\\.\\d+\\s*"), "") // WebView version tag
            .replace(Regex("\\s*Build/[^;)]+"), "")    // build ID fingerprint
            .replace(Regex("\\s+"), " ")               // collapse whitespace
            .trim()
    }

    /** Mobile Chrome UA (static fallback when context is unavailable) */
    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_VERSION Mobile Safari/537.36"

    // ═══════════════════════════════════════════════════════════════
    // GOOGLE DOMAIN ALLOWLIST
    // Never block sub-resource requests to these domains.
    // Blocking them breaks Google's challenge page, reCAPTCHA,
    // sign-in, consent screens, and "One Moment" verification.
    // ═══════════════════════════════════════════════════════════════

    val GOOGLE_ALLOWLIST = setOf(
        "google.com", "googleapis.com", "gstatic.com",
        "googletagmanager.com", "google-analytics.com",
        "recaptcha.net", "googlevideo.com", "googleusercontent.com",
        "ggpht.com", "ytimg.com", "youtube.com", "youtu.be",
        "accounts.google.com", "apis.google.com",
        "play.google.com", "consent.google.com",
        "myaccount.google.com", "ogs.google.com",
        "clients1.google.com", "clients2.google.com",
        "ssl.gstatic.com", "www.gstatic.com", "fonts.gstatic.com",
        "encrypted-tbn0.gstatic.com", "lh3.googleusercontent.com",
        // CAPTCHA providers
        "hcaptcha.com", "assets.hcaptcha.com",
        "challenges.cloudflare.com", "challenge.cloudflare.com",
        "cf-ns.com", "cloudflareinsights.com"
    )

    /** Check if a host is in the Google/challenge allowlist */
    fun isAllowlistedHost(host: String): Boolean {
        val lower = host.lowercase()
        return GOOGLE_ALLOWLIST.any { lower.endsWith(it) || lower == it }
    }

    // ═══════════════════════════════════════════════════════════════
    // WEBVIEW CHROME-COMPAT SETUP
    // Apply once after WebView creation, before first loadUrl().
    // ═══════════════════════════════════════════════════════════════

    /**
     * Apply all settings that make the WebView indistinguishable from Chrome.
     * Call in factory {} block right after new WebView().
     */
    @SuppressLint("RestrictedApi")
    @Suppress("DEPRECATION")
    fun applyFullChromeCompat(webView: WebView, isPrivate: Boolean = false) {
        // 1. Strip X-Requested-With header (exposes package name)
        // Use string literal to bypass library group restriction
        try {
            if (WebViewFeature.isFeatureSupported("REQUESTED_WITH_HEADER_ALLOW_LIST")) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
            }
        } catch (e: Exception) {
            android.util.Log.e("ChromiumCompat", "Error setting RequestedWithHeader allowlist", e)
        }

        // 2. Third-party cookies MUST be enabled (Google challenge sets them)
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // 3. Safe browsing (real Chrome has it)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = true
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, true)
        }

        // 4. Renderer priority
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }

        // 5. Inject anti-detection JS BEFORE any page script runs
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, ANTI_DETECT_JS, setOf("*"))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ANTI-DETECTION JAVASCRIPT
    // Injected via DOCUMENT_START_SCRIPT — runs before ANY page JS.
    // ═══════════════════════════════════════════════════════════════

    val ANTI_DETECT_JS = """
(function(){
if(window.__alasCD)return;window.__alasCD=true;
var h=location.hostname.toLowerCase();
if(h.indexOf('realme.')!==-1||h.indexOf('heytap')!==-1||h.indexOf('oppo.')!==-1||h.indexOf('oneplus.')!==-1||h.indexOf('heytapmobi')!==-1)return;

// 1. webdriver
Object.defineProperty(navigator,'webdriver',{get:function(){return false},configurable:true});
try{delete navigator.webdriver}catch(e){}

// 2. chrome object — Google validates csi()/loadTimes() return shape
if(!window.chrome||!window.chrome.runtime||!window.chrome.runtime.connect){
var ns=performance.timing?performance.timing.navigationStart:Date.now();
window.chrome={
app:{isInstalled:false,InstallState:{DISABLED:'disabled',INSTALLED:'installed',NOT_INSTALLED:'not_installed'},RunningState:{CANNOT_RUN:'cannot_run',READY_TO_RUN:'ready_to_run',RUNNING:'running'},getDetails:function(){return null},getIsInstalled:function(){return false},installState:function(){return'not_installed'},runningState:function(){return'cannot_run'}},
runtime:{OnInstalledReason:{CHROME_UPDATE:'chrome_update',INSTALL:'install',SHARED_MODULE_UPDATE:'shared_module_update',UPDATE:'update'},OnRestartRequiredReason:{APP_UPDATE:'app_update',OS_UPDATE:'os_update',PERIODIC:'periodic'},PlatformArch:{ARM:'arm',ARM64:'arm64',MIPS:'mips',MIPS64:'mips64',X86_32:'x86-32',X86_64:'x86-64'},PlatformNaclArch:{ARM:'arm',MIPS:'mips',MIPS64:'mips64',X86_32:'x86-32',X86_64:'x86-64'},PlatformOs:{ANDROID:'android',CROS:'cros',LINUX:'linux',MAC:'mac',OPENBSD:'openbsd',WIN:'win'},RequestUpdateCheckStatus:{NO_UPDATE:'no_update',THROTTLED:'throttled',UPDATE_AVAILABLE:'update_available'},connect:function(){return{onDisconnect:{addListener:function(){}},onMessage:{addListener:function(){}},postMessage:function(){},disconnect:function(){}}},sendMessage:function(){},id:undefined},
csi:function(){return{onloadT:ns+100,startE:ns,pageT:Date.now()-ns,tran:15}},
loadTimes:function(){return{commitLoadTime:ns/1e3,connectionInfo:'h2',finishDocumentLoadTime:(ns+200)/1e3,finishLoadTime:(ns+300)/1e3,firstPaintAfterLoadTime:(ns+150)/1e3,firstPaintTime:(ns+100)/1e3,navigationType:'Other',npnNegotiatedProtocol:'h2',requestTime:ns/1e3,startLoadTime:ns/1e3,wasAlternateProtocolAvailable:false,wasFetchedViaSpdy:true,wasNpnNegotiated:true}}
}}

// 3. Notification
if(!window.Notification){window.Notification=function(t,o){this.title=t;this.body=(o||{}).body||'';this.close=function(){}};window.Notification.permission='default';window.Notification.requestPermission=function(c){var p=Promise.resolve('default');if(c)c('default');return p};window.Notification.maxActions=2}

// 4. Permissions
if(navigator.permissions){var oq=navigator.permissions.query.bind(navigator.permissions);navigator.permissions.query=function(d){return oq(d).catch(function(){return{state:'prompt',onchange:null,addEventListener:function(){},removeEventListener:function(){}}})}}

// 5. hasFocus + Visibility Spoofing (CRITICAL for Background Play)
document.hasFocus=function(){return true};
Object.defineProperty(document,'hidden',{get:function(){return false},configurable:true});
Object.defineProperty(document,'visibilityState',{get:function(){return'visible'},configurable:true});
Object.defineProperty(document,'webkitVisibilityState',{get:function(){return'visible'},configurable:true});
window.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation()},true);
window.addEventListener('webkitvisibilitychange',function(e){e.stopImmediatePropagation()},true);
// Prevent YouTube Music from pausing on blur
window.addEventListener('blur',function(e){if(location.hostname.includes('music.youtube.com'))e.stopImmediatePropagation()},true);

// 6. Plugins
Object.defineProperty(navigator,'plugins',{get:function(){var a=[{name:'Chrome PDF Plugin',filename:'internal-pdf-viewer',description:'Portable Document Format',length:1,item:function(){return this},namedItem:function(){return this}},{name:'Chrome PDF Viewer',filename:'mhjfbmdgcfjbbpaeojofohoefgiehjai',description:'',length:1,item:function(){return this},namedItem:function(){return this}},{name:'Native Client',filename:'internal-nacl-plugin',description:'',length:2,item:function(){return this},namedItem:function(){return this}}];a.item=function(i){return a[i]};a.namedItem=function(n){for(var i=0;i<a.length;i++){if(a[i].name===n)return a[i]}return null};a.refresh=function(){};return a},configurable:true});

// 7. MimeTypes
Object.defineProperty(navigator,'mimeTypes',{get:function(){var a=[{type:'application/pdf',suffixes:'pdf',description:'Portable Document Format',enabledPlugin:navigator.plugins[0]},{type:'application/x-google-chrome-pdf',suffixes:'pdf',description:'Portable Document Format',enabledPlugin:navigator.plugins[0]}];a.item=function(i){return a[i]};a.namedItem=function(n){for(var i=0;i<a.length;i++){if(a[i].type===n)return a[i]}return null};return a},configurable:true});

// 8. Languages
Object.defineProperty(navigator,'languages',{get:function(){return['en-US','en']},configurable:true});
Object.defineProperty(navigator,'language',{get:function(){return'en-US'},configurable:true});

// 9. Screen
try{Object.defineProperty(screen,'colorDepth',{get:function(){return 24},configurable:true});Object.defineProperty(screen,'pixelDepth',{get:function(){return 24},configurable:true})}catch(e){}

// 10. WebGL
try{var gp=WebGLRenderingContext.prototype.getParameter;WebGLRenderingContext.prototype.getParameter=function(p){if(p===37445)return'Google Inc. (Qualcomm)';if(p===37446)return'ANGLE (Qualcomm, Adreno (TM) 730, OpenGL ES 3.2)';return gp.call(this,p)}}catch(e){}
try{if(typeof WebGL2RenderingContext!=='undefined'){var gp2=WebGL2RenderingContext.prototype.getParameter;WebGL2RenderingContext.prototype.getParameter=function(p){if(p===37445)return'Google Inc. (Qualcomm)';if(p===37446)return'ANGLE (Qualcomm, Adreno (TM) 730, OpenGL ES 3.2)';return gp2.call(this,p)}}}catch(e){}

// 11. Connection
if(navigator.connection){try{Object.defineProperty(navigator.connection,'rtt',{get:function(){return 50},configurable:true})}catch(e){}}

// 12. Hide JS bridge objects from enumeration
['__webview_bridge__','_AlasBrowser','AlasAutofill','AlasBlobDownloader','accessibility','accessibilityTraversal'].forEach(function(n){try{if(window[n])Object.defineProperty(window,n,{enumerable:false,configurable:true,writable:true,value:window[n]})}catch(e){}});

// 13. deviceMemory + hardwareConcurrency normalization
try{Object.defineProperty(navigator,'deviceMemory',{get:function(){return 8},configurable:true})}catch(e){}
try{Object.defineProperty(navigator,'hardwareConcurrency',{get:function(){return 8},configurable:true})}catch(e){}

// 14. Consistent performance.memory (Chrome-only API)
if(!performance.memory){try{Object.defineProperty(performance,'memory',{get:function(){return{jsHeapSizeLimit:2172649472,totalJSHeapSize:37062816,usedJSHeapSize:26102784}},configurable:true})}catch(e){}}

})();
    """.trimIndent()
}
