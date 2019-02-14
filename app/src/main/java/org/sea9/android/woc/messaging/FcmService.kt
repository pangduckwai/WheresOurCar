package org.sea9.android.woc.messaging

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

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
			Log.w(TAG, "Message data payload: ${remoteMessage.data} - $it")
		}

		remoteMessage?.notification?.let {
			Log.w(TAG, "Message Notification Body: ${it.body}")
		}
	}
}