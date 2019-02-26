package org.sea9.android.woc.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.sea9.android.woc.MainActivity
import org.sea9.android.woc.R

class SettingsManager(private val context: Context?) {
	companion object {
		private const val TAG = "woc.settings"
		const val KEY_MODE = "woc.mode"
		const val KEY_TOKEN = "woc.token"
		const val KEY_PUB = "woc.publisher"
		const val KEY_SUB = "woc.subscriber"
		const val KEY_PID = "woc.publisher.id"
		const val KEY_STATUS = "woc.publisher.statue"
		private const val CONTENT_TYPE = "plain/text"
		private const val MAIL_TO = "mailto:"
		private const val EMPTY = ""

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

		private fun sendMail(context: Context?, isSubscribe: Boolean, publisher: String, subscriber: String, token: String) {
			val intent = Intent(Intent.ACTION_SENDTO)
			intent.type = CONTENT_TYPE
			intent.data = Uri.parse(MAIL_TO)
			intent.putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(publisher))
			intent.putExtra(android.content.Intent.EXTRA_SUBJECT, context?.getString(R.string.sub_email_title))
			intent.putExtra(android.content.Intent.EXTRA_TEXT,
				if (isSubscribe)
					context?.getString(R.string.sub_email_subscribe, token, subscriber)
				else
					context?.getString(R.string.sub_email_unsubscribe, token)
			)
			context?.startActivity(Intent.createChooser(intent, context.getString(R.string.sub_email_chooser)))
		}
	}

	var operationMode: MODE =
		MODE.STANDALONE
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
	var subscriberEmail: String? = null

	/**
	 * Email address of the publisher to send subscription requests to.
	 */
	var publisherEmail: String? = null

	/**
	 * Publisher ID from the first publication after the publisher approved the subscription
	 */
	var publisherId: String? = null
		private set

	/**
	 * Indicate the subscriber also accepted the publication after the publisher approved, need to clear
	 * every time making a subscription. 0 - subscribe to nothing; 1 - waiting publisher approval; 2 - subscriber accepted publication
	 */
	var subscriptionStatus: Int = 0
		private set

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
						covertMode(
							it.getInt(
								KEY_MODE,
								0
							)
						)
					}

				subscriberEmail = it.getString(KEY_SUB, null)
				publisherEmail = it.getString(KEY_PUB, null)
				publisherId = it.getString(KEY_PID, null)
				subscriptionStatus = it.getInt(KEY_STATUS, 0)
			}
		}
	}

	fun updateMode(mode: MODE) {
		updateMode(convertMode(mode))
	}
	private fun updateMode(mode: Int) {
		save(
			context,
			KEY_MODE,
			mode
		)
	}

	fun updateToken(token: String) {
		load(false) //First sync in memory value of deviceToken from the stored copy
		if (deviceToken != token) {
			deviceToken = token
			save(
				context,
				KEY_TOKEN,
				token
			)
		}
	}

	/**
	 * For pid, publisher and subscriber, null means ignore, clear by setting to EMPTY
	 */
	private fun updateSubscription(status: Int, pid: String?, publisher: String?, subscriber: String?) {
		subscriptionStatus = status
		if (pid != null) publisherId = pid
		if (publisher != null) publisherEmail = publisher
		if (subscriber != null) subscriberEmail = subscriber
		context?.getSharedPreferences(TAG, Context.MODE_PRIVATE)?.let {
			with(it.edit()) {
				putInt(KEY_STATUS, status)
				if (pid != null) putString(KEY_PID, pid)
				if (publisher != null) putString(KEY_PUB, publisher)
				if (subscriber != null) putString(KEY_SUB, subscriber)
				apply()
			}
		}
	}

	/**
	 * This is called after the subscriber pressed the subscribe/unsubscribe button found in the settings dialog.
	 */
	fun makeSubscription(publisher: String, subscriber: String, token: String?) {
		if (token != null) {
			updateToken(token)
			makeSubscription(publisher, subscriber)
		}
	}
	fun makeSubscription(publisher: String, subscriber: String) {
		if (deviceToken == null) {
			val obj = Toast.makeText(context, context?.getString(R.string.msg_device_not_ready), Toast.LENGTH_LONG )
			obj.setGravity(Gravity.TOP, 0, 0)
			obj.show()
			return
		}

		when(subscriptionStatus) {
			0 -> { //Subscribing
				updateSubscription(1, EMPTY, publisher, subscriber) // name == null means ignore in updateSubcription()
				sendMail(
					context,
					true,
					publisher,
					subscriber,
					deviceToken!!
				)
			}
			1 -> { //Cancelling subscription request
				updateSubscription(0, EMPTY, null, null)
			}
			2 -> { //Unsubscribe
				updateSubscription(0, EMPTY, null, null)
				sendMail(
					context,
					false,
					publisher,
					subscriber,
					deviceToken!!
				)
			}
		}
	}

	/**
	 * This is for subscriber receiving publication for the first time.
	 */
	fun receiveApproval(id: String?) {
		if ((subscriptionStatus == 1) && (publisherId == null)) {
			updateSubscription(1, id, null, null)
		}
	}

	/**
	 * This is called after the subscriber accepted the received publication, after the publisher received the subscrption
	 * request, and subsequently send the first FCM message to the subscriber.
	 */
	fun acceptSubscription() {
		if ((subscriptionStatus == 1) && (publisherId != null)) {
			updateSubscription(2, null, null, null)
		}
	}

	fun notifyPublisher(token: String) {
		// TODO NOTE: make sure it is in subscription mode and subscriptionStatus is 'subscriber accepted' before proceeding
		// to send update to publisher
		Log.w(TAG, "Notifying publisher of the FCM token changed from $deviceToken to $token")
	}

	enum class MODE {
		STANDALONE, PUBLISHER, SUBSCRIBER, UNCONNECTED
	}
}