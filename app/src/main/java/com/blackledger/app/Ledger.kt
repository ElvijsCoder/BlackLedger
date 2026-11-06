package com.blackledger.app

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * The one copy of the ledger, shared by the app, the widget and the alarms (all one process).
 * Lives in a single JSON file in private storage; nothing leaves the phone.
 */
object Ledger {
    val state = mutableStateOf(Data())
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, "ledger.json")

    /** Current data, with any days that passed since last time settled into the log. */
    fun get(ctx: Context): Data = synchronized(this) {
        if (!loaded) {
            state.value = read(file(ctx))
            loaded = true
        }
        val settled = state.value.settle(LocalDate.now())
        if (settled != state.value) {
            write(file(ctx), settled)
            state.value = settled
        }
        settled
    }

    /** Changes the data, saves it, then redraws the widget and re-arms the alarms. */
    fun update(ctx: Context, change: (Data) -> Data) {
        synchronized(this) {
            val next = change(get(ctx))
            write(file(ctx), next)
            state.value = next
        }
        LedgerWidget.refresh(ctx)
        Alarms.schedule(ctx)
    }

    private fun read(f: File): Data {
        if (!f.exists()) return Data()
        return runCatching { fromJson(JSONObject(f.readText())) }.getOrElse {
            // Keep the unreadable file instead of overwriting it with an empty ledger.
            f.copyTo(File(f.parentFile, "ledger.broken.json"), overwrite = true)
            Data()
        }
    }

    /** Write-then-rename, so a crash mid-save never leaves half a file. */
    private fun write(f: File, d: Data) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(toJson(d).toString())
        tmp.renameTo(f)
    }

    private fun toJson(d: Data) = JSONObject().apply {
        put("version", 1)
        put("trip", d.trip)
        put("nextId", d.nextId)
        put("settledThrough", d.settledThrough?.toString())
        put("widgetShop", d.widgetShop)
        put("directives", JSONArray(d.directives.map { x ->
            JSONObject().apply {
                put("id", x.id)
                put("title", x.title)
                put("repeat", x.repeat.name)
                put("days", JSONArray(x.days.sorted()))
                put("dayOfMonth", x.dayOfMonth)
                put("date", x.date?.toString())
                put("alert", x.alert?.toString())
                put("doneOn", x.doneOn?.toString())
                put("doneAt", x.doneAt?.toString())
                put("since", x.since.toString())
            }
        }))
        // ponytail: the log grows by a few lines a day forever; trim to a year if the file ever gets slow.
        put("log", JSONArray(d.log.map { e ->
            JSONObject().apply {
                put("id", e.id)
                put("date", e.date.toString())
                put("cleared", e.cleared)
                put("at", e.at?.toString())
            }
        }))
        put("trips", JSONArray(d.trips.map { t ->
            JSONObject().apply {
                put("n", t.n)
                put("date", t.date.toString())
                put("got", JSONArray(t.got))
                put("na", JSONArray(t.na))
            }
        }))
        put("items", JSONArray(d.items.map { i ->
            JSONObject().apply {
                put("id", i.id)
                put("name", i.name)
                put("state", i.state.name)
            }
        }))
    }

    private fun fromJson(o: JSONObject): Data {
        fun JSONObject.date(k: String) = optString(k).takeIf { it.isNotEmpty() && !isNull(k) }?.let(LocalDate::parse)
        fun JSONObject.time(k: String) = optString(k).takeIf { it.isNotEmpty() && !isNull(k) }?.let(LocalTime::parse)
        fun <T> JSONArray?.list(f: (JSONObject) -> T) = if (this == null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }

        return Data(
            trip = o.optInt("trip", 1),
            nextId = o.optInt("nextId", 1),
            settledThrough = o.date("settledThrough"),
            widgetShop = o.optBoolean("widgetShop"),
            directives = o.optJSONArray("directives").list { x ->
                val days = x.optJSONArray("days")
                Directive(
                    id = x.getInt("id"),
                    title = x.getString("title"),
                    repeat = Repeat.valueOf(x.getString("repeat")),
                    days = if (days == null) emptySet() else (0 until days.length()).map { days.getInt(it) }.toSet(),
                    dayOfMonth = x.optInt("dayOfMonth", 1),
                    date = x.date("date"),
                    alert = x.time("alert"),
                    doneOn = x.date("doneOn"),
                    doneAt = x.time("doneAt"),
                    since = x.date("since") ?: LocalDate.now(),
                )
            },
            log = o.optJSONArray("log").list { e ->
                LogEntry(e.getInt("id"), LocalDate.parse(e.getString("date")), e.getBoolean("cleared"), e.time("at"))
            },
            trips = o.optJSONArray("trips").list { t ->
                fun names(k: String) = t.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                Trip(t.getInt("n"), LocalDate.parse(t.getString("date")), names("got"), names("na"))
            },
            items = o.optJSONArray("items").list { i ->
                Item(i.getInt("id"), i.getString("name"), ItemState.valueOf(i.getString("state")))
            },
        )
    }
}
