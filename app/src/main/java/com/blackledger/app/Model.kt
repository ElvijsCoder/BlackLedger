package com.blackledger.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

// Pure rules: no Android in here, so ModelCheck can run it on a plain JVM.

enum class Repeat { ONCE, WEEKLY, MONTHLY }

data class Directive(
    val id: Int,
    val title: String,
    val repeat: Repeat,
    val days: Set<Int> = emptySet(),   // WEEKLY: 1 = Monday … 7 = Sunday
    val dayOfMonth: Int = 1,           // MONTHLY: 1–31; 29–31 fall on the last day of short months
    val date: LocalDate? = null,       // ONCE: due date
    val alert: LocalTime? = null,
    val doneOn: LocalDate? = null,     // ONCE: when it was cleared
    val doneAt: LocalTime? = null,
    val since: LocalDate,              // recurring: no misses counted before this day
)

/** One day's outcome for a recurring directive. */
data class LogEntry(val id: Int, val date: LocalDate, val cleared: Boolean, val at: LocalTime? = null)

enum class ItemState { TO_GET, GOT, NA }
data class Item(val id: Int, val name: String, val state: ItemState = ItemState.TO_GET)

/** A filed requisition: what was got and what the shop didn't have. */
data class Trip(val n: Int, val date: LocalDate, val got: List<String>, val na: List<String>)

data class Data(
    val directives: List<Directive> = emptyList(),
    val log: List<LogEntry> = emptyList(),
    val items: List<Item> = emptyList(),
    val trip: Int = 1,
    val nextId: Int = 1,
    val settledThrough: LocalDate? = null,
    val widgetShop: Boolean = false,
    val trips: List<Trip> = emptyList(),
)

enum class Status { LATE, OPEN, CLEARED }
data class Due(val d: Directive, val status: Status, val clearedAt: LocalTime? = null)

fun Directive.dueOn(day: LocalDate): Boolean = when (repeat) {
    Repeat.ONCE -> date == day
    Repeat.WEEKLY -> day.dayOfWeek.value in days
    Repeat.MONTHLY -> day.dayOfMonth == minOf(dayOfMonth, day.lengthOfMonth())
}

val Directive.recurring get() = repeat != Repeat.ONCE

fun Data.entry(id: Int, day: LocalDate) = log.firstOrNull { it.id == id && it.date == day }

/** Logs every recurring directive that was due but not cleared, from the last settled day up to yesterday. */
fun Data.settle(today: LocalDate): Data {
    val start = settledThrough?.plusDays(1) ?: return copy(settledThrough = today.minusDays(1))
    if (start >= today) return this
    val misses = mutableListOf<LogEntry>()
    // ponytail: looks back at most 60 days; a phone left off longer just loses those misses.
    var day = maxOf(start, today.minusDays(60))
    while (day < today) {
        for (x in directives) {
            if (x.recurring && day >= x.since && x.dueOn(day) && entry(x.id, day) == null) {
                misses += LogEntry(x.id, day, cleared = false)
            }
        }
        day = day.plusDays(1)
    }
    return copy(log = log + misses, settledThrough = today.minusDays(1))
}

/** Today's route: overdue first, then open (by alert time), cleared last. */
fun Data.today(today: LocalDate): List<Due> = directives.mapNotNull { x ->
    if (x.repeat == Repeat.ONCE) {
        val due = x.date ?: return@mapNotNull null
        when {
            x.doneOn != null -> if (x.doneOn == today) Due(x, Status.CLEARED, x.doneAt) else null
            due < today -> Due(x, Status.LATE)
            due == today -> Due(x, Status.OPEN)
            else -> null
        }
    } else if (today >= x.since && x.dueOn(today)) {
        val e = entry(x.id, today)
        if (e?.cleared == true) Due(x, Status.CLEARED, e.at) else Due(x, Status.OPEN)
    } else null
}.sortedWith(compareBy<Due>({ it.status.ordinal }, { it.d.alert ?: LocalTime.MAX }, { it.d.id }))

/** Next day after [today] this directive is due, within two months. */
fun Directive.nextAfter(today: LocalDate): LocalDate? = when (repeat) {
    Repeat.ONCE -> date?.takeIf { it > today && doneOn == null }
    else -> (1L..62L).map { today.plusDays(it) }.firstOrNull { it >= since && dueOn(it) }
}

