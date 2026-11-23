package pt.cpwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Per-widget settings and the last fetched timetable, kept in SharedPreferences. */
class WidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun stationCode(id: Int): String? = prefs.getString("$id.code", null)
    fun stationName(id: Int): String? = prefs.getString("$id.name", null)
    fun lastUpdated(id: Int): Long = prefs.getLong("$id.updated", 0)
    fun lastError(id: Int): String? = prefs.getString("$id.error", null)

    fun setStation(id: Int, code: String, name: String) {
        prefs.edit()
            .putString("$id.code", code)
            .putString("$id.name", name)
            .putBoolean("$id.refresh", true)
            .apply()
    }

    /** Marks that the next list rebuild should fetch fresh data. */
    fun requestRefresh(id: Int) = prefs.edit().putBoolean("$id.refresh", true).commit()

    fun clearRefresh(id: Int) = prefs.edit().putBoolean("$id.refresh", false).commit()

    fun isRefreshing(id: Int): Boolean = prefs.getBoolean("$id.refresh", false)

    fun saveDepartures(id: Int, list: List<Departure>, at: Long) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("s", it.scheduled.toString())
                put("e", it.estimated ?: JSONObject.NULL)
                put("d", it.delayMinutes ?: JSONObject.NULL)
                put("c", it.cancelled)
                put("t", it.destination)
            })
        }
        prefs.edit()
            .putString("$id.data", arr.toString())
            .putLong("$id.updated", at)
            .remove("$id.error")
            .commit()
    }

    fun saveError(id: Int, message: String) = prefs.edit().putString("$id.error", message).commit()

    fun departures(id: Int): List<Departure>? {
        val raw = prefs.getString("$id.data", null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Departure(
                    scheduled = LocalDateTime.parse(o.getString("s")),
                    estimated = if (o.isNull("e")) null else o.getString("e"),
                    delayMinutes = if (o.isNull("d")) null else o.getInt("d"),
                    cancelled = o.getBoolean("c"),
                    destination = o.getString("t"),
                )
            }
        }.getOrNull()
    }

    fun delete(id: Int) {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$id.") }.forEach { e.remove(it) }
        e.apply()
    }
}
