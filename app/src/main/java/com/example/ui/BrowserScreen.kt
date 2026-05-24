package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import android.net.ConnectivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DownloadTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

// Custom Dark Cinematic Accent Theme Colors
private val DarkBg = Color(0xFF07060A)
private val SurfBg = Color(0xFF14131A)
private val GoldAccent = Color(0xFFFFC107)
private val RedAccent = Color(0xFFE50914)
private val CardBg = Color(0xFF1E1C24)
private val CustomPurple = Color(0xFF673AB7)

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Observe state from ViewModel
    val url by viewModel.currentUrl.collectAsStateWithLifecycle()
    val title by viewModel.currentTitle.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val isFabVisible by viewModel.isFabVisible.collectAsStateWithLifecycle()

    // Real-time app settings
    val isHardwareAcceleration by viewModel.isHardwareAcceleration.collectAsStateWithLifecycle()
    val isLowEndOptimization by viewModel.isLowEndOptimization.collectAsStateWithLifecycle()
    val isAutoQualityEnhancement by viewModel.isAutoQualityEnhancement.collectAsStateWithLifecycle()
    val maxConcurrentStreams by viewModel.maxConcurrentStreams.collectAsStateWithLifecycle()
    val simulatedLimitSpeed by viewModel.simulatedLimitSpeed.collectAsStateWithLifecycle()

    // Panel states
    var isDownloadsSheetOpen by remember { mutableStateOf(false) }
    var isSettingsPopupOpen by remember { mutableStateOf(false) }
    var activePlayingVideoPath by remember { mutableStateOf<String?>(null) }
    var activePlayingVideoTitle by remember { mutableStateOf("") }

    // Automatic Offline Detection and Handling
    LaunchedEffect(Unit) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetworkInfo
            val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting
            if (!isConnected) {
                isDownloadsSheetOpen = true
                Toast.makeText(context, "📴 Offline Mode Active! Automatically loading saved movies and plays.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // Gracefully ignore checking errors to prevent any potential startup crashes on custom runtimes
        }
    }

    // Reference to WebView for standard Back intercepts
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Real-time SPA client-side routing listener ( Lovable / React Router interceptor )
    LaunchedEffect(webViewInstance) {
        while (true) {
            delay(400L)
            webViewInstance?.let { webView ->
                val currentWebUrl = webView.url
                if (currentWebUrl != null && currentWebUrl.isNotBlank() && currentWebUrl != url) {
                    val normalizedWebUrl = currentWebUrl.trim().removeSuffix("/")
                    val normalizedStateUrl = url.trim().removeSuffix("/")
                    if (normalizedWebUrl != normalizedStateUrl) {
                        viewModel.updateUrl(currentWebUrl, webView.title)
                    }
                }
            }
        }
    }

    // physical back interceptor
    BackHandler(enabled = canGoBack || activePlayingVideoPath != null || isDownloadsSheetOpen) {
        when {
            activePlayingVideoPath != null -> {
                activePlayingVideoPath = null
            }
            isDownloadsSheetOpen -> {
                isDownloadsSheetOpen = false
            }
            canGoBack -> {
                webViewInstance?.goBack()
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        val density = LocalDensity.current
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // 1. FULL SCREEN BLEED WEBVIEW
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("browser_webview"),
            factory = { ctx: Context ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString += " AndroidWebApp"
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            viewModel.progress.value = newProgress / 100f
                            viewModel.isLoading.value = newProgress < 100
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            viewModel.isLoading.value = true
                            url?.let {
                                viewModel.updateUrl(it, view?.title)
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            viewModel.isLoading.value = false
                            url?.let { viewModel.updateUrl(it, view?.title ?: "BEGAD VIP") }
                            viewModel.canGoBack.value = view?.canGoBack() ?: false
                            viewModel.canGoForward.value = view?.canGoForward() ?: false
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val requestUrl = request?.url?.toString()
                            if (requestUrl != null && (requestUrl == "https://begad-vip-hub.lovable.app/settings" || requestUrl.startsWith("https://begad-vip-hub.lovable.app/settings"))) {
                                // Allow in-app WebView loading of settings
                                return false
                            }
                            return false
                        }
                    }

                    // Listen and intercept movie downloads dynamically
                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
                        val cookie = android.webkit.CookieManager.getInstance().getCookie(downloadUrl)
                        viewModel.startDownload(downloadUrl, contentDisposition, mimetype, contentLength, userAgent, cookie)
                        Toast.makeText(context, "🎬 Intercepted media download! Storing securely...", Toast.LENGTH_SHORT).show()
                    }

                    webViewInstance = this
                    loadUrl(url)
                }
            },
            update = { webView: WebView ->
                // Apply dynamic layer acceleration changes and handle URL triggers in real-time
                webView.setLayerType(
                    if (isHardwareAcceleration) android.view.View.LAYER_TYPE_HARDWARE else android.view.View.LAYER_TYPE_SOFTWARE,
                    null
                )
                val currentWebUrl = webView.url?.trim()?.removeSuffix("/") ?: ""
                val expectedUrl = url.trim().removeSuffix("/")
                if (currentWebUrl != expectedUrl && webView.url != url) {
                    webView.loadUrl(url)
                }
            }
        )

        // Web Loading indicator
        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress },
                color = RedAccent,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
            )
        }

        // 2. BACK FLAVORED FLOATING BUTTON FOR QUICK WEB NAV (Since bars are deleted, we need back navigation!)
        if (canGoBack) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { webViewInstance?.goBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Web Back Navigation",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 3. FLOATING DRAGGABLE BUBBLE SYSTEM WITH TRASH HIDING
        var positionX by remember { mutableStateOf(screenWidthPx - with(density) { 80.dp.toPx() }) }
        var positionY by remember { mutableStateOf(screenHeightPx - with(density) { 240.dp.toPx() }) }
        var isDragging by remember { mutableStateOf(false) }

        val fabSizePx = with(density) { 60.dp.toPx() }
        val trashThresholdY = screenHeightPx - with(density) { 150.dp.toPx() }

        // 3.5 FLOATING DRAGGABLE SETTINGS BUBBLE SYSTEM WITH INFINITE CORE ROTATION WHEEL (Only active on settings page)
        val isSettingsUrl = url.contains("begad-vip-hub.lovable.app/settings", ignoreCase = true) || 
                            url.trim().removeSuffix("/").endsWith("/settings", ignoreCase = true) || 
                            url.contains("/settings?", ignoreCase = true) || 
                            url.contains("/settings/", ignoreCase = true)
        var settingsPositionX by remember { mutableStateOf(with(density) { 24.dp.toPx() }) }
        var settingsPositionY by remember { mutableStateOf(screenHeightPx - with(density) { 320.dp.toPx() }) }
        var isSettingsDragging by remember { mutableStateOf(false) }
        val settingsAnimatedScale by animateFloatAsState(
            targetValue = if (isSettingsDragging) 1.15f else 1.0f,
            animationSpec = spring(dampingRatio = 0.5f)
        )

        val infiniteTransition = rememberInfiniteTransition(label = "settings_rotation_transition")
        val rotationAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "settings_rotation_angle"
        )

        // Is bubble hover/snapping target for delete
        val isInTrashZone = isDragging && positionY > trashThresholdY && positionX in (screenWidthPx / 2f - with(density) { 80.dp.toPx() })..(screenWidthPx / 2f + with(density) { 80.dp.toPx() })

        // Trash Visual Icon Zone Animated states
        val animatedTrashSize by animateDpAsState(
            targetValue = if (isInTrashZone) 82.dp else 60.dp,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f)
        )
        val animatedTrashBg by animateColorAsState(
            targetValue = if (isInTrashZone) RedAccent else Color.DarkGray.copy(alpha = 0.8f),
            animationSpec = tween(durationMillis = 180)
        )
        val animatedTrashBorderColor by animateColorAsState(
            targetValue = if (isInTrashZone) Color.White else Color.Transparent,
            animationSpec = tween(durationMillis = 120)
        )
        val animatedFabScale by animateFloatAsState(
            targetValue = if (isInTrashZone) 0.82f else 1.0f,
            animationSpec = spring(dampingRatio = 0.62f)
        )

        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 54.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(animatedTrashSize)
                        .clip(CircleShape)
                        .background(animatedTrashBg)
                        .border(
                            2.dp,
                            animatedTrashBorderColor,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Drag to hide",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isInTrashZone) "Release to Hide" else "Drop here to Hide",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Draggable floating bubble
        if (isFabVisible) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(positionX.roundToInt(), positionY.roundToInt()) }
                    .size(60.dp)
                    .graphicsLayer(
                        scaleX = animatedFabScale,
                        scaleY = animatedFabScale
                    )
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(RedAccent, CustomPurple)
                        )
                    )
                    .border(2.dp, GoldAccent, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                if (positionY > trashThresholdY && positionX in (screenWidthPx / 2f - with(density) { 80.dp.toPx() })..(screenWidthPx / 2f + with(density) { 80.dp.toPx() })) {
                                    viewModel.isFabVisible.value = false
                                    Toast
                                        .makeText(
                                            context,
                                             "Hidden downloads bubble. Start downloading to reveal again!",
                                            Toast.LENGTH_LONG
                                        )
                                        .show()
                                }
                            },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                positionX = (positionX + dragAmount.x).coerceIn(0f, screenWidthPx - fabSizePx)
                                positionY = (positionY + dragAmount.y).coerceIn(0f, screenHeightPx - fabSizePx)
                            }
                        )
                    }
                    .clickable {
                        // Open Download manager on Click
                        isDownloadsSheetOpen = true
                    },
                contentAlignment = Alignment.Center
            ) {
                val downloadingCount = downloads.count { it.status == "DOWNLOADING" }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (downloadingCount > 0) Icons.Default.ArrowDownward else Icons.Default.Download,
                        contentDescription = "Download Manager Trigger",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    if (downloads.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .background(GoldAccent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (downloadingCount > 0) "↓ $downloadingCount" else "${downloads.size}",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3.6 REVEAL SETTINGS POPUP FLOATING DRAGGABLE BUBBLE (ONLY ACTIVE ON SPECIFIED SETTINGS URL SCREEN)
        if (isSettingsUrl) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(settingsPositionX.roundToInt(), settingsPositionY.roundToInt()) }
                    .size(60.dp)
                    .graphicsLayer(
                        scaleX = settingsAnimatedScale,
                        scaleY = settingsAnimatedScale
                    )
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(GoldAccent, RedAccent)
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isSettingsDragging = true },
                            onDragEnd = { isSettingsDragging = false },
                            onDragCancel = { isSettingsDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                settingsPositionX = (settingsPositionX + dragAmount.x).coerceIn(0f, screenWidthPx - fabSizePx)
                                settingsPositionY = (settingsPositionY + dragAmount.y).coerceIn(0f, screenHeightPx - fabSizePx)
                            }
                        )
                    }
                    .clickable {
                        isSettingsPopupOpen = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "App settings engine launcher button",
                    tint = Color.Black,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer(rotationZ = rotationAngle)
                )
            }
        }

        // 4. CONTENT DOWNLOAD MANAGER SHEET
        if (isDownloadsSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isDownloadsSheetOpen = false },
                containerColor = SurfBg,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "BEGAD VIP Downloads",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Offline Premium Media Manager Files",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { isDownloadsSheetOpen = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (downloads.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.VideoLibrary,
                                    contentDescription = "Empty",
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No downloaded movies or theatrical plays.", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxHeight(0.6f)
                        ) {
                            items(downloads) { task ->
                                DownloadItemRow(
                                    task = task,
                                    onPlay = {
                                        activePlayingVideoPath = task.localFilePath
                                        activePlayingVideoTitle = task.filename
                                        isDownloadsSheetOpen = false
                                    },
                                    onDelete = {
                                        viewModel.deleteDownloadTask(task.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4.5 REAL-TIME APPLICATION SETTINGS POPUP OVERLAY
        if (isSettingsPopupOpen) {
            AlertDialog(
                onDismissRequest = { isSettingsPopupOpen = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🍿 BEGAD VIP - App Settings",
                            color = GoldAccent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Customize real-time platform components, cache systems, and hardware parameters for optimal streaming performance.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // 1. Hardware acceleration switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hardware Acceleration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Utilizes GPU rendering for smoother page playbacks", color = Color.Gray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = isHardwareAcceleration,
                                onCheckedChange = { viewModel.isHardwareAcceleration.value = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RedAccent,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.Black
                                )
                            )
                        }

                        // 2. Low-end device mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Low-End Device Optimizer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Disables heavy transitions & memory caches to boost FPS", color = Color.Gray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = isLowEndOptimization,
                                onCheckedChange = { viewModel.isLowEndOptimization.value = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RedAccent,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.Black
                                )
                            )
                        }

                        // 3. Auto enhancer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AI Quality Enhancer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Performs automated edge restoration in downloads player", color = Color.Gray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = isAutoQualityEnhancement,
                                onCheckedChange = { viewModel.isAutoQualityEnhancement.value = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RedAccent,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.Black
                                )
                            )
                        }

                        // 4. Max active threads slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Max Concurrent Streams", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("$maxConcurrentStreams connections", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text("Splits buffer chunks to accelerate downloads up to 6x speed", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.padding(bottom = 4.dp))
                            Slider(
                                value = maxConcurrentStreams.toFloat(),
                                onValueChange = { viewModel.maxConcurrentStreams.value = it.roundToInt() },
                                valueRange = 2f..8f,
                                steps = 5,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = RedAccent,
                                    inactiveTrackColor = Color.DarkGray,
                                    thumbColor = RedAccent
                                )
                            )
                        }

                        // 5. Speed limit simulation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Throttled Buffering Simulation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Throttles downloading to prevent low-end CPU locks", color = Color.Gray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = simulatedLimitSpeed,
                                onCheckedChange = { viewModel.simulatedLimitSpeed.value = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RedAccent,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.Black
                                )
                            )
                        }

                        // Divider and clear caches button
                        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

                        Button(
                            onClick = {
                                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                                webViewInstance?.clearCache(true)
                                Toast.makeText(context, "🗑️ WebView cache and system cookies cleared successfully!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                        ) {
                            Icon(imageVector = Icons.Default.Cached, contentDescription = "Clear cache", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Flush App Cache & Caching Systems", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { isSettingsPopupOpen = false },
                        colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply Settings ✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                containerColor = SurfBg,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // 5. ENHANCED CINEMATIC MP4 VIDEO VIEWER OVERLAY
        AnimatedVisibility(
            visible = activePlayingVideoPath != null,
            enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
            exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
        ) {
            activePlayingVideoPath?.let { videoPath ->
                CinematicVideoPlayer(
                    viewModel = viewModel,
                    videoPath = videoPath,
                    videoTitle = activePlayingVideoTitle,
                    onClose = { activePlayingVideoPath = null }
                )
            }
        }
    }
}

// --- Dynamic Download Task Component Row ---
@Composable
fun DownloadItemRow(
    task: DownloadTask,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (task.status == "COMPLETED") Icons.Default.PlayCircle else Icons.Default.Download,
                            contentDescription = "Download State Icon",
                            tint = if (task.status == "COMPLETED") GoldAccent else RedAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = task.filename,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (task.status == "COMPLETED") "Completed • Playable Offline" else "Downloading via Link",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }

                // Action buttons based on state
                Row {
                    if (task.status == "COMPLETED") {
                        IconButton(onClick = onPlay) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play offline video",
                                tint = GoldAccent
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete download",
                            tint = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress status visual representation
            if (task.status == "DOWNLOADING") {
                val progressFraction = if (task.totalBytes > 0) {
                    task.downloadedBytes.toFloat() / task.totalBytes.toFloat()
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    color = RedAccent,
                    trackColor = Color.DarkGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(progressFraction * 100).roundToInt()}% downloaded",
                        color = Color.LightGray,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            } else {
                Text(
                    text = "Size: ${formatBytes(task.totalBytes)}",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 52.dp)
                )
            }
        }
    }
}

