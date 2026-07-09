package com.cricketerstales.webapp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.cricketerstales.webapp.data.PreferenceManager
import com.cricketerstales.webapp.ui.theme.CricketerstalesTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CricketerstalesTheme {
                val context = LocalContext.current
                val preferenceManager = remember { PreferenceManager(context) }
                val isTermsAccepted by preferenceManager.isTermsAccepted.collectAsState(initial = null)
                val scope = rememberCoroutineScope()
                
                var showExitDialog by remember { mutableStateOf(false) }

                when (isTermsAccepted) {
                    null -> {
                        // Loading preferences, show nothing or a splash
                    }
                    false -> {
                        TermsAndConditionsDialog(
                            onAccept = {
                                scope.launch {
                                    preferenceManager.setTermsAccepted(true)
                                }
                            },
                            onDecline = {
                                finish()
                            }
                        )
                    }
                    true -> {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            CricketersTalesWebView(
                                url = "https://cricketerstales.com/",
                                modifier = Modifier.padding(innerPadding),
                                onShowExitDialog = { showExitDialog = true }
                            )
                        }

                        if (showExitDialog) {
                            ExitConfirmationDialog(
                                onConfirm = { finish() },
                                onDismiss = { showExitDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TermsAndConditionsDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Don't allow dismiss by clicking outside */ },
        title = {
            Text(text = "Welcome to CricketersTales", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "By using this application, you agree to our Terms and Conditions and Privacy Policy.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "\nThis app provides a native experience for cricketerstales.com. We respect your privacy and do not collect personal data through this native shell.\n\n" +
                            "Please ensure you follow our community guidelines while browsing.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Accept", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Decline")
            }
        }
    )
}

@Composable
fun ExitConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Exit App?", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Text(text = "Do you really want to exit the CricketersTales app?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Exit", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Stay")
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CricketersTalesWebView(
    url: String, 
    modifier: Modifier = Modifier,
    onShowExitDialog: () -> Unit
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    // Handle Back button
    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onShowExitDialog()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val requestUrl = request?.url?.toString() ?: return false
                            return if (requestUrl.contains("cricketerstales.com")) {
                                false // Load in WebView
                            } else {
                                // Open external links in default browser
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, requestUrl.toUri())
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    // Handle cases where no browser is available
                                }
                                true
                            }
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }

                    loadUrl(url)
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                // Instance is preserved
            }
        )

        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
