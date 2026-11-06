package com.blackledger.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE dd.MM.yyyy", Locale.ENGLISH)
private const val DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert

@Composable
fun EditorScreen(data: Data, id: Int?, today: LocalDate, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val existing = data.directives.firstOrNull { it.id == id }
    val myId = existing?.id ?: data.nextId

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var kind by remember { mutableStateOf(existing?.repeat ?: Repeat.ONCE) }
    var days by remember { mutableStateOf(existing?.days ?: setOf(today.dayOfWeek.value)) }
    var dayOfMonth by remember { mutableStateOf(existing?.dayOfMonth ?: today.dayOfMonth) }
    var date by remember { mutableStateOf(existing?.date ?: today) }
    var alert by remember { mutableStateOf(existing?.alert) }
    var armDelete by remember { mutableStateOf(false) }
    val valid = title.isNotBlank() && (kind != Repeat.WEEKLY || days.isNotEmpty())

    fun pickTime() {
        val t = alert ?: LocalTime.of(18, 0)
        TimePickerDialog(ctx, DIALOG_THEME, { _, h, m -> alert = LocalTime.of(h, m) }, t.hour, t.minute, true).show()
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp, bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Txt((if (existing == null) "NEW DIRECTIVE · " else "DIRECTIVE · ") + code(myId), label())
            RoundButton(Glyph.CROSS, "Close", ring = Color.Transparent, tint = Ink.White) { go(Screen.Todo) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().drawBehind {
                    drawLine(Ink.White, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }.padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt("DIRECTIVE", label())
                Box {
                    if (title.isEmpty()) Txt("What needs doing", body(28, Ink.Faint))
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Directive" },
                        singleLine = true,
                        textStyle = body(28),
                        cursorBrush = SolidColor(Ink.White),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt("REPEATS", label())
                Row(
                    Modifier.fillMaxWidth().border(1.dp, Ink.Faint, RoundedCornerShape(24.dp)).padding(3.dp)
                ) {
                    for ((r, name) in listOf(Repeat.ONCE to "Once", Repeat.WEEKLY to "Weekly", Repeat.MONTHLY to "Monthly")) {
                        val on = kind == r
                        Box(
                            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp))
                                .background(if (on) Ink.White else Color.Transparent)
                                .semantics { selected = on }
                                .clickable(role = Role.Tab) { kind = r },
                            contentAlignment = Alignment.Center,
                        ) { Txt(name, body(15, if (on) Ink.Black else Ink.White, FontWeight.Normal)) }
                    }
                }
            }

            when (kind) {
                Repeat.ONCE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt("DUE", label())
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                            .border(1.dp, Ink.Faint, RoundedCornerShape(28.dp))
                            .clickable(role = Role.Button) {
                                DatePickerDialog(ctx, DIALOG_THEME, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                                    date.year, date.monthValue - 1, date.dayOfMonth).show()
                            }
                            .padding(horizontal = 18.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically,
                    ) {
                        Txt(LONG_DATE.format(date), body(17, weight = FontWeight.Normal))
                        Txt("CHANGE", label())
                    }
                    Txt("IF MISSED: STAYS ON FILE AS OVERDUE", label())
                }
                Repeat.WEEKLY -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt("ON", label())
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        for (dow in DayOfWeek.values()) {
                            val on = dow.value in days
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .background(if (on) Ink.White else Color.Transparent)
                                    .border(1.dp, if (on) Ink.White else Ink.Faint, CircleShape)
                                    .semantics {
                                        contentDescription = dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                                        selected = on
                                    }
                                    .clickable { days = if (on) days - dow.value else days + dow.value },
                                contentAlignment = Alignment.Center,
                            ) { Txt(dow.name.take(1), label(if (on) Ink.Black else Ink.White, 13)) }
                        }
                    }
                    Txt("IF MISSED: LEAVES THE LIST, LOGGED AS MISSED", label())
                }
                Repeat.MONTHLY -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt("DAY OF THE MONTH", label(), Modifier.padding(bottom = 6.dp))
                    for (week in (1..31).chunked(7)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                            for (n in week) {
                                val on = n == dayOfMonth
                                Box(
                                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp))
                                        .background(if (on) Ink.White else Ink.Field)
                                        .semantics { selected = on }
                                        .clickable { dayOfMonth = n },
                                    contentAlignment = Alignment.Center,
                                ) { Txt("$n", label(if (on) Ink.Black else Ink.White, 12)) }
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Txt("29–31 IN SHORT MONTHS → LAST DAY", label(), Modifier.padding(top = 6.dp))
                }
            }

            Column {
                Divider()
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f).clickable(enabled = alert != null) { pickTime() },
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Txt("Alert", body(16, weight = FontWeight.Normal))
                        Txt(alert?.let { "$it · TAP TO CHANGE" } ?: "OFF", label())
                    }
                    Switch(on = alert != null) { if (alert == null) pickTime() else { alert = null } }
                }
                Divider()
                Txt("EVERY DIRECTIVE IS IN THE ${Alarms.BRIEFING} BRIEFING", label(), Modifier.padding(top = 10.dp))
            }

            if (existing != null) {
                Txt(
                    if (armDelete) "TAP AGAIN TO DELETE" else "DELETE DIRECTIVE",
                    label(Ink.Red),
                    Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) {
                        if (armDelete) { Ledger.update(ctx) { it.delete(existing.id) }; go(Screen.Todo) } else { armDelete = true }
                    }.padding(vertical = 14.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Pill("File directive", filled = true, enabled = valid) {
            val x = Directive(
                id = myId,
                title = title.trim(),
                repeat = kind,
                days = if (kind == Repeat.WEEKLY) days else emptySet(),
                dayOfMonth = dayOfMonth,
                date = if (kind == Repeat.ONCE) date else null,
                alert = alert,
                doneOn = existing?.doneOn,
                doneAt = existing?.doneAt,
                since = existing?.since ?: today,
            )
            Ledger.update(ctx) { it.save(x) }
            go(Screen.Todo)
        }
    }
}

@Composable
private fun Switch(on: Boolean, onToggle: () -> Unit) = Box(
    Modifier.size(52.dp, 30.dp).clip(RoundedCornerShape(15.dp))
        .background(if (on) Ink.White else Color.Transparent)
        .border(1.dp, Ink.White, RoundedCornerShape(15.dp))
        .semantics { contentDescription = "Alert"; selected = on }
        .clickable(role = Role.Switch, onClick = onToggle)
        .padding(3.dp),
    contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
) {
    Box(Modifier.size(22.dp).background(if (on) Ink.Black else Ink.White, CircleShape))
}
