package org.sea9.android.woc

import android.content.Context
import android.database.SQLException
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.BeforeClass
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper
import org.sea9.android.woc.data.ParkingRecord
import org.sea9.android.woc.data.VehicleRecord

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class MainInstrumentedTest {
	companion object {
		private lateinit var context: Context
		private lateinit var helper: DbHelper
		private lateinit var parkings: List<ParkingRecord>
		private var vids = longArrayOf(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1)

		@BeforeClass
		@JvmStatic
		fun prepare() {
			context = InstrumentationRegistry.getTargetContext()
			helper = DbHelper(object : DbHelper.Caller {
				override fun getContext(): Context? {
					return context
				}

				override fun onReady() {
					Log.w("woc.itest", "DB test connection ready")
				}
			})
			helper.writableDatabase.execSQL(DbContract.SQL_CONFIG)

			parkings = DbContract.Parking.select(helper)
			val vehicles = DbContract.Vehicle.selectAll(helper)

			if (parkings.isNotEmpty() || vehicles.isNotEmpty()) {
				if (parkings.isNotEmpty()) Log.w("woc.itest", "${parkings.size} parking already exists")
				if (vehicles.isNotEmpty()) Log.w("woc.itest", "${vehicles.size} vehicles already exists")

				helper.writableDatabase.beginTransactionNonExclusive()
				try {
					helper.writableDatabase.execSQL("delete from ${DbContract.Vehicle.TABLE}")
					helper.writableDatabase.execSQL("delete from ${DbContract.Parking.TABLE}")
					helper.writableDatabase.setTransactionSuccessful()
				} finally {
					helper.writableDatabase.endTransaction()
				}
			}

			Log.w("woc.itest", "Adding new Parkings")
			DbContract.Parking.insert(helper, "Monthly Parking")		//4
			DbContract.Parking.insert(helper, "Dung Kong Sing")		//2
			DbContract.Parking.insert(helper, "Dai Kwok Chung Sum")	//1
			DbContract.Parking.insert(helper, "On Dak Chung Sum")		//5
			DbContract.Parking.insert(helper, "MG Tower G/Floor")		//3
			DbContract.Parking.insert(helper, "Some Parking 01")		//6
			DbContract.Parking.insert(helper, "Some Parking 02")		//7
			DbContract.Parking.insert(helper, "Some Parking 03")		//8
			DbContract.Parking.insert(helper, "Some Parking 04")		//9
			DbContract.Parking.insert(helper, "Some Parking 05")		//10
			DbContract.Parking.insert(helper, "WTS Parking")			//11
			parkings = DbContract.Parking.select(helper)

			Log.w("woc.itest", "Adding new Vehicles")
			vids[0] = DbContract.Vehicle.insert(helper, VehicleRecord(-1, "Enzo Ferrari", "On Dak Chung Sum", null, null, false, null))
			vids[1] = DbContract.Vehicle.insert(helper, VehicleRecord(-1, "Lamborghini Huracán EVO", "Some Parking 01", null, null, false, null))
			vids[2] = DbContract.Vehicle.insert(helper, VehicleRecord(-1, "Porsche 911", "Some Parking 02", null, null, false, null))
			vids[3] = DbContract.Vehicle.insert(helper, VehicleRecord(-1, "Bugatti", "Some Parking 03", null, null, false, null))
			vids[4] = DbContract.Vehicle.insert(helper, VehicleRecord(-1, "Maserati", "Some Parking 04", null, null, false, null))
			vids[5] = DbContract.Vehicle.insert(helper, VehicleRecord(-1, "Koenigsegg One:1", "Some Parking 05", null, null, false, null))
			vids[6] = DbContract.Vehicle.insert(helper, VehicleRecord(-1, "Honda NSX", "Dai Kwok Chung Sum", null, null, false, null))
			vids[7] = DbContract.Vehicle.insert(helper, VehicleRecord(-1, "Toyota Prius", "Dung Kong Sing", null, null, false, null))
			vids[8] = DbContract.Vehicle.insert(helper, VehicleRecord(-1, "Tesla Model X", "MG Tower G/Floor", null, null, false, null))
			vids[9] = DbContract.Vehicle.insert(helper, VehicleRecord(-1, "VW e-Golf", "Monthly Parking", "5/F", "P50", true, null))
		}

//		@AfterClass
//		@JvmStatic
//		fun cleanup() {
//			helper.deleteDatabase()
//		}
	}

	@Test
	fun useAppContext() {
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getTargetContext()
		assertEquals("org.sea9.android.woc", appContext.packageName)
	}

	@Test
	fun testSelectCurrent() {
		val v = DbContract.Vehicle.select(helper)
		Log.w("woc.itest.testSelectCurrent", v.toString())
		assertTrue(v!!.current)
	}

	@Test
	fun testSelectPrevious() {
		val v = DbContract.Vehicle.select(helper, vids[7])
		Log.w("woc.itest.testSelectPrevious", v.toString())
		assertTrue(!v!!.current)
	}

	@Test(expected = SQLException::class)
	fun testUniqueIndexParking() {
		Log.w("woc.itest.testUniqueIndexParking", "Attempt to add parking with duplicated name")
		DbContract.Parking.insert(helper, "MONTHLY PARKING")
	}

	@Test(expected = SQLException::class)
	fun testUniqueIndex2() {
		Log.w("woc.itest.testUniqueIndex2", "Attempt to add vehicle with duplicated name")
		DbContract.Vehicle.insert(helper, VehicleRecord("{'id': -1, 'name': 'bugatti', 'parking': 'Dung Kong Sing', 'current': 'false'}"))
	}

	@Test
	fun testDeleteUnreferencedRecord() {
		Log.w("woc.itest.testDeleteUnreferencedRecord", "Delete unused parking record")
		val ret = DbContract.Parking.delete(helper, parkings[10].rid)
		var cnt = 0
		if (ret == 1) {
			val p = DbContract.Parking.select(helper)
			cnt = p.size
			p.forEachIndexed { i, rec ->
				Log.w("woc.testDeleteUnreferencedRecord", ">>> Parking: $i - $rec")
			}
		}
		assertTrue((ret == 1) && (cnt == 10))
	}

	@Test
	fun testDeleteVehicle() {
		Log.w("woc.itest.testDeleteVehicle", "Delete vehicle record")
		val ret = DbContract.Vehicle.delete(helper, vids[8])
		var cnt = 0
		if (ret == 1) {
			val v = DbContract.Vehicle.selectAll(helper)
			cnt = v.size
			v.forEachIndexed { i, rec ->
				Log.w("woc.testDeleteVehicle", ">>> Vehicle: $i - $rec")
			}
		}
		assertTrue((ret == 1) && (cnt == 9))
	}

	@Test
	fun testUpdateVehicle() {
		Log.w("woc.itest.testUpdateVehicle", "Update vehicle record")
		val ret = DbContract.Vehicle.update(helper,
			VehicleRecord("{" +
					"'id': ${vids[2]}," +
					"'name': 'Porsche 911 GT'," +
					"'parking': 'Some Parking 02'," +
					"'floor': 'B1'," +
					"'lot': 'P10'," +
					"'current': 'false'}")
		)
		assertTrue(ret == 1)
	}

	@Test
	fun testQuery() {
		Log.w("woc.itest.testQuery", "Query vehicle record")
		val result1 = DbContract.Vehicle.select(helper, 1234567890)
		assertTrue(result1 == null)
		val result2 = DbContract.Vehicle.select(helper, "HAHAHAHAHA")
		assertTrue(result2.isEmpty())

		val list = DbContract.Vehicle.select(helper, vids[5])
		val list1 = DbContract.Vehicle.select(helper, "Koenigsegg One:1")
		assertTrue(list?.rid == list1[0].rid)
		val list2 = DbContract.Vehicle.select(helper, "KOENIGSEGG ONE:1")
		assertTrue(list?.rid == list2[0].rid)
		val list3 = DbContract.Vehicle.select(helper, "koenigsegg one:1")
		assertTrue(list?.rid == list3[0].rid)
	}
}
