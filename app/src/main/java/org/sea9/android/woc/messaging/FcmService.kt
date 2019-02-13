package org.sea9.android.woc.messaging

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService

class FcmService: FirebaseMessagingService() {
	companion object {
		const val TAG = "woc.main"
	}

	/**
	 * Called if InstanceID token is updated. This may occur if the security of
	 * the previous token had been compromised. Note that this is called when the InstanceID token
	 * is initially generated so this is where you would retrieve the token.
	 */
	override fun onNewToken(token: String?) {
		Log.d(TAG, "Refreshed token: $token")
	}
}