package org.sea9.android.woc

import android.content.Context
import android.database.SQLException
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.util.Log
import android.widget.ArrayAdapter
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.json.JSONObject
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper
import org.sea9.android.woc.data.VehicleRecord
import java.lang.RuntimeException
import java.util.*

class MainContext: Fragment(), DbHelper.Caller {
	companion object {
		const val TAG = "woc.retained_frag"
		const val PATTERN_DATE = "yyyy-MM-dd HH:mm:ss"
		const val STATUS_NORMAL = 0
		const val STATUS_UPDATED = 1
		const val STATUS_ADDED = 2

		fun getInstance(sfm: FragmentManager): MainContext {
			var instance = sfm.findFragmentByTag(TAG) as MainContext?
			if (instance == null) {
				instance = MainContext()
				sfm.beginTransaction().add(instance, TAG).commit()
			}
			return instance
		}

		fun getOperationMode(context: Context?): MODE {
			return if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
				context?.getSharedPreferences(TAG, Context.MODE_PRIVATE)?.let {
					when (it.getInt(MainActivity.KEY_MODE, 2)) { //TODO Testing only! Default value should be "STANDALONE"
						0 -> MODE.STANDALONE
						1 -> MODE.SUBSCRIBER
						2 -> MODE.PUBLISHER
						else -> null
					}
				} ?: MODE.STANDALONE // default
			} else {
				Log.i(TAG, "Google Play NOT available")
				MODE.UNCONNECTED
			}
		}

		fun getPlayAccessToken(context: Context?): String? {
			return GoogleCredential.fromStream(
				context?.assets?.open("wheres-our-car-firebase-adminsdk-swq5c-6b91c19357.json")
			).createScoped(
				listOf("https://www.googleapis.com/auth/firebase.messaging")
			)?.let {
				it.refreshToken()
				it.accessToken
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
						callback?.doNotify("Switch to '$vehicle'")
						val current = DbContract.Vehicle.switch(dbHelper!!, list[0].rid)
						populateCurrent(current)
						resetStatus()
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
					callback?.doNotify("No change made")
					return false
				}
				((result and 1) > 0) -> {
					callback?.doNotify("Vehicle name cannot be empty")
					return false
				}
				else -> {
					if ((result and 2) > 0) {
						populateParkingList()
						callback?.doNotify("New parking ${currentVehicle.parking} added")
					} else if ((result and 4) > 0) {
						callback?.doNotify("Exiting parking ${currentVehicle.parking} chosen")
					}

					return if ((result and 24) > 0) {
						populateCurrent(null, true)
						if ((result and 8) > 0) populateVehicleList()
						resetStatus()
						callback?.onUpdated()
						true
					} else {
						false
					}
				}
			}
		} else {
			Log.w(TAG, "Database not ready")
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

	/*==========================
	 * Firebase Cloud Messaging
	 */
	enum class MODE {
		STANDALONE, PUBLISHER, SUBSCRIBER, UNCONNECTED
	}

	private lateinit var operationMode: MODE
	fun isSubscriber(): Boolean {
		return (operationMode == MODE.SUBSCRIBER)
	}
	fun isPublisher(): Boolean {
		return (operationMode == MODE.PUBLISHER)
	}

//	private var messagingToken: String? = null
//	private fun retrieveFirebaseToken() {
//		FirebaseInstanceId.getInstance().instanceId
//			.addOnCompleteListener(OnCompleteListener { task ->
//				if (!task.isSuccessful) {
//					Log.w(TAG, "getInstanceId failed", task.exception)
//					return@OnCompleteListener
//				}
//
//				// Get new Instance ID token
//				messagingToken = task.result?.token
//
//				Log.w(TAG, "FCM Token: $messagingToken")
//			})
//	}

	fun notifyPublisher(token: String) {
		Log.w(TAG, "Notifying publisher of the FCM token $token")
	}

	/*
{
  "message":{
    "token":"bk3RNwTe3H0:CI2k_HHwgIpoDKCIZvvDMExUdFQ3P1...",
    "data":{
      "Nick" : "Mario",
      "body" : "great match!",
      "Room" : "PortugalVSDenmark"
    }
  }
}
	 */
	fun publish() {
		val data = JSONObject()
		data.put(VehicleRecord.NAM, currentVehicle.name)
		data.put(VehicleRecord.PRK, currentVehicle.parking)
		data.put(VehicleRecord.FLR, currentVehicle.floor?: VehicleRecord.EMPTY)
		data.put(VehicleRecord.LOT, currentVehicle.lot?: VehicleRecord.EMPTY)
		data.put(VehicleRecord.MOD, currentVehicle.modified?: Date().time)
		val message = JSONObject()
		message.put("data", data)
		message.put("token", "")

		Log.w(TAG, "Building message $message")
	}

	/*=========================
	 * SQLite database related
	 */
	private var dbHelper: DbHelper? = null
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
	class AsyncDbInitTask (private val caller: MainContext): AsyncTask<Void, Void, Void>() {
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

		operationMode = getOperationMode(activity)
		Log.w(TAG, "Operation mode $operationMode")
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
//		if ((operationMode == MODE.PUBLISHER) || (operationMode == MODE.SUBSCRIBER)) {
//			val savedToken = activity
//				?.getSharedPreferences(MainActivity.TAG, Context.MODE_PRIVATE)
//				?.getString(MainActivity.KEY_TOKEN, null)
//			Log.w(TAG, "Saved Token: $savedToken")
//		}
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
		fun doNotify(msg: String)
		fun doNotify(msg: String, stay: Boolean)
		fun doNotify(ref: Int, msg: String, stay: Boolean)
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