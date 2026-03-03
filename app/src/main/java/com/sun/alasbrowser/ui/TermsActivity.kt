package com.sun.alasbrowser.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.ui.theme.AlasBrowserTheme

class TermsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = BrowserPreferences(this)
        setContent {
            AlasBrowserTheme(appTheme = preferences.appTheme) {
                TermsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy & Terms", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "ALAS BROWSER Privacy Policy & Terms",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                SectionBody(
                    text = "Effective Date: March 2026",
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                SectionTitle("1. Information We Collect")
                SectionBody(
                    """
                    Account Information:
                    When you create an account, we collect your email address and authentication identifiers through Firebase Authentication.

                    Local Data:
                    - Browsing history (stored on your device)
                    - Cookies from visited websites
                    - Downloaded files

                    We do not sell your personal data.
                    """.trimIndent()
                )

                SectionTitle("2. Use of Firebase")
                SectionBody(
                    """
                    We use Firebase services provided by Google for authentication.
                    Firebase may process technical metadata needed to operate the service.
                    """.trimIndent()
                )
                LinkText(
                    text = "Google Privacy Policy: https://policies.google.com/privacy",
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))
                        )
                    }
                )

                SectionTitle("3. Security Practices")
                SectionBody(
                    """
                    - Browsing uses Android WebView with HTTPS support when available.
                    - Downloads are saved to device storage under your control.
                    - Cookies and site data are managed in-app and can be cleared by the user.
                    """.trimIndent()
                )

                SectionTitle("4. Browser-Specific Disclosures")
                SectionBody(
                    """
                    ALAS BROWSER opens third-party websites that are not controlled by us.
                    We are not responsible for third-party website content, security, or privacy practices.
                    """.trimIndent()
                )

                SectionTitle("5. Open-Source Status")
                SectionBody(
                    """
                    ALAS BROWSER is being released as an open-source project.
                    Legal policy page:
                    https://alaslife.github.io/alas-browser-policy/
                    """.trimIndent()
                )
                LinkText(
                    text = "Open policy page",
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://alaslife.github.io/alas-browser-policy/"))
                        )
                    }
                )

                SectionTitle("6. Children's Privacy")
                SectionBody("We do not knowingly collect personal data from children under 13.")

                SectionTitle("7. Terms and Conditions")
                SectionBody(
                    """
                    - By using ALAS BROWSER, you agree to these terms.
                    - You are responsible for maintaining your account credentials.
                    - You must not use the app for illegal or harmful activities.
                    - We are not responsible for external website content.
                    - The app is provided "as is" without warranties.
                    - We may update these terms and policy at any time.
                    """.trimIndent()
                )

                SectionTitle("8. Contact")
                SectionBody("For questions, contact: YOUR_EMAIL_HERE")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun SectionBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.LightGray,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        modifier = modifier
    )
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color(0xFF7FC8FF),
        fontSize = 15.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .padding(top = 6.dp)
            .clickable(onClick = onClick)
    )
}
