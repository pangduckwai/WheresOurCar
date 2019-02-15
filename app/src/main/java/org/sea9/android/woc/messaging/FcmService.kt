package org.sea9.android.woc.messaging

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper
import org.sea9.android.woc.data.VehicleRecord

class FcmService: FirebaseMessagingService() {
	companion object {
		const val TAG = "woc.fcm"
	}

	/**
	 * Called if InstanceID token is updated. This may occur if the security of
	 * the previous token had been compromised. Note that this is called when the InstanceID token
	 * is initially generated so this is where you would retrieve the token.
	 */
	override fun onNewToken(token: String?) {
		Log.w(TAG, "Refreshed token: $token")
	}

	override fun onMessageReceived(remoteMessage: RemoteMessage?) {
		Log.w(TAG, "From: ${remoteMessage?.from}")

		remoteMessage?.data?.isNotEmpty()?.let {
			Log.w(TAG, "Message data payload: ${remoteMessage.data}")
			val veh = remoteMessage.data[VehicleRecord.NAM]
			val prk = remoteMessage.data[VehicleRecord.PRK]
			if ((veh != null) && (prk != null) &&
				remoteMessage.data.containsKey(VehicleRecord.PRK) &&
				remoteMessage.data.containsKey(VehicleRecord.FLR) &&
				remoteMessage.data.containsKey(VehicleRecord.LOT)) {
				val record = VehicleRecord(-1, veh, prk,
					remoteMessage.data[VehicleRecord.FLR],
					remoteMessage.data[VehicleRecord.LOT],
					true, null)

				val helper = DbHelper(object : DbHelper.Caller {
					override fun getContext(): Context? {
						return this@FcmService
					}
					override fun onReady() {
						Log.d(TAG, "DB connection ready for app widget")
					}
				})

				val list = DbContract.Vehicle.select(helper!!, veh)
			}
		}

		remoteMessage?.notification?.let {
			Log.w(TAG, "Message Notification Body: ${it.body}")
		}
	}
}