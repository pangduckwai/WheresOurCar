package org.sea9.android.woc.messaging

import android.content.Context
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.util.Log
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.json.JSONObject
import org.sea9.android.woc.R
import org.sea9.android.woc.RetainedContext
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.VehicleRecord
import java.io.BufferedReader
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class PublishingUtils(private val caller: RetainedContext) {
	companion object {
		const val TAG = "woc.publish"
		private const val JSON_MESSAGE = "message"
		private const val JSON_DATA = "data"
		private const val JSON_TOKEN = "token"

		fun getPlayAccessToken(context: Context?): String? {
			return if (context == null) {
				null
			} else {
				GoogleCredential.fromStream(
					context.assets?.open(context.getString(R.string.firebase_account_key))
				).createScoped(
					listOf(context.getString(R.string.firebase_scope_fcm))
				)?.let {
					it.refreshToken()
					it.accessToken
				}
			}
		}
	}

	fun publish(record: VehicleRecord?) {
		if (record == null) return
		if (!caller.getSettingsManager().isPublisher()) return

		val data = JSONObject()
		data.put(VehicleRecord.NAM, record.name)
		data.put(VehicleRecord.PRK, record.parking)
		data.put(VehicleRecord.FLR, record.floor?: VehicleRecord.EMPTY)
		data.put(VehicleRecord.LOT, record.lot?: VehicleRecord.EMPTY)
		data.put(VehicleRecord.MOD, (record.modified?: Date().time).toString())

		val tokens = DbContract.Token.select(caller.getDbHelper()!!)
		val messages = Array<JSONObject?>(tokens.size) { null }
		tokens.forEachIndexed { index, token ->
			val body = JSONObject()
			body.put(JSON_TOKEN, token.token)
			body.put(JSON_DATA, data)
			JSONObject().put(JSON_MESSAGE, body).also {
				Log.w(TAG, "Publishing message $it...")
			}.also {
				messages[index] = it
			}
		}
		AsyncPublishTask(caller).execute(*messages)
	}
	private class AsyncPublishTask(private val caller: RetainedContext): AsyncTask<JSONObject, Void, Void?>() {
		companion object {
			private const val METHOD = "POST"
			private const val CNTENT = "Content-Type"
			private const val JSONU8 = "application/json; UTF-8"
			private const val AUTHZN = "Authorization"
			private const val RETRY = 5
			private const val INIT_BACKOFF_DELAY = 1000
			private const val MAX_BACKOFF_DELAY = 1024000

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

						when {
							(responseCode / 100 == 5) -> {
								null
							}
							(responseCode == 200) -> {
								inputStream.bufferedReader().use(BufferedReader::readText)
							}
							else -> {
								errorStream.bufferedReader().use(BufferedReader::readText)
							}
						}
					}
				} catch (e: Exception) {
					Log.w(TAG, e)
					null
				} finally {
					connection?.disconnect()
				}

				return if (result == null)
					false
				else {
					Log.w(TAG, "Publishing response $result")
					val match = pattern.find(result)
					match?.groupValues?.get(1) == projectId
				}
			}
		}

		override fun onPreExecute() {
			if (!(caller.getContext()
					?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
					.activeNetworkInfo.isConnected)
				cancel(true)
		}

		override fun doInBackground(vararg params: JSONObject?): Void? {
			if (params.isEmpty()) return null

			val projectId = caller.getContext()?.getString(R.string.firebase_project_id)
			val url = URL(caller.getContext()?.getString(R.string.firebase_endpoint_fcm, projectId))
			val pattern = caller.getContext()?.getString(R.string.firebase_succeed_fcm)!!.toRegex()
			params.forEach { param ->
				if (isCancelled) return@forEach

				// TODO TEMP - for debug
				if (param?.getJSONObject(JSON_MESSAGE)?.getString(JSON_TOKEN)?.startsWith("0000000")!!) {
					Log.w(TAG, "Testing token starting with '0000000' found, ignoring...")
					return@forEach
				}

				var count = 1
				var backoff = INIT_BACKOFF_DELAY
				val rand = Random()
				while (!httpRequest(url, getPlayAccessToken(caller.getContext()), param, projectId!!, pattern) &&
					(count < RETRY) && (backoff < (MAX_BACKOFF_DELAY/2))) {
					val sleep = (backoff / 2 + rand.nextInt(backoff)).toLong()
					Log.w(TAG, "Failed publishing $param, retry #$count in ${sleep}ms")
					count ++
					backoff *= 2
					try {
						Thread.sleep(sleep)
					} catch (e: InterruptedException) {
						Thread.currentThread().interrupt()
					}
				}
			}
			return null
		}
	}
}