package com.sun.alasbrowser.web

import android.content.Context
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object SimpleAdBlocker {
    private data class InitConfig(val mode: String, val enabledLists: Set<String>)
    private val initLock = Any()
    @Volatile private var initInProgress = false
    @Volatile private var lastInitConfig: InitConfig? = null
    @Volatile private var pendingInitConfig: InitConfig? = null
   
    private val blockedCount = AtomicInteger(0)
    private val blockedAdsToday = AtomicInteger(0)
   
    // Atomic references for thread-safe updates (double buffering)
    // Split rules: domain rules use HashSet O(1) lookup; pattern rules use linear scan
    private val filterDomainRules = AtomicReference<Set<String>>(emptySet())
    private val filterPatternRules = AtomicReference<Set<String>>(emptySet())
    private val filterExceptions = AtomicReference<Set<String>>(emptySet())
    private val hideSelectors = AtomicReference<Set<String>>(emptySet())
    // Hosts-file domains (StevenBlack, AdAway — 60K+ domains)
    private val hostsDomains = AtomicReference<Set<String>>(emptySet())
   
    // Cache by HOST for performance (not full URL — avoids unbounded growth)
    private val hostCache = ConcurrentHashMap<String, Boolean>(1024)
   
    private val excludedSites = ConcurrentHashMap.newKeySet<String>()
    private var isInitialized = false
   
    // ⚡ Advanced Analytics
    private val blockingStats = ConcurrentHashMap<String, AtomicInteger>()
    private var dataSavedBytes = AtomicInteger(0)
    private var timesSavedMs = AtomicInteger(0)
    private var lastResetTime = System.currentTimeMillis()
    
    // 🧠 ADAPTIVE LEARNING SYSTEM - Learns new ad patterns
    private val learnedAdDomains = ConcurrentHashMap.newKeySet<String>()
    private val learnedAdPatterns = ConcurrentHashMap.newKeySet<String>()
    private val redirectChainTracker = ConcurrentHashMap<String, MutableList<String>>() // tabId -> list of URLs
    private val legitimateNavigationHistory = ConcurrentHashMap<String, MutableList<String>>() // tabId -> list of real user navigations
    
    // 🌍 REGIONAL AD BLOCKING
    private val regionalAdDomains = ConcurrentHashMap<String, Set<String>>()
    
    // 📍 SMART BACK NAVIGATION - Tracks real navigation vs ad redirects  
    private val realNavigationStack = ConcurrentHashMap<String, java.util.ArrayDeque<String>>()
    private val adRedirectTimestamps = ConcurrentHashMap<String, Long>()
    
    // 🎬 YOUTUBE INTERSTITIAL AD PROTECTION - Prevents YouTube video hijacking on manga sites
    private val YOUTUBE_INTERSTITIAL_PATTERNS = setOf(
        "youtube.com/embed/",                    // Embedded YouTube player
        "youtube-nocookie.com/embed/",           // Privacy-enhanced embed
        "youtube.com/v/",                        // Old embed format
        "youtu.be/",                             // Short links
        "/watch?v=",                             // Direct watch links
        "&autoplay=1",                           // Auto-play parameter
        "?autoplay=1",
        "mime=video",                            // Video MIME types
        "googlevideo.com/videoplayback",         // Direct video streams
        "redirector.googlevideo.com",            // YouTube redirector
        "youtube.com/api/stats/playback"         // Playback tracking
    )
    
    private val MANGA_YOUTUBE_TRIGGERS = setOf(
        "kingofshojo.com/video/",
        "kingofshojo.com/player/",
        "kingofshojo.com/embed/",
        "mangakakalot.com/video/",
        "manganato.com/player/",
        "/swf/",          // Flash players (older sites)
        "/player.swf",
        "/video.swf"
    )
   
    // Per-site blocking stats for site security dialog
    private val perSiteAdsBlocked = ConcurrentHashMap<String, AtomicInteger>()
    private val perSiteTrackersBlocked = ConcurrentHashMap<String, AtomicInteger>()
   
    // Per-site blocked domain lists for detail views
    private val perSiteAdDomains = ConcurrentHashMap<String, MutableSet<String>>()
    private val perSiteTrackerDomains = ConcurrentHashMap<String, MutableSet<String>>()
   
    // Known tracker domains (separate from ad domains) - Expanded with more common trackers
    private val trackerDomains = setOf(
        "google-analytics.com", "googletagmanager.com", "facebook.com/tr",
        "facebook.net", "connect.facebook.net", "scorecardresearch.com",
        "analytics.twitter.com", "quantserve.com", "chartbeat.com",
        "mixpanel.com", "segment.io", "hotjar.com", "fullstory.com",
        "mouseflow.com", "crazyegg.com", "clicktale.net", "heap.io",
        "amplitude.com", "kissmetrics.com", "statcounter.com", "matomo.org",
        "piwik.pro", "clicky.com", "woopra.com", "branch.io", "appsflyer.com",
        "adjust.com", "kochava.com", "singular.net", "tune.com",
        "intercom.io", "sentry.io", "newrelic.com", "datadog.com",
        "logrocket.com", "usercentrics.com", "onetrust.com", "cookielaw.org",
        "tealium.com", "tealiumiq.com", "demdex.net", "omtrdc.net", // Adobe trackers
        // Modern trackers (2024-2026)
        "clarity.ms", "mouseflow.com", "luckyorange.com", "smartlook.com",
        "plausible.io", "umami.is", "pirsch.io", "simpleanalytics.com",
        "usefathom.com", "goatcounter.com", "counter.dev",
        "ads.tiktok.com", "analytics.tiktok.com", "tr.snapchat.com",
        "ct.pinterest.com", "bat.bing.com", "ad.doubleclick.net"
    )
   
    private val essentialDomains = setOf(
        "googlevideo.com", "ytimg.com", "ggpht.com",
        "gstatic.com", "ssl.gstatic.com",
        "fonts.googleapis.com", "fonts.gstatic.com",
        "ajax.googleapis.com", "maps.googleapis.com",
        "play.google.com",
        "accounts.google.com", "accounts.youtube.com",
        "apis.google.com", "oauth2.googleapis.com",
        "securetoken.googleapis.com", "identitytoolkit.googleapis.com",
        "www.googleapis.com", "content-autofill.googleapis.com",
        "myaccount.google.com",
        "login.microsoftonline.com", "appleid.apple.com",
        "challenges.cloudflare.com", "hcaptcha.com",
        "wikipedia.org", "wikimedia.org",
        
        // Manufacturer Accounts (often false positive as trackers)
        "realme.com", "id.realme.com", "c.realme.com",
        "oppo.com", "id.oppo.com", "account.oppo.com",
        "oneplus.com", "account.oneplus.com", "account.oneplus.in",
        "heytap.com", "uc-res-gl.heytap.com", "heytapmobile.com", "realmemobile.com"
    )
    
    // Broader patterns for essential domain matching (covers country-specific TLDs like google.co.in)
    private val essentialDomainPatterns = setOf(
        "accounts.google.", "accounts.youtube.",
        "login.yahoo.", "login.live.", "login.microsoftonline.",
        "signin.google.", "oauth.google."
    )
   
    // High-performance ad domain matching using hash sets - Expanded with more ad networks
    private val adDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "facebook.com/tr",
        "facebook.net", "connect.facebook.net", "scorecardresearch.com",
        "outbrain.com", "taboola.com", "advertising.com", "adnxs.com",
        "amazon-adsystem.com", "pubmatic.com", "rubiconproject.com",
        "criteo.com", "exoclick.com", "popads.net", "popcash.net",
        "propellerads.com", "juicyads.com", "trafficjunky.net",
        "serving-sys.com", "2mdn.net", "adcolony.com", "adform.net",
        "adsafeprotected.com", "adtechus.com", "bidswitch.net",
        "casalemedia.com", "openx.net", "revcontent.com", "smartadserver.com",
        "yieldmo.com", "zedo.com", "analytics.twitter.com", "ads-twitter.com",
        "moatads.com", "nexac.com", "quantserve.com", "addthis.com",
        "chartbeat.com", "branch.io",
        "inmobi.com", "tapjoy.com", "vungle.com", "unityads.unity3d.com",
        "ironsource.com", "applovin.com", "chartboost.com", "fyber.com",
        "supersonic.com", "admob.com", "adsense.com",
        // Modern ad networks & trackers (2024-2026)
        "amazon-adsystem.com", "ads.linkedin.com", "ads.tiktok.com",
        "analytics.tiktok.com", "ads.pinterest.com", "ads.reddit.com",
        "ads.snapchat.com", "adsrvr.org", "thetradedesk.com",
        "demdex.net", "omtrdc.net", "sc-static.net", "snap.licdn.com",
        "tr.snapchat.com", "ct.pinterest.com", "ads-api.twitter.com",
        "adsymptotic.com", "adthrive.com", "mediavine.com", "ezoic.net",
        "ezoic.com", "ezojs.com", "ezoadtags.com",
        "prebid.org", "rubiconproject.com", "pubmatic.com",
        "indexexchange.com", "33across.com", "sharethrough.com",
        "triplelift.com", "yieldmo.com", "liveintent.com",
        "openx.com", "adroll.com", "retargetlinks.com",
        "criteo.net", "outbrainssp.com", "disqusads.com",
        
        // 🎌 MANGA SITE SPECIFIC AD NETWORKS (kingofshojo, mangakakalot, etc.)
        "exdynsrv.com", "syndication.exdynsrv.com", "syndication.exosrv.com",
        "tsyndicate.com", "trkinator.com", "trklnks.com", "trkur.com",
        "clksite.com", "clkrev.com", "clkmon.com", "clkdrive.com",
        "a-ads.com", "coinzillatag.com", "coinad.com", "adsterra.com",
        "hilltopads.com", "clickadu.com", "propellerads.com", "popcash.net",
        "popads.net", "popads.media", "adsco.re", "adspyglass.com",
        "juicyads.com", "exoclick.com", "trafficjunky.com", "trafficstars.com",
        "plugrush.com", "adnium.com", "adnium.net", "adnium.org",
        "bidvertiser.com", "mgid.com", "revcontent.com", "contentad.net",
        "admaven.com", "admaven.co", "ad-maven.com", "admavens.com",
        "adnow.com", "adpushup.com", "adskeeper.com", "adskeeper.co.uk",
        "richpush.co", "pushground.com", "push.house", "pushads.biz",
        "onclicka.com", "onclickads.net", "onclickmega.com", "onclickmax.com",
        "highcpmgate.com", "highrevenuegate.com", "highcpmrevenue.com",
        "profitabledisplaynetwork.com", "profitabledisplaycontent.com",
        "abovertuead.com", "abovertuecontent.com", "revenuewire.com",
        "monetag.com", "monetag.io", "propellerclick.com", "propellerpops.com",
        "adcash.com", "adcash.net", "acintpub.com", "acint.net",
        "wpadmngr.com", "rtmark.net", "rdrtr.com", "d2cmedia.ca",
        "affec.tv", "afftrack.io", "clickfuse.com", "clickomax.com",
        "dollarade.com", "earnify.com", "strpjmp.com", "go.strpjmp.com",
        // 2025-2026 modern ad networks & trackers
        "go.adskeeper.co.uk", "s.adskeeper.co.uk",
        "cdn.onthe.io", "cdn.sstatic.net",
        "cdn-gl.imrworldwide.com", "secure-gl.imrworldwide.com",
        "a.pub.network", "pub.network",
        "btloader.com", "btstatic.com",
        "cdn.carbonads.com", "srv.carbonads.net",
        "ads.betweendigital.com", "betweendigital.com",
        "cdn.segment.com", "api.segment.io",
        "cdn.mxpnl.com", "api-js.mixpanel.com",
        "va.tawk.to", "embed.tawk.to",
        "static.hotjar.com", "script.hotjar.com",
        "cdn.heapanalytics.com", "heapanalytics.com",
        "cdn.amplitude.com", "api2.amplitude.com",
        "static.cloudflareinsights.com",
        "rum.browser-intake-datadoghq.com",
        "browser.sentry-cdn.com",
        "js.sentry-cdn.com"
    )
   
    // ✅ Whitelisted download CDN domains (these are legitimate download sources, not ads) - Expanded with more mod/APK and file hosts
    val DOWNLOAD_CDN_WHITELIST = setOf(
        // Mod/APK sites CDNs
        "9mod.cloud", "cdn.9mod.cloud", "9mod.space", "cloud.9mod.space", "liteapks.com", "apkpure.com", "apkmirror.com",
        "apkcombo.com", "happymod.com", "an1.com", "revdl.com", "rexdl.com",
        "androeed.ru", "androidapksfree.com", "apkhere.com", "apknite.com",
        "apksfull.com", "moddroid.com", "modapkdown.com", "apkdone.com",
        "modyolo.com", "apkmody.io", "pdalife.com", "mobilism.org",
        "getmodsapk.com", "apkaward.com", "apkvision.com", "apkmonk.com",
        "apkfab.com", "apk.support", "apkdlmod.com", "apk4all.com",
        
        // General file hosting CDNs
        "cloudflare.com", "cdn.cloudflare.com", "cdnjs.cloudflare.com",
        "akamai.net", "akamaized.net", "fastly.net", "stackpath.com",
        "jsdelivr.net", "unpkg.com", "cdnjs.com",
        "bunnycdn.com", "keycdn.com", "limelight.com",
        
        // Common file sharing
        "mediafire.com", "mega.nz", "mega.io", "drive.google.com",
        "dropbox.com", "onedrive.live.com", "box.com", "wetransfer.com",
        "sendspace.com", "zippyshare.com", "4shared.com",
        "file.io", "anonfiles.com", "gofile.io", "bayfiles.com",
        
        // GitHub/GitLab releases
        "github.com", "raw.githubusercontent.com", "gitlab.com",
        "objects.githubusercontent.com", "github-releases.githubusercontent.com",
        "bitbucket.org", "sourceforge.net", "codeberg.org"
    )
   
    // 🚫 Popup Ad Networks & Redirect Domains (commonly used for download site ads) - Expanded significantly
    // Based on Opera browser's blocking behavior and LiteApks redirects
    val POPUP_AD_DOMAINS = setOf(
        // Popup networks
        "popads.net", "popcash.net", "popunder.net", "propellerads.com",
        "exoclick.com", "juicyads.com", "trafficjunky.net", "trafficstars.com",
        "hilltopads.com", "adsterra.com", "clickadu.com", "adcash.com",
        "bidvertiser.com", "mgid.com", "revcontent.com", "contentad.net",
        "admaven.com", "adnow.com", "adpushup.com", "adskeeper.com",
        "plugrush.com", "zeropark.com", "mgid.com", "push.house",
        "gateit.com", "cpmgate.com", "revenuegate.com", "highcpmcreative.com", "profitabledisplaycontent.com", "abovertuecontent.com",
        "pushads.biz", "adpopcorn.com", "adpopcorn.net", "traffichaus.com", "adclick.g.doubleclick.net", "ads.google.com",
        
        // 🆕 LiteApks specific ad domains (from user reports)
        "smsonline.cloud", "dogdrip.net", // From Opera's filter error
        
        // Redirect chains
        "adf.ly", "bc.vc", "j.gs", "q.gs", "linkbucks.com", "shorte.st",
        "ouo.io", "ouo.press", "adfoc.us", "clk.sh", "exe.io", "sub2unlock.com",
        "cpmlink.net", "shrinkearn.com", "shrinkme.io", "linkshrink.net",
        "linkvertise.com", "linkvertise.net", "up4ever.org", "upload.ee",
        "adshort.co", "adshrink.it", "cutt.ly", "bit.ly", // Shorteners that often lead to ads
        
        // Malicious ad domains
        "adclerks.com", "adskeeper.co.uk", "adspyglass.com", "adversal.com",
        "affiliaxe.com", "ayboll.com", "bannerflow.com", "bebi.com",
        "begun.ru", "bidsopt.com", "blogads.com", "buysellads.com",
        "chitika.com", "clicksor.com", "contextweb.com", "cpmstar.com",
        "directrev.com", "exponential.com", "fastclick.com", "infolinks.com",
        "intellitxt.com", "kontera.com", "media.net", "mediavine.com",
        "netshelter.net", "optimatic.com", "pjatr.com", "pjtra.com",
        "revenuehits.com", "skimlinks.com", "sovrn.com", "viglink.com",
        "yllix.com", "zergnet.com", "zemanta.com",
        "spoutable.com", "sharethrough.com", "nativeroll.tv", "kargo.com",
        
        // Download site specific ad networks
        "downloadatoz.com", "downloadha.com", "downloadly.ir", "downloadming.com",
        "uploadrar.com", "uploadgig.com", "rapidgator.net", "uploaded.net",
        "nitroflare.com", "turbobit.net", "keep2share.cc", "filefactory.com",
        "novafile.com", "alfafile.net", "hitfile.net", "fileboom.net",
        
        // Gambling/Betting sites (commonly used for download site ad redirects) - Expanded
        "1xbet.com", "indi-1xbet.com", "1xbet.mobi", "1xstavka.ru",
        "betway.com", "bet365.com", "888casino.com", "williamhill.com",
        "bwin.com", "unibet.com", "ladbrokes.com", "paddypower.com",
        "coral.co.uk", "skybet.com", "betfair.com", "bet-at-home.com",
        "10bet.com", "22bet.com", "melbet.com", "parimatch.com",
        "dafabet.com", "pinnacle.com", "sportsbet.io", "betvictor.com",
        "betfred.com", "boylesports.com", "sportingbet.com", "betsson.com",
        "intertops.eu", "bovada.lv", "mybookie.ag", "betonline.ag",
        
        // Obfuscated redirect domains (used to hijack download button clicks) - Added more variants
        "obqj2.com", "obqj.com", "obqs.com", "obqt.com", "obqu.com",
        "zvnr.com", "zvns.com", "zvnt.com", "zvnu.com", "zvnv.com",
        "tsyndicate.com", "trkinator.com", "trklnks.com", "trkur.com",
        "clksite.com", "clkrev.com", "clkmon.com", "clkdrive.com",
        "go2cloud.org", "tracking.link", "affiliate.net", "partners.io",
        
        // LiteApks & APK site specific ad domains
        "monetag.com", "a-ads.com", "richpush.co", "pushground.com",
        "adsco.re", "adserverplus.com", "adspyglass.com", "trafficshop.com",
        "onclicka.com", "onclickads.net", "onclickmega.com", "onclickmax.com",
        "acintpub.com", "acint.net", "ad-maven.com", "admaven.co",
        "wpadmngr.com", "rtmark.net", "d2cmedia.ca", "rdrtr.com",
        "highcpmgate.com", "highrevenuegate.com", "highcpmrevenue.com",
        "profitabledisplaynetwork.com", "abovertuead.com", "revenuewire.com",
        "affec.tv", "afftrack.io", "clickfuse.com", "clickomax.com",
        "dollarade.com", "earnify.com", "go.strpjmp.com", "strpjmp.com",
        "syndication.dynsrvtbg.com", "dynsrvtbg.com", "syndication.exdynsrv.com",
        "exdynsrv.com", "syndication.realsrv.com", "realsrv.com",
        "a.magsrv.com", "magsrv.com", "exosrv.com", "syndication.exosrv.com",
        
        // Crypto miners (often bundled with ads)
        "coinhive.com", "coin-hive.com", "jsecoin.com", "cryptoloot.pro",
        "webminepool.com", "monerominer.rocks", "crypto-loot.com",
        "authedmine.com", "coinimp.com", "minerstat.com",
        
        // Tracking pixels that trigger redirects
        "pixel.quantserve.com", "pixel.mathtag.com", "pixel.rubiconproject.com",
        "pixel.advertising.com", "pixel.adsafeprotected.com",
        "pixel.facebook.com", "pixel.google.com", "pxl.tsyndicate.com",
        
        // 🚨 USER REPORTED AD DOMAINS (LiteApks redirects)
        "foundhertobeconsist.org", "oundhertobeconsist.org", "3ckz.com", "cdn.3ckz.com",
        "casual-sl.com", "flirtconnect.com", "flirt-connect.com", "date-connect.com",
        "red-gifs.com", "adult-finder.com", "sex-finder.com",
        
        // Additional APK site ad redirects
        "monetag.com", "a-monetag.com", "d-monetag.com",
        "adsco.re", "surfrads.com", "onclicka.com", "onclickads.net",
        "syndication.realsrv.com", "realsrv.com", "exosrv.com",
        "syndication.exosrv.com", "syndication.exdynsrv.com", "exdynsrv.com",
        "a.magsrv.com", "magsrv.com", "a.realsrv.com",
        "notifpush.com", "pushame.com", "pushwhy.com", "pushnow.net",
        "servedby.clicks2earn.com", "clicks2earn.com",
        "adblockerext.online", "contentstop.net", "datebest.net",
        "dede1.space", "herofaster.com", "notifzone.com",
        "sloyd.one", "videoslot.games", "winner-way.com",
        "getadvanced.network", "sloyo.one", "wincombine.com",
        // 2025-2026 popup ad domains
        "glizauvo.net", "yomeno.xyz", "trk.nzfrr.cn",
        "notifpush.com", "pushame.com", "pushwhy.com", "pushnow.net",
        "servedby.clicks2earn.com", "clicks2earn.com",
        "adblockerext.online", "contentstop.net", "datebest.net",
        "herofaster.com", "notifzone.com", "getadvanced.network",
        "sloyd.one", "sloyo.one", "wincombine.com", "winner-way.com",
        "videoslot.games", "dede1.space",
        "topcreativeformat.com", "bfrggkrs.com", "rtbsystem.com",
        "newstarads.com", "displayvertising.com", "adtrafficquality.google",
        "pagead2.googlesyndication.com", "tpc.googlesyndication.com"
    )
   
    private val adPatterns = setOf(
        "/ads/", "/ad/", "/advert", "-ad-", "-ads-", ".ads.",
        "_ads_", "ad.php", "ads.js", "adframe", "adserver", "pagead",
        "/adsbygoogle", "/ad-provider", "/beacon",
        "/pixel", "adservice", "sponsored", "/popunder", "/popup",
        "/redirect", "/aff_c", "/aff_", "/click/", "/clk/",
        "/track/", "/tracker/", "/imp/", "/impression",
        "/sponsor/", "/promo/", "/banner/", "/interstitial/",
        // Additional URL patterns for catching sneaky ads
        "/prebid/", "/header-bid", "/hb.js", "/hbadv",
        "/vast.xml", "/vpaid.js", "/vast/", "/vpaid/",
        "_ad_", ".ad.", "/native-ad", "/in-feed-ad",
        "/sticky-ad", "/anchor-ad", "/rewarded-ad",
        "/ad-refresh", "/ad-lazy", "/ad-load",
        "/monetization/", "/programmatic/",
        "/analytics.js", "/gtag/js", "/gtm.js"
    )
    
    // 🛑 AGGRESSIVE YouTube Ad Patterns for Network Interception - Added more specific paths
    val YOUTUBE_AD_PATTERNS = setOf(
        "googleads.g.doubleclick.net",
        "pubads.g.doubleclick.net",
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "google-analytics.com",
       
        // Paths
        "/pagead/",
        "/get_midroll_info",
        "/get_ads",
        "/ptracking",
        "/log_event",
        "/stats/ads",
        "api/stats/ads",
        "/qoe",
        "/ads?adurl=",
        "/ads?correlator=",
        "/generate_204",
       
        // Query Params (Strong Signals)
        "&adformat=",
        "&ad_logging_flag=",
        "&ctier=",
        "&ad_type=",
        "&ad_rule=",
        "&adunit=",
        "&adk=",
        "&gdfp_req=",
        "&impl=s",
        
        // 🎌 MANGA SITE AD PATTERNS
        "/banner", "/banners/", "banner.php", "banner.js",
        "/interstitial", "/popunder", "/popup",
        "adserver", "ad-server", "adserve",
        "/adframe", "/ad_frame", "adframe.php",
        "/redirect.php", "/redir.php", "/out.php", "/go.php",
        "click.php?", "clk.php?", "track.php?",
        "&clickid=", "&aff_sub=", "&aff_id=",
        "&campaign_id=", "&zone_id=", "&banner_id=",
        "/vast.xml", "/vpaid", "/video-ad",
        "prebid.js", "prebid-", "header-bidding"
    )
    
    // 🎌 MANGA SITE REDIRECT PATTERNS (for detecting and blocking redirect chains)
    private val mangaRedirectPatterns = setOf(
        "exdynsrv.com", "tsyndicate.com", "trkinator.com",
        "clksite.com", "clkrev.com", "go.strpjmp.com",
        "/redirect", "/redir", "/out", "/go", "/click",
        "?url=", "?redirect=", "?target=", "?destination=",
        "&url=", "&redirect=", "&target=", "&destination="
    )
    
    @OptIn(DelicateCoroutinesApi::class)
    fun initialize(context: Context, mode: String = "BALANCED", enabledLists: Set<String> = emptySet()) {
        val appContext = context.applicationContext
        val normalizedMode = mode.uppercase()
        val config = InitConfig(normalizedMode, enabledLists.toSet())

        synchronized(initLock) {
            if (initInProgress) {
                pendingInitConfig = config
                Log.d("SimpleAdBlocker", "Initialization in progress, queued reinit for mode=$normalizedMode")
                return
            }
            if (isInitialized && config == lastInitConfig) return
            initInProgress = true
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val tempDomainRules = HashSet<String>()
                val tempPatternRules = HashSet<String>()
                val tempSelectors = HashSet<String>()
                val tempExceptions = HashSet<String>()
               
                val filterIds = when (normalizedMode) {
                    "BALANCED" -> listOf("easylist", "easyprivacy", "ublock-filters")
                    "ENHANCED" -> listOf("easylist", "easyprivacy", "ublock-filters", "ublock-privacy", "ublock-badware", "adguard-mobile-ublock", "adguard-tracking-ublock")
                    "CUSTOM" -> enabledLists.toList()
                    else -> listOf("easylist", "easyprivacy", "ublock-filters")
                }

                // Hosts file lists — always loaded for all modes (huge domain coverage)
                val hostsIds = listOf("stevenblack-hosts", "adaway-hosts")

                val allIds = filterIds + hostsIds
                val uncachedFilters = allIds.filter { !FilterListManager.isFilterCached(appContext, it) }
                if (uncachedFilters.isNotEmpty()) {
                    Log.d("SimpleAdBlocker", "Downloading ${uncachedFilters.size} uncached filters")
                    FilterListManager.downloadFilters(appContext, uncachedFilters.toSet())
                }
               
                // Load EasyList/uBlock format filters
                filterIds.forEach { filterId ->
                    try {
                        val content = FilterListManager.loadCachedFilter(appContext, filterId)
                        if (content != null) {
                            parseFilters(content, tempDomainRules, tempPatternRules, tempSelectors, tempExceptions)
                            Log.d("SimpleAdBlocker", "Loaded $filterId from cache")
                        } else {
                            Log.w("SimpleAdBlocker", "No cached filter found for $filterId")
                        }
                    } catch (e: Exception) {
                        Log.w("SimpleAdBlocker", "Failed to load $filterId: ${e.message}")
                    }
                }

                // Load hosts files into a dedicated fast-lookup set
                val tempHosts = HashSet<String>()
                hostsIds.forEach { hostsId ->
                    try {
                        val content = FilterListManager.loadCachedFilter(appContext, hostsId)
                        if (content != null) {
                            parseHostsFile(content, tempHosts)
                            Log.d("SimpleAdBlocker", "Loaded hosts $hostsId: ${tempHosts.size} domains")
                        }
                    } catch (e: Exception) {
                        Log.w("SimpleAdBlocker", "Failed to load hosts $hostsId: ${e.message}")
                    }
                }
               
                filterDomainRules.set(tempDomainRules)
                filterPatternRules.set(tempPatternRules)
                filterExceptions.set(tempExceptions)
                hideSelectors.set(tempSelectors)
                hostsDomains.set(tempHosts)
                hostCache.clear()
               
                isInitialized = true
                lastInitConfig = config
                Log.d("SimpleAdBlocker", "Initialized with ${tempDomainRules.size} domain rules, ${tempPatternRules.size} pattern rules, ${tempHosts.size} hosts, ${tempExceptions.size} exceptions, ${tempSelectors.size} selectors")
            } catch (e: Exception) {
                Log.e("SimpleAdBlocker", "Failed to load filters, keeping existing rules", e)
                isInitialized = true
                lastInitConfig = config
            } finally {
                val queuedConfig = synchronized(initLock) {
                    initInProgress = false
                    val queued = pendingInitConfig
                    pendingInitConfig = null
                    queued
                }
                if (queuedConfig != null && queuedConfig != config) {
                    initialize(appContext, queuedConfig.mode, queuedConfig.enabledLists)
                }
            }
        }
    }

    private fun parseHostsFile(content: String, domains: MutableSet<String>) {
        content.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                    val domain = parts[1].trim()
                    if (domain != "localhost" && domain != "local" &&
                        domain != "localhost.localdomain" && domain != "broadcasthost" &&
                        domain.contains(".")) {
                        domains.add(domain)
                    }
                }
            }
    }
   
    private fun parseFilters(
        content: String,
        domainRuleSet: MutableSet<String>,
        patternRuleSet: MutableSet<String>,
        selectorSet: MutableSet<String>,
        exceptionSet: MutableSet<String>
    ) {
        content.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("!") && !it.startsWith("[") }
            .forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("@@||") && trimmed.endsWith("^") -> {
                        exceptionSet.add(trimmed.substring(4, trimmed.length - 1))
                    }
                    trimmed.startsWith("@@") -> {
                        val exception = trimmed.substring(2)
                            .removePrefix("||").removeSuffix("^")
                            .split("$").first()
                        if (exception.isNotBlank()) exceptionSet.add(exception)
                    }
                    trimmed.startsWith("##") -> {
                        selectorSet.add(trimmed.substring(2))
                    }
                    trimmed.contains("##") && !trimmed.startsWith("/") -> {
                        selectorSet.add(trimmed.substringAfter("##"))
                    }
                    trimmed.startsWith("||") && trimmed.endsWith("^") -> {
                        // Domain rule: ||example.com^ → HashSet O(1) lookup
                        val domain = trimmed.substring(2, trimmed.length - 1).split("$").first()
                        if (domain.isNotBlank()) domainRuleSet.add(domain)
                    }
                    trimmed.startsWith("||") -> {
                        val domain = trimmed.substring(2).split("^").first().split("$").first()
                        if (domain.isNotBlank() && domain.contains(".")) {
                            // Pure domain (no path) → domain rule; with path → pattern rule
                            if (!domain.contains("/")) {
                                domainRuleSet.add(domain)
                            } else {
                                patternRuleSet.add(domain)
                            }
                        }
                    }
                    trimmed.startsWith("/") && trimmed.endsWith("/") -> {
                        // regex rules - skip (too expensive)
                    }
                    trimmed.contains("$") && trimmed.contains("domain=") -> {
                        // domain-specific rules - skip for simplicity
                    }
                    else -> {
                        // URL pattern rule → linear scan
                        val rule = trimmed.split("$").first()
                        if (rule.isNotBlank() && rule.length > 4) patternRuleSet.add(rule)
                    }
                }
            }
    }
   
    /**
     * Generate cosmetic filter CSS injection script from parsed EasyList/uBlock selectors.
     * These selectors hide site-specific ad elements that survive network blocking.
     */
    fun getCosmeticFilterScript(): String {
        val selectors = hideSelectors.get()
        if (selectors.isEmpty()) return ""
        
        // Filter out overly broad or unsupported selectors
        val safeSelectors = selectors.filter { sel ->
            sel.length in 3..200 &&
            !sel.startsWith("body") &&
            !sel.startsWith("html") &&
            !sel.startsWith("*") &&
            !sel.contains(":has(") &&
            !sel.contains(":xpath(") &&
            !sel.contains(":style(") &&
            !sel.contains(":remove(") &&
            !sel.contains(":-abp-") &&
            !sel.contains(":matches-css") &&
            !sel.contains(":upward(") &&
            !sel.contains(":nth-ancestor(")
        }
        
        if (safeSelectors.isEmpty()) return ""
        
        // Escape for JS string embedding
        val escapedSelectors = safeSelectors.joinToString(",") { 
            it.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", "")
        }
        
        return """
(function() {
    if (window.__alasCosmeticFilter) return;
    window.__alasCosmeticFilter = true;
    try {
        var style = document.createElement('style');
        style.id = 'alas-cosmetic-filters';
        style.textContent = '$escapedSelectors { display: none !important; visibility: hidden !important; height: 0 !important; overflow: hidden !important; }';
        (document.head || document.documentElement).appendChild(style);
    } catch(e) { console.error('[Alas] Cosmetic filter error:', e); }
})();
        """.trimIndent()
    }

    fun shouldBlock(url: String): Boolean {
        if (url.isBlank()) return false
       
        try {
            val lowerUrl = url.lowercase()
            val host = try {
                android.net.Uri.parse(url).host?.lowercase() ?: ""
            } catch (e: Exception) { "" }
            if (host.isBlank()) return false

            // Fast host-level cache check
            hostCache[host]?.let { cached ->
                if (cached) { recordBlock(url); return true }
                // For non-blocked hosts, still need to check URL patterns below
                // But skip domain-level checks
            }

            val isExcluded = excludedSites.any { lowerUrl.contains(it.lowercase()) }
            if (isExcluded) return false
           
            // Essential domains — never block
            val isEssential = essentialDomains.any { host.endsWith(it) || host == it } ||
                essentialDomainPatterns.any { host.startsWith(it) }
            if (isEssential) {
                hostCache[host] = false
                return false
            }

            // Exceptions from filter lists
            val currentExceptions = filterExceptions.get()
            if (currentExceptions.isNotEmpty()) {
                val isException = currentExceptions.any { exc ->
                    host.endsWith(exc) || host == exc
                }
                if (isException) {
                    hostCache[host] = false
                    return false
                }
            }

            // ── DOMAIN-LEVEL CHECKS (fast, HashSet-based) ──

            // 1. Built-in ad domains
            val isAdDomain = adDomains.any { host.endsWith(it) || host == it }
            if (isAdDomain) {
                recordBlock(url)
                hostCache[host] = true
                return true
            }

            // 2. Hosts file domains (StevenBlack + AdAway — 60K+ domains, exact match)
            val hosts = hostsDomains.get()
            if (hosts.contains(host)) {
                recordBlock(url)
                hostCache[host] = true
                return true
            }

            // 3. Filter list domain rules (EasyList ||domain^ rules)
            val domainRules = filterDomainRules.get()
            val isDomainRuleMatch = domainRules.any { rule ->
                host.endsWith(rule) || host == rule
            }
            if (isDomainRuleMatch) {
                recordBlock(url)
                hostCache[host] = true
                return true
            }

            // ── URL-LEVEL CHECKS (slower, substring matching) ──

            // 4. Built-in ad patterns
            val isPatternMatch = adPatterns.any { lowerUrl.contains(it) }
            if (isPatternMatch) {
                recordBlock(url)
                return true
            }

            // 5. Filter list pattern rules (URL path patterns)
            val patternRules = filterPatternRules.get()
            val isFilterPatternMatch = patternRules.any { rule -> lowerUrl.contains(rule) }
            if (isFilterPatternMatch) {
                recordBlock(url)
                return true
            }

            // 6. Manga redirect patterns
            val isMangaRedirect = mangaRedirectPatterns.any { lowerUrl.contains(it) }
            if (isMangaRedirect) {
                recordBlock(url)
                return true
            }

            // 7. Learned ad domains (adaptive blocking)
            val isLearnedDomain = learnedAdDomains.any { host.endsWith(it) || host == it }
            if (isLearnedDomain) {
                recordBlock(url)
                hostCache[host] = true
                return true
            }

            // 8. Common ad script/resource indicators in URL
            val adResourcePatterns = listOf(
                "prebid", "header-bidding", "hb_adid", "hb_pb",
                "amazon-adsystem", "aps.amazon", "c.amazon-adsystem",
                "ssp.yahoo.com", "ads.yahoo.com",
                "/vast/", "/vpaid/", "vast.xml", "vpaid.js",
                "ad_banner", "ad_popup", "ad_overlay",
                "pagead/js/", "show_ads", "serve_ads",
                "adsbygoogle.js", "adsbygoogle"
            )
            val isAdResource = adResourcePatterns.any { lowerUrl.contains(it) }
            if (isAdResource) {
                recordBlock(url)
                return true
            }
            
            return false
        } catch (e: Exception) {
            Log.e("SimpleAdBlocker", "Error checking URL", e)
            return false
        }
    }
   
    /**
     * Check if a URL is a popup ad domain
     * Used for blocking popup windows and redirects during downloads
     */
    fun isPopupAd(url: String): Boolean {
        if (url.isBlank()) return false
       
        try {
            val lowerUrl = url.lowercase()
            val host = try {
                android.net.Uri.parse(url).host?.lowercase() ?: ""
            } catch (e: Exception) { "" }
           
            val isWhitelisted = DOWNLOAD_CDN_WHITELIST.any { host.endsWith(it) || host == it }
            if (isWhitelisted) return false
            
            val isEssential = essentialDomains.any { host.endsWith(it) || host == it } ||
                essentialDomainPatterns.any { host.startsWith(it) }
            if (isEssential) return false
             
            val currentExceptions = filterExceptions.get()
            if (currentExceptions.isNotEmpty()) {
                val isException = currentExceptions.any { exc ->
                    host.endsWith(exc) || host == exc
                }
                if (isException) return false
            }
           
            // ✅ PRIORITY 1: Never block direct download file URLs - Expanded extensions
            val downloadExtensions = listOf(
                ".apk", ".xapk", ".apks", ".apkm", // Android apps
                ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", // Archives
                ".exe", ".msi", ".dmg", ".pkg", // Executables
                ".pdf", ".doc", ".docx", ".xls", ".xlsx", // Documents
                ".mp3", ".mp4", ".avi", ".mkv", ".mov", // Media
                ".iso", ".img", ".deb", ".rpm", ".bin", // Disk images and binaries
                ".jar", ".war", ".ear" // Java archives
            )
            val isDirectDownload = downloadExtensions.any { lowerUrl.endsWith(it) }
            if (isDirectDownload) {
                Log.d("SimpleAdBlocker", "✅ Direct download file URL, not blocking: $url")
                return false
            }
           
            val isPopupDomain = POPUP_AD_DOMAINS.any { host.endsWith(it) || host == it }
            if (isPopupDomain) {
                return true
            }
           
            // Check for common popup patterns (but NOT generic /redirect patterns)
            val popupPatterns = listOf(
                "/popunder", "/popup", "/pop-",
                "pop.php", "pop.js", "popads",
                "/aff_c?", "/aff_",
                "/interstitial", "/overlay", "/splash"
                // Removed: "/go.php", "/out.php", "/click.php", "/redirect.php" - too aggressive for download sites
            )
           
            val isPopupPattern = popupPatterns.any { lowerUrl.contains(it) }
            if (isPopupPattern) {
                Log.d("SimpleAdBlocker", "Blocked popup pattern: $url")
                return true
            }
           
            return false
        } catch (e: Exception) {
            Log.e("SimpleAdBlocker", "Error checking popup ad", e)
            return false
        }
    }
   
    /**
     * Check if a URL is a known download CDN/site that should be allowed
     * Used to ensure download redirects work properly
     */
    fun isDownloadCdnUrl(url: String): Boolean {
        if (url.isBlank()) return false
       
        val lowerUrl = url.lowercase()
       
        // Check whitelist domains
        val isWhitelisted = DOWNLOAD_CDN_WHITELIST.any { lowerUrl.contains(it) }
        if (isWhitelisted) return true
       
        // Check for download file extensions - Use endsWith for accuracy
        val downloadExtensions = listOf(
            ".apk", ".xapk", ".apks", ".apkm",
            ".zip", ".rar", ".7z", ".tar", ".gz",
            ".exe", ".msi", ".dmg", ".pkg",
            ".pdf", ".mp3", ".mp4", ".avi", ".mkv", ".iso", ".deb"
        )
       
        return downloadExtensions.any { lowerUrl.endsWith(it) }
    }

    fun isEssential(url: String): Boolean {
        if (url.isBlank()) return false
        try {
            val lowerUrl = url.lowercase()
            val host = android.net.Uri.parse(url).host?.lowercase() ?: ""
            
            return essentialDomains.any { host.endsWith(it) || host == it } ||
                   essentialDomainPatterns.any { host.startsWith(it) }
        } catch (e: Exception) {
            return false
        }
    }
   
    private const val BLOCKER_VERSION = "3.0.0"
    
    /**
     * Advanced Ad Blocker Script - Modern Implementation
     * Features: Cosmetic filtering, Network blocking, Procedural filters,
     * Gambling/Crypto targeting, RequestIdleCallback optimization
     */
    fun getBlockerScript(): String {
        val whitelistDomainsJs = DOWNLOAD_CDN_WHITELIST.joinToString(",") { "\"$it\"" }
        val popupAdDomainsJs = POPUP_AD_DOMAINS.joinToString(",") { "\"$it\"" }

        return """
(function() {
    'use strict';
    try {
        var h = (location && location.hostname ? location.hostname : '').toLowerCase();
        if (
            h.indexOf('realme.com') !== -1 ||
            h.indexOf('heytap.com') !== -1 ||
            h.indexOf('heytapmobile.com') !== -1 ||
            h.indexOf('realmemobile.com') !== -1 ||
            h.indexOf('oppo.com') !== -1 ||
            h.indexOf('oneplus.com') !== -1
        ) return;
    } catch (e) {}
    
    // ══════════════════════════════════════════════════════════════════
    // ALAS AD BLOCKER v$BLOCKER_VERSION - Advanced Implementation
    // ══════════════════════════════════════════════════════════════════
    
    if (window.__alasAB) return;
    window.__alasAB = { v: '$BLOCKER_VERSION', blocked: 0 };
    
    // ══════════════════════════════════════════════════════════════════
    // MODULE 1: COSMETIC ENGINE - CSS-based element hiding
    // ══════════════════════════════════════════════════════════════════
    
    const Cosmetic = {
        inject() {
            if (document.getElementById('alas-cosmetic')) return;
            const s = document.createElement('style');
            s.id = 'alas-cosmetic';
            s.textContent = `
                /* ═══ GENERIC AD CLASSES ═══ */
                .ad,.ads,.adv,.advert,.advertisement,.advertising,
                .ad-banner,.ad-box,.ad-container,.ad-content,.ad-frame,
                .ad-header,.ad-holder,.ad-inner,.ad-label,.ad-leaderboard,
                .ad-placement,.ad-slot,.ad-space,.ad-sponsor,.ad-text,
                .ad-unit,.ad-wrap,.ad-wrapper,.ad-zone,
                .ads-banner,.ads-container,.adsbox,.adsbygoogle,
                .adslot,.adspace,.adspot,.adtag,.adtext,.adunit,
                .banner-ad,.banner-ads,.banner_ad,.BannerAd,
                .sponsored,.sponsored-ad,.sponsored-content,.sponsoredLink,
                
                /* ═══ ATTRIBUTE SELECTORS ═══ */
                [class*="ad-banner"],[class*="ad-container"],[class*="ad-slot"],
                [class*="ad-wrapper"],[class*="ad-unit"],[class*="ads-box"],
                [class*="adsbox"],[class*="advert-"],[class*="advertisement"],
                [id*="ad-banner"],[id*="ad-container"],[id*="ad-slot"],
                [id*="google_ads"],[id*="googleAd"],[id*="divAd"],[id*="AdSlot"],
                [data-ad],[data-ads],[data-ad-slot],[data-ad-unit],
                [data-google-query-id],[data-ad-client],
                
                /* ═══ GOOGLE ADS ═══ */
                ins.adsbygoogle,.adsbygoogle[data-ad-status],
                iframe[src*="googlesyndication"],iframe[src*="doubleclick"],
                iframe[id*="google_ads"],div[id*="google_ads"],
                
                /* ═══ AD NETWORK IFRAMES ═══ */
                iframe[src*="monetag"],iframe[src*="exosrv"],iframe[src*="exdynsrv"],
                iframe[src*="realsrv"],iframe[src*="magsrv"],iframe[src*="onclicka"],
                iframe[src*="popads"],iframe[src*="popcash"],iframe[src*="adsterra"],
                iframe[src*="propellerads"],iframe[src*="hilltopads"],
                iframe[src*="trafficjunky"],iframe[src*="juicyads"],
                iframe[src*="highcpmgate"],iframe[src*="profitabledisplay"],
                iframe[src*="clickadu"],iframe[src*="evadav"],
                
                /* ═══ GAMBLING/CRYPTO ADS ═══ */
                a[href*="bc.game"],a[href*="bcgame"],a[href*="1xbet"],
                a[href*="yolo247"],a[href*="yolo24"],a[href*="bet365"],
                a[href*="stake.com"],a[href*="roobet"],a[href*="rollbit"],
                a[href*="22bet"],a[href*="melbet"],a[href*="mostbet"],
                a[href*="casino"],a[href*="betting"],a[href*="poker"],
                a[href*="slots"],a[href*="jackpot"],a[href*="gamble"],
                img[src*="bc.game"],img[src*="bcgame"],img[src*="1xbet"],
                img[src*="yolo247"],img[src*="yolo24"],img[src*="casino"],
                img[src*="betting"],img[src*="gambling"],img[src*="bonus"],
                [class*="bcgame"],[id*="bcgame"],
                
                /* ═══ POPUP/OVERLAY ═══ */
                .popup-ad,.popunder,.pop-up-ad,.overlay-ad,.modal-ad,
                div[class*="popup"][class*="ad"],div[class*="popunder"],
                div[id*="popup"][id*="ad"],div[id*="popunder"],
                
                /* ═══ STICKY/FLOATING ═══ */
                .sticky-ad,.floating-ad,.fixed-ad,.bottom-ad,.top-ad,
                div[class*="sticky"][class*="ad"],div[class*="floating"][class*="ad"],
                
                /* ═══ NATIVE ADS ═══ */
                .taboola,.outbrain,.mgid,.revcontent,.zergnet,
                div[id*="taboola"],div[id*="outbrain"],div[id*="mgid"],
                div[class*="taboola"],div[class*="outbrain"],
                .sponsored-recommendations,.content-recommendations,
                
                /* ═══ SITE-SPECIFIC ═══ */
                .c-ads,.widget-ads,.manga-ad,.chapter-ad,.sidebar-ad
                {
                    display: none !important;
                    visibility: hidden !important;
                    opacity: 0 !important;
                    height: 0 !important;
                    max-height: 0 !important;
                    overflow: hidden !important;
                }
                
                /* REMOVED: Overly broad selectors that broke page interaction:
                   - div[class*="banner"][class*="fixed"] - matches legitimate UI
                   - div[style*="z-index: 9999"] - matches site modals/popups
                   - pointer-events: none - breaks all clicks on hidden elements' siblings
                */
            `;
            (document.head || document.documentElement).appendChild(s);
        }
    };
    
    // ══════════════════════════════════════════════════════════════════
    // MODULE 1.1: STICKY ELEMENT REMOVER - Targeted cleanup
    // ══════════════════════════════════════════════════════════════════
    const StickyRemover = {
        remove() {
            // Generic "Open App" sticky banners
            const stickySelectors = [
                '.UnwantedSticky', '#UnwantedSticky',
                '[class*="StickyBanner"]', '[id*="StickyBanner"]',
                '.AppBanner', '#AppBanner', 
                '[class*="AppBanner"]', '[id*="AppBanner"]',
                '.OpenApp', '#OpenApp',
                '[aria-label*="Open App"]', 'a[href*="market://"]',
                
                // Specific: Realme Community
                '.realme-header', '.realme-footer',
                '.m-header-fixed', '.m-footer-fixed',
                
                // Specific: Reddit/Twitter/Instagram "Open in App" overlays
                '.XPromo', '.AppPrompt', '.CookieBanner',
                
                // Generic floating action buttons that block content
                '.floating-btn', '.fab-wrapper',
                
                // Content blockers / "Read more in app"
                '.read-more-mask', '.app-continue-overlay'
            ];
            
            stickySelectors.forEach(sel => {
                try {
                    document.querySelectorAll(sel).forEach(el => {
                        el.style.display = 'none';
                        el.style.visibility = 'hidden';
                    });
                } catch(e) {}
            });
        }
    };
    
    // Run periodically to catch dynamic elements
    setInterval(() => StickyRemover.remove(), 2000); // Check every 2s
    
    // ══════════════════════════════════════════════════════════════════
    // MODULE 2: PROCEDURAL ENGINE - Smart element detection
    // ══════════════════════════════════════════════════════════════════
    
    const Procedural = {
        gamblingKeywords: [
            'casino', 'betting', 'jackpot', 'slots', 'poker', 'roulette',
            'bet now', 'play now', 'register now', 'welcome bonus',
            'crypto game', 'btc game', 'free spins', 'no deposit',
            '500%', '1000%', 'bc.game', 'bcgame', '1xbet', 'yolo247'
        ],
        
        scanAndHide() {
            // Find images with gambling content
            document.querySelectorAll('img').forEach(img => {
                const src = (img.src || '').toLowerCase();
                const alt = (img.alt || '').toLowerCase();
                if (this.gamblingKeywords.some(k => src.includes(k) || alt.includes(k))) {
                    this.hideElement(img);
                }
            });
            
            // Find links with gambling content
            document.querySelectorAll('a').forEach(a => {
                const href = (a.href || '').toLowerCase();
                const text = (a.textContent || '').toLowerCase();
                if (this.gamblingKeywords.some(k => href.includes(k) || text.includes(k))) {
                    this.hideElement(a);
                }
            });
            
            // Find divs with ad-like structure
            const adClassPattern = /(?:^|[\s_-])ad(?:[\s_-]|s(?:[\s_-]|$)|vert|unit|slot|banner|box|frame|space|zone|wrap|label|block|container|content|holder|sponsor|$)/i;
            document.querySelectorAll('div, aside, section').forEach(el => {
                const cls = (el.className || '');
                const id = (el.id || '');
                if ((adClassPattern.test(cls) || adClassPattern.test(id)) && 
                    (el.querySelector('img') || el.querySelector('iframe'))) {
                    const rect = el.getBoundingClientRect();
                    if ((rect.width >= 300 && rect.height >= 200) ||
                        (rect.width >= 728 && rect.height >= 90) ||
                        (rect.width >= 320 && rect.height >= 50)) {
                        this.hideElement(el);
                    }
                }
            });
        },
        
        hideElement(el) {
            if (el && el.style) {
                el.style.cssText = 'display:none!important;visibility:hidden!important;';
                window.__alasAB.blocked++;
            }
        }
    };
    
    // ══════════════════════════════════════════════════════════════════
    // MODULE 3: NETWORK ENGINE - Request interception
    // ══════════════════════════════════════════════════════════════════
    
    const Network = {
        blockedPatterns: [
            'adsbygoogle', 'pagead2.googlesyndication', 'doubleclick.net',
            'monetag', 'exosrv', 'realsrv', 'magsrv', 'onclicka',
            'popads', 'popcash', 'propellerads', 'adsterra', 'hilltopads',
            'trafficjunky', 'juicyads', 'clickadu', 'evadav',
            'bc.game', 'bcgame', 'yolo247', 'yolo24', '1xbet', 'bet365',
            'stake.com', 'roobet', 'rollbit', '22bet', 'melbet',
            'taboola', 'outbrain', 'mgid', 'revcontent',
            'highcpmgate', 'profitabledisplay', 'tsyndicate'
        ],
        
        essentialDomains: [
            'realme.com', 'heytap.com', 'heytapmobile.com', 'realmemobile.com',
            'oppo.com', 'oneplus.com', 'obus-in.dc.heytapmobile.com',
            'accounts.google.com', 'googleapis.com', 'gstatic.com',
            'googlevideo.com', 'ytimg.com', 'wikipedia.org',
            'login.microsoftonline.com', 'appleid.apple.com',
            'challenges.cloudflare.com', 'hcaptcha.com'
        ],
        
        init() {
            this.interceptFetch();
            this.interceptXHR();
            this.interceptScripts();
        },
        
        shouldBlock(url) {
            if (!url) return false;
            const lower = url.toLowerCase();
            if (this.essentialDomains.some(d => lower.includes(d))) return false;
            return this.blockedPatterns.some(p => lower.includes(p));
        },
        
        interceptFetch() {
            const orig = window.fetch;
            const self = this;
            window.fetch = function(url, opts) {
                const urlStr = typeof url === 'string' ? url : url?.url || '';
                if (self.shouldBlock(urlStr)) {
                    console.log('[Alas] Blocked fetch:', urlStr.substring(0, 60));
                    window.__alasAB.blocked++;
                    return Promise.reject(new Error('Blocked'));
                }
                return orig.call(window, url, opts);
            };
        },
        
        interceptXHR() {
            const origOpen = XMLHttpRequest.prototype.open;
            const self = this;
            XMLHttpRequest.prototype.open = function(m, url) {
                this._alasBlocked = self.shouldBlock(url);
                if (this._alasBlocked) {
                    console.log('[Alas] Blocked XHR:', url?.substring(0, 60));
                    window.__alasAB.blocked++;
                }
                return origOpen.apply(this, arguments);
            };
            
            const origSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.send = function(body) {
                if (this._alasBlocked) {
                    setTimeout(() => this.dispatchEvent(new Event('error')), 0);
                    return;
                }
                return origSend.call(this, body);
            };
        },
        
        interceptScripts() {
            const self = this;
            const observer = new MutationObserver(muts => {
                muts.forEach(m => m.addedNodes.forEach(node => {
                    const src = node.src || '';
                    if (!src) return;
                    if (node.nodeName === 'SCRIPT' && self.shouldBlock(src)) {
                        node.remove();
                        window.__alasAB.blocked++;
                    }
                    if (node.nodeName === 'IFRAME' && self.shouldBlock(src)) {
                        node.remove();
                        window.__alasAB.blocked++;
                    }
                }));
            });
            observer.observe(document.documentElement, { childList: true, subtree: true });
        }
    };

    // ========== SMART POPUP BLOCKER ==========
    // Allows: OAuth, download CDNs, same-domain popups
    // Blocks: Ad networks, malicious redirects, gambling sites

    const whitelistDomains = [$whitelistDomainsJs];
    const popupAdDomains = [$popupAdDomainsJs];
    const oauthPatterns = ['accounts.google.com', 'login.microsoftonline.com', 'appleid.apple.com',
                           'facebook.com/v', 'github.com/login/oauth', 'twitter.com/oauth',
                           'discord.com/oauth', 'api.twitter.com/oauth', 'auth0.com', 'okta.com',
                           'cognito-idp.amazonaws.com', 'login.live.com']; // Expanded OAuth patterns
    const downloadExtensions = ['.apk', '.xapk', '.apks', '.apkm',
                                 '.zip', '.rar', '.7z', '.tar', '.gz', '.bz2',
                                 '.exe', '.msi', '.dmg', '.pkg',
                                 '.pdf', '.doc', '.docx', '.xls', '.xlsx',
                                 '.mp3', '.mp4', '.avi', '.mkv', '.mov',
                                 '.iso', '.img', '.deb', '.rpm', '.bin',
                                 '.jar', '.war', '.ear'];
  
    const currentDomain = window.location.hostname.replace(/^www\./, '');

    function isAllowedPopup(url) {
        if (!url || url === 'about:blank') return true;

        try {
            const urlObj = new URL(url, window.location.href);
            const targetDomain = urlObj.hostname.replace(/^www\./, '');
            const lowerUrl = url.toLowerCase();

            // Allow same-domain popups
            if (targetDomain === currentDomain || targetDomain.endsWith('.' + currentDomain)) {
                return true;
            }

            // Allow OAuth/authentication popups
            if (oauthPatterns.some(pattern => lowerUrl.includes(pattern))) {
                console.log('[AdBlocker] Allowing OAuth popup:', url);
                return true;
            }

            // Allow whitelisted download CDNs
            if (whitelistDomains.some(domain => lowerUrl.includes(domain))) {
                console.log('[AdBlocker] Allowing whitelisted CDN popup:', url);
                return true;
            }

            // Allow direct download file URLs
            if (downloadExtensions.some(ext => lowerUrl.endsWith(ext))) {
                console.log('[AdBlocker] Allowing download popup:', url);
                return true;
            }

            // BLOCK known popup ad domains
            if (popupAdDomains.some(domain => lowerUrl.includes(domain))) {
                console.log('[AdBlocker] Blocked popup ad domain:', url);
                return false;
            }

            // Block gambling/betting patterns - Expanded patterns
            const gamblingPatterns = ['1xbet', 'bet365', 'casino', 'betting', 'poker', 'slots', 'jackpot',
                                      'lottery', 'sportsbet', 'gamble', 'bookie', 'odds', 'wager',
                                      'betfair', 'betvictor', 'betfred', 'boylesports', 'sportingbet',
                                      'betsson', 'intertops', 'bovada', 'mybookie', 'betonline',
                                      'dating', 'flirt', 'adult', 'xxx', 'porn', 'sex'];
            if (gamblingPatterns.some(pattern => lowerUrl.includes(pattern))) {
                console.log('[AdBlocker] Blocked gambling/adult popup:', url);
                return false;
            }

            // Block suspicious URL patterns - Expanded for download sites
            const suspiciousPatterns = ['/aff_c', '/aff_', '/click.php', '/go.php', '/out.php', '/redirect.php',
                                        '/popunder', '/popup', 'tsyndicate', 'trkinator', 'clksite',
                                        '/interstitial', '/splash', '/overlay', '/promo', '/dl.php',
                                        '/link.php', '/shortlink', '/adlink', '/sponsorlink',
                                        'casual-sl', 'consist.org', '3ckz.com'];
            if (suspiciousPatterns.some(pattern => lowerUrl.includes(pattern))) {
                console.log('[AdBlocker] Blocked suspicious redirect popup:', url);
                return false;
            }
            
            // DISABLED: Download protection was breaking legitimate taps on download pages
            // if (isDownloadProtectionActive) {
            //     console.log('[AdBlocker] Download protection active - blocking popup:', url);
            //     return false;
            // }

            // Allow other popups by default (to not break legitimate sites)
            return true;
        } catch (e) {
            console.error('[AdBlocker] Error checking popup:', e);
            return true;
        }
    }

    // Override window.open with smart filtering
    const originalOpen = window.open;
    window.open = function(url, target, features) {
        // Block empty or about:blank popups (common ad technique)
        if (!url || url === 'about:blank' || url === '') {
             console.log('[AdBlocker] Blocked empty/about:blank popup');
             return null;
        }

        if (isAllowedPopup(url)) {
            return originalOpen.call(window, url, target, features);
        } else {
            console.log('[AdBlocker] Blocked popup:', url);
            return null;
        }
    };
    
    // ========== DOWNLOAD BUTTON PROTECTION ==========
    // DISABLED: Was too aggressive on download sites like liteapks.com
    // The protection was marking every click as a "download click" because
    // many page elements had "download" in their class/id, breaking all taps
    
    /* DISABLED - Causing tap issues on download pages
    function setupDownloadProtection() {
        // Find elements that look like download buttons/links
        const downloadSelectors = [
            'a[href*="download"]', 'button[class*="download"]', 
            'div[class*="download"]', 'span[class*="download"]',
            'a[class*="download"]', '[id*="download"]',
            'a[href*=".apk"]', 'a[href*=".zip"]', 'a[href*=".exe"]',
            '[onclick*="download"]', '[data-download]'
        ];
        
        downloadSelectors.forEach(selector => {
            document.querySelectorAll(selector).forEach(el => {
                if (el._alasProtected) return;
                el._alasProtected = true;
                
                el.addEventListener('click', function(e) {
                    window._alasDownloadClickTime = Date.now();
                    window._alasDownloadClickTarget = e.target;
                    console.log('[AdBlocker] Download element clicked, protection active for 3s');
                }, { capture: true, passive: true });
            });
        });
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', setupDownloadProtection);
    } else {
        setupDownloadProtection();
    }
    
    const downloadObserver = new MutationObserver(() => {
        setupDownloadProtection();
    });
    downloadObserver.observe(document.documentElement, { childList: true, subtree: true });
    */
    
    // ========== LOCATION REDIRECT PROTECTION ==========
    // Prevents malicious scripts from redirecting the current page to ads
    
    let lastUserInteraction = 0;
    let userClickedDownload = false;
    
    // Track real user interactions
    ['click', 'touchstart', 'keydown'].forEach(event => {
        document.addEventListener(event, function(e) {
            lastUserInteraction = Date.now();
            // Check if clicking a download button/link
            const target = e.target;
            const text = (target.innerText || target.textContent || '').toLowerCase();
            const href = (target.getAttribute && target.getAttribute('href')) || '';
            const className = (target.className || '').toLowerCase();
            const parentText = (target.parentElement?.innerText || '').toLowerCase();
            
            // Enhanced detection for download-related elements
            if (text.includes('download') || href.includes('download') || 
                target.classList?.contains('download') || target.id?.includes('download')) {
                userClickedDownload = true;
                // Reset after 5 seconds
                setTimeout(() => { userClickedDownload = false; }, 5000);
            }
        }, { passive: true });
    });
    
    // Protect location.href from ad redirects
    const originalLocation = window.location;
    let locationLocked = false;
    
    // Override location.assign and location.replace
    const originalAssign = window.location.assign;
    const originalReplace = window.location.replace;
    
    function isAllowedRedirect(url) {
        if (!url) return true;
        const lowerUrl = url.toLowerCase();
        
        // Always allow same-origin redirects
        try {
            const urlObj = new URL(url, window.location.href);
            if (urlObj.hostname === window.location.hostname) return true;
        } catch(e) {}
        
        // Allow download URLs and CDNs
        if (downloadExtensions.some(ext => lowerUrl.endsWith(ext))) return true;
        if (whitelistDomains.some(domain => lowerUrl.includes(domain))) return true;
        
        // Block if this looks like an ad redirect
        if (popupAdDomains.some(domain => lowerUrl.includes(domain))) {
            console.log('[AdBlocker] Blocked location redirect to ad:', url);
            return false;
        }
        
        // DISABLED: Download protection was breaking legitimate taps on download pages
        // if (isDownloadProtectionActive) {
        //     console.log('[AdBlocker] Download protection active - blocking external redirect:', url);
        //     return false;
        // }
        
        return true;
    }
    
    try {
        if (typeof originalAssign === 'function') {
            window.location.assign = function(url) {
                if (isAllowedRedirect(url)) {
                    return originalAssign.call(window.location, url);
                }
            };
        }
    } catch(e) {}
    
    try {
        if (typeof originalReplace === 'function') {
            window.location.replace = function(url) {
                if (isAllowedRedirect(url)) {
                    return originalReplace.call(window.location, url);
                }
            };
        }
    } catch(e) {}
    
    // Protect against direct location.href assignment via defineProperty
    try {
        const locationDescriptor = Object.getOwnPropertyDescriptor(window, 'location');
        if (locationDescriptor && locationDescriptor.configurable !== false) {
            // Can't override location directly, but we intercept navigation via other means
        }
    } catch(e) {}
    
    // ========== CLICK HIJACKING PROTECTION ==========
    // Prevents invisible overlay clicks from triggering ads
    // NOTE: Only block TRULY invisible overlays, not normal page elements

    let lastUserClick = null;

    document.addEventListener('click', function(e) {
        // Record genuine user clicks for timing check
        lastUserClick = { time: Date.now(), target: e.target };

        /* DISABLED: Causing false positives on liteapk (blocking taps)
        // Check if click target is an invisible fullscreen overlay (common ad technique)
        const el = e.target;
        if (el.tagName === 'DIV') {
            const style = window.getComputedStyle(el);
            const rect = el.getBoundingClientRect();
            const zIndex = parseInt(style.zIndex) || 0;

            // Only block if it's a FULLSCREEN transparent overlay with very high z-index
            // This is the signature of ad click-jacking, not normal page elements
            const isFullscreen = rect.width >= window.innerWidth * 0.95 && rect.height >= window.innerHeight * 0.95;
            const isTransparent = parseFloat(style.opacity) < 0.1 || style.backgroundColor === 'transparent';
            const isHighZ = zIndex > 9000;
            const isFixed = style.position === 'fixed';

            if (isFullscreen && isTransparent && isHighZ && isFixed) {
                console.log('[AdBlocker] Blocked fullscreen click hijack overlay');
                e.preventDefault();
                e.stopPropagation();
                el.remove();
                return false;
            }
        }
        */
    }, { capture: true, passive: false });

    // ========== SYNTHETIC CLICK PROTECTION ==========
    // Block programmatic clicks that open ad popups

    /* DISABLED: Causing legitimate taps to fail on some sites (liteapk)
    const originalClick = HTMLElement.prototype.click;
    HTMLElement.prototype.click = function() {
        // If this is a link with an ad URL, block it
        if (this.tagName === 'A' || this.tagName === 'BUTTON') {
            const href = this.getAttribute('href') || this.getAttribute('data-href') || '';
            if (href && !isAllowedPopup(href)) {
                // Check if this is a synthetic click (no recent user interaction)
                if (!lastUserClick || Date.now() - lastUserClick.time > 100) { // Tightened to 100ms
                    console.log('[AdBlocker] Blocked synthetic ad click:', href);
                    return;
                }
            }
        }
        return originalClick.call(this);
    };
    */

    // ========== FORM SUBMISSION HIJACK PROTECTION ==========
    // Block forms that submit to ad networks

    document.addEventListener('submit', function(e) {
        const form = e.target;
        const action = form.getAttribute('action') || '';

        if (action && !isAllowedPopup(action)) {
            console.log('[AdBlocker] Blocked ad form submission:', action);
            e.preventDefault();
            e.stopPropagation();
            return false;
        }
    }, { capture: true });

    // Minimal overlay detection with debouncing
    let cleanupScheduled = false;

    function isLegitimateUIElement(el) {
        const cls = (el.className || '').toLowerCase();
        const id = (el.id || '').toLowerCase();
        const role = (el.getAttribute('role') || '').toLowerCase();
        const ariaPopup = el.getAttribute('aria-haspopup');
        const ariaExpanded = el.getAttribute('aria-expanded');
        const uiPatterns = ['menu', 'dropdown', 'popover', 'modal', 'dialog', 'sheet',
                            'select', 'nav', 'tooltip', 'collapse', 'accordion', 'toggle',
                            'picker', 'combobox', 'listbox', 'autocomplete', 'drawer',
                            'offcanvas', 'sidebar', 'panel', 'tab', 'popper', 'backdrop'];
        if (uiPatterns.some(p => cls.includes(p) || id.includes(p) || role.includes(p))) return true;
        if (ariaPopup || ariaExpanded) return true;
        if (el.closest('[role="menu"], [role="listbox"], [role="dialog"], [role="navigation"], nav, [aria-haspopup], [aria-expanded], .dropdown, .dropdown-menu, .collapse, .accordion')) return true;
        return false;
    }

    function removeMaliciousOverlays() {
        if (cleanupScheduled) return;
        cleanupScheduled = true;

        setTimeout(() => {
            const suspiciousKeywords = [
                'verify you are not a robot', 'click allow to continue',
                'enable notifications', 'you have won', 'congratulations',
                'claim your prize', 'free spin', 'bonus waiting',
                'update your browser', 'virus detected', 'download now',
                'your device may be infected', 'install our app', 'subscribe now',
                'press allow', 'click here to continue', 'waiting for verification',
                'claim reward', 'free gift', 'you are winner', 'spin the wheel',
                'limited time offer', 'act now', 'your prize', 'lucky visitor'
            ];

            document.querySelectorAll('div[style*="position: fixed"]').forEach(el => {
                if (isLegitimateUIElement(el)) return;
                const style = window.getComputedStyle(el);
                const zIndex = parseInt(style.zIndex) || 0;
                const rect = el.getBoundingClientRect();
                const isFullscreen = rect.width >= window.innerWidth * 0.9 && rect.height >= window.innerHeight * 0.9;

                if (zIndex > 9000 && isFullscreen) {
                    const text = (el.innerText || '').toLowerCase();
                    if (suspiciousKeywords.some(kw => text.includes(kw))) {
                        console.log('[AdBlocker] Removed malicious overlay:', text.substring(0, 50));
                        el.remove();
                    }
                }
            });

            cleanupScheduled = false;
        }, 100);
    }

    // ENHANCED ad removal - comprehensive selectors including gambling/crypto
    function removeVisibleAds() {
        const adSelectors = [
            // Ad network iframes
            'iframe[src*="monetag"]', 'iframe[src*="exosrv"]', 'iframe[src*="exdynsrv"]',
            'iframe[src*="realsrv"]', 'iframe[src*="magsrv"]', 'iframe[src*="onclicka"]',
            'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
            'iframe[src*="popads"]', 'iframe[src*="popcash"]', 'iframe[src*="adsterra"]',
            'iframe[src*="trafficjunky"]', 'iframe[src*="juicyads"]',
            // Google ads
            'ins.adsbygoogle', '.adsbygoogle',
            // Ad containers
            'div[id*="popunder"]', 'div[class*="popunder"]', 'div[class*="adsbox"]',
            // Gambling/Crypto ads - BC.GAME, YOLO247, 1xbet, etc.
            'a[href*="bc.game"]', 'a[href*="bcgame"]', 'a[href*="1xbet"]',
            'a[href*="yolo247"]', 'a[href*="yolo24"]', 'a[href*="bet365"]',
            'a[href*="stake.com"]', 'a[href*="roobet"]', 'a[href*="rollbit"]',
            'a[href*="highcpmgate"]', 'a[href*="profitabledisplay"]',
            'a[href*="strpjmp"]', 'a[href*="monetag"]',
            'img[src*="bc.game"]', 'img[src*="bcgame"]', 'img[src*="1xbet"]',
            'img[src*="yolo247"]', 'img[src*="yolo24"]',
            // Native ad platforms
            'div[id*="taboola"]', 'div[id*="outbrain"]', 'div[id*="mgid"]',
            '.taboola', '.outbrain', '.mgid'
        ];

        adSelectors.forEach(selector => {
            try {
                document.querySelectorAll(selector).forEach(el => {
                    // Remove element and its parent if parent is just an ad wrapper
                    el.style.display = 'none';
                    el.style.visibility = 'hidden';
                    
                    // Check if parent is just an ad container
                    const parent = el.parentElement;
                    if (parent && parent.children.length === 1) {
                        const pClass = (parent.className || '').toLowerCase();
                        const pId = (parent.id || '').toLowerCase();
                        if (pClass.includes('ad') || pId.includes('ad') || 
                            pClass.includes('banner') || pClass.includes('sponsor')) {
                            parent.style.display = 'none';
                        }
                    }
                });
            } catch (e) {}
        });
        
        // Additional: Find and hide elements containing gambling ad images
        document.querySelectorAll('img').forEach(img => {
            const src = (img.src || '').toLowerCase();
            const alt = (img.alt || '').toLowerCase();
            if (src.includes('bc.game') || src.includes('bcgame') || 
                src.includes('1xbet') || src.includes('yolo') ||
                src.includes('casino') || src.includes('betting') ||
                alt.includes('casino') || alt.includes('betting') ||
                alt.includes('bonus') || alt.includes('crypto game')) {
                // Hide the image and its container
                img.style.display = 'none';
                const container = img.closest('a, div');
                if (container) container.style.display = 'none';
            }
        });
    }

    // Efficient mutation observer with throttling
    let observerTimeout;
    const observer = new MutationObserver(() => {
        clearTimeout(observerTimeout);
        observerTimeout = setTimeout(() => {
            removeMaliciousOverlays();
            removeVisibleAds();
        }, 150); // Slightly reduced throttle for faster response
    });

    observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['src', 'href']
    });

    // Initial cleanup
    document.addEventListener('DOMContentLoaded', () => {
        removeMaliciousOverlays();
        removeVisibleAds();
    }, { once: true });

    // Run once after page load
    window.addEventListener('load', () => {
        removeMaliciousOverlays();
        removeVisibleAds();
    }, { once: true });

    // ========== XHR/FETCH INTERCEPTION (Like Opera Browser) ==========
    // NOTE: Network.init() already intercepts fetch/XHR above.
    // This section only handles script/iframe injection blocking via MutationObserver.
    
    // SCRIPT INJECTION BLOCKING is handled by Network.interceptScripts() above.

    // ══════════════════════════════════════════════════════════════════
    // INITIALIZATION - Run all modules
    // ══════════════════════════════════════════════════════════════════
    
    // Immediate: CSS injection
    Cosmetic.inject();
    
    // Immediate: Network interception
    Network.init();
    
    // Deferred: Procedural scanning (use requestIdleCallback if available)
    const runProcedural = () => {
        Procedural.scanAndHide();
        removeMaliciousOverlays();
        removeVisibleAds();
    };
    
    if (window.requestIdleCallback) {
        requestIdleCallback(runProcedural, { timeout: 1000 });
    } else {
        setTimeout(runProcedural, 100);
    }
    
    // Run again after DOM ready and page load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', runProcedural, { once: true });
    }
    window.addEventListener('load', () => {
        runProcedural();
        setTimeout(runProcedural, 1000); // Catch late-loaded ads
        setTimeout(runProcedural, 3000); // Final sweep
    }, { once: true });

    console.log('[Alas AdBlocker v$BLOCKER_VERSION] Initialized - Modules: Cosmetic, Procedural, Network');
})();
        """.trimIndent()
    }
   
    fun getBlockedCount(): Int = blockedCount.get()
   
    fun getBlockedToday(): Int {
        resetDailyStatsIfNeeded()
        return blockedAdsToday.get()
    }
   
    fun getDataSaved(): Long = dataSavedBytes.get().toLong()
   
    fun getTimeSaved(): Long = timesSavedMs.get().toLong()
    
    private fun resetDailyStatsIfNeeded() {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        if (now - lastResetTime > dayMs) {
            blockedAdsToday.set(0)
            lastResetTime = now
        }
    }
   
    fun recordBlockPublic(url: String, pageDomain: String? = null) {
        recordBlock(url, pageDomain)
    }
    
    private fun recordBlock(url: String, pageDomain: String? = null) {
        blockedCount.incrementAndGet()
        blockedAdsToday.incrementAndGet()
       
        // Estimate data saved (avg ad is ~50KB)
        dataSavedBytes.addAndGet(50 * 1024)
       
        // Estimate time saved (avg ad loads in ~200ms)
        timesSavedMs.addAndGet(200)
       
        // Track domain statistics
        try {
            val blockedDomain = url.split("//").getOrNull(1)?.split("/")?.firstOrNull() ?: ""
            if (blockedDomain.isNotEmpty()) {
                blockingStats.computeIfAbsent(blockedDomain) { AtomicInteger(0) }.incrementAndGet()
            }
           
            // Track per-site stats if we know which page is being viewed
            val site = pageDomain ?: currentPageDomain
            if (site.isNotEmpty() && blockedDomain.isNotEmpty()) {
                val isTracker = trackerDomains.any { blockedDomain.contains(it) || url.contains(it) }
                if (isTracker) {
                    perSiteTrackersBlocked.computeIfAbsent(site) { AtomicInteger(0) }.incrementAndGet()
                    perSiteTrackerDomains.computeIfAbsent(site) { ConcurrentHashMap.newKeySet() }.add(blockedDomain)
                } else {
                    perSiteAdsBlocked.computeIfAbsent(site) { AtomicInteger(0) }.incrementAndGet()
                    perSiteAdDomains.computeIfAbsent(site) { ConcurrentHashMap.newKeySet() }.add(blockedDomain)
                }
            }
        } catch (_: Exception) {
            // Ignore parsing errors
        }
    }
    
    fun setExcludedSites(sites: Set<String>) {
        excludedSites.clear()
        excludedSites.addAll(sites)
        hostCache.clear() // Clear cache when excluded sites change
    }
   
    // Current page domain for per-site tracking
    @Volatile
    private var currentPageDomain: String = ""
   
    fun setCurrentPageDomain(domain: String) {
        currentPageDomain = domain.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .split("/").firstOrNull() ?: ""
    }
   
    fun getAdsBlockedForSite(domain: String): Int {
        val normalizedDomain = normalizeDomain(domain)
        return perSiteAdsBlocked[normalizedDomain]?.get() ?: 0
    }
   
    fun getTrackersBlockedForSite(domain: String): Int {
        val normalizedDomain = normalizeDomain(domain)
        return perSiteTrackersBlocked[normalizedDomain]?.get() ?: 0
    }
   
    fun getBlockedAdDomainsForSite(domain: String): List<String> {
        val normalizedDomain = normalizeDomain(domain)
        return perSiteAdDomains[normalizedDomain]?.toList() ?: emptyList()
    }
   
    fun getBlockedTrackerDomainsForSite(domain: String): List<String> {
        val normalizedDomain = normalizeDomain(domain)
        return perSiteTrackerDomains[normalizedDomain]?.toList() ?: emptyList()
    }
    
    private fun normalizeDomain(domain: String): String {
        return domain.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .split("/").firstOrNull() ?: ""
    }
    
    fun isSiteExcluded(domain: String): Boolean {
        val normalizedDomain = normalizeDomain(domain)
        return excludedSites.any { normalizedDomain.contains(it.lowercase()) }
    }
   
    fun addExcludedSite(domain: String) {
        val normalizedDomain = normalizeDomain(domain)
        if (normalizedDomain.isNotEmpty()) {
            excludedSites.add(normalizedDomain)
            hostCache.clear()
        }
    }
   
    fun removeExcludedSite(domain: String) {
        val normalizedDomain = normalizeDomain(domain)
        excludedSites.removeIf { it.equals(normalizedDomain, ignoreCase = true) }
        hostCache.clear()
    }
    
    // ============================================================
    // 🧠 ADAPTIVE LEARNING SYSTEM
    // ============================================================
    
    /**
     * Learn a new ad domain from user reports or detection
     */
    fun learnAdDomain(domain: String) {
        val normalized = normalizeDomain(domain)
        if (normalized.isNotEmpty() && 
            !DOWNLOAD_CDN_WHITELIST.any { normalized.contains(it) } &&
            !essentialDomains.any { normalized.endsWith(it) || normalized == it } &&
            !essentialDomainPatterns.any { normalized.startsWith(it) }) {
            learnedAdDomains.add(normalized)
            hostCache.clear()
            Log.d("SimpleAdBlocker", "🧠 Learned new ad domain: $normalized")
        }
    }
    
    /**
     * Learn a new ad pattern from detection
     */
    fun learnAdPattern(pattern: String) {
        if (pattern.length >= 5 && !pattern.contains("download")) {
            learnedAdPatterns.add(pattern.lowercase())
            hostCache.clear()
            Log.d("SimpleAdBlocker", "🧠 Learned new ad pattern: $pattern")
        }
    }
    
    /**
     * Check if URL matches learned patterns
     */
    private fun matchesLearnedPatterns(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return learnedAdDomains.any { lowerUrl.contains(it) } ||
               learnedAdPatterns.any { lowerUrl.contains(it) }
    }
    
    /**
     * Get learned domains for persistence
     */
    fun getLearnedDomains(): Set<String> = learnedAdDomains.toSet()
    
    /**
     * Load previously learned domains
     */
    fun loadLearnedDomains(domains: Set<String>) {
        learnedAdDomains.clear()
        learnedAdDomains.addAll(domains)
    }
    
    // ============================================================
    // 📍 SMART BACK NAVIGATION
    // ============================================================
    
    /**
     * Record a navigation event - determines if it's a real navigation or ad redirect
     * Call this from onLocationChange in GeckoViewContainer
     */
    fun recordNavigation(tabId: String, url: String, hasUserGesture: Boolean, timeSinceLastNav: Long, fromUrl: String? = null) {
        val lowerUrl = url.lowercase()
        
        // Skip empty or about: URLs
        if (url.isBlank() || url.startsWith("about:")) return
        
        // Get or create navigation stack for this tab
        val navStack = realNavigationStack.getOrPut(tabId) { java.util.ArrayDeque() }
        
        // Use fromUrl if navStack is empty (e.g. after restart)
        val previousUrl = navStack.peekLast() ?: fromUrl
        
        // 🎬 YOUTUBE INTERSTITIAL PROTECTION: Block YouTube videos from manga sites
        if (isYouTubeInterstitialAd(url, previousUrl)) {
            Log.d("SimpleAdBlocker", "🚫 Blocking YouTube interstitial ad from history: $url")
            
            // Learn this pattern
            learnAdDomain(extractDomain(url))
            
            // Track as ad redirect
            adRedirectTimestamps[tabId] = System.currentTimeMillis()
            
            // DON'T add to navigation stack
            return
        }
        
        // 🎌 MANGA TAP HIJACK PROTECTION: Detect manga -> YouTube hijacking
        if (isMangaTapHijack(url, previousUrl)) {
            Log.d("SimpleAdBlocker", "🎌 Preventing manga tap hijack to YouTube: $url")
            
            // Learn this pattern
            learnAdDomain(extractDomain(url))
            
            // Track as ad redirect
            adRedirectTimestamps[tabId] = System.currentTimeMillis()
            
            // DON'T add YouTube to stack, keep manga page
            return
        }
        
        // 🎌 MANGA SITE SPECIFIC: Skip recording ad pages entirely
        if (isMangaAdPage(url) && !hasUserGesture) {
            Log.d("SimpleAdBlocker", "🎌 Skipping manga ad page in history: $url")
            
            // But still learn the ad domain
            val adDomain = extractDomain(url)
            if (adDomain.isNotEmpty()) {
                learnAdDomain(adDomain)
            }
            
            // Track as ad redirect
            adRedirectTimestamps[tabId] = System.currentTimeMillis()
            return
        }
        
        // Check if this URL belongs to an essential/auth domain
        val host = try {
            android.net.Uri.parse(url).host?.lowercase() ?: ""
        } catch (e: Exception) { "" }
        val isEssentialDomain = essentialDomains.any { host.endsWith(it) || host == it } ||
            essentialDomainPatterns.any { host.startsWith(it) }
        
        // Determine if this is a real navigation or ad redirect
        val isRealNavigation = when {
            // User gesture means real navigation
            hasUserGesture -> true
            
            // Essential domains (Google, Realme, etc.) are always real navigation
            isEssentialDomain -> true
            
            // Download CDN URLs are always legitimate
            isDownloadCdnUrl(url) -> true
            
            // Known ad/popup domains are not real navigation
            isPopupAd(url) -> false
            shouldBlock(url) -> false
            matchesLearnedPatterns(url) -> false
            isMangaAdPage(url) -> false  // 🎌 Manga ad pages are not real navigation
            
            // Very fast redirects (< 500ms) are likely ads
            timeSinceLastNav < 500 && navStack.isNotEmpty() -> false
            
            // Cross-domain redirects within 1 second are suspicious
            timeSinceLastNav < 1000 && navStack.isNotEmpty() -> {
                val lastUrl = navStack.peekLast() ?: ""
                val lastDomain = extractDomain(lastUrl)
                val currentDomain = extractDomain(url)
                lastDomain != currentDomain && !isDownloadCdnUrl(url)
            }
            
            // Default: treat as real navigation
            else -> true
        }
        
        if (isRealNavigation) {
            // Add to real navigation stack (limit size to prevent memory issues)
            // Deduplicate: don't push if same as current top
            if (navStack.peekLast() != url) {
                if (navStack.size >= 50) navStack.removeFirst()
                navStack.addLast(url)
                Log.d("SimpleAdBlocker", "📍 Real navigation recorded: $url")
            }
        } else {
            // Track this as ad redirect for learning
            adRedirectTimestamps[tabId] = System.currentTimeMillis()
            
            // Learn the domain if it redirected us
            val redirectDomain = extractDomain(url)
            if (redirectDomain.isNotEmpty()) {
                learnAdDomain(redirectDomain)
            }
            Log.d("SimpleAdBlocker", "🚫 Ad redirect detected (not added to history): $url")
        }
    }
    
    /**
     * Get the previous REAL page URL (skipping ad redirects)
     * Use this for smart back navigation
     * @deprecated Use peekPreviousRealUrl + popCurrentRealUrl instead
     */
    fun getPreviousRealUrl(tabId: String): String? {
        val navStack = realNavigationStack[tabId] ?: return null
        
        // Remove current page
        if (navStack.size > 1) {
            navStack.removeLast()
            return navStack.peekLast()
        }
        return null
    }
    
    fun peekPreviousRealUrl(tabId: String): String? {
        val navStack = realNavigationStack[tabId] ?: return null
        val list = navStack.toList()
        return if (list.size >= 2) list[list.lastIndex - 1] else null
    }
    
    fun popCurrentRealUrl(tabId: String): Boolean {
        val navStack = realNavigationStack[tabId] ?: return false
        if (navStack.isEmpty()) return false
        navStack.removeLast()
        return true
    }

    /**
     * Get the safe back URL based on current position
     * If currentUrl is NOT the top of the stack (e.g. we are on an Ad page), return the TOP of the stack.
     * If currentUrl IS the top of the stack, return the PREVIOUS item.
     */
    fun getSafeBackUrl(tabId: String, currentUrl: String): String? {
        val navStack = realNavigationStack[tabId] ?: return null
        if (navStack.isEmpty()) return null
        
        val top = navStack.peekLast() ?: return null
        
        // Normalize URLs for comparison (ignore trailing slashes)
        val normCurrent = currentUrl.trim().trimEnd('/')
        val normTop = top.trim().trimEnd('/')
        
        // If we are currently on the top page, go to the one before it
        if (normTop == normCurrent) {
             val list = navStack.toList()
             return if (list.size >= 2) list[list.lastIndex - 1] else null
        }
        
        // If we are NOT on the top page (e.g. on an unrecorded Ad page), go to the top page
        return top
    }
    
    /**
     * Check if URL is a manga site ad page (kingofshojo, mangakakalot, etc.)
     * These sites use aggressive ad networks that hijack the back button
     */
    fun isMangaAdPage(url: String): Boolean {
        val lowerUrl = url.lowercase()
        
        // Check if it's a known manga site first
        val isMangaSite = lowerUrl.contains("kingofshojo") || 
                          lowerUrl.contains("mangakakalot") ||
                          lowerUrl.contains("manganato") ||
                          lowerUrl.contains("mangadex") ||
                          lowerUrl.contains("mangafire") ||
                          lowerUrl.contains("readm.org")
        
        if (!isMangaSite) {
            // Check ad patterns for any site
            return isPopupAd(url) || 
                   matchesLearnedPatterns(url) ||
                   lowerUrl.contains("tsyndicate") ||
                   lowerUrl.contains("exdynsrv") ||
                   lowerUrl.contains("trkinator") ||
                   lowerUrl.contains("clksite") ||
                   lowerUrl.contains("strpjmp") ||
                   lowerUrl.contains("/popunder") ||
                   lowerUrl.contains("/redirect")
        }
        
        // For manga sites, be more aggressive with ad detection
        return lowerUrl.contains("tsyndicate") ||
               lowerUrl.contains("exdynsrv") ||
               lowerUrl.contains("trkinator") ||
               lowerUrl.contains("clksite") ||
               lowerUrl.contains("clkrev") ||
               lowerUrl.contains("strpjmp") ||
               lowerUrl.contains("/go/") ||
               lowerUrl.contains("/out/") ||
               lowerUrl.contains("/go.php") ||
               lowerUrl.contains("/out.php") ||
               lowerUrl.contains("/redirect.php") ||
               lowerUrl.contains("/click.php") ||
               lowerUrl.contains("?redirect=") ||
               lowerUrl.contains("&url=") ||
               lowerUrl.contains("?url=") ||
               lowerUrl.contains("&target=") ||
               (lowerUrl.contains("kingofshojo") && 
                (lowerUrl.contains("/redirect") || lowerUrl.contains("/ads/")))
    }
    
    /**
     * Get the safe back URL that skips ad redirects
     * Enhanced version for manga sites with aggressive ad redirects
     */
    fun getSafeBackUrlForManga(tabId: String, currentUrl: String): String? {
        val navStack = realNavigationStack[tabId] ?: return null
        if (navStack.isEmpty()) return null
        
        // 🎌 MANGA SITE SPECIFIC: Check if current URL is an ad page
        val isCurrentAdPage = isMangaAdPage(currentUrl)
        
        // If current page is an ad and we have real navigation history, 
        // skip directly to the last real page
        if (isCurrentAdPage && navStack.isNotEmpty()) {
            // Find the last non-ad page in history
            val realPages = navStack.toList().filterNot { isMangaAdPage(it) }
            if (realPages.isNotEmpty()) {
                Log.d("SimpleAdBlocker", "🎌 Manga ad detected, skipping to last real page: ${realPages.last()}")
                return realPages.last()
            }
        }
        
        // Normal back navigation logic
        return getSafeBackUrl(tabId, currentUrl)
    }
    
    /**
     * Check if URL is a YouTube interstitial ad (common on manga sites)
     */
    fun isYouTubeInterstitialAd(url: String, referrer: String? = null): Boolean {
        if (url.isBlank()) return false
        
        val lowerUrl = url.lowercase()
        val lowerReferrer = referrer?.lowercase() ?: ""
        
        // Check if it's a YouTube URL
        val isYouTube = lowerUrl.contains("youtube.com") || 
                        lowerUrl.contains("youtu.be") ||
                        lowerUrl.contains("googlevideo.com")
        
        if (!isYouTube) return false
        
        // Check if coming from a manga site
        val isFromMangaSite = lowerReferrer.contains("kingofshojo") ||
                              lowerReferrer.contains("mangakakalot") ||
                              lowerReferrer.contains("manganato") ||
                              lowerReferrer.contains("mangadex") ||
                              lowerReferrer.contains("manga")
        
        // YouTube links from manga sites are ALWAYS ads
        if (isFromMangaSite) {
            Log.d("SimpleAdBlocker", "🚫 YouTube interstitial from manga site: $url")
            return true
        }
        
        // Check for ad patterns
        val isAdPattern = YOUTUBE_INTERSTITIAL_PATTERNS.any { lowerUrl.contains(it) } ||
                          lowerUrl.contains("&ad") ||
                          lowerUrl.contains("?ad") ||
                          lowerUrl.contains("/ad_")
        
        return isAdPattern
    }
    
    /**
     * Check if this is a manga site tap hijack (tap -> YouTube video)
     */
    fun isMangaTapHijack(currentUrl: String, previousUrl: String?): Boolean {
        val lowerCurrent = currentUrl.lowercase()
        val lowerPrevious = previousUrl?.lowercase() ?: ""
        
        // Check if we went from manga site to YouTube
        val wasOnManga = lowerPrevious.contains("kingofshojo") ||
                         lowerPrevious.contains("mangakakalot") ||
                         lowerPrevious.contains("manganato") ||
                         lowerPrevious.contains("mangadex") ||
                         lowerPrevious.contains("/manga/") ||
                         lowerPrevious.contains("/chapter/")
        
        val nowOnYouTube = lowerCurrent.contains("youtube.com") ||
                           lowerCurrent.contains("youtu.be") ||
                           lowerCurrent.contains("googlevideo.com")
        
        if (wasOnManga && nowOnYouTube) {
            Log.d("SimpleAdBlocker", "🎌 Manga tap hijack detected: $previousUrl -> $currentUrl")
            return true
        }
        
        return false
    }
    
    /**
     * Get the last manga page from navigation history
     */
    fun getLastMangaPage(tabId: String): String? {
        val navStack = realNavigationStack[tabId] ?: return null
        return navStack.toList().reversed().firstOrNull { url ->
            val lowerUrl = url.lowercase()
            (lowerUrl.contains("kingofshojo") ||
             lowerUrl.contains("mangakakalot") ||
             lowerUrl.contains("manganato") ||
             lowerUrl.contains("mangadex") ||
             lowerUrl.contains("/manga/") ||
             lowerUrl.contains("/chapter/")) &&
            !lowerUrl.contains("youtube") &&
            !isMangaAdPage(url)
        }
    }
    
    // Per-tab login compatibility mode flag
    private val loginCompatTabs = ConcurrentHashMap.newKeySet<String>()
    
    fun setLoginCompatMode(tabId: String, enabled: Boolean) {
        if (enabled) loginCompatTabs.add(tabId) else loginCompatTabs.remove(tabId)
    }
    
    fun isInLoginCompatMode(tabId: String): Boolean = tabId in loginCompatTabs
    
    /**
     * Find the last non-auth URL in the navigation stack (without modifying the stack)
     * Used to find the origin site after an OAuth flow completes
     */
    fun getLastNonAuthUrl(tabId: String): String? {
        val navStack = realNavigationStack[tabId] ?: return null
        return navStack.reversed().firstOrNull { url ->
            !url.contains("accounts.google.com") &&
            !url.contains("accounts.youtube.com") &&
            !url.contains("accounts.google.co.") &&
            !url.contains("login.microsoftonline.com") &&
            !url.contains("appleid.apple.com") &&
            url.startsWith("http")
        }
    }
    
    /**
     * Get the last N real URLs for a tab
     */
    fun getRealNavigationHistory(tabId: String, count: Int = 10): List<String> {
        val navStack = realNavigationStack[tabId] ?: return emptyList()
        return navStack.toList().takeLast(count)
    }
    
    /**
     * Clear navigation history for a tab
     */
    fun clearNavigationHistory(tabId: String) {
        realNavigationStack.remove(tabId)
        redirectChainTracker.remove(tabId)
        adRedirectTimestamps.remove(tabId)
    }
    
    /**
     * Check if we recently had an ad redirect (within last 2 seconds)
     * Useful for determining if back button should skip multiple entries
     */
    fun hadRecentAdRedirect(tabId: String): Boolean {
        val lastRedirect = adRedirectTimestamps[tabId] ?: return false
        return System.currentTimeMillis() - lastRedirect < 2000
    }
    
    private fun extractDomain(url: String): String {
        return try {
            url.lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .split("/").firstOrNull()
                ?.split(":")?.firstOrNull() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    // ============================================================
    // 🌍 REGIONAL AD BLOCKING
    // ============================================================
    
    /**
     * Initialize regional ad lists
     */
    fun initializeRegionalLists() {
        // Indian ad networks
        regionalAdDomains["IN"] = setOf(
            "mgid.com", "taboola.com", "outbrain.com",
            "adblade.com", "adgebra.in", "media.net",
            "inmobi.com", "vserv.mobi", "revenuewire.com",
            "webads.in", "adiquity.com", "smaato.net"
        )
        
        // Chinese ad networks
        regionalAdDomains["CN"] = setOf(
            "baidu.com/adserver", "alimama.com", "tanx.com",
            "mediav.com", "ipinyou.com", "youku.com/ad",
            "qq.com/ad", "163.com/ad", "sina.com/ad"
        )
        
        // Russian ad networks  
        regionalAdDomains["RU"] = setOf(
            "adfox.ru", "yandex.ru/ads", "begun.ru",
            "rambler.ru/ad", "mail.ru/ad", "rbc.ru/ad"
        )
        
        // Southeast Asian ad networks
        regionalAdDomains["SEA"] = setOf(
            "adnow.com", "propellerads.com", "hilltopads.com",
            "adsterra.com", "popads.net", "popcash.net",
            "trafficstars.com", "clickadu.com"
        )
        
        Log.d("SimpleAdBlocker", "🌍 Regional ad lists initialized")
    }
    
    /**
     * Check if URL is blocked by regional filters
     */
    fun isBlockedByRegion(url: String, regionCode: String): Boolean {
        val lowerUrl = url.lowercase()
        val regionalDomains = regionalAdDomains[regionCode] ?: return false
        return regionalDomains.any { lowerUrl.contains(it) }
    }
    
    // ============================================================
    // 🔗 LITEAPKS / APK DOWNLOAD SITE SUPPORT
    // ============================================================
    
    // Specific patterns for download sites like liteapks.com
    private val downloadSitePatterns = setOf(
        "liteapks.com", "apkpure.com", "apkmirror.com", "happymod.com",
        "an1.com", "modyolo.com", "apkmody.io", "moddroid.com",
        "apkdone.com", "revdl.com", "rexdl.com", "androeed.ru"
    )
    
    // Track active download sessions (tabId -> last download page URL)
    private val activeDownloadSessions = ConcurrentHashMap<String, DownloadSession>()
    
    data class DownloadSession(
        val startUrl: String,
        val siteDomain: String,
        val startTime: Long = System.currentTimeMillis()
    )
    
    /**
     * Start tracking a download session when user clicks download on a mod site
     */
    fun startDownloadSession(tabId: String, url: String) {
        val lowerUrl = url.lowercase()
        val site = downloadSitePatterns.find { lowerUrl.contains(it) }
        if (site != null && (lowerUrl.contains("/download") || lowerUrl.contains("/dl/"))) {
            activeDownloadSessions[tabId] = DownloadSession(url, site)
            Log.d("SimpleAdBlocker", "📥 Started download session for $site: $url")
        }
    }
    
    /**
     * Check if URL is part of an active download session
     */
    fun isInDownloadSession(tabId: String, url: String): Boolean {
        val session = activeDownloadSessions[tabId] ?: return false
        val lowerUrl = url.lowercase()
        
        // Session expires after 60 seconds
        if (System.currentTimeMillis() - session.startTime > 60000) {
            activeDownloadSessions.remove(tabId)
            return false
        }
        
        // Check if URL is from the same download site or a CDN
        val isSameSite = lowerUrl.contains(session.siteDomain)
        val isCdn = isDownloadCdnUrl(url)
        
        if (isSameSite || isCdn) {
            Log.d("SimpleAdBlocker", "📥 URL in active download session: $url")
            return true
        }
        
        return false
    }
    
    /**
     * End download session (call when download completes or user navigates away)
     */
    fun endDownloadSession(tabId: String) {
        activeDownloadSessions.remove(tabId)
    }
    
    /**
     * Check if this is a legitimate download site navigation sequence
     * Handles: liteapks.com/download/app -> /download/app/1 -> cdn.9mod.cloud/...
     */
    fun isLegitimateDownloadSequence(url: String, previousUrl: String?): Boolean {
        val lowerUrl = url.lowercase()
        val lowerPrevUrl = previousUrl?.lowercase() ?: ""
        
        // ✅ PRIORITY 1: Check if URL is a CDN download
        if (isDownloadCdnUrl(url)) {
            Log.d("SimpleAdBlocker", "✅ CDN download URL: $url")
            return true
        }
        
        // ✅ PRIORITY 2: Check if we're on a known download site
        val isCurrentDownloadSite = downloadSitePatterns.any { lowerUrl.contains(it) }
        val isPrevDownloadSite = downloadSitePatterns.any { lowerPrevUrl.contains(it) }
        
        if (!isCurrentDownloadSite && !isPrevDownloadSite) return false
        
        // ✅ PRIORITY 3: LiteApks specific patterns
        // Pattern: liteapks.com/download/xxx-123456 -> liteapks.com/download/xxx-123456/1
        if (lowerUrl.contains("liteapks.com")) {
            // Any URL on liteapks.com with /download/ is legitimate
            if (lowerUrl.contains("/download/")) {
                Log.d("SimpleAdBlocker", "✅ LiteApks download page: $url")
                return true
            }
        }
        
        // ✅ PRIORITY 4: General download site patterns
        // Pattern: /download/xxx or /dl/xxx or /get/xxx
        val downloadPathPatterns = listOf("/download/", "/dl/", "/get/", "/file/", "/files/")
        val hasDownloadPath = downloadPathPatterns.any { lowerUrl.contains(it) }
        val prevHasDownloadPath = downloadPathPatterns.any { lowerPrevUrl.contains(it) }
        
        if (hasDownloadPath && isCurrentDownloadSite) {
            Log.d("SimpleAdBlocker", "✅ Download site page: $url")
            return true
        }
        
        // ✅ PRIORITY 5: Redirect from download page to CDN or numbered page
        if (prevHasDownloadPath && isPrevDownloadSite) {
            // Numbered page pattern: /download/app/1, /download/app/2, etc.
            val numberedPagePattern = Regex("/\\d+/?$")
            if (numberedPagePattern.containsMatchIn(lowerUrl)) {
                Log.d("SimpleAdBlocker", "✅ Download numbered page: $url")
                return true
            }
            
            // Still on same site
            if (isCurrentDownloadSite) {
                Log.d("SimpleAdBlocker", "✅ Still on download site: $url")
                return true
            }
        }
        
        return false
    }
    
    /**
     * Enhanced shouldBlock that includes learned patterns and regional blocking
     */
    fun shouldBlockEnhanced(url: String, regionCode: String? = null, previousUrl: String? = null): Boolean {
        if (url.isBlank()) return false
        
        // Check whitelist first
        if (isDownloadCdnUrl(url)) return false
        
        // Check if it's a legitimate download sequence
        if (isLegitimateDownloadSequence(url, previousUrl)) return false
        
        // Standard blocking
        if (shouldBlock(url)) return true
        
        // Check learned patterns
        if (matchesLearnedPatterns(url)) return true
        
        // Check regional blocking
        if (regionCode != null && isBlockedByRegion(url, regionCode)) return true
        
        return false
    }
}
