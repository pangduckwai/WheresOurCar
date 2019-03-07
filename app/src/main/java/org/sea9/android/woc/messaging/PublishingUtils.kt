package org.sea9.android.woc.messaging

import android.content.Context
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.util.Log
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.json.JSONObject
import org.sea9.android.crypto.KryptoUtils
import org.sea9.android.woc.RetainedContext
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.VehicleRecord
import java.io.BufferedReader
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class PublishingUtils(
	private val retainedContext: RetainedContext,
	private val projectId: String,
	private val keyFile: String,
	private val scopeUrl: String,
	private val fbEndpoint: String,
	private val fbPattern: String
) {
	companion object {
		const val TAG = "woc.publish"
		const val JSON_SALT = "salt"
		const val JSON_SECRET = "secret"
		private const val JSON_MESSAGE = "message"
		private const val JSON_DATA = "data"
		private const val JSON_TOKEN = "token"
		private const val MSG_NOW = "now"
		private const val METHOD = "POST"
		private const val CNTENT = "Content-Type"
		private const val JSONU8 = "application/json; UTF-8"
		private const val AUTHZN = "Authorization"
		private const val EMPTY = ""

		private fun httpRequest(url: URL, oauthToken: String?, obj: JSONObject, projectId: String, pattern: Regex): Boolean {
			// The doc said each HttpURLConnection instance is used to make a single request
			val buffer = obj.toString().toByteArray()
			var connection: HttpURLConnection? = null

			val result = try {
				connection = url.openConnection() as HttpURLConnection
				with(connection) {
					doOutput = true
					useCaches = false
					setFixedLengthStreamingMode(buffer.size)
					requestMethod = METHOD
					setRequestProperty(CNTENT, JSONU8)
					setRequestProperty(AUTHZN, "Bearer $oauthToken")

					outputStream.apply {
						try {
							write(buffer)
						} finally {
							flush()
							close()
						}
					}

					if (responseCode / 100 == 2) {
						inputStream.bufferedReader().use(BufferedReader::readText)
					} else {
						errorStream.bufferedReader().use(BufferedReader::readText)
					}
				}
			} catch (e: Exception) {
				Log.w(TAG, e)
				throw e
			} finally {
				connection?.disconnect()
			}

			val match = pattern.find(result)
			return if (match?.groupValues?.get(1) == projectId) {
				Log.d(TAG, "Publishing succeed with response $result")
				true
			} else {
				Log.w(TAG, "Publishing failed with response $result")
				false
			}
		}
	}

	private var accessToken: String? = null

	/**
	 * Publish the vehicle record via FCM to all subscribers.
	 */
	fun publish(record: VehicleRecord?) {
		if (record == null) return
		if (!retainedContext.getSettingsManager().isPublisher()) return
		if (retainedContext.getDbHelper() == null) return
		if (record.name.isBlank() || record.parking.isBlank()) return
		accessToken = null

		val data = JSONObject()
		val now = Date().time.toString()
		data.put(VehicleRecord.NAM, record.name)
		data.put(VehicleRecord.PRK, record.parking)
		data.put(VehicleRecord.FLR, record.floor?: VehicleRecord.EMPTY)
		data.put(VehicleRecord.LOT, record.lot?: VehicleRecord.EMPTY)
		data.put(VehicleRecord.MOD, record.modified?.toString() ?: now)
		data.put(MSG_NOW, now) // To make the message different everytime...

		val tokens = DbContract.Token.select(retainedContext.getDbHelper()!!)
		tokens.forEach { token ->
			// This function will start the dispatch task. It is needed to start so early because the
			// app attempts to publish changes when the user exit the app. All async tasks need to be
			// started before finish() is called, otherwise they won't be started (as the UI thread
			// already exit when finish() is called).
			publish(token.token, data)
		}
		AsyncOAuthTask(this, keyFile, scopeUrl).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
	}

	/**
	 * Publish an empty record to a specific subscriber. Actually this is called when the publisher remove
	 * the subscriber with the given token, letting the subscriber to update its own subscription status.
	 */
	fun publish(token: String) {
		val data = JSONObject()
		data.put(MSG_NOW, Date().time.toString())
		publish(token, data)
		AsyncOAuthTask(this, keyFile, scopeUrl).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
	}

	private fun publish(token: String, data: JSONObject) {
		val salt = KryptoUtils.generateSalt() //Use a different salt for each subscriber
		val secret = KryptoUtils.encrypt(data.toString().toCharArray(), MessagingService.getKey(retainedContext.getContext()), salt)
		val payload = JSONObject()
		payload.put(JSON_SALT, KryptoUtils.convert(KryptoUtils.encode(salt))?.joinToString(EMPTY))
		payload.put(JSON_SECRET, secret?.joinToString(EMPTY))
		val body = JSONObject()
		body.put(JSON_TOKEN, token)
		body.put(JSON_DATA, payload)
		JSONObject().put(JSON_MESSAGE, body).also {
			Log.d(TAG, "Publishing message $it...")
		}.also {
			AsyncDispatchTask(this, projectId, String.format(fbEndpoint, projectId), fbPattern)
				.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, it)
		}
	}

	private class AsyncOAuthTask(
		private val caller: PublishingUtils,
		private val keyFile: String,
		private val scopeUrl: String
	): AsyncTask<Void, Void, Void>() {
		override fun onPreExecute() {
			if (!(caller.retainedContext.getContext()?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo.isConnected)
				cancel(true)
		}
		override fun doInBackground(vararg params: Void?): Void? {
			if (isCancelled) return null
			caller.accessToken = caller.retainedContext.getContext()?.let { context ->
				GoogleCredential.fromStream(
					context.assets?.open(keyFile)
				).createScoped(
					listOf(scopeUrl)
				)?.let {
					it.refreshToken()
					it.accessToken
				}
			}
			return null
		}
	}
	private class AsyncDispatchTask(
		private val caller: PublishingUtils,
		private val projectId: String,
		private val urlString: String,
		private val fbPattern: String
	): AsyncTask<JSONObject, Void, Void?>() {
		companion object {
			private const val RETRY = 5
			private const val INIT_BACKOFF_DELAY = 1000
			private const val MAX_BACKOFF_DELAY = 1024000
		}
		override fun onPreExecute() {
			if (!(caller.retainedContext.getContext()?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo.isConnected)
				cancel(true)
		}
		override fun doInBackground(vararg params: JSONObject?): Void? {
			if (isCancelled || params.isEmpty() || (params[0] == null)) return null

			var count = 0
			while ((caller.accessToken == null) && (count < RETRY)) {
				count ++
				try {
					Thread.sleep(INIT_BACKOFF_DELAY.toLong())
				} catch (e: InterruptedException) {
					Thread.currentThread().interrupt()
				}
			}
			if (caller.accessToken == null) return null //Give up...

			count = 0
			val url = URL(urlString)
			val pattern = fbPattern.toRegex()
			var backoff = INIT_BACKOFF_DELAY
			val rand = Random()
			while (!httpRequest(url, caller.accessToken, params[0]!!, projectId, pattern) && (count < RETRY) && (backoff < (MAX_BACKOFF_DELAY/2))) {
				val sleep = (backoff / 2 + rand.nextInt(backoff)).toLong()
				Log.d(TAG, "Retry #$count in ${sleep}ms: failed publishing ${params[0]}")
				count ++
				backoff *= 2
				try {
					Thread.sleep(sleep)
				} catch (e: InterruptedException) {
					Thread.currentThread().interrupt()
				}
			}
			return null
		}
	}
}