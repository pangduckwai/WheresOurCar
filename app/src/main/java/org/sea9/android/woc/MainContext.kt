package org.sea9.android.woc

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.database.SQLException
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.util.Log
import android.widget.ArrayAdapter
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper
import org.sea9.android.woc.data.TokenAdaptor
import org.sea9.android.woc.data.VehicleRecord
import org.sea9.android.woc.messaging.PublishingUtils
import org.sea9.android.woc.settings.SettingsManager
import java.lang.RuntimeException
import java.lang.StringBuilder

class MainContext: Fragment(), RetainedContext, DbHelper.Caller, TokenAdaptor.Caller {
	companion object {
		const val TAG = "woc.main_retained"
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
	private lateinit var settingsManager: SettingsManager
	override fun getSettingsManager(): SettingsManager {
		return settingsManager
	}

	private lateinit var publishingUtils: PublishingUtils

	fun publish() {
		publishingUtils.publish(currentVehicle)
	}

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

	private var currentVehicle = VehicleRecord()
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
						publishingUtils.publish(current)
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

	fun acceptSubscription() {
		settingsManager.acceptSubscription()
		DbContract.Vehicle.selectTemp(dbHelper!!)?.let {
			if (DbContract.Vehicle.switch(dbHelper!!, it.rid) != null) {
				populateCurrent(null, true)
				callback?.onUpdated()
			}
		}
	}
	fun rejectSubscription() {
		settingsManager.rejectSubscription()
		DbContract.Vehicle.selectTemp(dbHelper!!)?.let {
			if (DbContract.Vehicle.delete(dbHelper!!, it.rid) == 1) {
				populateCurrent(null, true)
				callback?.onUpdated()
			}
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
	fun deleteToken(token: String) {
		DbContract.Token.delete(dbHelper!!, token)
		tokenAdaptor.populateCache()
		tokenAdaptor.notifyDataSetChanged()
	}

	fun subscribes(publisher: String, subscriber: String) {
		if (settingsManager.deviceToken == null) { // No token yet, obtaining one...
			AsyncTokenTask(this).execute(publisher, subscriber)
		} else {
			settingsManager.makeSubscription(publisher, subscriber)
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
					caller.settingsManager.makeSubscription(result[0], result[1], caller.settingsManager.deviceToken)
				}
			}
		}
	}

	/*=============
	 * Key related
	 */
	@Suppress("DEPRECATION")
	@SuppressLint("PackageManagerGetSignatures")
	fun getKey(): String? {
		context?.let {
			val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				it.packageManager.getPackageInfo(it.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.apkContentsSigners
			} else {
				it.packageManager.getPackageInfo(it.packageName, PackageManager.GET_SIGNATURES).signatures
			}
			val buffer = StringBuilder()
			signatures.forEach {s ->
				buffer.append(s.toCharsString())
			}
			return buffer.toString()
		} ?: return null
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

		val projectId = getString(R.string.firebase_project_id)
		publishingUtils = PublishingUtils(
			this, projectId,
			getString(R.string.firebase_account_key),
			getString(R.string.firebase_scope_fcm),
			getString(R.string.firebase_endpoint_fcm, projectId),
			getString(R.string.firebase_succeed_fcm)
		)
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