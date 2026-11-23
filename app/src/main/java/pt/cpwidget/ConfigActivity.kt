package pt.cpwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Shown when the widget is placed (and when its station name is tapped).
 * The user pastes a cp.pt station link; we check it with CP and save it.
 */
class ConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the user backs out of first-time setup, the widget is not added.
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }
        setContentView(R.layout.activity_config)

        val input = findViewById<EditText>(R.id.input)
        val message = findViewById<TextView>(R.id.message)
        val save = findViewById<Button>(R.id.save)

        val store = WidgetStore(this)
        store.stationCode(widgetId)?.let { input.setText(TrainWidgetProvider.stationUrl(it)) }
            ?: clipboardLink()?.let { input.setText(it) }

        findViewById<Button>(R.id.open_site).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.cp.pt/pt/pesquisa-estacao")))
        }

        save.setOnClickListener {
            val code = parseStationCode(input.text.toString())
            if (code == null) {
                message.setText(R.string.invalid_link)
                return@setOnClickListener
            }
            save.isEnabled = false
            message.setText(R.string.checking)
            thread {
                val result = runCatching { CpApi.stationName(code) }
                runOnUiThread {
                    save.isEnabled = true
                    result.fold(
                        onSuccess = { name ->
                            if (name == null) message.setText(R.string.unknown_station)
                            else finishWith(code, name)
                        },
                        onFailure = { message.setText(R.string.network_error) },
                    )
                }
            }
        }
        message.visibility = View.VISIBLE
    }

    private fun finishWith(code: String, name: String) {
        WidgetStore(this).setStation(widgetId, code, name)
        val manager = AppWidgetManager.getInstance(this)
        TrainWidgetProvider.render(this, manager, widgetId)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.list)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    /** Pre-fill from the clipboard if it already holds a cp.pt station link. */
    private fun clipboardLink(): String? {
        val cm = getSystemService(ClipboardManager::class.java) ?: return null
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        return text?.takeIf { it.contains("cp.pt") && parseStationCode(it) != null }
    }

    companion object {
        const val EXTRA_RECONFIGURE = "reconfigure"
    }
}
