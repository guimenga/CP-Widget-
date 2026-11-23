package pt.cpwidget

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** One departure as shown in the widget. */
data class Departure(
    val scheduled: LocalDateTime,
    val estimated: String?,       // ETD "HH:mm", only meaningful when delayed
    val delayMinutes: Int?,
    val cancelled: Boolean,
    val destination: String,
)

/**
 * Client for the (unofficial) API used by www.cp.pt.
 *
 * The keys are not hard-coded: cp.pt publishes them to every browser in
 * /fe-config.json, so we read that file each time and keep working if CP rotates them.
 */
object CpApi {
    val LISBON: ZoneId = ZoneId.of("Europe/Lisbon")
    private const val CONFIG_URL = "https://www.cp.pt/fe-config.json"
    private const val TIMEOUT_MS = 20_000

    private class Config(val travelApiUrl: String, val apiKey: String, val connectId: String, val connectSecret: String)

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun loadConfig(): Config {
        val j = JSONObject(get(CONFIG_URL))
        return Config(
            j.getString("travelApiUrl").trimEnd('/'),
            j.getString("travelApiKey"),
            j.getString("xcck"),
            j.getString("xccs"),
        )
    }

    private fun apiGet(cfg: Config, path: String): String = get(
        cfg.travelApiUrl + path,
        mapOf(
            "X-Api-Key" to cfg.apiKey,
            "x-cp-connect-id" to cfg.connectId,
            "x-cp-connect-secret" to cfg.connectSecret,
        ),
    )

    /** Station name for a code like "94-60087", or null if CP doesn't know it. */
    fun stationName(code: String): String? {
        val arr = JSONArray(apiGet(loadConfig(), "/stations"))
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            if (s.optString("code") == code) return s.optString("designation")
        }
        return null
    }

    /**
     * Departures from [station] from about now onwards. Pulls tomorrow's timetable
     * too when few trains remain today, so the list doesn't run dry late at night.
     */
    fun upcomingDepartures(station: String): List<Departure> {
        val cfg = loadConfig()
        val now = LocalDateTime.now(LISBON)
        val cutoff = now.minusMinutes(1)
        val today = now.toLocalDate()
        var result = fetchDay(cfg, station, today).filter { it.effectiveTime() >= cutoff }
        if (result.size < 30) {
            result = result + fetchDay(cfg, station, today.plusDays(1))
        }
        return result
    }

    private fun fetchDay(cfg: Config, station: String, date: LocalDate): List<Departure> =
        parseDay(apiGet(cfg, "/stations/$station/timetable/$date?view=DEPARTURES"), station, date)

    /** Parses one day's timetable JSON into departures (trains ending at [station] are dropped). */
    fun parseDay(body: String, station: String, date: LocalDate): List<Departure> {
        val stops = JSONObject(body).optJSONArray("stationStops") ?: return emptyList()
        val out = ArrayList<Departure>(stops.length())
        for (i in 0 until stops.length()) {
            val s = stops.getJSONObject(i)
            // Departures only: skip trains that end here.
            val dest = s.optJSONObject("trainDestination")
            if (dest?.optString("code") == station) continue
            val dep = s.strOrNull("departureTime") ?: continue
            val time = runCatching { LocalTime.parse(dep) }.getOrNull() ?: continue
            out += Departure(
                scheduled = LocalDateTime.of(date, time),
                estimated = s.strOrNull("ETD"),
                delayMinutes = if (s.isNull("delay")) null else s.optInt("delay"),
                cancelled = !s.isNull("supression"),
                destination = dest?.optString("designation").orEmpty(),
            )
        }
        out.sortBy { it.scheduled }
        return out
    }

    private fun JSONObject.strOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

/** Same rule as cp.pt: a train counts as delayed only when delay > 0 and it isn't cancelled. */
fun Departure.isDelayed(): Boolean = !cancelled && (delayMinutes ?: 0) > 0

/** Estimated departure when delayed, else scheduled (handles ETD rolling past midnight). */
fun Departure.effectiveTime(): LocalDateTime {
    if (!isDelayed()) return scheduled
    val etd = estimated?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        ?: return scheduled.plusMinutes(delayMinutes!!.toLong())
    var t = LocalDateTime.of(scheduled.toLocalDate(), etd)
    if (t.isBefore(scheduled.minusHours(12))) t = t.plusDays(1)
    return t
}

/** Accepts a cp.pt station URL or a bare code; returns e.g. "94-60087". */
fun parseStationCode(input: String): String? =
    Regex("""\b(\d{2}-\d{4,6})\b""").find(input.trim())?.groupValues?.get(1)
