package com.cricketerstales.webapp

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.cricketerstales.webapp.data.PreferenceManager
import com.cricketerstales.webapp.ui.theme.CricketerstalesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    private var downloadId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        // Immediate edge-to-edge
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        setContent {
            CricketerstalesTheme {
                val context = LocalContext.current
                val preferenceManager = remember { PreferenceManager(context) }
                val isTermsAccepted by preferenceManager.isTermsAccepted.collectAsState(initial = null)
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                
                var showExitDialog by remember { mutableStateOf(false) }
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

                LaunchedEffect(Unit) {
                    val info = checkForUpdates(context)
                    if (info != null) {
                        updateInfo = info
                    }
                }

                DisposableEffect(Unit) {
                    val onDownloadComplete = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                            if (id == downloadId) {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Download complete!",
                                        actionLabel = "INSTALL",
                                        duration = SnackbarDuration.Indefinite
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        installApk(context)
                                    }
                                }
                            }
                        }
                    }
                    context.registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
                    onDispose { context.unregisterReceiver(onDownloadComplete) }
                }

                when (isTermsAccepted) {
                    null -> { /* Silent loading */ }
                    false -> {
                        TermsAndConditionsDialog(
                            onAccept = { scope.launch { preferenceManager.setTermsAccepted(true) } },
                            onDecline = { finish() }
                        )
                    }
                    true -> {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            contentWindowInsets = WindowInsets(0, 0, 0, 0)
                        ) { _ ->
                            CricketersTalesWebView(
                                url = "https://cricketerstales.com/",
                                modifier = Modifier.fillMaxSize(),
                                onShowExitDialog = { showExitDialog = true }
                            )
                        }

                        if (showExitDialog) {
                            ExitConfirmationDialog(
                                onConfirm = { finish() },
                                onDismiss = { showExitDialog = false }
                            )
                        }

                        updateInfo?.let { info ->
                            CustomUpdateDialog(
                                onUpdate = {
                                    updateInfo = null
                                    downloadId = downloadAndInstallApk(context, info.downloadUrl)
                                },
                                onDismiss = { updateInfo = null }
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/nikhil2001gowda/Cricketerstales/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.getString("tag_name")
                val latestVersion = tagName.removePrefix("v").trim()
                val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
                
                if (isNewerVersion(currentVersion, latestVersion)) {
                    val assets = json.getJSONArray("assets")
                    if (assets.length() > 0) {
                        val apkUrl = assets.getJSONObject(0).getString("browser_download_url")
                        return@withContext UpdateInfo(tagName, apkUrl)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val curr = currentParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > curr) return true
            if (curr > lat) return false
        }
        return false
    }

    private fun downloadAndInstallApk(context: Context, url: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("CricketersTales Update")
            .setDescription("Downloading new version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    private fun installApk(context: Context) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

data class UpdateInfo(val version: String, val downloadUrl: String)

@Composable
fun CustomUpdateDialog(onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "New Update Available!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "A new version of CricketersTales is ready. Update now to get the latest features and a smoother experience.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Later", color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onUpdate,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Update Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedLoadingScreen(isVisible: Boolean, isTransition: Boolean = false) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(if (isTransition) 150 else 300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isTransition) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isTransition) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(scale)
                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "CricketersTales",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun TermsAndConditionsDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
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
    var isLoading by remember { mutableStateOf(true) }
    var showSplashScreen by remember { mutableStateOf(true) }
    var isNavigating by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onShowExitDialog()
        }
    }

    Box(modifier = modifier) {
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
                            if (!showSplashScreen) {
                                isNavigating = true
                            }
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            isNavigating = false
                            showSplashScreen = false
                        }
                        
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val requestUrl = request?.url?.toString() ?: return false
                            return if (requestUrl.contains("cricketerstales.com")) {
                                false 
                            } else {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, requestUrl.toUri())
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                                true
                            }
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            // Smart auto-hide as soon as page is mostly loaded (visible)
                            if (newProgress > 60) {
                                showSplashScreen = false
                                isNavigating = false
                            }
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
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        )

        // Ultra-fast Animations
        if (showSplashScreen) {
            AdvancedLoadingScreen(isVisible = true)
        } else if (isNavigating) {
            AdvancedLoadingScreen(isVisible = true, isTransition = true)
        }
    }
}
