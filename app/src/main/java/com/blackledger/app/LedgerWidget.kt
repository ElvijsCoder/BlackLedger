package com.blackledger.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The home screen widget: today's directives or the requisition, switched from its header. */
class LedgerWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) = refresh(ctx)

    companion object {
        private const val WHITE = 0xFFF2F2F2.toInt()
        private const val DIM = 0xFF9A9A9A.toInt()
        private const val RED = 0xFFE5322D.toInt()

        fun refresh(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, LedgerWidget::class.java))
            if (ids.isEmpty()) return
            val data = Ledger.get(ctx)
            // The launcher picks the biggest layout that fits: 4×2 shows 3 rows, 4×4 shows 6.
            val views = RemoteViews(
                mapOf(
                    SizeF(200f, 100f) to build(ctx, data, 3),
                    SizeF(200f, 300f) to build(ctx, data, 6),
                )
            )
            manager.updateAppWidget(ids, views)
        }

        private fun build(ctx: Context, data: Data, rows: Int): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val shop = data.widgetShop
            v.setTextColor(R.id.tab_todo, if (shop) DIM else WHITE)
            v.setTextColor(R.id.tab_shop, if (shop) WHITE else DIM)
            v.setViewVisibility(R.id.line_todo, if (shop) View.INVISIBLE else View.VISIBLE)
            v.setViewVisibility(R.id.line_shop, if (shop) View.VISIBLE else View.INVISIBLE)
            v.setOnClickPendingIntent(R.id.tab_todo, broadcast(ctx, LedgerReceiver.MODE, 0))
            v.setOnClickPendingIntent(R.id.tab_shop, broadcast(ctx, LedgerReceiver.MODE, 1))
            v.setOnClickPendingIntent(R.id.title, open(ctx, shop))
            v.removeAllViews(R.id.rows)

            if (shop) {
                val left = data.items.filter { it.state == ItemState.TO_GET }
                v.setTextViewText(R.id.title, "Requisition")
                v.setTextViewText(R.id.count, "${left.size} TO GET")
                for (item in left.take(rows)) {
                    val r = RemoteViews(ctx.packageName, R.layout.widget_row_item)
                    r.setTextViewText(R.id.name, item.name)
                    r.setOnClickPendingIntent(R.id.name, open(ctx, true))
                    r.setOnClickPendingIntent(R.id.got, broadcast(ctx, LedgerReceiver.GOT, item.id))
                    r.setOnClickPendingIntent(R.id.na, broadcast(ctx, LedgerReceiver.NA, item.id))
                    v.addView(R.id.rows, r)
                }
                if (left.isEmpty()) v.addView(R.id.rows, empty(ctx, "Nothing to get."))
            } else {
                val today = LocalDate.now()
                val all = data.today(today)
                val open = all.filter { it.status != Status.CLEARED }
                v.setTextViewText(R.id.title, "Directives")
                v.setTextViewText(R.id.count, "${all.size - open.size}/${all.size} CLEARED")
                for (row in open.take(rows)) {
                    val r = RemoteViews(ctx.packageName, R.layout.widget_row_task)
                    val late = row.status == Status.LATE
                    r.setImageViewResource(R.id.node, if (late) R.drawable.node_late else R.drawable.node_open)
                    r.setTextViewText(R.id.name, row.d.title)
                    val tag = when {
                        late -> "−" + ChronoUnit.DAYS.between(row.d.date, today) + "D"
                        row.d.alert != null -> row.d.alert.toString()
                        else -> ""
                    }
                    r.setTextViewText(R.id.tag, tag)
                    r.setTextColor(R.id.tag, if (late) RED else DIM)
                    r.setOnClickPendingIntent(R.id.name, open(ctx, false))
                    r.setOnClickPendingIntent(R.id.check, broadcast(ctx, LedgerReceiver.CLEAR, row.d.id))
                    v.addView(R.id.rows, r)
                }
                if (open.isEmpty()) v.addView(R.id.rows, empty(ctx, if (all.isEmpty()) "Nothing on file today." else "All directives cleared."))
            }
            return v
        }

        private fun empty(ctx: Context, text: String) =
            RemoteViews(ctx.packageName, R.layout.widget_row_empty).apply { setTextViewText(R.id.name, text) }

        private fun broadcast(ctx: Context, action: String, id: Int) = PendingIntent.getBroadcast(
            ctx, action.hashCode() * 31 + id,
            Intent(ctx, LedgerReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun open(ctx: Context, shop: Boolean) = PendingIntent.getActivity(
            ctx, if (shop) 2 else 1,
            Intent(ctx, MainActivity::class.java).putExtra("shop", shop)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
