package com.blackledger.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Morning briefing, per-directive alerts and the midnight redraw. All exact, all local. */
object Alarms {
    // ponytail: fixed briefing time; make it a setting if 07:30 ever stops suiting.
    val BRIEFING: LocalTime = LocalTime.of(7, 30)

    private const val REQ_BRIEFING = 0
    private const val REQ_MIDNIGHT = -1
    private const val SNOOZE = 1_000_000

    fun schedule(ctx: Context) {
        val data = Ledger.get(ctx)
        val now = LocalDateTime.now()
        set(ctx, REQ_BRIEFING, nextAt(now, BRIEFING), LedgerReceiver.BRIEFING)
        set(ctx, REQ_MIDNIGHT, now.toLocalDate().plusDays(1).atTime(0, 0, 30), LedgerReceiver.DAY)
        for (x in data.directives) {
            val at = data.nextAlert(x, now)
            if (at != null) set(ctx, x.id, at, LedgerReceiver.ALERT, x.id) else cancel(ctx, x.id, LedgerReceiver.ALERT)
        }
    }

    fun snooze(ctx: Context, id: Int) =
        set(ctx, SNOOZE + id, LocalDateTime.now().plusHours(1), LedgerReceiver.ALERT, id)

    private fun intent(ctx: Context, req: Int, action: String, id: Int) = PendingIntent.getBroadcast(
        ctx, req,
        Intent(ctx, LedgerReceiver::class.java).setAction(action).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun set(ctx: Context, req: Int, at: LocalDateTime, action: String, id: Int = 0) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = intent(ctx, req, action, id)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    private fun cancel(ctx: Context, req: Int, action: String) =
        ctx.getSystemService(AlarmManager::class.java).cancel(intent(ctx, req, action, 0))
}

object Notes {
    private const val CH_BRIEFING = "briefing"
    private const val CH_ALERTS = "alerts"
    private const val BRIEFING_ID = 0

    private fun manager(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_BRIEFING, "Morning briefing", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Directive alerts", NotificationManager.IMPORTANCE_HIGH))
        return nm
    }

    private fun allowed(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(ctx: Context, action: String, id: Int) = PendingIntent.getBroadcast(
        ctx, action.hashCode() + id,
        Intent(ctx, LedgerReceiver::class.java).setAction(action).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun briefing(ctx: Context) {
        if (!allowed(ctx)) return
        val rows = Ledger.get(ctx).today(LocalDate.now()).filter { it.status != Status.CLEARED }
        if (rows.isEmpty()) return
        val late = rows.filter { it.status == Status.LATE }
        val body = buildString {
            append(rows.filter { it.status == Status.OPEN }.joinToString(" · ") { it.d.title })
            if (late.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Overdue: " + late.joinToString(" · ") { it.d.title })
            }
        }
        val n = Notification.Builder(ctx, CH_BRIEFING)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Briefing: ${rows.size} directive${if (rows.size == 1) "" else "s"} today")
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open(ctx))
            .setAutoCancel(true)
            .build()
        manager(ctx).notify(BRIEFING_ID, n)
    }

    fun alert(ctx: Context, id: Int) {
        if (!allowed(ctx)) return
        val row = Ledger.get(ctx).today(LocalDate.now()).firstOrNull { it.d.id == id && it.status != Status.CLEARED } ?: return
        val n = Notification.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setSubText(code(id))
            .setContentTitle(row.d.title)
            .setContentIntent(open(ctx))
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Clear", action(ctx, LedgerReceiver.CLEAR, id)).build())
            .addAction(Notification.Action.Builder(null, "In 1 hour", action(ctx, LedgerReceiver.SNOOZE, id)).build())
            .build()
        manager(ctx).notify(id, n)
    }

    fun dismiss(ctx: Context, id: Int) = ctx.getSystemService(NotificationManager::class.java).cancel(id)
}

/** Every alarm, notification button and widget tap lands here. */
class LedgerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        when (intent.action) {
            BRIEFING -> { Notes.briefing(ctx); Alarms.schedule(ctx) }
            ALERT -> { Notes.alert(ctx, id); Alarms.schedule(ctx) }
            CLEAR -> { Notes.dismiss(ctx, id); Ledger.update(ctx) { it.toggle(id, LocalDate.now(), LocalTime.now()) } }
            SNOOZE -> { Notes.dismiss(ctx, id); Alarms.snooze(ctx, id) }
            GOT -> Ledger.update(ctx) { it.mark(id, ItemState.GOT) }
            NA -> Ledger.update(ctx) { it.mark(id, ItemState.NA) }
            MODE -> Ledger.update(ctx) { it.copy(widgetShop = id == 1) }
            // Day change, reboot, clock change, app update: settle the log and re-arm everything.
            else -> { Ledger.get(ctx); LedgerWidget.refresh(ctx); Alarms.schedule(ctx) }
        }
    }

    companion object {
        const val BRIEFING = "com.blackledger.BRIEFING"
        const val ALERT = "com.blackledger.ALERT"
        const val DAY = "com.blackledger.DAY"
        const val CLEAR = "com.blackledger.CLEAR"
        const val SNOOZE = "com.blackledger.SNOOZE"
        const val GOT = "com.blackledger.GOT"
        const val NA = "com.blackledger.NA"
        const val MODE = "com.blackledger.MODE"
    }
}
