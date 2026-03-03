/**
 * Web to PDF Exporter for GeckoView
 * 
 * Handles webpage to PDF conversion using HTML5 Canvas and print functionality
 */

package com.sun.alasbrowser.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


/**
 * Handles PDF export for GeckoView and WebView
 */
object WebToPdfExporter {
    private const val TAG = "WebToPdfExporter"
    
    /**
     * Export webpage to PDF using WebView's print functionality
     * Creates a temporary WebView to render the page and print to PDF
     */
    suspend fun exportWebpageToPdf(
        context: Context,
        pageTitle: String,
        pageUrl: String,
        pageContent: String? = null,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.Main) {
        try {
            // Create a temporary WebView for PDF generation
            val tempWebView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    defaultTextEncodingName = "utf-8"
                }
            }
            
            // Load content into WebView
            if (!pageContent.isNullOrEmpty()) {
                tempWebView.loadDataWithBaseURL(
                    pageUrl,
                    pageContent,
                    "text/html",
                    "utf-8",
                    null
                )
            } else {
                tempWebView.loadUrl(pageUrl)
            }
            
            // Set up WebViewClient to know when page is loaded
            tempWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Give it a moment to fully render
                    tempWebView.postDelayed({
                        try {
                            generatePdfFromWebView(
                                context,
                                tempWebView,
                                pageTitle,
                                onSuccess,
                                onError
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error generating PDF: ${e.message}", e)
                            onError("Failed to generate PDF: ${e.message}")
                        } finally {
                            // Clean up WebView
                            tempWebView.destroy()
                        }
                    }, 500)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in exportWebpageToPdf: ${e.message}", e)
            onError("Error: ${e.message}")
        }
    }
    
    /**
     * Generate PDF from loaded WebView using Print framework
     */
    private fun generatePdfFromWebView(
        context: Context,
        webView: WebView,
        pageTitle: String,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                onError("Print service not available")
                return
            }
            
            // Create filename based on page title and timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val sanitizedTitle = pageTitle
                .replace(Regex("[^a-zA-Z0-9]"), "_")
                .take(30)
            val fileName = "${sanitizedTitle}_$timestamp"
            
            // Get the print adapter from WebView
            val printAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webView.createPrintDocumentAdapter(fileName)
            } else {
                @Suppress("DEPRECATION")
                webView.createPrintDocumentAdapter()
            }
            
            // Get app cache directory for PDF storage
            val pdfDir = File(context.cacheDir, "pdfs")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }
            
            val pdfFile = File(pdfDir, "$fileName.pdf")
            val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile
                )
            } else {
                @Suppress("DEPRECATION")
                Uri.fromFile(pdfFile)
            }
            
            // Print to PDF
            val printAttrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("RESOLUTION_300_DPI", "300 DPI", 300, 300))
                .setMinMargins(PrintAttributes.Margins(0, 0, 0, 0))
                .build()
            
            printManager.print(
                fileName,
                printAdapter,
                printAttrs
            )
            
            // Schedule PDF save after a delay
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({
                if (pdfFile.exists()) {
                    Log.d(TAG, "PDF saved to: ${pdfFile.absolutePath}")
                    onSuccess(fileUri)
                }
            }, 2000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating PDF: ${e.message}", e)
            onError("Error generating PDF: ${e.message}")
        }
    }
    
    /**
     * Simpler alternative: Export webpage as HTML file that can be printed
     */
    fun exportWebpageAsHtml(
        context: Context,
        pageTitle: String,
        pageContent: String,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val sanitizedTitle = pageTitle
                .replace(Regex("[^a-zA-Z0-9]"), "_")
                .take(30)
            val fileName = "${sanitizedTitle}_$timestamp.html"
            
            // Create safe HTML wrapper
            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>$pageTitle</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 1cm; line-height: 1.6; }
                        @media print { body { margin: 0; } }
                    </style>
                </head>
                <body>
                    <h1>$pageTitle</h1>
                    <hr>
                    $pageContent
                    <hr>
                    <p><small>Generated on ${java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US).format(Date())}</small></p>
                </body>
                </html>
            """.trimIndent()
            
            // Save to file
            val htmlDir = File(context.cacheDir, "html_exports")
            if (!htmlDir.exists()) {
                htmlDir.mkdirs()
            }
            
            val htmlFile = File(htmlDir, fileName)
            htmlFile.writeText(htmlContent, Charsets.UTF_8)
            
            val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    htmlFile
                )
            } else {
                @Suppress("DEPRECATION")
                Uri.fromFile(htmlFile)
            }
            
            Log.d(TAG, "HTML exported to: ${htmlFile.absolutePath}")
            onSuccess(fileUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting HTML: ${e.message}", e)
            onError("Error exporting HTML: ${e.message}")
        }
    }
    
    /**
     * Open PDF or HTML file with appropriate viewer
     */
    fun openFile(context: Context, uri: Uri, mimeType: String = "application/pdf") {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Log.e(TAG, "Error opening file: ${e.message}", e)
        }
    }
    
    /**
     * Share PDF or HTML file
     */
    fun shareFile(context: Context, uri: Uri, fileName: String, mimeType: String = "application/pdf") {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share with"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing file: ${e.message}", e)
        }
    }
}
