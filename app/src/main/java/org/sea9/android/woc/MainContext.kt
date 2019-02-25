package org.sea9.android.woc

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.util.Log
import android.widget.ArrayAdapter
import com.google.android.gms.tasks.OnCompleteListener
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.firebase.iid.FirebaseInstanceId
import org.json.JSONObject
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper
import org.sea9.android.woc.data.TokenAdaptor
import org.sea9.android.woc.data.VehicleRecord
import java.io.BufferedReader
import java.lang.Exception
import java.lang.RuntimeException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class MainContext: Fragment(), DbHelper.Caller, TokenAdaptor.Caller {
	companion object {
		const val TAG = "woc.retained_frag"
		const val PATTERN_DATE = "yyyy-MM-dd HH:mm:ss"
		const val STATUS_NORMAL = 0
		const val STATUS_UPDATED = 1
		const val STATUS_ADDED = 2
		private const val JSON_MESSAGE = "message"
		private const val JSON_DATA = "data"
		private const val JSON_TOKEN = "token"

		fun getInstance(sfm: FragmentManager): MainContext {
			var instance = sfm.findFragmentByTag(TAG) as MainContext?
			if (instance == null) {
				instance = MainContext()
				sfm.beginTransaction().add(instance, TAG).commit()
			}
			return instance
		}

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

		/**
		 * @return
		 * 00001  1 - Vehicle name empty
		 * 00010  2 - Parking updated
		 * 00100  4 - Parking already exists
		 * 01000  8 - New vehicle added
		 * 10000 16 - Vehicle updated
		 */
		fun saveVehicle(status: Int, record: VehicleRecord, helper: DbHelper, ignoreTimestamp: Boolean): Int {
			var result = 0
			if (status <= 0) {
				return 0 // No change
			} else if (record.name.isBlank()) {
				return 1 // Vehicle name cannot be empty
			} else {
				if (record.parking.isNotBlank()) {
					result = try {
						DbContract.Parking.insert(helper, record.parking)
						(result or 2) //populateParkingList()
					} catch (e: SQLException) {
						Log.d(TAG, e.message) // this mean the parking already exists, so no problem here
						(result or 4)
					}
				}

				if (ignoreTimestamp) record.modified = null

				if ((status and STATUS_ADDED) > 0) { //is new record
					if (DbContract.Vehicle.add(helper, record) != null)
						result = (result or 8)
				} else {
					if (DbContract.Vehicle.update(helper, record) == 1)
						result = (result or 16)
				}
			}
			return result
		}
	}

	/*======================
	 * Main UI interactions
	 */
	lateinit var settingsManager: SettingsManager

	private var status = STATUS_NORMAL
	fun isUpdated(): Boolean {
		return (status > 0)
	}
	fun setUpdated() {
		status = status or STATUS_UPDATED
		callback?.onStatusChanged()
	}
	private fun setAdded() {
		status = status or STATUS_ADDED
		callback?.onStatusChanged()
	}
	fun resetStatus() {
		status = STATUS_NORMAL
	}

	private lateinit var currentVehicle: VehicleRecord
	fun updateParking(parking: String) {
		if (parking != currentVehicle.parking) {
			Log.d(TAG, "updateParking $parking...")
			currentVehicle.parking = parking
			setUpdated()
			populateCurrent(null)
		}
	}
	fun updateFloor(floor: String) {
		if ((floor.isNotBlank() && (floor != currentVehicle.floor)) ||
			(floor.isBlank() && (currentVehicle.floor?.isNotBlank() == true))) {
			Log.d(TAG, "updateFloor $floor...")
			currentVehicle.floor = floor
			setUpdated()
			populateCurrent(null)
		}
	}
	fun updateLot(lot: String) {
		if ((lot.isNotBlank() && (lot != currentVehicle.lot)) ||
			(lot.isBlank() && (currentVehicle.lot?.isNotBlank() == true))) {
			Log.d(TAG, "updateLot $lot...")
			currentVehicle.lot = lot
			setUpdated()
			populateCurrent(null)
		}
	}
	fun switchVehicle(vehicle: String) {
		if (vehicle.isNotBlank() && (vehicle != currentVehicle.name)) {
			if (isUpdated()) {
				callback?.onSwitchVehicle(vehicle)
			} else {
				val list = DbContract.Vehicle.select(dbHelper!!, vehicle)
				when {
					list.size > 1 -> throw RuntimeException("Vehicle table corrupted") // should not happen because of unique index
					list.size == 1 -> {
						callback?.doNotify(activity?.getString(R.string.msg_ui_switching, vehicle))
						val current = DbContract.Vehicle.switch(dbHelper!!, list[0].rid)
						populateCurrent(current)
						resetStatus()
						publish()
						callback?.onUpdated()
					}
					else -> {
						callback?.onNewVehicle(vehicle)
					}
				}
			}
		}
	}
	fun newVehicle(vehicle: String) {
		if (vehicle.isNotBlank()) {
			Log.d(TAG, "newVehicle...")
			val current = VehicleRecord()
			current.name = vehicle
			populateCurrent(current)
			setAdded() //set the is_new flag
		}
	}
	fun saveVehicle(): Boolean {
		if (dbHelper != null) {
			// Ignore timestamp here because any changes made from the UI should have the modified timestamp updated
			val result = MainContext.saveVehicle(status, currentVehicle, dbHelper!!, true)
			when {
				(result == 0) -> {
					//callback?.doNotify(activity?.getString(R.string.msg_ui_no_change))
					return false
				}
				((result and 1) > 0) -> {
					callback?.doNotify(activity?.getString(R.string.msg_ui_empty))
					return false
				}
				else -> {
					if ((result and 2) > 0) {
						populateParkingList()
						callback?.doNotify(activity?.getString(R.string.msg_ui_new_parking, currentVehicle.parking))
					} else if ((result and 4) > 0) {
						callback?.doNotify(activity?.getString(R.string.msg_ui_existing_parking, currentVehicle.parking))
					}

					return if ((result and 24) > 0) {
						populateCurrent(null, true)
						if ((result and 8) > 0) populateVehicleList()
						resetStatus()
						publish()
						callback?.onUpdated()
						true
					} else {
						false
					}
				}
			}
		} else {
			callback?.doNotify(activity?.getString(R.string.msg_ui_db_not_ready))
			return false
		}
	}

	lateinit var vehicleAdaptor: ArrayAdapter<String>
		private set

	lateinit var parkingAdaptor: ArrayAdapter<String>
		private set

	fun initializeAdaptors(context: Context) {
		vehicleAdaptor = ArrayAdapter(context, android.R.layout.simple_list_item_1)
		parkingAdaptor = ArrayAdapter(context, android.R.layout.simple_list_item_1)
	}

	fun populateVehicleList() {
		val v = DbContract.Vehicle.selectAll(dbHelper!!)
		vehicleAdaptor.clear()
		vehicleAdaptor.addAll(v.map {
			it.name
		})
	}

	fun populateParkingList() {
		val p = DbContract.Parking.select(dbHelper!!)
		parkingAdaptor.clear()
		parkingAdaptor.addAll(p.map {
			it.name
		})
	}

	fun populateCurrent(current: VehicleRecord?, retrieve: Boolean) {
		if (current == null) {
			if (retrieve && (dbHelper != null))
				currentVehicle = DbContract.Vehicle.select(dbHelper!!) ?: VehicleRecord()
		} else {
			currentVehicle = current
		}
		callback?.onPopulated(currentVehicle, !isUpdated()) //true if not updated -> clear focus
	}
	fun populateCurrent(current: VehicleRecord?) {
		populateCurrent(current, false)
	}

	lateinit var tokenAdaptor: TokenAdaptor
		private set

	fun getActiveNetworkInfo(): NetworkInfo {
		val connectivityManager = activity?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
		return connectivityManager.activeNetworkInfo
	}

	fun subscribes(email: String?, subscriber: String?) {
		if (settingsManager.deviceToken == null) { // No token yet, obtaining one...
			AsyncTokenTask(this).execute(email, subscriber)
		} else {
			subscribe(email, subscriber, settingsManager.deviceToken)
		}
	}
	private fun subscribe(email: String?, subscriber: String?, token: String?) {
		if (email != null) {
			val intent = Intent(Intent.ACTION_SENDTO)
			intent.type = "plain/text"
			intent.data = Uri.parse("mailto:")
			intent.putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(email))
			intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Where's Our Car subscription request")
			intent.putExtra(android.content.Intent.EXTRA_TEXT,
				"http://sea9.org/woc/s?t=$token&s=${subscriber ?: MainActivity.EMPTY}"
			)
			startActivity(Intent.createChooser(intent, "Sending subscription request…"))
		}
	}
	private class AsyncTokenTask(private val caller: MainContext): AsyncTask<String, Void, Array<String>?>() {
		@SuppressLint("ApplySharedPref")
		override fun doInBackground(vararg params: String): Array<String>? {
			FirebaseInstanceId.getInstance().instanceId
				.addOnCompleteListener(OnCompleteListener { task ->
					if (!task.isSuccessful) {
						Log.w(TAG, "getInstanceId failed", task.exception)
						return@OnCompleteListener
					}

					task.result?.token.let { token ->
						if (token != null) caller.settingsManager.updateToken(token)
					}
				})
			return if (params.isNotEmpty()) arrayOf(*params) else null
		}

		override fun onPostExecute(result: Array<String>?) {
			if (result != null) {
				caller.activity?.runOnUiThread {
					caller.subscribe(result[0], result[1], caller.settingsManager.deviceToken)
				}
			}
		}
	}

	fun publish() {
		if (!settingsManager.isPublisher()) return

		val data = JSONObject()
		data.put(VehicleRecord.NAM, currentVehicle.name)
		data.put(VehicleRecord.PRK, currentVehicle.parking)
		data.put(VehicleRecord.FLR, currentVehicle.floor?: VehicleRecord.EMPTY)
		data.put(VehicleRecord.LOT, currentVehicle.lot?: VehicleRecord.EMPTY)
		data.put(VehicleRecord.MOD, (currentVehicle.modified?: Date().time).toString())

		val tokens = DbContract.Token.select(dbHelper!!)
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
		AsyncPublishTask(this).execute(*messages)
	}
	private class AsyncPublishTask(private val caller: MainContext): AsyncTask<JSONObject, Void, Void?>() {
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
			if (!caller.getActiveNetworkInfo().isConnected)
				cancel(true)
		}

		override fun doInBackground(vararg params: JSONObject?): Void? {
			if (params.isEmpty()) return null

			val projectId = caller.getString(R.string.firebase_project_id)
			val url = URL(caller.getString(R.string.firebase_endpoint_fcm, projectId))
			val pattern = caller.getString(R.string.firebase_succeed_fcm).toRegex()
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
				while (!httpRequest(url, getPlayAccessToken(caller.context), param, projectId, pattern) &&
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

	/*=========================
	 * SQLite database related
	 */
	private var dbHelper: DbHelper? = null
	override fun getDbHelper(): DbHelper? {
		return dbHelper
	}
	private fun setDbHelper(helper: DbHelper) {
		dbHelper = helper
	}
	private fun isDbReady(): Boolean {
		return ((dbHelper != null) && dbHelper!!.ready)
	}

	//@see org.sea9.android.woc.data.DbHelper.Caller
	override fun onReady() {
		Log.d(TAG, "db ready")
		activity?.runOnUiThread {
			populateVehicleList()
			populateCurrent(DbContract.Vehicle.select(dbHelper!!) ?: VehicleRecord())
		}
	}

	//Init DB asynchronously
	private fun initDb() {
		Log.d(TAG, "init db")
		AsyncDbInitTask(this).execute()
	}
	private class AsyncDbInitTask (private val caller: MainContext): AsyncTask<Void, Void, Void>() {
		override fun doInBackground(vararg params: Void?): Void? {
			val helper = DbHelper(caller)
			caller.setDbHelper(helper)
			caller.populateParkingList() //Call this instead of SQL_CONFIG since this app does not need foreign key
			return null
		}
	}

	/*=====================================================
	 * Lifecycle method of android.support.v4.app.Fragment
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "onCreate()")
		retainInstance = true
		settingsManager = SettingsManager(context)
		tokenAdaptor = TokenAdaptor(this)
	}

	override fun onResume() {
		super.onResume()
		Log.d(TAG, "onResume()")

		if (!isDbReady()) {
			initDb()
		} else {
			populateVehicleList()
			populateParkingList()
			populateCurrent(null, true)
		}
	}

	override fun onDestroy() {
		Log.d(TAG, "onDestroy")
		if (dbHelper != null) {
			dbHelper!!.close()
			dbHelper = null
		}
		super.onDestroy()
	}

	/*========================================
	 * Callback interface to the MainActivity
	 */
	interface Callback {
		fun onPopulated(data: VehicleRecord?, clearFocus: Boolean)
		fun onNewVehicle(vehicle: String)
		fun onSwitchVehicle(vehicle: String)
		fun onStatusChanged()
		fun onUpdated()
		fun doNotify(msg: String?)
		fun doNotify(msg: String?, stay: Boolean)
		fun doNotify(ref: Int, msg: String?, stay: Boolean)
	}
	private var callback: Callback? = null

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		try {
			callback = context as Callback
		} catch (e: ClassCastException) {
			throw ClassCastException("$context missing implementation of MainContext.Callback")
		}
	}

	override fun onDetach() {
		super.onDetach()
		callback = null
	}
}