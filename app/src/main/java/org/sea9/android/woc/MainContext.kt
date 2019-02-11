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
//	fun getDbHelper(): DbHelper? {
////		return dbHelper
////	}
	private fun isDbReady(): Boolean {
		return ((dbHelper != null) && dbHelper!!.ready)
	}

	var isUpdated = false
		private set

	private var isNew = false

	private lateinit var currentVehicle: VehicleRecord
	fun updateParking(parking: String) {
		if (parking != currentVehicle.parking) {
			currentVehicle.parking = parking
			isUpdated = true
			populateCurrent(null)
		}
	}
	fun updateFloor(floor: String) {
		if (floor != currentVehicle.floor) {
			currentVehicle.floor = floor
			isUpdated = true
			populateCurrent(null)
		}
	}
	fun updateLot(lot: String) {
		if (lot != currentVehicle.lot) {
			currentVehicle.lot = lot
			isUpdated = true
			populateCurrent(null)
		}
	}
	fun switchVehicle(vehicle: String) {
		if (vehicle.isNotBlank() && (vehicle != currentVehicle.name)) {
			val list = DbContract.Vehicle.select(dbHelper!!, vehicle)
			when {
				list.size > 1 -> throw RuntimeException("Vehicle table corrupted") // should not happen because of unique index
				list.size == 1 -> {
					callback?.doNotify("Switch to '$vehicle'")
					val current = DbContract.Vehicle.switch(dbHelper!!, list[0].rid)
					populateCurrent(current)
					isNew = false
				}
				else -> {
					callback?.onNewVehicle(vehicle)
				}
			}
		}
	}
	fun newVehicle(vehicle: String) {
		if (vehicle.isNotBlank()) {
			val current = VehicleRecord()
			current.name = vehicle
			populateCurrent(current)
			isNew = true
		}
	}
	fun saveVehicle(): Boolean {
		if (currentVehicle.name.isBlank()) {
			callback?.doNotify("Vehicle name cannot be empty")
			return false
		} else if (!isUpdated) {
			callback?.doNotify("No change made")
			return false
		} else {
			try {
				DbContract.Parking.insert(dbHelper!!, currentVehicle.parking)
				populateParkingList()
				callback?.doNotify("New parking ${currentVehicle.parking} added")
			} catch (e: SQLException) {
				Log.w(TAG, e.message) // this mean the parking already exists, so no problem here
				callback?.doNotify("Exiting parking ${currentVehicle.parking} chosen")
			}

			if (isNew) {
				Log.w(TAG, "Saving new vehicle $currentVehicle ...")
				val current = DbContract.Vehicle.add(dbHelper!!, currentVehicle)
				populateCurrent(current)
				return if (current != null) {
					MainAppWidget.update(context, currentVehicle.floor, currentVehicle.lot)
					populateVehicleList()
					true
				} else
					false
			} else {
				Log.w(TAG, "Updating existing vehicle $currentVehicle ...")
				return if (DbContract.Vehicle.update(dbHelper!!, currentVehicle) == 1) {
					MainAppWidget.update(context, currentVehicle.floor, currentVehicle.lot)
					populateCurrent(currentVehicle)
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

	fun populateCurrent(current: VehicleRecord?) {
		if (current != null) {
			currentVehicle = current
			isUpdated = false
			isNew = false
		}
		callback?.onPopulated(currentVehicle, !isUpdated)
	}

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
			populateCurrent(DbContract.Vehicle.select(dbHelper!!) ?: VehicleRecord())
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
			populateParkingList()
			populateCurrent(DbContract.Vehicle.select(dbHelper!!) ?: VehicleRecord())
		}
	}

	private fun initDb() {
		Log.d(TAG, "init db")
		AsyncDbInitTask(this).execute()
	}
	class AsyncDbInitTask (private val caller: MainContext): AsyncTask<Void, Void, Void>() {
		override fun doInBackground(vararg params: Void?): Void? {
			val helper = DbHelper(caller)
			caller.setDbHelper(helper)
			helper.writableDatabase.execSQL(DbContract.SQL_CONFIG)
			return null
		}
	}
}