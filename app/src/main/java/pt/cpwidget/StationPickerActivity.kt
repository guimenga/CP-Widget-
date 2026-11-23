package pt.cpwidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle

/**
 * Small pop-up over the home screen, opened by tapping the station name in the widget.
 * Lists the favourite stations plus a "Gerir estações" entry.
 */
class StationPickerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        val stations = Favorites(this).list()
        val current = WidgetStore(this).stationCode(id)
        val labels = stations.map { (if (it.code == current) "●  " else "     ") + it.name } +
            getString(R.string.manage_stations)

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_station)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < stations.size) {
                    TrainWidgetProvider.switchStation(this, id, stations[which])
                } else {
                    startActivity(
                        Intent(this, ConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .putExtra(ConfigActivity.EXTRA_MANAGE, true),
                    )
                }
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
