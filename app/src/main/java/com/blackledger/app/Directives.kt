package com.blackledger.app

import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val DAY_LABEL = DateTimeFormatter.ofPattern("EEE dd.MM.yyyy", Locale.ENGLISH)
private val SHORT = DateTimeFormatter.ofPattern("EEE dd.MM", Locale.ENGLISH)
private val DM = DateTimeFormatter.ofPattern("dd.MM", Locale.ENGLISH)

fun dayLabel(d: LocalDate): String = DAY_LABEL.format(d).uppercase()

fun Due.dot() = when (status) {
    Status.CLEARED -> Dot.DONE
    Status.LATE -> Dot.LATE
    Status.OPEN -> Dot.OPEN
}

private fun Due.meta(today: LocalDate): String = buildString {
    append(code(d.id)).append(" · ")
    when (status) {
        Status.CLEARED -> append("CLEARED ").append(clearedAt ?: "").append(" · ").append(d.schedule())
        Status.LATE -> {
            val n = ChronoUnit.DAYS.between(d.date, today)
            append("OVERDUE $n DAY").append(if (n == 1L) "" else "S").append(" · ONCE")
        }
        Status.OPEN -> {
            append(d.schedule())
            if (d.repeat == Repeat.ONCE) append(" · TODAY")
            d.alert?.let { append(" · ALERT ").append(it) }
        }
    }
}

@Composable
fun DirectivesScreen(data: Data, today: LocalDate, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val rows = data.today(today)
    val cleared = rows.count { it.status == Status.CLEARED }
    val missedWeek = data.log.count { !it.cleared && it.date >= today.with(DayOfWeek.MONDAY) }
    val upcoming = data.upcoming(today)
    val buzz = rememberHaptics()

    Column(Modifier.fillMaxSize().padding(top = 18.dp, bottom = 24.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { TopLine("BLACK LEDGER · BL-01", dayLabel(today)) }
            item { Heading("Directives", AnnotatedString("TODAY · $cleared OF ${rows.size} CLEARED")) }
            // The strand reads left to right: cleared first, then what's left.
            if (rows.isNotEmpty()) item { Strand(rows.sortedBy { it.status != Status.CLEARED }.map { it.dot() }) }
            item {
                if (rows.isEmpty()) Txt("Nothing on file today.", body(16, Ink.Dim), Modifier.padding(vertical = 12.dp))
                else Route(rows, today,
                    onToggle = { id -> buzz(true); Ledger.update(ctx) { it.toggle(id, today, LocalTime.now()) } },
                    onOpen = { id -> go(Screen.Edit(id)) })
            }
            if (upcoming.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Divider()
                    Spacer(Modifier.height(10.dp))
                    Txt("NEXT ON FILE", label())
                    for ((x, day) in upcoming) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { go(Screen.Edit(x.id)) },
                            Arrangement.SpaceBetween, Alignment.CenterVertically,
                        ) {
                            Txt(x.title, body(16), Modifier.weight(1f))
                            Txt("${code(x.id)} · ${DM.format(day)}", label())
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.Button) { go(Screen.Log) }
                        .drawBehind { drawLine(Ink.Line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) },
                    Arrangement.SpaceBetween, Alignment.CenterVertically,
                ) {
                    Txt("MISSED LOG · $missedWeek THIS WEEK", label())
                    Txt(buildAnnotatedString {
                        if (missedWeek > 0) withStyle(SpanStyle(color = Ink.Red)) { append("●  ") }
                        append("OPEN ›")
                    }, label())
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Pill("+ New directive", filled = false) { go(Screen.Edit(null)) }
    }
}

/** Today's directives as stops on a vertical strand. */
@Composable
private fun Route(rows: List<Due>, today: LocalDate, onToggle: (Int) -> Unit, onOpen: (Int) -> Unit) = Column(
    Modifier.fillMaxWidth().drawBehind {
        if (rows.size > 1) {
            val x = 7.5.dp.toPx()
            drawLine(Ink.Faint, Offset(x, 33.dp.toPx()), Offset(x, size.height - 33.dp.toPx()), 1.dp.toPx())
        }
    }
) {
    for (r in rows) {
        val done = r.status == Status.CLEARED
        Row(
            Modifier.fillMaxWidth().heightIn(min = 66.dp).clickable { onOpen(r.d.id) },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Node(r.dot())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt(r.d.title, body(18, if (done) Ink.Dim else Ink.White, FontWeight.Normal))
                Txt(r.meta(today), label(if (r.status == Status.LATE) Ink.Red else Ink.Dim))
            }
            RoundButton(
                Glyph.CHECK,
                if (done) "Undo ${r.d.title}" else "Clear ${r.d.title}",
                ring = if (done) Ink.White else Ink.Faint,
                tint = if (done) Ink.Black else Ink.White,
                fill = if (done) Ink.White else Color.Transparent,
            ) { onToggle(r.d.id) }
        }
    }
}

