package org.sea9.android.woc.messaging

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import org.sea9.android.crypto.KryptoUtils
import org.sea9.android.woc.MainActivity
import org.sea9.android.woc.MainWidget
import org.sea9.android.woc.MainContext
import org.sea9.android.woc.settings.SettingsManager
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper
import org.sea9.android.woc.data.VehicleRecord
import java.lang.RuntimeException

class MessagingService: FirebaseMessagingService() {
	companion object {
		const val TAG = "woc.fcm"
		private const val EMPTY = ""

		@Suppress("DEPRECATION")
		@SuppressLint("PackageManagerGetSignatures")
		fun getKey(context: Context?): CharArray {
			var buffer = CharArray(0)
			context?.let {
				val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
					it.packageManager.getPackageInfo(it.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.apkContentsSigners
				} else {
					it.packageManager.getPackageInfo(it.packageName, PackageManager.GET_SIGNATURES).signatures
				}
				signatures.forEach {s ->
					buffer += s.toChars()
				}
			}
			return buffer
		}
	}

	/**
	 * Called if InstanceID token is updated. This may occur if the security of
	 * the previous token had been compromised. Note that this is called when the InstanceID token
	 * is initially generated so this is where you would retrieve the token.
	 */
	override fun onNewToken(token: String?) {
		val settingsManager = SettingsManager(this)

		// Save the refreshed token
		if (token != null) settingsManager.updateToken(token)

		// Need to update main activity only if the app is active
		Intent(this, MainActivity.MessagingReceiver::class.java).also {
			it.putExtra(SettingsManager.KEY_TOKEN, token)
			sendBroadcast(it)
		}
	}

	override fun onMessageReceived(remoteMessage: RemoteMessage?) {
		val settingsManager = SettingsManager(this)

		when(settingsManager.operationMode) {
			SettingsManager.MODE.SUBSCRIBER -> {
				remoteMessage?.notification?.let {
					Log.w(TAG, "Invalid message with notification body received: ${it.body}")
					return
				}

				if (remoteMessage?.data?.isNotEmpty() == true) {
					val slt = remoteMessage.data[PublishingUtils.JSON_SALT]
					val srt = remoteMessage.data[PublishingUtils.JSON_SECRET]
					Log.d(TAG, "Received $srt")

					if ((slt == null) || (srt == null)) return
					val salt = KryptoUtils.decode(KryptoUtils.convert(slt.toCharArray())!!)
					val secret = KryptoUtils.decrypt(srt.toCharArray(), getKey(this), salt)

					val json = JSONObject(secret?.joinToString(EMPTY))
					val veh = json.optString(VehicleRecord.NAM)
					val prk = json.optString(VehicleRecord.PRK)
					val flr = json.optString(VehicleRecord.FLR)
					val lot = json.optString(VehicleRecord.LOT)
					val mod = json.optString(VehicleRecord.MOD)

					val helper = DbHelper(object : DbHelper.Caller {
						override fun getContext(): Context? {
							return this@MessagingService
						}
						override fun onReady() {
							Log.d(TAG, "DB connection ready for app widget")
						}
					})

					if ((veh != null) && (prk != null) && (flr != null) && (lot != null)) {
						// The FCM message must contain all 4 of the above fields, even in the cases when the
						// publisher didn't specify the Floor or Lot. These 2 fields will be sent as empty string
						// in those cases. Therefore check for null for all the 4 fields.
						if ((settingsManager.subscriptionStatus == 1) && settingsManager.publisherId.isNullOrEmpty()) {
							Log.d(TAG, "Pending approval: ${remoteMessage.from}")
							settingsManager.receiveApproval(remoteMessage.from)
							DbContract.Vehicle.insertTemp(
								helper,
								VehicleRecord(-1, veh, prk, flr, lot, false, mod?.toLongOrNull())
							)
						} else if ((settingsManager.subscriptionStatus == 2) && (settingsManager.publisherId == remoteMessage.from)) {
							Log.d(TAG, "Subscribed: ${settingsManager.publisherId} / ${remoteMessage.from}")
							val list = DbContract.Vehicle.select(helper, veh)
							val status: Int
							val record = when (list.size) {
								0 -> {
									status = MainContext.STATUS_ADDED or MainContext.STATUS_UPDATED
									VehicleRecord(-1, veh, prk, flr, lot, true, mod?.toLongOrNull())
								}
								1 -> {
									status = MainContext.STATUS_UPDATED
									VehicleRecord(list[0].rid, veh, prk, flr, lot, true, mod?.toLongOrNull())
								}
								else -> throw RuntimeException("Vehicle table corrupted") // should not happen because of unique index
							}

							// Keep the modified timestamp here because should use the modified timestamp from the publisher
							onUpdate(MainContext.saveVehicle(status, record, helper, false))
						} else {
							Log.d(TAG, "Ignoring messages: ${settingsManager.publisherId} / ${remoteMessage.from}")
						}
					} else if ((veh == null) && (prk == null) && (flr == null) && (lot == null)) {
						// Otherwise if none of the 4 field present means the publisher removed this subscriber from his/her subscriber list,
						// therefore reset the subscriber status
						if (settingsManager.publisherId == remoteMessage.from) {
							settingsManager.receiveCancellation()
						}
					} else {
						Log.w(TAG, "Invalid message format: ${remoteMessage.data}")
					}
				} else {
					Log.w(TAG, "Invalid message format: no data section")
				}
			}
			else -> {
				Log.d(TAG, "Ignoring messages from ${remoteMessage?.from}")
			}
		}
	}

	private fun onUpdate(result: Int = -1) {
		// Update app widget
		MainWidget.update(this)

		// Update main activity if the app is active
		Intent(this, MainActivity.MessagingReceiver::class.java).also {
			it.putExtra(SettingsManager.KEY_PUB, result)
			sendBroadcast(it)
		}
	}
}