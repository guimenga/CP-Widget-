package pt.cpwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TrainWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TrainListFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
}

/**
 * Builds the scrollable list. [onDataSetChanged] runs on a background thread,
 * so the network fetch happens here when a refresh was requested.
 */
class TrainListFactory(private val context: Context, private val id: Int) : RemoteViewsService.RemoteViewsFactory {
    private val store = WidgetStore(context)
    private var items: List<Departure> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val code = store.stationCode(id)
        val cached = store.departures(id)
        if (code != null && (store.isRefreshing(id) || cached == null)) {
            try {
                val fresh = CpApi.upcomingDepartures(code)
                store.saveDepartures(id, fresh, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w("CpWidget", "Fetch failed for $code", e)
                store.saveError(id, e.message ?: e.javaClass.simpleName)
            } finally {
                store.clearRefresh(id)
                TrainWidgetProvider.renderHeader(context, AppWidgetManager.getInstance(context), id)
            }
        }
        // Drop trains that have already left since the last fetch.
        val cutoff = LocalDateTime.now(CpApi.LISBON).minusMinutes(1)
        items = store.departures(id).orEmpty().filter { it.effectiveTime() >= cutoff }
    }

    override fun getCount() = items.size
    override fun getViewTypeCount() = 1
    override fun hasStableIds() = false
    override fun getItemId(position: Int) = position.toLong()
    override fun getLoadingView(): RemoteViews? = null
    override fun onDestroy() {}

    override fun getViewAt(position: Int): RemoteViews {
        val d = items[position]
        val v = RemoteViews(context.packageName, R.layout.widget_row)
        val time = HHMM.format(d.scheduled)

        when {
            d.cancelled -> {
                v.setTextViewText(R.id.time, strike(time))
                v.setTextViewText(R.id.status, context.getString(R.string.cancelled))
                v.setTextColor(R.id.status, RED)
                v.setViewVisibility(R.id.status, View.VISIBLE)
            }
            d.isDelayed() -> {
                v.setTextViewText(R.id.time, strike(time))
                v.setTextViewText(
                    R.id.status,
                    context.getString(R.string.delayed, HHMM.format(d.effectiveTime()), d.delayMinutes),
                )
                v.setTextColor(R.id.status, ORANGE)
                v.setViewVisibility(R.id.status, View.VISIBLE)
            }
            else -> {
                v.setTextViewText(R.id.time, time)
                v.setTextViewText(R.id.status, "✓")
                v.setTextColor(R.id.status, GREEN)
                v.setViewVisibility(R.id.status, View.VISIBLE)
            }
        }
        v.setTextViewText(R.id.destination, d.destination)

        // Mark the first row of the next day so a late-night list isn't confusing.
        val newDay = position > 0 && items[position - 1].scheduled.toLocalDate() != d.scheduled.toLocalDate()
        v.setViewVisibility(R.id.day_label, if (newDay) View.VISIBLE else View.GONE)

        // The click target itself comes from the widget's (immutable) template intent.
        v.setOnClickFillInIntent(R.id.row, Intent())
        return v
    }

    private fun strike(s: String): CharSequence =
        SpannableString(s).apply { setSpan(StrikethroughSpan(), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }

    companion object {
        private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
        private val RED = Color.parseColor("#D32F2F")
        private val ORANGE = Color.parseColor("#EF6C00")
        private val GREEN = Color.parseColor("#2E7D32")
    }
}