fun Data.upcoming(today: LocalDate, n: Int = 3): List<Pair<Directive, LocalDate>> =
    directives.mapNotNull { x -> x.nextAfter(today)?.let { x to it } }.sortedBy { it.second }.take(n)

/** Clears a directive for today, or undoes it if already cleared. */
fun Data.toggle(id: Int, today: LocalDate, now: LocalTime): Data {
    val x = directives.firstOrNull { it.id == id } ?: return this
    val at = now.truncatedTo(ChronoUnit.MINUTES)
    if (x.repeat == Repeat.ONCE) {
        val done = x.doneOn == null
        val updated = x.copy(doneOn = if (done) today else null, doneAt = if (done) at else null)
        return copy(directives = directives.map { if (it.id == id) updated else it })
    }
    val rest = log.filterNot { it.id == id && it.date == today }
    val wasCleared = entry(id, today)?.cleared == true
    return copy(log = if (wasCleared) rest else rest + LogEntry(id, today, cleared = true, at = at))
}

fun Data.save(x: Directive): Data =
    if (directives.any { it.id == x.id }) copy(directives = directives.map { if (it.id == x.id) x else it })
    else copy(directives = directives + x, nextId = maxOf(nextId, x.id + 1))

fun Data.delete(id: Int): Data =
    copy(directives = directives.filterNot { it.id == id }, log = log.filterNot { it.id == id })

// Shopping

fun Data.addItem(name: String): Data {
    val n = name.trim()
    if (n.isEmpty()) return this
    return copy(items = items + Item((items.maxOfOrNull { it.id } ?: 0) + 1, n))
}

fun Data.mark(id: Int, s: ItemState): Data = copy(items = items.map { if (it.id == id) it.copy(state = s) else it })
fun Data.removeItem(id: Int): Data = copy(items = items.filterNot { it.id == id })

fun Data.renameItem(id: Int, name: String): Data {
    val n = name.trim()
    if (n.isEmpty()) return this
    return copy(items = items.map { if (it.id == id) it.copy(name = n) else it })
}

/** Files the trip into the log; whatever wasn't got and is in [carry] goes on the next requisition. */
fun Data.endTrip(carry: Set<Int>, today: LocalDate): Data = copy(
    items = items.filter { it.state != ItemState.GOT && it.id in carry }.map { it.copy(state = ItemState.TO_GET) },
    trip = trip + 1,
    trips = trips + Trip(
        trip, today,
        got = items.filter { it.state == ItemState.GOT }.map { it.name },
        na = items.filter { it.state == ItemState.NA }.map { it.name },
    ),
)

fun Data.deleteTrip(n: Int): Data = copy(trips = trips.filterNot { it.n == n })

// Alarms

/** When this directive's alert should next ring, or null. */
fun Data.nextAlert(x: Directive, now: LocalDateTime): LocalDateTime? {
    val time = x.alert ?: return null
    val today = now.toLocalDate()
    val openToday = today(today).any { it.d.id == x.id && it.status != Status.CLEARED && it.d.dueOn(today) }
    if (openToday && now.toLocalTime() < time) return today.atTime(time)
    return x.nextAfter(today)?.atTime(time)
}

fun nextAt(now: LocalDateTime, time: LocalTime): LocalDateTime =
    if (now.toLocalTime() < time) now.toLocalDate().atTime(time) else now.toLocalDate().plusDays(1).atTime(time)

// Labels

private val DAY = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

fun ordinal(n: Int) = n.toString() + when {
    n % 100 in 11..13 -> "TH"
    n % 10 == 1 -> "ST"
    n % 10 == 2 -> "ND"
    n % 10 == 3 -> "RD"
    else -> "TH"
}

fun Directive.schedule(): String = when (repeat) {
    Repeat.ONCE -> "ONCE"
    Repeat.WEEKLY -> if (days.size == 7) "DAILY" else days.sorted().joinToString(" ") { DAY[it - 1] }
    Repeat.MONTHLY -> "MONTHLY · " + ordinal(dayOfMonth)
}

fun code(id: Int) = "D-%03d".format(id)
