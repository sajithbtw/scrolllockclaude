package com.sweatlock.ui.workout

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sweatlock.blocking.Perms
import com.sweatlock.ui.theme.*
import com.sweatlock.workout.*
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

private fun title(e: Exercise, goal: Int) = when (e) {
    Exercise.PLANK -> "Hold a plank for $goal seconds"
    Exercise.PAGE_TURN -> "Turn $goal pages"
    else -> "Do $goal ${e.unit}"
}

private fun hint(e: Exercise) = when (e) {
    Exercise.PAGE_TURN -> "Point the back camera at your open book, keep it steady, and turn pages one at a time."
    Exercise.PLANK -> "Place the phone side-on, show your whole body, and hold a straight line."
    Exercise.SITUP -> "Place the phone side-on so your whole body is visible."
    Exercise.JUMPING_JACK, Exercise.HIGH_KNEES -> "Face the camera and step back until your whole body is in frame."
    else -> "Show your whole body to the camera, ideally side-on."
}

/**
 * Used in two modes:
 *  - practice (lockedPackage == null): pick any exercise, nothing gets unlocked
 *  - locked (from LockActivity): exercise/goal come from Settings; finishing unlocks [lockedPackage]
 */
@Composable
fun WorkoutScreen(
    lockedPackage: String? = null,
    lockedLabel: String? = null,
    onUnlocked: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    vm: WorkoutViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var hasCamera by remember { mutableStateOf(Perms.camera(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }

    LaunchedEffect(Unit) { if (!hasCamera) launcher.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(lockedPackage) { vm.setLockedPackage(lockedPackage) }
    LaunchedEffect(ui.completed) {
        if (ui.completed && lockedPackage != null) { delay(1200); onUnlocked() }
    }

    val e = ui.exercise

    GlassBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title(e, ui.goal), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
            Text(
                if (lockedLabel != null) "to unlock $lockedLabel" else "practice mode",
                color = Muted, fontSize = 16.sp
            )
            Spacer(Modifier.height(14.dp))

            if (lockedPackage == null) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Exercise.values().forEach { ex ->
                        GlassButton("${ex.emoji} ${ex.label}", { vm.setExercise(ex) }, primary = ex == e)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.padding(6.dp).fillMaxWidth().aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(24.dp)).background(Color.Black)
                ) {
                    if (hasCamera) {
                        // key() rebuilds the camera when switching between body mode and study mode
                        key(e.isStudy) {
                            CameraPreview(e.isStudy, vm::onLandmarks, vm::onGrid, Modifier.fillMaxSize())
                        }
                        if (!e.isStudy) SkeletonOverlay(ui.landmarks, ui.state.fullBodyVisible, Modifier.fillMaxSize())
                    } else {
                        // Edge case: permission denied -> explain + deep-link to app settings
                        Column(
                            Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Camera access is needed to count your progress.", color = Color.White, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            GlassButton("Open settings", { ctx.startActivity(Perms.appDetailsIntent(ctx)) }, primary = true)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${ui.state.reps} / ${ui.goal}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(e.unit, color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (ui.state.reps.toFloat() / ui.goal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = if (ui.completed) Good else Accent,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(10.dp))
                    val msg = when {
                        ui.completed && lockedPackage != null -> "Unlocked for ${ui.durationMin} min \uD83C\uDF89"
                        ui.completed -> "Nice work! \uD83C\uDF89"
                        !ui.state.fullBodyVisible -> "Step back: show your full body"
                        ui.state.lastRepRejected -> if (e.isStudy) "Too fast: read the page first" else "Too fast, slow down"
                        e.isStudy -> if (ui.state.phase == RepPhase.DOWN) "Page moving\u2026" else "Ready: turn the next page"
                        e == Exercise.PLANK ->
                            if (ui.state.phase == RepPhase.DOWN) "Holding \u2713" else "Get into plank position"
                        e.showsAngle -> "${ui.state.phase.name} \u00B7 angle ${ui.state.angle.toInt()}\u00B0"
                        else -> if (ui.state.phase == RepPhase.DOWN) "Go!" else "Ready"
                    }
                    Text(
                        msg, textAlign = TextAlign.Center,
                        color = when {
                            ui.completed -> Good
                            !ui.state.fullBodyVisible || ui.state.lastRepRejected -> Warn
                            else -> Muted
                        }
                    )
                    if (!ui.completed) {
                        Spacer(Modifier.height(6.dp))
                        Text(hint(e), color = Muted.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (lockedPackage == null && ui.completed) GlassButton("Go again", vm::restart, primary = true)
                onClose?.let { GlassButton(if (lockedPackage != null) "Not now" else "Close", it) }
            }
            if (lockedPackage != null && !ui.completed) {
                Spacer(Modifier.height(14.dp))
                HoldButton(
                    "Emergency access \u00B7 hold 5s (${ui.emergencyLeft} left today)",
                    enabled = ui.emergencyLeft > 0, onConfirm = vm::useEmergency
                )
                ui.emergencyMsg?.let { Text(it, color = Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
    }
}

/** Must be held for 5 seconds: stops accidental or impulsive use, but guarantees you're never locked out. */
@Composable
private fun HoldButton(text: String, enabled: Boolean, onConfirm: () -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.animateTo(1f, tween(5000, easing = LinearEasing))
                        onConfirm()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    job.cancel()
                    scope.launch { progress.snapTo(0f) }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(Warn.copy(alpha = 0.35f)))
        }
        Text(text, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    }
}

@Composable
private fun CameraPreview(
    study: Boolean,
    onLandmarks: (Map<Int, Landmark>) -> Unit,
    onGrid: (FloatArray) -> Unit,
    modifier: Modifier
) {
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(modifier = modifier, factory = { c ->
        val pv = PreviewView(c).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        val future = ProcessCameraProvider.getInstance(c)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                .also { it.setAnalyzer(executor, if (study) PageAnalyzer(onGrid) else PoseAnalyzer(onLandmarks)) }
            provider.unbindAll()
            // Study mode points the BACK camera at a book; body exercises use the front camera.
            provider.bindToLifecycle(
                owner,
                if (study) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                preview, analysis
            )
        }, ContextCompat.getMainExecutor(c))
        pv
    })
}

private val BONES = listOf(
    11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
    11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28
)

/** Skeleton overlay. x is mirrored because the front camera preview is mirrored. */
@Composable
private fun SkeletonOverlay(lm: Map<Int, Landmark>, fullBody: Boolean, modifier: Modifier) {
    val color = if (fullBody) Good else Warn
    Canvas(modifier) {
        fun pt(l: Landmark) = Offset((1f - l.x) * size.width, l.y * size.height)
        BONES.forEach { (a, b) ->
            val la = lm[a]; val lb = lm[b]
            if (la != null && lb != null) drawLine(color, pt(la), pt(lb), 8f, StrokeCap.Round)
        }
        lm.values.forEach { drawCircle(Color.White, 7f, pt(it)) }
    }
}
