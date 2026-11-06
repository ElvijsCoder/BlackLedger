package com.blackledger.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// Run: kotlinc Model.kt ModelCheck.kt -include-runtime -d check.jar && java -jar check.jar
fun main() {
    val fri = LocalDate.of(2026, 11, 6)
    val plants = Directive(1, "Water the plants", Repeat.WEEKLY, days = setOf(1, 5), alert = LocalTime.of(18, 0), since = fri.minusDays(10))
    val rent = Directive(2, "Pay the rent", Repeat.MONTHLY, dayOfMonth = 31, since = fri.minusDays(10))
    val insurance = Directive(3, "Renew car insurance", Repeat.ONCE, date = fri.minusDays(2), since = fri.minusDays(10))
    var d = Data(directives = listOf(plants, rent, insurance), settledThrough = fri.minusDays(5))

    // Monday 02.11 plants not cleared → logged as missed once, even if settled twice.
    d = d.settle(fri).settle(fri)
    check(d.log.count { !it.cleared && it.id == 1 } == 1) { d.log }
    check(d.log.single().date == LocalDate.of(2026, 11, 2))

    // Today: overdue once-task first, then plants; rent (31st) isn't due.
    val rows = d.today(fri)
    check(rows.map { it.d.id to it.status } == listOf(3 to Status.LATE, 1 to Status.OPEN)) { rows }

    // 31st in a 30-day month falls on the 30th.
    check(rent.dueOn(LocalDate.of(2026, 11, 30)) && !rent.dueOn(LocalDate.of(2026, 11, 29)))
    check(rent.dueOn(LocalDate.of(2027, 2, 28)))

    // Clearing and undoing.
    d = d.toggle(1, fri, LocalTime.of(9, 14, 33))
    check(d.today(fri).first { it.d.id == 1 }.let { it.status == Status.CLEARED && it.clearedAt == LocalTime.of(9, 14) })
    check(d.toggle(1, fri, LocalTime.NOON).today(fri).first { it.d.id == 1 }.status == Status.OPEN)
    d = d.toggle(3, fri, LocalTime.NOON)
    check(d.today(fri).first { it.d.id == 3 }.status == Status.CLEARED)
    check(d.today(fri.plusDays(1)).none { it.d.id == 3 })

    // Alerts: cleared today → next is Monday 18:00.
    check(d.nextAlert(plants, fri.atTime(8, 0)) == LocalDate.of(2026, 11, 9).atTime(18, 0))
    check(Data(directives = listOf(plants)).nextAlert(plants, fri.atTime(8, 0)) == fri.atTime(18, 0))

    // Shopping: N/A carried, got dropped, unchecked carried only if chosen.
    var s = Data().addItem("Milk").addItem("Kefir").addItem(" Eggs ").addItem("  ")
    check(s.items.map { it.name } == listOf("Milk", "Kefir", "Eggs"))
    check(s.renameItem(2, " Kefīrs ").items[1].name == "Kefīrs" && s.renameItem(2, "  ").items[1].name == "Kefir")
    s = s.mark(1, ItemState.GOT).mark(2, ItemState.NA).endTrip(carry = setOf(1, 2), today = fri)
    check(s.items.map { it.name to it.state } == listOf("Kefir" to ItemState.TO_GET) && s.trip == 2)
    check(s.trips.single() == Trip(1, fri, got = listOf("Milk"), na = listOf("Kefir")))
    check(s.deleteTrip(1).trips.isEmpty() && s.deleteTrip(9).trips.size == 1)
    // Drop all: nothing carried.
    check(s.endTrip(emptySet(), fri).items.isEmpty())

    check(nextAt(LocalDateTime.of(fri, LocalTime.of(8, 0)), LocalTime.of(7, 30)) == fri.plusDays(1).atTime(7, 30))
    check(ordinal(1) == "1ST" && ordinal(12) == "12TH" && ordinal(23) == "23RD")
    println("ModelCheck OK")
}
