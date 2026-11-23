package pt.cpwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * The home-screen widget. Data is only fetched when the user taps refresh
 * (or right after the widget is set up) - there are no background updates.
 */
class TrainWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) refresh(context, id)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val store = WidgetStore(context)
        ids.forEach { store.delete(it) }
    }

    companion object {
        const val ACTION_REFRESH = "pt.cpwidget.REFRESH"
        private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

        fun stationUrl(code: String) = "https://www.cp.pt/pt/pesquisa-estacao-detalhe/$code"

        /** Shows [station] in widget [id] and fetches its times. */
        fun switchStation(context: Context, id: Int, station: Station) {
            WidgetStore(context).setStation(id, station.code, station.name)
            val manager = AppWidgetManager.getInstance(context)
            render(context, manager, id)
            manager.notifyAppWidgetViewDataChanged(id, R.id.list)
        }

        /** Ask the list to re-fetch; the network call happens in [TrainListFactory]. */
        fun refresh(context: Context, id: Int) {
            WidgetStore(context).requestRefresh(id)
            val manager = AppWidgetManager.getInstance(context)
            renderHeader(context, manager, id)
            manager.notifyAppWidgetViewDataChanged(id, R.id.list)
        }

        fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget)

            val serviceIntent = Intent(context, TrainWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                // Unique data URI so each widget gets its own list factory.
                data = Uri.parse("cpwidget://widget/$id")
            }
            views.setRemoteAdapter(R.id.list, serviceIntent)
            views.setEmptyView(R.id.list, R.id.empty)

            // Tapping a train opens the station page on cp.pt.
            WidgetStore(context).stationCode(id)?.let { code ->
                val open = Intent(Intent.ACTION_VIEW, Uri.parse(stationUrl(code)))
                views.setPendingIntentTemplate(
                    R.id.list,
                    PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
            }

            fillHeader(context, views, id)
            manager.updateAppWidget(id, views)
        }

        fun renderHeader(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget)
            fillHeader(context, views, id)
            manager.partiallyUpdateAppWidget(id, views)
        }

        private fun fillHeader(context: Context, views: RemoteViews, id: Int) {
            val store = WidgetStore(context)
            views.setTextViewText(R.id.station, store.stationName(id) ?: context.getString(R.string.no_station))

            val updated = store.lastUpdated(id)
            val status = when {
                store.isRefreshing(id) -> context.getString(R.string.refreshing)
                store.lastError(id) != null -> context.getString(R.string.error_short)
                updated > 0 -> context.getString(
                    R.string.updated_at,
                    HHMM.format(Instant.ofEpochMilli(updated).atZone(CpApi.LISBON)),
                )
                else -> ""
            }
            views.setTextViewText(R.id.updated, status)
            views.setTextViewText(
                R.id.empty,
                context.getString(if (store.lastError(id) != null) R.string.error_long else R.string.empty),
            )

            val refreshIntent = Intent(context, TrainWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            val pi = PendingIntent.getBroadcast(
                context, id, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.refresh, pi)

            // Tapping the station name opens the station picker (or setup if none chosen yet).
            val target = if (store.stationCode(id) == null) ConfigActivity::class.java else StationPickerActivity::class.java
            val pickIntent = Intent(context, target).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse("cpwidget://pick/$id")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            views.setOnClickPendingIntent(
                R.id.station_button,
                PendingIntent.getActivity(context, id, pickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            views.setViewVisibility(R.id.progress, if (store.isRefreshing(id)) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.refresh, if (store.isRefreshing(id)) View.GONE else View.VISIBLE)
        }
    }
}
