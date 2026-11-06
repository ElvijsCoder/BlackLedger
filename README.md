```
  ──●──────────────●──────────────○──────────────○──
  BLACK LEDGER · BL-01                FILE IN ORDER
  ████████████████████████████████
  ─────────────────────────────
```

# B L A C K   L E D G E R

**Directives for the day. A requisition for the shop.**

`TO DO · SHOPPING · WIDGET · NOTHING LEAVES THE PHONE`

**Black Ledger × Claude** · Designed by Elvijs S.

---

## ● What is this

Black Ledger is a to-do list and a shopping list in one small Android app, built for (and tested on) a **Nothing Phone (3a)**. Tasks are *directives*: you clear them, or they get logged as missed. The shopping list is a *requisition*: you tick what you got, cross what the shop didn't have, and file a report when you leave.

Black and white, one red for things that went wrong. No accounts, no cloud, no backups to Google. One JSON file on the phone.

---

## ◉ Inspiration

- **Death Stranding.** Thin lines, quiet type, and the strand: every day is a route, every task a stop on it. Hollow until it's done, filled when it's delivered.
- **Control.** The Bureau's paperwork voice: numbered directives (`D-017`), forms (`BL-01`), requisitions, reports. Even the icon is a redacted line.
- **PlayStation 2.** The late-night boot-screen calm of black with a few white lights in it.
- **Nothing OS.** Monochrome, mono labels, and a widget that sits next to Black Sand without arguing with it.

---

## ○ What's in it

### Directives
- **Once, weekly or monthly.** Once = a date. Weekly = any set of days. Monthly = a day of the month; 29–31 fall on the last day of short months.
- **Missed is missed.** A recurring directive you didn't clear leaves the list at midnight and goes in the log as missed.
- **Overdue stays.** A one-off you didn't do stays on file, in red, counting the days.
- **Tap the circle** to clear, tap again to undo. Tap the directive itself to edit or delete it.
- **Next on file** shows what's coming after today.

### Log
- This week as a strand, everything missed this month, and the last ten runs of every recurring directive as dots.

### Requisition
- **Type and press enter.** The keyboard stays up. `milk, bread, eggs` files three at once.
- **✓ Got** or **✕ Was not able to get.** Tap anything in the Got or N/A sections to put it back.
- **Long-press** an item to strike it off entirely.
- **End trip · file report.** Shows what you got and what you didn't, and asks per item whether to carry it to the next requisition or drop it.

### Alerts
- **Morning briefing at 07:30:** what's on today and what's overdue. Every directive is in it.
- **Per-directive alerts** at a time you pick, off by default. *Clear* or *In 1 hour* right from the notification.

### The widget
- **One widget, two tabs:** *To do* and *Shop*, switched from its header.
- **4×2** shows three rows; stretch it to **4×4** on shopping days for six.
- Clear, tick or cross straight from the home screen.

---

## ⌕ Where to find things

| Looking for… | It's here |
|---|---|
| New directive | **+ New directive** at the bottom of Directives |
| Edit or delete one | Tap its name |
| Missed log | **MISSED LOG** under Next on file |
| Shopping list | **Shopping** tab, or the widget's *Shop* |
| Remove a shopping item | Long-press its name |
| End the trip | **End trip · file report** |
| Widget | Home screen → Widgets → **Black Ledger** |

---

## ⚙ Under the hood

- **Kotlin + Jetpack Compose**, drawn by hand: no Material, no icon packs.
- **Storage:** one `ledger.json` in private storage, written safely (temp file, then rename). Backups are off.
- **Rules** live in `Model.kt` with no Android in them; `app/src/test/.../ModelCheck.kt` checks them on a plain JVM.
- **Alarms:** exact `AlarmManager` alarms for the briefing, each alert and the midnight roll-over; re-armed after reboot and clock changes.
- **Widget:** classic `RemoteViews`, rows added per draw, sized by the launcher.

```
app/src/main/java/com/blackledger/app/
├─ Model.kt         directives, log, requisition: the rules
├─ Ledger.kt        the JSON file and the one shared copy of it
├─ Alarms.kt        briefing, alerts, notifications, the receiver
├─ LedgerWidget.kt  the home screen widget
├─ MainActivity.kt  screen switching
├─ Directives.kt    Directives + Log
├─ Requisition.kt   Requisition + Report
├─ Editor.kt        new / edit directive
└─ Ui.kt            colours, type, strand, buttons
```

---

## ⬇ Getting it on your phone

Same as Black Sand: push to `main`, GitHub Actions builds a signed APK as a release, Obtainium picks it up. First-time setup is in **SETUP.md**.

---

## ⏱ Tuning knobs

| What | Where |
|---|---|
| Briefing time (07:30) | `BRIEFING` in `Alarms.kt` |
| Snooze length (1 hour) | `snooze()` in `Alarms.kt` |
| Red | `Ink.Red` in `Ui.kt`, `RED` in `LedgerWidget.kt`, `node_late.xml` |
| Widget rows per size | `refresh()` in `LedgerWidget.kt` |

---

## ✦ Credits

- **Design & direction:** Elvijs S.
- **Built with:** Claude, one conversation, three directions and a lot of Bureau talk.
- **Fonts:** [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk) and [Space Mono](https://fonts.google.com/specimen/Space+Mono), SIL Open Font License (see `OFL-fonts.txt`). Stand-ins for Nothing's own type.

Black Ledger is a personal project and isn't affiliated with Nothing, Remedy, Kojima Productions or Sony. Their stuff was just very inspiring.

`● FILED`   for bins on Fridays and kefir that's never in stock

**Black Ledger × Claude** · Designed by Elvijs S.
