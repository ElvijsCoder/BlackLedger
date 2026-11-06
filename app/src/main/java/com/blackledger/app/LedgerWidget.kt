package com.blackledger.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
            manager.updateAppWidget(ids, build(ctx, Ledger.get(ctx)))
        }

        private fun build(ctx: Context, data: Data): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val shop = data.widgetShop
            v.setTextColor(R.id.tab_todo, if (shop) DIM else WHITE)
            v.setTextColor(R.id.tab_shop, if (shop) WHITE else DIM)
            v.setViewVisibility(R.id.line_todo, if (shop) View.INVISIBLE else View.VISIBLE)
            v.setViewVisibility(R.id.line_shop, if (shop) View.VISIBLE else View.INVISIBLE)
            v.setOnClickPendingIntent(R.id.tab_todo, broadcast(ctx, LedgerReceiver.MODE, 0))
            v.setOnClickPendingIntent(R.id.tab_shop, broadcast(ctx, LedgerReceiver.MODE, 1))
            v.setOnClickPendingIntent(R.id.title, open(ctx, shop))

            // Every row's buttons share one template; each button fills in its own action and id.
            v.setPendingIntentTemplate(R.id.rows, PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, LedgerReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            v.setEmptyView(R.id.rows, R.id.empty)
            val rows = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(2)

            if (shop) {
                val left = data.items.filter { it.state == ItemState.TO_GET }
                v.setTextViewText(R.id.title, "Requisition")
                v.setTextViewText(R.id.count, "${left.size} TO GET")
                v.setTextViewText(R.id.empty, "Nothing to get.")
                for (item in left) {
                    val r = RemoteViews(ctx.packageName, R.layout.widget_row_item)
                    r.setTextViewText(R.id.name, item.name)
                    r.setOnClickFillInIntent(R.id.got, fill(LedgerReceiver.GOT, item.id))
                    r.setOnClickFillInIntent(R.id.na, fill(LedgerReceiver.NA, item.id))
                    rows.addItem(item.id.toLong(), r)
                }
            } else {
                val today = LocalDate.now()
                val all = data.today(today)
                val open = all.filter { it.status != Status.CLEARED }
                v.setTextViewText(R.id.title, "Directives")
                v.setTextViewText(R.id.count, "${all.size - open.size}/${all.size} CLEARED")
                v.setTextViewText(R.id.empty, if (all.isEmpty()) "Nothing on file today." else "All directives cleared.")
                for (row in open) {
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
                    r.setOnClickFillInIntent(R.id.check, fill(LedgerReceiver.CLEAR, row.d.id))
                    rows.addItem(row.d.id.toLong(), r)
                }
            }
            // ponytail: deprecated in API 35 but still the simplest scrolling widget list; move to Glance if it ever goes.
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(R.id.rows, rows.build())
            return v
        }

        private fun fill(action: String, id: Int) = Intent().setAction(action).putExtra("id", id)

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
