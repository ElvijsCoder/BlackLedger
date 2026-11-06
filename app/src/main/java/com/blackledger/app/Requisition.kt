package com.blackledger.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private fun tripLabel(n: Int) = "TRIP %03d".format(n)

@Composable
fun RequisitionScreen(data: Data, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val toGet = data.items.filter { it.state == ItemState.TO_GET }
    val got = data.items.filter { it.state == ItemState.GOT }
    val na = data.items.filter { it.state == ItemState.NA }
    val set = { id: Int, s: ItemState -> Ledger.update(ctx) { it.mark(id, s) } }

    Column(Modifier.fillMaxSize().padding(top = 18.dp, bottom = 24.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TopLine("BLACK LEDGER · BL-02", tripLabel(data.trip)) }
            item {
                Heading("Requisition", buildAnnotatedString {
                    append("${got.size} GOT · ")
                    withStyle(SpanStyle(color = if (na.isEmpty()) Ink.Dim else Ink.Red)) { append("${na.size} N/A") }
                    append(" · ${toGet.size} TO GET")
                })
            }
            item {
                AddField { text ->
                    // "milk, bread, eggs" files three items at once.
                    Ledger.update(ctx) { d -> text.split(',').fold(d) { acc, name -> acc.addItem(name) } }
                }
            }
            if (toGet.isNotEmpty()) item { Txt("TO GET", label()) }
            items(toGet, key = { it.id }) { item -> ToGetRow(item, set, onRemove = { Ledger.update(ctx) { it.removeItem(item.id) } }) }
            if (got.isNotEmpty()) item { Txt("GOT · TAP TO UNDO", label()) }
            items(got, key = { it.id }) { item ->
                Txt(item.name, body(16, Ink.Dim), Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    .clickable { set(item.id, ItemState.TO_GET) }.padding(vertical = 10.dp), strike = true)
            }
            if (na.isNotEmpty()) item { Txt("N/A · WAS NOT ABLE TO GET", label(Ink.Red)) }
            items(na, key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { set(item.id, ItemState.TO_GET) },
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Node(Dot.LATE, 9.dp)
                    Txt(item.name, body(16))
                }
            }
            if (data.items.isEmpty()) item { Txt("Requisition is empty. Type above and press enter.", body(16, Ink.Dim)) }
        }
        Spacer(Modifier.height(12.dp))
        Pill("End trip · file report", filled = true, enabled = got.isNotEmpty() || na.isNotEmpty()) { go(Screen.Report) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToGetRow(item: Item, set: (Int, ItemState) -> Unit, onRemove: () -> Unit) = Row(
    Modifier.fillMaxWidth().height(56.dp).drawBehind {
        drawLine(Ink.Line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    },
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    // Long-press the name to strike it off the list entirely.
    Txt(item.name, body(17), Modifier.weight(1f).combinedClickable(onLongClick = onRemove, onClick = {}))
    RoundButton(Glyph.CHECK, "Got ${item.name}", ring = Ink.White, tint = Ink.White) { set(item.id, ItemState.GOT) }
    RoundButton(Glyph.CROSS, "Was not able to get ${item.name}", ring = Ink.Faint, tint = Ink.Dim) { set(item.id, ItemState.NA) }
}

@Composable
private fun AddField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) onAdd(text); text = "" }
    Row(
        Modifier.fillMaxWidth().height(50.dp).border(1.dp, Ink.Faint, RoundedCornerShape(25.dp)).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("+", body(20))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Txt("Milk, bread, …", body(16, Ink.Dim))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Add to requisition" },
                singleLine = true,
                textStyle = body(16),
                cursorBrush = SolidColor(Ink.White),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                // Keeps the keyboard up, so the whole list can be typed in one go.
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        }
    }
}

@Composable
fun ReportScreen(data: Data, today: LocalDate, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val got = data.items.filter { it.state == ItemState.GOT }
    val left = data.items.filter { it.state != ItemState.GOT }
    var carry by remember { mutableStateOf(left.map { it.id }.toSet()) }
    val na = left.count { it.state == ItemState.NA }

    Column(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 24.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item { TopLine("BLACK LEDGER · BL-03", dayLabel(today)) }
            item { Heading("Report", AnnotatedString("REQUISITION · ${tripLabel(data.trip)} · CLOSING")) }
            item {
                Strand(got.map { Dot.DONE } + left.map { if (it.state == ItemState.NA) Dot.LATE else Dot.OPEN })
            }
            item {
                Row(Modifier.fillMaxWidth().drawBehind {
                    drawLine(Ink.Line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                    drawLine(Ink.Line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    drawLine(Ink.Line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
                }) {
                    Stat("%02d".format(got.size), "GOT", Ink.White, Modifier.weight(1f))
                    Stat("%02d".format(na), "WAS NOT ABLE TO GET", if (na > 0) Ink.Red else Ink.White, Modifier.weight(1f).padding(start = 20.dp))
                }
            }
            if (left.isNotEmpty()) item { Txt("CARRY TO NEXT REQUISITION?", label()) }
            items(left, key = { it.id }) { item ->
                val on = item.id in carry
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Node(if (item.state == ItemState.NA) Dot.LATE else Dot.OPEN, 9.dp)
                    Column(Modifier.weight(1f)) {
                        Txt(item.name, body(17))
                        if (item.state == ItemState.TO_GET) Txt("NOT TICKED", label())
                    }
                    Chip("Carry over", on) { carry = carry + item.id }
                    Chip("Drop", !on) { carry = carry - item.id }
                }
            }
            if (got.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt("GOT", label())
                    Txt(got.joinToString(" · ") { it.name }, body(15, Ink.Dim))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Two ways out: file with the carry / drop choices above, or drop everything left.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Drop all", filled = false, modifier = Modifier.weight(1f)) {
                Ledger.update(ctx) { it.endTrip(emptySet(), today) }
                go(Screen.Shop)
            }
            Pill("File report", filled = true, modifier = Modifier.weight(1f)) {
                Ledger.update(ctx) { it.endTrip(carry, today) }
                go(Screen.Shop)
            }
        }
    }
}

@Composable
private fun Stat(value: String, name: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) =
    Column(modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Txt(value, body(56, color))
        Txt(name, label())
    }
