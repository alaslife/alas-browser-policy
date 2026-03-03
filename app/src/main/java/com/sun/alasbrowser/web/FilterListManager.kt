package com.sun.alasbrowser.web

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

data class FilterList(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val category: String = "recommended"
)

object FilterListManager {
    
    val availableFilterLists = listOf(
        // Recommended lists
        FilterList(
            "easylist",
            "EasyList",
            "Filter list for common sites",
            "https://easylist.to/easylist/easylist.txt",
            "recommended"
        ),
        FilterList(
            "easyprivacy",
            "EasyPrivacy",
            "Privacy protection list",
            "https://easylist.to/easylist/easyprivacy.txt",
            "recommended"
        ),
        FilterList(
            "nocoin",
            "NoCoin",
            "Cryptocurrency mining protection",
            "https://raw.githubusercontent.com/hoshsadiq/adblock-nocoin-list/master/nocoin.txt",
            "recommended"
        ),
        FilterList(
            "ublock-filters",
            "uBlock Filters",
            "Filter list from uBlock Origin with additional filters",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
            "recommended"
        ),
        FilterList(
            "ublock-privacy",
            "uBlock Privacy",
            "Privacy filters from uBlock Origin",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
            "recommended"
        ),
        FilterList(
            "ublock-badware",
            "uBlock Badware",
            "Badware risks filters from uBlock Origin",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
            "recommended"
        ),
        
        // Other lists
        FilterList(
            "adguard-base",
            "AdGuard Base",
            "Filter list from AdGuard with additional filters for common sites",
            "https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/BaseFilter/sections/adservers.txt",
            "other"
        ),
        FilterList(
            "adguard-mobile",
            "AdGuard Mobile Ads",
            "Filter list from AdGuard for mobile variants of common sites",
            "https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/MobileFilter/sections/adservers.txt",
            "other"
        ),
        FilterList(
            "adguard-mobile-app",
            "AdGuard Mobile App Ads",
            "Filter list for mobile app advertisements",
            "https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/MobileFilter/sections/adservers_firstparty.txt",
            "other"
        ),
        FilterList(
            "adguard-tracking",
            "AdGuard Tracking Protection",
            "Protection against web analytics and tracking",
            "https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/SpywareFilter/sections/tracking_servers.txt",
            "other"
        ),
        FilterList(
            "ublock-annoyances",
            "uBlock Annoyances",
            "Removes annoyances like cookie notices",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/annoyances.txt",
            "other"
        ),
        FilterList(
            "fanboy-annoyance",
            "Fanboy Annoyance",
            "Blocks annoying elements on websites",
            "https://easylist.to/easylist/fanboy-annoyance.txt",
            "other"
        ),
        
        // Regional lists
        FilterList(
            "easylist-china",
            "EasyList China",
            "Chinese supplement for EasyList",
            "https://easylist-downloads.adblockplus.org/easylistchina.txt",
            "regional"
        ),
        FilterList(
            "easylist-germany",
            "EasyList Germany",
            "German supplement for EasyList",
            "https://easylist-downloads.adblockplus.org/easylistgermany.txt",
            "regional"
        ),
        FilterList(
            "easylist-italy",
            "EasyList Italy",
            "Italian supplement for EasyList",
            "https://easylist-downloads.adblockplus.org/easylistitaly.txt",
            "regional"
        ),
        FilterList(
            "easylist-dutch",
            "EasyList Dutch",
            "Dutch supplement for EasyList",
            "https://easylist-downloads.adblockplus.org/easylistdutch.txt",
            "regional"
        ),
        FilterList(
            "easylist-spanish",
            "EasyList Spanish",
            "Spanish supplement for EasyList",
            "https://easylist-downloads.adblockplus.org/easylistspanish.txt",
            "regional"
        ),
        FilterList(
            "easylist-french",
            "EasyList French",
            "French supplement for EasyList",
            "https://easylist-downloads.adblockplus.org/liste_fr.txt",
            "regional"
        ),
        FilterList(
            "ruadlist",
            "RuAdList",
            "Russian ad blocking filter",
            "https://easylist-downloads.adblockplus.org/advblock.txt",
            "regional"
        ),
        FilterList(
            "indianlist",
            "IndianList",
            "Indian supplement for EasyList",
            "https://easylist-downloads.adblockplus.org/indianlist.txt",
            "regional"
        ),
        
        // Additional recommended lists
        FilterList(
            "adguard-mobile-ublock",
            "AdGuard Mobile Ads (uBlock)",
            "AdGuard mobile ads filter in uBlock format",
            "https://filters.adtidy.org/extension/ublock/filters/11.txt",
            "recommended"
        ),
        FilterList(
            "adguard-tracking-ublock",
            "AdGuard Tracking Protection (uBlock)",
            "AdGuard tracking protection in uBlock format",
            "https://filters.adtidy.org/extension/ublock/filters/3.txt",
            "recommended"
        ),
        FilterList(
            "urlhaus-filter",
            "uBlock Badware/Malware",
            "Blocks malware URLs from URLhaus",
            "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter.txt",
            "recommended"
        ),
        
        // Additional other lists
        FilterList(
            "easylist-annoyances",
            "EasyList Annoyances",
            "Blocks annoying elements from EasyList",
            "https://easylist.to/easylist/easylist-annoyances.txt",
            "other"
        ),
        
        // Hosts-based lists
        FilterList(
            "stevenblack-hosts",
            "StevenBlack Hosts",
            "Unified hosts file blocking ads and malware",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "hosts"
        ),
        FilterList(
            "adaway-hosts",
            "AdAway Hosts",
            "Android-focused hosts file for ad blocking",
            "https://adaway.org/hosts.txt",
            "hosts"
        )
    )
    