@Composable
fun LogScreen(data: Data, today: LocalDate) {
    val monthStart = today.withDayOfMonth(1)
    val inMonth = { d: LocalDate? -> d != null && d >= monthStart && d <= today }
    val missed = data.log.filter { !it.cleared && inMonth(it.date) }.sortedByDescending { it.date }
    val clearedCount = data.log.count { it.cleared && inMonth(it.date) } + data.directives.count { inMonth(it.doneOn) }
    val title = { id: Int -> data.directives.firstOrNull { it.id == id }?.title ?: "Deleted directive" }
    val monday = today.with(DayOfWeek.MONDAY)
    val week = (0L..6L).map { monday.plusDays(it) }
    val recurring = data.directives.filter { it.recurring }
    val trips = data.trips.asReversed().take(12)

    LazyColumn(Modifier.fillMaxSize().padding(top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { TopLine("BLACK LEDGER · BL-04", dayLabel(today)) }
        item {
            Heading("Log", buildAnnotatedString {
                append("${today.month.name} · $clearedCount CLEARED · ")
                withStyle(SpanStyle(color = if (missed.isEmpty()) Ink.Dim else Ink.Red)) { append("${missed.size} MISSED") }
            })
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt("THIS WEEK", label())
                Row(
                    Modifier.fillMaxWidth().drawBehind {
                        val y = 7.5.dp.toPx()
                        drawLine(Ink.Faint, Offset(7.5.dp.toPx(), y), Offset(size.width - 7.5.dp.toPx(), y), 1.dp.toPx())
                    },
                    Arrangement.SpaceBetween,
                ) {
                    for (d in week) {
                        Column(Modifier.width(15.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Node(weekDot(data, d, today))
                            Txt(d.dayOfWeek.name.take(1), label(if (d == today) Ink.White else Ink.Dim))
                        }
                    }
                }
            }
        }
        item { Txt("MISSED", label(Ink.Red)) }
        if (missed.isEmpty()) item { Txt("Nothing missed this month.", body(16, Ink.Dim)) }
        items(missed) { e ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Txt(title(e.id), body(17), Modifier.weight(1f))
                Txt("${code(e.id)} · ${SHORT.format(e.date).uppercase()}", label())
            }
        }
        if (recurring.isNotEmpty()) item { Txt("PER DIRECTIVE · LAST 10", label()) }
        items(recurring) { x ->
            val last = data.log.filter { it.id == x.id }.sortedBy { it.date }.takeLast(10)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Txt(x.title, body(16), Modifier.weight(1f))
                    Txt(if (last.isEmpty()) "NO RECORD YET" else "${last.count { it.cleared }} / ${last.size}", label())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (e in last) Node(if (e.cleared) Dot.DONE else Dot.LATE, 9.dp)
                }
                Divider()
            }
        }
        item { Txt("PAST REQUISITIONS · TAP TO OPEN · HOLD TO DELETE", label()) }
        if (trips.isEmpty()) item { Txt("No trips filed yet.", body(16, Ink.Dim)) }
        items(trips, key = { it.n }) { t -> TripRow(t) }
    }
}

/** One filed trip: closed it's a single line; tap opens what was got and missed; hold asks to delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripRow(t: Trip) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .combinedClickable(onClick = { open = !open; confirm = false }, onLongClick = { confirm = true }),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(if (open) "−" else "+", body(18, Ink.Dim))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Txt("Trip ${t.n}", body(17, weight = FontWeight.Normal))
                Txt(SHORT.format(t.date).uppercase(), label())
            }
            Txt(buildAnnotatedString {
                append("${t.got.size} GOT")
                if (t.na.isNotEmpty()) withStyle(SpanStyle(color = Ink.Red)) { append(" · ${t.na.size} N/A") }
            }, label())
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 26.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (t.got.isEmpty() && t.na.isEmpty()) Txt("Nothing was ticked on this trip.", body(15, Ink.Dim))
                for (name in t.got) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Node(Dot.DONE, 9.dp); Txt(name, body(15, Ink.Dim))
                }
                for (name in t.na) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Node(Dot.LATE, 9.dp); Txt(name, body(15))
                }
            }
        }
        if (confirm) {
            Row(
                Modifier.fillMaxWidth().padding(start = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("DELETE TRIP ${t.n}?", label(Ink.Red), Modifier.weight(1f))
                Chip("Keep", on = false) { confirm = false }
                Chip("Delete", on = true) { Ledger.update(ctx) { it.deleteTrip(t.n) } }
            }
        }
        Divider()
    }
}

private fun weekDot(data: Data, d: LocalDate, today: LocalDate): Dot {
    if (d > today) return Dot.FUTURE
    if (data.log.any { it.date == d && !it.cleared }) return Dot.LATE
    if (d == today) return Dot.OPEN
    val anyDue = data.directives.any { (it.recurring && d >= it.since && it.dueOn(d)) || it.doneOn == d }
    return if (anyDue) Dot.DONE else Dot.FUTURE
}
