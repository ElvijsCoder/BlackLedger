package com.blackledger.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Cold-start opening: the strand draws across, one node lights per directive today
 * (filled = already cleared), then it fades into the app. Under a second; a tap skips it.
 */
@Composable
fun Intro(dots: List<Dot>, cleared: Int, onDone: () -> Unit) {
    val line = remember { Animatable(0f) }
    val lit = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        line.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        if (dots.isNotEmpty()) lit.animateTo(dots.size.toFloat(), tween(60 + 70 * dots.size, easing = LinearEasing))
        delay(220)
        fade.animateTo(0f, tween(220))
        onDone()
    }

    Column(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Ink.Black)
            .pointerInput(Unit) { detectTapGestures { onDone() } }
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val r = 6.dp.toPx()
            val stroke = 1.5.dp.toPx()
            val y = size.height / 2
            val span = size.width - 2 * r
            drawLine(Ink.Faint, Offset(r, y), Offset(r + span * line.value, y), 1.dp.toPx())
            val step = if (dots.size <= 1) 0f else span / (dots.size - 1)
            dots.forEachIndexed { i, d ->
                // Each node pops in as the count passes it.
                val grow = (lit.value - i).coerceIn(0f, 1f)
                if (grow <= 0f) return@forEachIndexed
                val c = Offset(r + i * step, y)
                val radius = r * grow
                if (d == Dot.DONE) drawCircle(Ink.White, radius, c)
                else {
                    drawCircle(Ink.Black, radius, c)
                    drawCircle(if (d == Dot.LATE) Ink.Red else Ink.White, (radius - stroke / 2).coerceAtLeast(0f), c, style = Stroke(stroke))
                }
            }
        }
        Txt(
            if (dots.isEmpty()) "BLACK LEDGER · NOTHING ON FILE TODAY" else "BLACK LEDGER · $cleared OF ${dots.size} CLEARED",
            label(),
            Modifier.graphicsLayer { alpha = line.value },
        )
    }
}