    fun getFilterCacheDir(context: Context): File {
        val dir = File(context.filesDir, "filter_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    fun getFilterFile(context: Context, filterId: String): File {
        return File(getFilterCacheDir(context), "$filterId.txt")
    }
    
    suspend fun downloadFilter(context: Context, filterList: FilterList): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("FilterListManager", "Downloading ${filterList.name} from ${filterList.url}")
            val content = URL(filterList.url).readText()
            
            val file = getFilterFile(context, filterList.id)
            file.writeText(content)
            
            Log.d("FilterListManager", "Successfully downloaded ${filterList.name} (${content.length} bytes)")
            Result.success(content)
        } catch (e: Exception) {
            Log.e("FilterListManager", "Failed to download ${filterList.name}", e)
            Result.failure(e)
        }
    }
    
    suspend fun downloadFilters(context: Context, filterIds: Set<String>): Boolean = withContext(Dispatchers.IO) {
        var allSuccess = true
        filterIds.forEach { id ->
            val filterList = availableFilterLists.find { it.id == id }
            if (filterList != null) {
                val result = downloadFilter(context, filterList)
                if (result.isFailure) {
                    allSuccess = false
                }
            }
        }
        allSuccess
    }
    
    fun loadCachedFilter(context: Context, filterId: String): String? {
        return try {
            val file = getFilterFile(context, filterId)
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("FilterListManager", "Failed to load cached filter $filterId", e)
            null
        }
    }
    
    fun isFilterCached(context: Context, filterId: String): Boolean {
        return getFilterFile(context, filterId).exists()
    }
    
    fun isHostsList(filterId: String): Boolean {
        return availableFilterLists.find { it.id == filterId }?.category == "hosts"
    }
    
    fun parseHostsFile(content: String): Set<String> {
        val domains = mutableSetOf<String>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val parts = trimmed.split("\\s+".toRegex())
            if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                val domain = parts[1].trim()
                if (domain.isNotEmpty() && domain != "localhost" && domain != "local" &&
                    domain != "localhost.localdomain" && domain != "broadcasthost") {
                    domains.add(domain)
                }
            }
        }
        return domains
    }

}
