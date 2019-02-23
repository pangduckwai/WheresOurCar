package org.sea9.android.woc

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class SettingsManager(private val context: Context?) {
	companion object {
		private const val TAG = "woc.settings"
		const val KEY_MODE = "woc.mode"
		const val KEY_TOKEN = "woc.token"
		const val KEY_PUB = "woc.publisher"
		const val KEY_SUB = "woc.subscriber"
		const val KEY_PID = "woc.publisher.id"
		const val KEY_STATUS = "woc.publisher.statue"

		private fun covertMode(mode: Int): MODE {
			return when (mode) {
				1 -> MODE.SUBSCRIBER
				2 -> MODE.PUBLISHER
				else -> MODE.STANDALONE
			}
		}
		private fun convertMode(mode: MODE): Int {
			return when (mode) {
				MODE.SUBSCRIBER -> 1
				MODE.PUBLISHER -> 2
				else -> 0
			}
		}

		private fun save(context: Context?, key: String, value: Int) {
			context?.getSharedPreferences(TAG, Context.MODE_PRIVATE)?.let {
				with(it.edit()) {
					putInt(key, value)
					apply()
				}
			}
		}
		private fun save(context: Context?, key: String, value: String) {
			context?.getSharedPreferences(TAG, Context.MODE_PRIVATE)?.let {
				with(it.edit()) {
					putString(key, value)
					apply()
				}
			}
		}
	}

	lateinit var operationMode: MODE
		private set
	fun isSubscriber(): Boolean {
		return (operationMode == MODE.SUBSCRIBER)
	}
	fun isPublisher(): Boolean {
		return (operationMode == MODE.PUBLISHER)
	}

	/**
	 * NOTE!!! Both the main activity and FCM messages can update this value
	 */
	var deviceToken: String? = null
		private set

	/**
	 * Name of the subscriber for the publisher to use
	 */
	private var subscriberName: String? = null

	/**
	 * Email address of the publisher to send subscription requests to.
	 */
	private var publisherEmail: String? = null

	/**
	 * Publisher ID from the first publication after the publisher approved the subscription
	 */
	private var publisherId: String? = null

	/**
	 * Indicate the subscriber also accepted the publication after the publisher approved, need to clear
	 * every time making a subscription. 0 - subscribe to nothing; 1 - waiting publisher approval; 2 - subscriber accepted publication
	 */
	private var publisherStatus: Int = 0

	init {
		load(true)
	}

	private fun load(all: Boolean) {
		context?.getSharedPreferences(TAG, Context.MODE_PRIVATE)?.let {
			deviceToken = it.getString(KEY_TOKEN, null)

			if (all) {
				operationMode =
					if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
						MODE.UNCONNECTED
					} else {
						covertMode(it.getInt(KEY_MODE, 0))
					}

				subscriberName = it.getString(KEY_SUB, null)
				publisherEmail = it.getString(KEY_PUB, null)
				publisherId = it.getString(KEY_PID, null)
				publisherStatus = it.getInt(KEY_STATUS, 0)
			}
		}
	}

	fun updateMode(mode: MODE) {
		updateMode(convertMode(mode))
	}
	private fun updateMode(mode: Int) {
		val newMode = covertMode(mode)
		if (newMode != operationMode) {
			operationMode = newMode
			save(context, KEY_MODE, mode)
		}
	}

	fun updateToken(token: String) {
		load(false) //First sync in memory value of deviceToken from the stored copy
		if (deviceToken != token) {
			deviceToken = token
			save(context, KEY_TOKEN, token)
		}
	}

	fun notifyPublisher(token: String) {
		// TODO NOTE: make sure it is in subscription mode and publisherStatus is 'subscriber accepted' before proceeding
		// to send update to publisher
		Log.w(TAG, "Notifying publisher of the FCM token changed from $deviceToken to $token")
	}

	/**
	 * This is called after the subscriber pressed the subscribe/unsubscribe button found in the settings dialog.
	 */
	fun makeSubscription(email: String, name: String?) {
		if (publisherStatus != 0) {
			// Unsubscribe
			publisherStatus = 0
			publisherId = null
			context?.getSharedPreferences(TAG, Context.MODE_PRIVATE)?.let {
				with(it.edit()) {
					putString(KEY_PID, null)
					putInt(KEY_STATUS, 0)
					apply()
				}
			}
		} else {
			// Subscribe
			publisherStatus = 1
			publisherId = null
			publisherEmail = email
			subscriberName = name
			context?.getSharedPreferences(TAG, Context.MODE_PRIVATE)?.let {
				with(it.edit()) {
					putString(KEY_PID, null)
					putInt(KEY_STATUS, 1)
					putString(KEY_PUB, email)
					putString(KEY_SUB, name)
					apply()
				}
			}
		}
	}

	/**
	 * This is called after the subscriber accepted the received publication, after the publisher received the subscrption
	 * request, and subsequently send the first FCM message to the subscriber.
	 */
	fun subscriptionAccepted(id: String) {
		if (publisherStatus == 1) {
			publisherId = id
			publisherStatus = 2
			context?.getSharedPreferences(TAG, Context.MODE_PRIVATE)?.let {
				with(it.edit()) {
					putString(KEY_PID, id)
					putInt(KEY_STATUS, 2)
					apply()
				}
			}
		}
	}

	enum class MODE {
		STANDALONE, PUBLISHER, SUBSCRIBER, UNCONNECTED
	}
}