package com.scd.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ScanState { Idle, Found, Invalid }

@Composable
fun GlassLoginScreen(onGuest: () -> Unit) {
    val p = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraOn by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(ScanState.Idle) }
    var showPull by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraOn = granted
        denied = !granted
    }

    LaunchedEffect(PendingLink.token, state) {
        if (state == ScanState.Found && PendingLink.token == null) {
            delay(1200)
            if (!SessionGuard.signedIn) state = ScanState.Idle
        }
        if (state == ScanState.Invalid) {
            delay(2500)
            state = ScanState.Idle
        }
    }

    if (showPull) LinkQrDialog(push = false, onDismiss = { showPull = false })

    Box(Modifier.fillMaxSize()) {
        Backdrop(colors = listOf(p.accent, Color(0xFF8B5CF6), Color(0xFF22D3EE)), stars = true)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(68.dp).glass(RoundedCornerShape(22.dp), p, shadow = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_wave), null, tint = p.text, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(20.dp))
            GradientText(
                stringResource(R.string.login_qr_title),
                GlassType.hero.copy(textAlign = TextAlign.Center),
                listOf(p.text, Color(0xFFD8B4FE), p.text),
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.login_qr_sub),
                style = GlassType.body.copy(fontSize = 15.sp),
                color = p.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
            Spacer(Modifier.height(22.dp))

            Column(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(32.dp), p, shadow = 22.dp)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScannerFrame(
                    active = cameraOn && state != ScanState.Found && PendingLink.token == null,
                    granted = cameraOn,
                    denied = denied,
                    onEnable = { permission.launch(Manifest.permission.CAMERA) },
                    onCode = { text ->
                        val parsed = QrLink.parse(text)
                        if (parsed == null) {
                            Logs.add("qr", "scanned code is not a login link")
                            state = ScanState.Invalid
                        } else {
                            Logs.add("qr", "scanned ${if (parsed.second) "pull" else "push"} link")
                            state = ScanState.Found
                            PendingLink.set(parsed.first, pull = parsed.second, fromScanner = true)
                        }
                    },
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(live = state != ScanState.Invalid, color = if (state == ScanState.Invalid) Color(0xFFFF6B6B) else p.accent, size = 6.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            when (state) {
                                ScanState.Found -> R.string.login_qr_found
                                ScanState.Invalid -> R.string.qr_not_link
                                ScanState.Idle -> if (cameraOn) R.string.login_qr_aim else R.string.login_qr_camera_off
                            },
                        ),
                        style = GlassType.small.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                        color = p.text,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.login_qr_hint),
                    style = GlassType.small.copy(fontSize = 12.5.sp),
                    color = p.text2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                WideGlassButton(
                    icon = R.drawable.ic_globe,
                    text = stringResource(R.string.login_browser),
                    busy = BrowserLogin.busy,
                    onClick = { scope.launch { BrowserLogin.run(context) } },
                )
                BrowserLogin.status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = GlassType.small, color = p.text2, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.login_browser_warn), style = GlassType.small.copy(fontSize = 11.sp), color = p.text3, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.link_show_title),
                    style = GlassType.small.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text2,
                    modifier = Modifier.clip(CircleShape).pressable { showPull = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
                if (Downloads.downloaded.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    OrDivider()
                    Spacer(Modifier.height(12.dp))
                    GuestCard(onGuest)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScannerFrame(
    active: Boolean,
    granted: Boolean,
    denied: Boolean,
    onEnable: () -> Unit,
    onCode: (String) -> Unit,
) {
    val p = LocalGlass.current
    var torch by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(30.dp)
    val scan = if (active && granted) {
        rememberInfiniteTransition(label = "scan").animateFloat(0.12f, 0.88f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "scanY")
    } else null
    Box(
        Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(26.dp, shape, clip = false, ambientColor = p.accent, spotColor = p.accent)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1B2140), Color(0xFF101018))))
            .border(1.dp, Brush.sweepGradient(listOf(p.accent, Color.White.copy(alpha = 0.55f), p.accentHover, p.accent)), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (granted) {
            CameraScanner(active = active, torch = torch, onCode = onCode, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                Icon(painterResource(R.drawable.ic_camera), null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(12.dp))
                if (denied) {
                    Text(stringResource(R.string.login_qr_denied), style = GlassType.small, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                }
                AccentButton(stringResource(R.string.login_qr_enable), R.drawable.ic_camera, onClick = onEnable)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(26.dp)
                .drawWithContent {
                    drawContent()
                    val len = size.minDimension * 0.18f
                    val w = 3.dp.toPx()
                    val r = 14.dp.toPx()
                    val c = p.accent
                    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                        drawLine(c, Offset(x, y + dy * r), Offset(x, y + dy * len), w, StrokeCap.Round)
                        drawLine(c, Offset(x + dx * r, y), Offset(x + dx * len, y), w, StrokeCap.Round)
                        drawArc(
                            c,
                            startAngle = when {
                                dx > 0 && dy > 0 -> 180f
                                dx < 0 && dy > 0 -> 270f
                                dx < 0 && dy < 0 -> 0f
                                else -> 90f
                            },
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(if (dx > 0) x else x - 2 * r, if (dy > 0) y else y - 2 * r),
                            size = Size(2 * r, 2 * r),
                            style = Stroke(w, cap = StrokeCap.Round),
                        )
                    }
                    corner(0f, 0f, 1f, 1f)
                    corner(size.width, 0f, -1f, 1f)
                    corner(0f, size.height, 1f, -1f)
                    corner(size.width, size.height, -1f, -1f)
                    val sy = scan?.value
                    if (sy != null) {
                        val y = size.height * sy
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.28f), Color.Transparent), startY = y - 26.dp.toPx(), endY = y + 26.dp.toPx()),
                            topLeft = Offset(0f, y - 26.dp.toPx()),
                            size = Size(size.width, 52.dp.toPx()),
                        )
                        drawLine(
                            Brush.horizontalGradient(listOf(Color.Transparent, c, Color.Transparent)),
                            Offset(size.width * 0.05f, y),
                            Offset(size.width * 0.95f, y),
                            1.5.dp.toPx(),
                        )
                    }
                },
        )
        if (granted) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = if (torch) 0.15f else 0.45f))
                    .border(0.75.dp, if (torch) p.accent else Color.White.copy(alpha = 0.18f), CircleShape)
                    .pressable { torch = !torch },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_flashlight),
                    stringResource(R.string.login_qr_torch),
                    tint = if (torch) p.accent else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (granted && !active) {
            CircularProgressIndicator(Modifier.size(28.dp), color = p.accent, strokeWidth = 2.dp)
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScanner(active: Boolean, torch: Boolean, onCode: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onCode)
    val scanning by rememberUpdatedState(active)
    var camera by remember { mutableStateOf<Camera?>(null) }
    val preview = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val lastHit = remember { AtomicLong(0L) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val cp = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = cp
            val previewUse = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || !scanning) {
                    proxy.close()
                    return@setAnalyzer
                }
                val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(input)
                    .addOnSuccessListener { codes ->
                        val value = codes.firstNotNullOfOrNull { it.rawValue } ?: return@addOnSuccessListener
                        val now = System.currentTimeMillis()
                        if (now - lastHit.get() < 2500L) return@addOnSuccessListener
                        lastHit.set(now)
                        ContextCompat.getMainExecutor(context).execute { if (scanning) latest(value) }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                cp.unbindAll()
                camera = cp.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, previewUse, analysis)
            }.onFailure { Logs.add("qr", "camera failed: ${it.javaClass.simpleName}") }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            camera = null
            runCatching { provider?.unbindAll() }
        }
    }

    LaunchedEffect(torch, camera, active) {
        val cam = camera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) runCatching { cam.cameraControl.enableTorch(torch && active) }
    }
    AndroidView(factory = { preview }, modifier = modifier)
}

@Composable
private fun WideGlassButton(icon: Int, text: String, busy: Boolean, onClick: () -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .glass(RoundedCornerShape(18.dp), p, shadow = 0.dp)
            .pressable(enabled = !busy, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = p.accent, strokeWidth = 2.dp)
        } else {
            Icon(painterResource(icon), null, tint = p.text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = GlassType.rowTitle.copy(fontSize = 16.sp), color = p.text)
    }
}

@Composable
private fun OrDivider() {
    val p = LocalGlass.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassDivider(Modifier.weight(1f))
        Text(stringResource(R.string.login_or).uppercase(), style = GlassType.eyebrow, color = p.text3, modifier = Modifier.padding(horizontal = 14.dp))
        GlassDivider(Modifier.weight(1f))
    }
}

@Composable
private fun GuestCard(onGuest: () -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), p, shadow = 0.dp)
            .pressable(onClick = onGuest)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).glass(RoundedCornerShape(14.dp), p, shadow = 0.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_download), null, tint = p.text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.login_guest), style = GlassType.rowTitle, color = p.text)
            Text(stringResource(R.string.login_guest_sub), style = GlassType.small, color = p.text2)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = p.text3, modifier = Modifier.size(18.dp))
    }
}
