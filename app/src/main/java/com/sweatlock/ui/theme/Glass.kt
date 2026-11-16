package com.sweatlock.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlin.math.cos
import kotlin.math.sin

/** Shared info so every glass card can redraw the exact same background behind itself, blurred. */
@Stable
class Backdrop(val time: State<Float>) {
    var origin by mutableStateOf(Offset.Zero) // window position of the background
    var size by mutableStateOf(Size.Zero)
}

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/** Dark gradient + slow-drifting soft glows and crisp glossy orbs (the things the glass blurs). */
private fun DrawScope.drawAura(t: Float, w: Float, h: Float) {
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF0A0F2E), Color(0xFF1A1048), Color(0xFF2B0F3F)), startY = 0f, endY = h),
        size = Size(w, h)
    )
    fun a(phase: Float) = (2.0 * Math.PI * (t + phase)).toFloat()

    fun glow(c: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
        val ctr = Offset(cx, cy)
        drawCircle(
            Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), center = ctr, radius = r),
            radius = r, center = ctr
        )
    }
    fun orb(c1: Color, c2: Color, cx: Float, cy: Float, r: Float) {
        drawCircle(
            Brush.linearGradient(listOf(c1, c2), start = Offset(cx - r, cy - r), end = Offset(cx + r, cy + r)),
            radius = r, center = Offset(cx, cy)
        )
    }

    glow(Color(0xFF3D7BFF), w * (0.15f + 0.10f * sin(a(0f))), h * (0.12f + 0.05f * cos(a(0f))), w * 0.95f, 0.65f)
    glow(Color(0xFFFF3D9A), w * (0.92f + 0.06f * cos(a(0.3f))), h * (0.45f + 0.06f * sin(a(0.3f))), w * 0.85f, 0.55f)
    glow(Color(0xFF19E3C0), w * (0.18f + 0.08f * sin(a(0.6f))), h * (0.88f + 0.04f * cos(a(0.6f))), w * 0.95f, 0.45f)

    orb(Color(0xFF5AC8FA), Color(0xFF5E5CE6), w * (0.80f + 0.05f * sin(a(0.1f))), h * (0.18f + 0.03f * cos(a(0.1f))), w * 0.16f)
    orb(Color(0xFFFF6482), Color(0xFFFF9F0A), w * (0.14f + 0.05f * cos(a(0.4f))), h * (0.52f + 0.04f * sin(a(0.4f))), w * 0.20f)
    orb(Color(0xFFBF5AF2), Color(0xFF0A84FF), w * (0.86f + 0.04f * sin(a(0.7f))), h * (0.78f + 0.03f * cos(a(0.7f))), w * 0.14f)
    orb(Color(0xFF30D158), Color(0xFF5AC8FA), w * (0.30f + 0.05f * cos(a(0.9f))), h * (0.10f + 0.03f * sin(a(0.9f))), w * 0.09f)

    drawCircle(Color.White.copy(alpha = 0.10f), radius = w * 0.34f, center = Offset(w * 0.75f, h * 0.62f), style = Stroke(3f))
    drawCircle(Color.White.copy(alpha = 0.08f), radius = w * 0.22f, center = Offset(w * 0.2f, h * 0.30f), style = Stroke(3f))
}

@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "aura")
    val time = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t"
    )
    val backdrop = remember { Backdrop(time) }

    Box(
        modifier.fillMaxSize().onGloballyPositioned {
            backdrop.origin = it.positionInWindow()
            backdrop.size = it.size.toSize()
        }
    ) {
        Canvas(Modifier.fillMaxSize()) { drawAura(backdrop.time.value, size.width, size.height) }
        CompositionLocalProvider(LocalBackdrop provides backdrop) { content() }
    }
}

/**
 * Liquid-glass card: a blurred copy of the background behind it, a dark+white tint,
 * and a bright diagonal edge highlight. Real blur needs Android 12+ (you're on 16).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .onGloballyPositioned { pos = it.positionInWindow() }
            .shadow(18.dp, shape, ambientColor = Color(0x40000000), spotColor = Color(0x66000000))
            .clip(shape)
            .border(
                1.2.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.02f), Color.White.copy(alpha = 0.30f))
                ),
                shape
            )
    ) {
        if (backdrop != null) {
            Canvas(
                Modifier.matchParentSize().graphicsLayer {
                    renderEffect = BlurEffect(70f, 70f, TileMode.Clamp)
                    clip = true
                }
            ) {
                translate(left = backdrop.origin.x - pos.x, top = backdrop.origin.y - pos.y) {
                    drawAura(backdrop.time.value, backdrop.size.width, backdrop.size.height)
                }
            }
        }
        Box(Modifier.matchParentSize().background(Color(0x33000014)))
        Box(
            Modifier.matchParentSize().background(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f)))
            )
        )
        Column(content = content)
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(
                if (primary) Brush.linearGradient(listOf(Color(0xFF5AA9FF), Color(0xFF7D5BFF)))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.08f)))
            )
            .border(1.dp, Color.White.copy(alpha = if (primary) 0.45f else 0.30f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RoundGlassButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Medium) }
}

@Composable
fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf("\uD83C\uDFE0" to "Home", "\uD83D\uDCF5" to "Apps", "\uD83D\uDCCA" to "Stats", "\u2699\uFE0F" to "Settings")
    GlassCard(
        modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(40.dp)
    ) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { i, (emoji, label) ->
                Column(
                    Modifier.clip(RoundedCornerShape(30.dp))
                        .background(if (i == selected) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(emoji, fontSize = 20.sp)
                    Text(
                        label, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = if (i == selected) Color.White else Color.White.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}
