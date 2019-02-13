package org.sea9.android.woc

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.app.PendingIntent
import android.content.ComponentName
import android.util.Log
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper

/**
 * Someone online suggested to pass the ID of the appwidget-provider resource (R.xml.appwidget_wmc in /xml/appwidget_wmc.xml for WMC) to
 * the AppWidgetProvider (which is this class, AppWidgetWmc) for the use of the onUpdate() callback. While passing the ID using the
 * following does reulst in onUpdate() using the values in the array:
 * <code>
 * int[] ids = { R.xml.appwidget_wmc };
 * intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
 * </code>
 * This value is not what onUpdate() actually expected. What it needs is the actual ID of the app widget given by the system during the
 * widget instance is added to the home screen. Thus the above code is removed.
 * See org.sea9.android.woc.MainAppWidget#populate(Context, String, String) for how to obtain this ID.
 */
class MainAppWidget: AppWidgetProvider() {
	companion object {
		const val TAG = "woc.widget"
		private const val SFX_FLR = "/F"
		private const val PFX_LOT = "P"

		fun update(context: Context?) {
			context?.let {
				Log.d(TAG, "Updating app widget with explicit request...")
				val intent = Intent(context, MainAppWidget::class.java)
				intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
				context.sendBroadcast(intent)
			}
		}
	}

	override fun onReceive(context: Context?, intent: Intent?) {
		if (intent?.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
			Log.d(TAG, "AppWidget onReceive()...")
			updateWidget(context)
		}
		super.onReceive(context, intent)
	}

	override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
		context?.let {
			Log.d(TAG, "AppWidget onUpdate()...")
			appWidgetIds?.forEach {_ ->
				updateWidget(context)
			}
		}
	}

	private fun updateWidget(context: Context?) {
		context?.let {
			val manager = AppWidgetManager.getInstance(it)
			val component = ComponentName(it, this::class.java)
			if (manager.getAppWidgetIds(component).isEmpty()) {
				Log.i(TAG, "No widget are currently in use")
				return
			}

			val helper = DbHelper(object : DbHelper.Caller {
				override fun getContext(): Context? {
					return context
				}
				override fun onReady() {
					Log.d(TAG, "DB connection ready for app widget")
				}
			})

			val views = RemoteViews(it.packageName, R.layout.app_widget)

			views.setOnClickPendingIntent(
				R.id.start,
				PendingIntent.getActivity(it, 0, Intent(it, MainActivity::class.java), 0)
			)

			val rec = DbContract.Vehicle.select(helper)
			if (rec != null) {
				Log.d(TAG, "Updating widget: ${rec.floor} / ${rec.lot}")
				rec.floor?.let {s ->
					views.setTextViewText(R.id.floor, if (s.matches("[0-9]+".toRegex())) s + SFX_FLR else s)
				}
				rec.lot?. let {s ->
					if (s.isNotBlank())
						views.setTextViewText(R.id.lot, if (s.toUpperCase().startsWith(PFX_LOT)) s else PFX_LOT + s)
				}
			} else {
				Log.i(TAG, "No current record found")
			}
			helper.close()
			manager.updateAppWidget(component, views)
		}
	}
}