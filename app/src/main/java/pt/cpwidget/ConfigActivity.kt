package pt.cpwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Favourite stations screen. Shown when a widget is placed, and from
 * "Gerir estações" in the widget's station picker.
 * Tap a station to show it in this widget; add by pasting a cp.pt link.
 */
class ConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var favorites: Favorites
    private lateinit var list: LinearLayout
    private lateinit var count: TextView
    private lateinit var input: EditText
    private lateinit var message: TextView
    private lateinit var add: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the user backs out of first-time setup, the widget is not added.
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }
        setContentView(R.layout.activity_config)
        favorites = Favorites(this)

        list = findViewById(R.id.stations)
        count = findViewById(R.id.count)
        input = findViewById(R.id.input)
        message = findViewById(R.id.message)
        add = findViewById(R.id.add)

        clipboardLink()?.takeIf { link ->
            favorites.list().none { it.code == parseStationCode(link) }
        }?.let { input.setText(it) }

        findViewById<Button>(R.id.open_site).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.cp.pt/pt/pesquisa-estacao")))
        }
        add.setOnClickListener { addStation() }
        showList()
    }

    private fun showList() {
        val stations = favorites.list()
        val current = WidgetStore(this).stationCode(widgetId)
        list.removeAllViews()
        count.text = getString(R.string.favorites_count, stations.size, Favorites.MAX)
        findViewById<TextView>(R.id.empty_list).visibility =
            if (stations.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        stations.forEachIndexed { i, station ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val name = TextView(this).apply {
                text = (if (station.code == current) "●  " else "") + station.name
                textSize = 17f
                setPadding(0, dp(12), 0, dp(12))
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { choose(station) }
            }
            row.addView(name, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (i > 0) row.addView(smallButton("↑", getString(R.string.move_up)) {
                favorites.move(station.code, -1); showList()
            })
            row.addView(smallButton("✕", getString(R.string.remove)) {
                favorites.remove(station.code); showList()
            })
            list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        val full = stations.size >= Favorites.MAX
        add.isEnabled = !full
        input.isEnabled = !full
        if (full) message.setText(R.string.list_full)
    }

    private fun smallButton(label: String, description: String, onClick: () -> Unit) =
        Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = label
            contentDescription = description
            minWidth = dp(44)
            minimumWidth = dp(44)
            setOnClickListener { onClick() }
        }

    private fun choose(station: Station) {
        TrainWidgetProvider.switchStation(this, widgetId, station)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun addStation() {
        val code = parseStationCode(input.text.toString())
        if (code == null) {
            message.setText(R.string.invalid_link)
            return
        }
        add.isEnabled = false
        message.setText(R.string.checking)
        thread {
            val result = runCatching { CpApi.stationName(code) }
            runOnUiThread {
                add.isEnabled = true
                result.fold(
                    onSuccess = { name ->
                        if (name == null) {
                            message.setText(R.string.unknown_station)
                        } else if (!favorites.add(Station(code, name))) {
                            message.setText(R.string.list_full)
                        } else {
                            input.text.clear()
                            message.text = getString(R.string.added, name)
                            showList()
                        }
                    },
                    onFailure = { message.setText(R.string.network_error) },
                )
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Pre-fill from the clipboard if it already holds a cp.pt station link. */
    private fun clipboardLink(): String? {
        val cm = getSystemService(ClipboardManager::class.java) ?: return null
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        return text?.takeIf { it.contains("cp.pt") && parseStationCode(it) != null }
    }

    companion object {
        const val EXTRA_MANAGE = "manage"
    }
}
