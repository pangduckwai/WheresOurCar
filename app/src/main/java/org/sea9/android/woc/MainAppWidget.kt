package org.sea9.android.woc

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.os.AsyncTask
import android.os.IBinder
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
		const val KEY_FLR = "woc.floor"
		const val KEY_LOT = "woc.lot"
		private const val SFX_FLR = "/F"
		private const val PFX_LOT = "P"

		private fun populate(context: Context?, floor: String?, lot: String?) {
			Log.w(TAG, "Populating app widget $floor / $lot")
			context?.let {
				val view = RemoteViews(it.packageName, R.layout.app_widget)

				floor?.let {s ->
					view.setTextViewText(R.id.floor, if (s.matches("[0-9]+".toRegex())) s + SFX_FLR else s)
				}

				lot?.let {s ->
					view.setTextViewText(R.id.lot, if (s.toUpperCase().startsWith(PFX_LOT)) s else PFX_LOT + s)
				}

				view.setOnClickPendingIntent(
					R.id.start,
					PendingIntent.getActivity(it, 0, Intent(it, MainActivity::class.java), 0)
				)

				AppWidgetManager.getInstance(it).updateAppWidget(
					ComponentName(it, MainAppWidget::class.java),
					view
				)
			}
		}

		fun update(context: Context?, floor: String?, lot: String?) {
			context?.let {
				AsyncUpdateWidgetTask(it.applicationContext).execute(floor, lot)
			}
		}
	}

	override fun onReceive(context: Context?, intent: Intent?) {
		if (intent?.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
			populate(context, intent.getStringExtra(KEY_FLR), intent.getStringExtra(KEY_LOT))
		}
		super.onReceive(context, intent)
	}

	override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
		context?.startService(Intent(context, UpdateService::class.java))
	}

	override fun onEnabled(context: Context?) {
		context?.let {
			(it.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
				.setInexactRepeating(
					AlarmManager.RTC,
					System.currentTimeMillis(),
//					AlarmManager.INTERVAL_HALF_HOUR,
					60000, //TODO FOR Testing
					PendingIntent.getService(it, 0,
						Intent(it, UpdateService::class.java),
						PendingIntent.FLAG_CANCEL_CURRENT
					)
				)
		}
		super.onEnabled(context)
	}

	override fun onDisabled(context: Context?) {
		context?.let {
			(it.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
				.cancel(
					PendingIntent.getService(it, 0,
						Intent(it, UpdateService::class.java),
						PendingIntent.FLAG_CANCEL_CURRENT
					)
				)
		}
		super.onDisabled(context)
	}

	class UpdateService: Service() {
		override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
			val helper = DbHelper(object : DbHelper.Caller {
				override fun getContext(): Context? {
					return this@UpdateService
				}

				override fun onReady() {
					Log.w(TAG, "DB connection ready for service")
				}
			})
			helper.writableDatabase.execSQL(DbContract.SQL_CONFIG)

			val record = DbContract.Vehicle.select(helper)
			record?.let {
				populate(this, record.floor, record.lot)
			}

			helper.close()
			return START_NOT_STICKY
		}

		override fun onBind(intent: Intent?): IBinder? {
			return null
		}
	}

	@SuppressLint("StaticFieldLeak")
	private class AsyncUpdateWidgetTask(private val context: Context): AsyncTask<String, Void, Void>() {
		override fun doInBackground(vararg params: String?): Void? {
			if (params.size == 2) {
				val intent = Intent(context, MainAppWidget::class.java)
				intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
				intent.putExtra(MainAppWidget.KEY_FLR, params[0])
				intent.putExtra(MainAppWidget.KEY_LOT, params[1])
				context.sendBroadcast(intent)
			}
			return null
		}
	}
}