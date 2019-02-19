package org.sea9.android.woc.messaging

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.sea9.android.woc.MainActivity
import org.sea9.android.woc.MainAppWidget
import org.sea9.android.woc.MainContext
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper
import org.sea9.android.woc.data.VehicleRecord
import java.lang.RuntimeException

class MessagingService: FirebaseMessagingService() {
	companion object {
		const val TAG = "woc.fcm"
	}

	/**
	 * Called if InstanceID token is updated. This may occur if the security of
	 * the previous token had been compromised. Note that this is called when the InstanceID token
	 * is initially generated so this is where you would retrieve the token.
	 */
	override fun onNewToken(token: String?) {
		// Save the refreshed token
		getSharedPreferences(MainActivity.TAG, Context.MODE_PRIVATE)?.let {
			with(it.edit()) {
				putString(MainActivity.KEY_TOKEN, token)
				apply()
			}
		}

		// Need to update main activity only if the app is active
		Intent(this, MainActivity.MessagingReceiver::class.java).also {
			it.putExtra(MainActivity.KEY_TOKEN, token)
			sendBroadcast(it)
		}
	}

	override fun onMessageReceived(remoteMessage: RemoteMessage?) {
		Log.d(TAG, "From: ${remoteMessage?.from}")
		remoteMessage?.data?.isNotEmpty()?.let {
			val veh = remoteMessage.data[VehicleRecord.NAM]
			val prk = remoteMessage.data[VehicleRecord.PRK]
			val flr = remoteMessage.data[VehicleRecord.FLR]
			val lot = remoteMessage.data[VehicleRecord.LOT]

			// Expecting the FCM must contain all 4 of the above fields, even in the cases when the
			// publisher didn't specify the Floor or Lot. These 2 fields will be sent as empty string
			// in those cases. Therefore check for null for all the 4 fields.
			if ((veh != null) && (prk != null) && (flr != null) && (lot != null)) {
				val helper = DbHelper(object : DbHelper.Caller {
					override fun getContext(): Context? {
						return this@MessagingService
					}
					override fun onReady() {
						Log.d(TAG, "DB connection ready for app widget")
					}
				})

				val list = DbContract.Vehicle.select(helper, veh)
				val status: Int
				val record = when(list.size) {
					0 -> {
						status = MainContext.STATUS_ADDED or MainContext.STATUS_UPDATED
						VehicleRecord(-1, veh, prk, flr, lot, true, null)
					}
					1 -> {
						status = MainContext.STATUS_UPDATED
						VehicleRecord(list[0].rid, veh, prk, flr, lot, true, null)
					}
					else -> throw RuntimeException("Vehicle table corrupted") // should not happen because of unique index
				}
				onUpdate(MainContext.saveVehicle(status, record, helper))
			} else {
				Log.w(TAG, "Invalid message format: ${remoteMessage.data}")
			}
		}

		remoteMessage?.notification?.let {
			Log.w(TAG, "Message Notification Body: ${it.body}")
		}
	}

	private fun onUpdate(result: Int = -1) {
		// Update app widget
		MainAppWidget.update(this)

		// Update main activity if the app is active
		Intent(this, MainActivity.MessagingReceiver::class.java).also {
			it.putExtra(MainActivity.KEY_PUB, result)
			sendBroadcast(it)
		}
	}
}