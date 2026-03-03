package com.sun.alasbrowser.ui

import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.sun.alasbrowser.data.BrowserTab
import kotlinx.coroutines.Job


@Stable
class BrowserScreenState(
    openSearchBar: Boolean = false,
    openCameraSearch: Boolean = false
) {
    var pendingDownloadRequest by mutableStateOf<DownloadRequest?>(null)
    val tabs = mutableStateListOf<BrowserTab>()
    var activeTabId by mutableStateOf<String?>(null)
    var showTabSwitcher by mutableStateOf(false)
    var showPageMenu by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showFullSettings by mutableStateOf(false)

    val currentTab: BrowserTab? get() = tabs.firstOrNull { it.id == activeTabId }

    var url by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var showSearchBar by mutableStateOf(openSearchBar)
    var showHomePage by mutableStateOf(true)
    var isListening by mutableStateOf(false)
    var showVoiceOverlay by mutableStateOf(false)
    var voicePartialResult by mutableStateOf("")
    var voiceError by mutableStateOf<String?>(null)
    var voiceRms by mutableFloatStateOf(0f)

    var showSiteSettings by mutableStateOf(false)
    var showAdBlocker by mutableStateOf(false)
    var showIncognitoInfo by mutableStateOf(false)
    var showFindInPage by mutableStateOf(false)
    var showActualFindInPage by mutableStateOf(false)
    var showQrScanner by mutableStateOf(openCameraSearch)
    var showQrDialog by mutableStateOf(false)
    var showTranslationDialog by mutableStateOf(false)
    var showSiteSecurityDialog by mutableStateOf(false)
    var showZoomControl by mutableStateOf(false)

    var showSummaryDialog by mutableStateOf(false)
    var summaryText by mutableStateOf<String?>(null)
    var summaryIsLoading by mutableStateOf(false)
    var summaryError by mutableStateOf<String?>(null)
    var isAskingQuestion by mutableStateOf(false)
    var currentQuestion by mutableStateOf("")

    var findInPageQuery by mutableStateOf("")
    var customView by mutableStateOf<View?>(null)
    var customViewCallback by mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
    var pendingFilePickerRequest by mutableStateOf<FilePickerRequest?>(null)

    var credentialToSave by mutableStateOf<Triple<String, String, String>?>(null)

    var topBarHeight by mutableStateOf(50.dp)
    var bottomBarHeight by mutableStateOf(60.dp)
    var bottomBarHeightPx by mutableFloatStateOf(0f)


    var isScrollbarVisible by mutableStateOf(false)
    var scrollbarHideJob by mutableStateOf<Job?>(null)

    var saveTabsJob by mutableStateOf<Job?>(null)

    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    var pendingVideoUri by mutableStateOf<Uri?>(null)
    var pendingAudioUri by mutableStateOf<Uri?>(null)
}

@Composable
fun rememberBrowserScreenState(
    openSearchBar: Boolean = false,
    openCameraSearch: Boolean = false
): BrowserScreenState {
    return remember {
        BrowserScreenState(
            openSearchBar = openSearchBar,
            openCameraSearch = openCameraSearch
        )
    }
}

data class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long,
    val cookies: String?,
    val referer: String?,
    val fileName: String,
    val pageTitle: String?,
    val pageUrl: String?
)

/**
 * Simple file picker request (replaces GeckoView's FilePickerRequest).
 * Holds callbacks invoked by the activity result launchers in BrowserScreen.
 */
data class FilePickerRequest(
    val mimeTypes: Array<String> = arrayOf("*/*"),
    val isMultiple: Boolean = false,
    val capture: String = "",
    val onPicked: (List<Uri>) -> Unit,
    val onCancelled: () -> Unit
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
