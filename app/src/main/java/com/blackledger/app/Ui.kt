package com.blackledger.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs

object Ink {
    val Black = Color(0xFF000000)
    val White = Color(0xFFF2F2F2)
    val Dim = Color(0xFF9A9A9A)
    val Faint = Color(0xFF4A4A4A)
    val Line = Color(0xFF2E2E2E)
    val Field = Color(0xFF141414)
    val Red = Color(0xFFE5322D)
}

// Same stand-ins as Black Sand: Space Grotesk ≈ NType 82, Space Mono for labels.
val Grotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Light),
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
)
val Mono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

fun body(size: Int = 17, color: Color = Ink.White, weight: FontWeight = FontWeight.Light) =
    TextStyle(fontFamily = Grotesk, fontSize = size.sp, fontWeight = weight, color = color)

fun label(color: Color = Ink.Dim, size: Int = 10) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, letterSpacing = 0.16.em, color = color)

@Composable
fun Txt(text: String, style: TextStyle, modifier: Modifier = Modifier, strike: Boolean = false) =
    BasicText(text, modifier, if (strike) style.copy(textDecoration = TextDecoration.LineThrough) else style)

@Composable
fun Txt(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier) = BasicText(text, modifier, style)

/** "BLACK LEDGER · BL-01 ……… FRI 06.11.2026" */
@Composable
fun TopLine(left: String, right: String) = Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
    Txt(left, label())
    Txt(right, label())
}

@Composable
fun Heading(title: String, sub: AnnotatedString) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Txt(title, body(40))
    Txt(sub, label(size = 11))
}

/** The fixed tab bar over the pager; the underline slides with your finger. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBar(pager: PagerState, names: List<String>, onTab: (Int) -> Unit) {
    val lefts = remember { mutableStateListOf(*Array(names.size) { 0f }) }
    val widths = remember { mutableStateListOf(*Array(names.size) { 0f }) }
    val pos = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (names.size - 1).toFloat())
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(Ink.Line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            val i = pos.toInt().coerceAtMost(names.size - 2)
            val f = pos - i
            val x = lefts[i] + (lefts[i + 1] - lefts[i]) * f
            val w = widths[i] + (widths[i + 1] - widths[i]) * f
            drawLine(Ink.White, Offset(x, size.height), Offset(x + w, size.height), 1.5.dp.toPx())
        },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        names.forEachIndexed { i, name ->
            val near = 1f - abs(pos - i).coerceIn(0f, 1f)
            Box(
                Modifier.height(44.dp)
                    .onGloballyPositioned { lefts[i] = it.positionInParent().x; widths[i] = it.size.width.toFloat() }
                    .semantics { selected = pager.currentPage == i }
                    .clickable(role = Role.Tab) { onTab(i) },
                contentAlignment = Alignment.Center,
            ) { Txt(name, body(15, lerp(Ink.Dim, Ink.White, near), FontWeight.Normal)) }
        }
    }
}

enum class Dot { OPEN, DONE, LATE, FUTURE }

/** One node on the strand. */
@Composable
fun Node(dot: Dot, size: Dp = 15.dp) = Canvas(Modifier.size(size)) {
    val r = this.size.minDimension / 2
    val stroke = 1.5.dp.toPx()
    when (dot) {
        Dot.DONE -> drawCircle(Ink.White, r)
        else -> {
            drawCircle(Ink.Black, r)
            drawCircle(if (dot == Dot.LATE) Ink.Red else if (dot == Dot.FUTURE) Ink.Faint else Ink.White, r - stroke / 2, style = Stroke(stroke))
        }
    }
}

/** A horizontal strand: lit up to the last done node. */
@Composable
fun Strand(dots: List<Dot>, modifier: Modifier = Modifier) = Canvas(modifier.fillMaxWidth().height(18.dp)) {
    if (dots.isEmpty()) return@Canvas
    val r = 6.dp.toPx()
    val stroke = 1.5.dp.toPx()
    val y = size.height / 2
    val step = if (dots.size == 1) 0f else (size.width - 2 * r) / (dots.size - 1)
    val x = { i: Int -> r + i * step }
    drawLine(Ink.Faint, Offset(r, y), Offset(size.width - r, y), 1.dp.toPx())
    val lit = dots.indexOfLast { it == Dot.DONE }
    if (lit > 0) drawLine(Ink.White, Offset(r, y), Offset(x(lit), y), 1.dp.toPx())
    dots.forEachIndexed { i, d ->
        val c = Offset(x(i), y)
        if (d == Dot.DONE) drawCircle(Ink.White, r, c)
        else {
            drawCircle(Ink.Black, r, c)
            drawCircle(if (d == Dot.LATE) Ink.Red else Ink.White, r - stroke / 2, c, style = Stroke(stroke))
        }
    }
}

enum class Glyph { CHECK, CROSS }

/** 44dp round button holding a hand-drawn tick or cross. */
@Composable
fun RoundButton(glyph: Glyph, description: String, ring: Color, tint: Color, fill: Color = Color.Transparent, onClick: () -> Unit) = Box(
    Modifier.size(44.dp).semantics { contentDescription = description }
        .clip(CircleShape)
        .background(fill, CircleShape)
        .border(1.dp, ring, CircleShape)
        .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Canvas(Modifier.size(if (glyph == Glyph.CHECK) 18.dp else 15.dp)) {
        val w = size.width
        val s = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
        if (glyph == Glyph.CHECK) {
            drawLine(tint, Offset(w * 0.15f, w * 0.52f), Offset(w * 0.38f, w * 0.75f), s.width, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.38f, w * 0.75f), Offset(w * 0.86f, w * 0.25f), s.width, StrokeCap.Round)
        } else {
            drawLine(tint, Offset(w * 0.15f, w * 0.15f), Offset(w * 0.85f, w * 0.85f), s.width, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.85f, w * 0.15f), Offset(w * 0.15f, w * 0.85f), s.width, StrokeCap.Round)
        }
    }
}

/** Full-width 56dp pill: filled for the main action, outlined otherwise. */
@Composable
fun Pill(text: String, filled: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) = Box(
    modifier.height(56.dp)
        .clip(RoundedCornerShape(28.dp))
        .background(if (filled && enabled) Ink.White else Color.Transparent, RoundedCornerShape(28.dp))
        .border(1.dp, if (enabled) Ink.White else Ink.Faint, RoundedCornerShape(28.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Txt(text, body(16, if (!enabled) Ink.Faint else if (filled) Ink.Black else Ink.White, if (filled) FontWeight.Medium else FontWeight.Normal))
}

/** Small rounded chip button used for Carry over / Drop and similar. */
@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) = Box(
    Modifier.height(40.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(if (on) Ink.White else Color.Transparent, RoundedCornerShape(20.dp))
        .border(1.dp, if (on) Ink.White else Ink.Faint, RoundedCornerShape(20.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
) { Txt(text, body(14, if (on) Ink.Black else Ink.White, FontWeight.Normal)) }

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