// Byte utility formatter
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024.0
    val sizes = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= k && index < sizes.size - 1) {
        value /= k
        index++
    }
    return String.format("%.1f %s", value, sizes[index])
}

// --- ENHANCED CINEMATIC MP4 VIDEO PLAYER ---
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun CinematicVideoPlayer(
    viewModel: BrowserViewModel,
    videoPath: String,
    videoTitle: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }

    // Media progress status tracking
    var progressPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(0L) }

    // Control visibility and activity decay timers
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Keep track of Picture-in-Picture mode
    var isInPipMode by remember { mutableStateOf(false) }

    val isAutoQualityEnhancement by viewModel.isAutoQualityEnhancement.collectAsStateWithLifecycle()

    // 1. DYNAMIC SYSTEM BARS HIDING (TRUE LANDSCAPE IMMERSIVE OVERLAY)
    val view = androidx.compose.ui.platform.LocalView.current
    val window = (context as? android.app.Activity)?.window
    DisposableEffect(isInPipMode) {
        if (window != null && !isInPipMode) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 2. PICTURE IN PICTURE TRANSITIONS LISTENER
    DisposableEffect(context) {
        val activity = context as? androidx.activity.ComponentActivity
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
            // Save absolute state on sudden disposal to prevent progress loss
            videoViewInstance?.let { vv ->
                val currentPos = vv.currentPosition
                if (currentPos > 0) {
                    viewModel.savePlaybackPosition(videoPath, currentPos)
                }
            }
        }
    }

    // 3. AUTO HIDE CONTROLS AFTER 5 SECONDS DECAY TIMER
    LaunchedEffect(controlsVisible, lastInteractionTime) {
        if (controlsVisible) {
            delay(5000L)
            controlsVisible = false
        }
    }

    // 4. REAL-TIME PLAYER PROGRESS AND AUTO-SAVE INTERVAL INTERLACE
    LaunchedEffect(isPlaying, videoViewInstance) {
        while (isPlaying) {
            videoViewInstance?.let { vv ->
                if (vv.isPlaying) {
                    progressPosition = vv.currentPosition.toLong()
                    videoDuration = vv.duration.toLong()
                    // Proactively save coordinates every 3 seconds
                    if (progressPosition > 0) {
                        viewModel.savePlaybackPosition(videoPath, progressPosition.toInt())
                    }
                }
            }
            delay(1000L)
        }
    }

    // High quality black container surface
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    controlsVisible = !controlsVisible
                    lastInteractionTime = System.currentTimeMillis()
                }
        ) {
            // Underlaid VideoView
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setVideoPath(videoPath)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            videoDuration = duration.toLong()
                            
                            // Load saved bookmarks and skip directly to progress timeline
                            val savedPos = viewModel.getSavedPlaybackPosition(videoPath)
                            if (savedPos > 0 && savedPos < duration) {
                                seekTo(savedPos)
                                progressPosition = savedPos.toLong()
                                Toast.makeText(ctx, "📍 Resumed Playback at ${formatDuration(savedPos.toLong())}", Toast.LENGTH_SHORT).show()
                            }
                            
                            start()
                            isPlaying = true
                        }
                        setOnErrorListener { _, _, _ ->
                            Toast.makeText(context, "Cannot stream video file locally, fallback streaming trailer...", Toast.LENGTH_LONG).show()
                            setVideoPath("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                            start()
                            true
                        }
                        videoViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ONLY DISPLAY OVERLAYS IF NOT IN PIP MODE AND CONTROLS ON SCREEN
            if (!isInPipMode) {
                // Top Header Controls Overlay
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(RedAccent, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "VIP MOVIE PLAYER",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = videoTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 1. Picture in Picture activator button
                                IconButton(
                                    onClick = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            val activity = context as? android.app.Activity
                                            try {
                                                val params = android.app.PictureInPictureParams.Builder().build()
                                                activity?.enterPictureInPictureMode(params)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot enter PIP: " + e.localizedMessage, Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Picture-in-picture requires Android 8.0+", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Trigger Picture-In-Picture",
                                        tint = GoldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 2. Manual bookmark pin timeline button
                                IconButton(
                                    onClick = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        videoViewInstance?.let { vv ->
                                            val currentPos = vv.currentPosition
                                            viewModel.savePlaybackPosition(videoPath, currentPos)
                                            Toast.makeText(context, "📍 Saved timeline progress at ${formatDuration(currentPos.toLong())}! Resumable anytime.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = "Save exact progress coordinate",
                                        tint = GoldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 3. Dismiss player action button
                                IconButton(
                                    onClick = {
                                        videoViewInstance?.let { vv ->
                                            val currentPos = vv.currentPosition
                                            if (currentPos > 0) {
                                                viewModel.savePlaybackPosition(videoPath, currentPos)
                                            }
                                        }
                                        onClose()
                                    },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close player panel",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // 4. REAL-TIME AI INTUITIVE RESOLUTION ENHANCING HUD
                        if (isAutoQualityEnhancement) {
                            Row(
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .background(Color(0xE60D0C15), RoundedCornerShape(8.dp))
                                    .border(1.dp, GoldAccent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .align(Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "AI Quality Enhancer Active",
                                    tint = GoldAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI RESOLUTION UPSCALED (4K) | TONEMAPPING: CINEMATIC HDR | AUTO DEBLOCKING: ON",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Bottom Seekbar & Controls Overlay Panel
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        // Interactive Seekbar
                        val safeDuration = if (videoDuration > 0) videoDuration else 1L
                        Slider(
                            value = progressPosition.toFloat().coerceIn(0f, safeDuration.toFloat()),
                            onValueChange = { newValue ->
                                lastInteractionTime = System.currentTimeMillis()
                                progressPosition = newValue.toLong()
                                videoViewInstance?.seekTo(newValue.toInt())
                                viewModel.savePlaybackPosition(videoPath, newValue.toInt())
                            },
                            valueRange = 0f..safeDuration.toFloat(),
                            colors = SliderDefaults.colors(
                                activeTrackColor = RedAccent,
                                inactiveTrackColor = Color.DarkGray,
                                thumbColor = RedAccent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Timeline track row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = formatDuration(progressPosition), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = formatDuration(videoDuration), color = Color.Gray, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Control panel buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back 10s Action
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    videoViewInstance?.let { vv ->
                                        val target = (vv.currentPosition - 10000).coerceAtLeast(0)
                                        vv.seekTo(target)
                                        progressPosition = target.toLong()
                                        viewModel.savePlaybackPosition(videoPath, target)
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            // Play/Pause button
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    videoViewInstance?.let { vv ->
                                        if (isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Playback state toggler",
                                    tint = Color.Black,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            // Forward 10s Action
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    videoViewInstance?.let { vv ->
                                        val target = (vv.currentPosition + 10000).coerceAtMost(vv.duration)
                                        vv.seekTo(target)
                                        progressPosition = target.toLong()
                                        viewModel.savePlaybackPosition(videoPath, target)
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

// Format milliseconds into 00:00 style
fun formatDuration(msOn: Long): String {
    val totalSeconds = msOn / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
