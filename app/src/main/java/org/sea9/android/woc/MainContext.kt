package org.sea9.android.woc

import android.content.Context
import android.database.SQLException
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.util.Log
import android.widget.ArrayAdapter
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper
import org.sea9.android.woc.data.VehicleRecord
import java.lang.RuntimeException

class MainContext: Fragment(), DbHelper.Caller {
	companion object {
		const val TAG = "woc.retained_frag"
		const val PATTERN_DATE = "yyyy-MM-dd HH:mm:ss"
		private const val STATUS_NORMAL = 0
		private const val STATUS_UPDATED = 1
		private const val STATUS_ADDED = 2

		fun getInstance(sfm: FragmentManager): MainContext {
			var instance = sfm.findFragmentByTag(TAG) as MainContext?
			if (instance == null) {
				instance = MainContext()
				sfm.beginTransaction().add(instance, TAG).commit()
			}
			return instance
		}
	}

	private var dbHelper: DbHelper? = null
	private fun setDbHelper(helper: DbHelper) {
		dbHelper = helper
	}
	private fun isDbReady(): Boolean {
		return ((dbHelper != null) && dbHelper!!.ready)
	}

	private var status = STATUS_NORMAL
	fun isUpdated(): Boolean {
		return (status > 0)
	}
	fun resetStatus() {
		status = STATUS_NORMAL
	}

	private lateinit var currentVehicle: VehicleRecord
	fun updateParking(parking: String) {
		if (parking != currentVehicle.parking) {
			Log.d(TAG, "updateParking $parking...")
			currentVehicle.parking = parking
			status = status or STATUS_UPDATED
			populateCurrent(null)
		}
	}
	fun updateFloor(floor: String) {
		if ((floor.isNotBlank() && (floor != currentVehicle.floor)) ||
			(floor.isBlank() && (currentVehicle.floor?.isNotBlank() == true))) {
			Log.d(TAG, "updateFloor $floor...")
			currentVehicle.floor = floor
			status = status or STATUS_UPDATED
			populateCurrent(null)
		}
	}
	fun updateLot(lot: String) {
		if ((lot.isNotBlank() && (lot != currentVehicle.lot)) ||
			(lot.isBlank() && (currentVehicle.lot?.isNotBlank() == true))) {
			Log.d(TAG, "updateLot $lot...")
			currentVehicle.lot = lot
			status = status or STATUS_UPDATED
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
			status = STATUS_ADDED //set the is_new flag
		}
	}
	fun saveVehicle(): Boolean {
		if (status == STATUS_NORMAL) {
			callback?.doNotify("No change made")
			return false
		} else if (currentVehicle.name.isBlank()) {
			callback?.doNotify("Vehicle name cannot be empty")
			return false
		} else {
			if (currentVehicle.parking.isNotBlank()) {
				try {
					DbContract.Parking.insert(dbHelper!!, currentVehicle.parking)
					populateParkingList()
					callback?.doNotify("New parking ${currentVehicle.parking} added")
				} catch (e: SQLException) {
					Log.d(TAG, e.message) // this mean the parking already exists, so no problem here
					callback?.doNotify("Exiting parking ${currentVehicle.parking} chosen")
				}
			}

			if ((status and STATUS_ADDED) > 0) { //isNew
				Log.d(TAG, "Saving new vehicle $currentVehicle ...")
				val current = DbContract.Vehicle.add(dbHelper!!, currentVehicle)
				populateCurrent(current)
				return if (current != null) {
					populateVehicleList()
					resetStatus()
					callback?.onUpdated()
					true
				} else
					false
			} else {
				Log.d(TAG, "Updating existing vehicle $currentVehicle ...")
				return if (DbContract.Vehicle.update(dbHelper!!, currentVehicle) == 1) {
					populateCurrent(currentVehicle)
					resetStatus()
					callback?.onUpdated()
					true
				} else
					false
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

	private fun populateVehicleList() {
		val v = DbContract.Vehicle.selectAll(dbHelper!!)
		vehicleAdaptor.clear()
		vehicleAdaptor.addAll(v.map {
			it.name
		})
	}

	private fun populateParkingList() {
		val p = DbContract.Parking.select(dbHelper!!)
		parkingAdaptor.clear()
		parkingAdaptor.addAll(p.map {
			it.name
		})
	}

	fun populateCurrent(current: VehicleRecord?, retrieve: Boolean) {
		if (current == null) {
			if (retrieve) currentVehicle = DbContract.Vehicle.select(dbHelper!!) ?: VehicleRecord()
		} else {
			currentVehicle = current
		}
		callback?.onPopulated(currentVehicle, ((status and STATUS_UPDATED) == 0)) //true if not updated
	}
	fun populateCurrent(current: VehicleRecord?) {
		populateCurrent(current, false)
	}

	/*=====================================================
	 * Lifecycle method of android.support.v4.app.Fragment
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "onCreate()")
		retainInstance = true
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
	//========================================

	/*================================================
	 * @see org.sea9.android.woc.data.DbHelper.Caller
	 */
	override fun onReady() {
		Log.d(TAG, "db ready")
		activity?.runOnUiThread {
			populateVehicleList()
			populateCurrent(DbContract.Vehicle.select(dbHelper!!) ?: VehicleRecord())
		}
	}

	/*========================
	 * Init DB asynchronously
	 */
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
}