package org.sea9.android.woc.data

import android.content.ContentValues
import android.database.Cursor
import android.provider.BaseColumns
import java.util.*

object DbContract {
	const val DATABASE = "Bookmarks.db_contract"
	const val PKEY = BaseColumns._ID
	const val COMMON_NAME = "name"
	const val COMMON_MODF = "modified"
	const val COMMON_PKEY = "$PKEY = ?"
	const val SQL_CONFIG = "PRAGMA foreign_keys=ON"

	class Parking : BaseColumns {
		companion object {
			const val TABLE = "Parking"
			private const val IDX_PARK = "idxParking"
			private val COLUMNS = arrayOf(
				PKEY,
				COMMON_NAME,
				COMMON_MODF
			)

			const val SQL_CREATE =
				"create table $TABLE (" +
						"$PKEY integer primary key autoincrement," +
						"$COMMON_NAME text not null COLLATE NOCASE," +
						"$COMMON_MODF integer)"
			const val SQL_CREATE_IDX = "create unique index $IDX_PARK on $TABLE ($COMMON_NAME)"
			const val SQL_DROP = "drop table if exists $TABLE"
			const val SQL_DROP_IDX = "drop index if exists $IDX_PARK"

			fun select(helper: DbHelper): List<ParkingRecord> {
				val cursor = helper.readableDatabase
					.query(
						TABLE,
						COLUMNS, null, null, null, null,
						COMMON_NAME
					)

				val result = mutableListOf<ParkingRecord>()
				cursor.use {
					with(it) {
						while (moveToNext()) {
							val rid = getLong(getColumnIndexOrThrow(PKEY))
							val name = getString(getColumnIndexOrThrow(COMMON_NAME))
							val modified = getLong(getColumnIndexOrThrow(COMMON_MODF))
							result.add(ParkingRecord(rid, name, modified))
						}
					}
				}
				return result
			}

			fun insert(helper: DbHelper, name: String): Long {
				val newRow = ContentValues().apply {
					put(COMMON_NAME, name)
					put(COMMON_MODF, Date().time)
				}
				return helper.writableDatabase.insertOrThrow(TABLE, null, newRow)
			}

			fun delete(helper: DbHelper, rid: Long): Int {
				val args = arrayOf(rid.toString())
				return helper.writableDatabase.delete(TABLE, COMMON_PKEY, args)
			}
		}
	}

	//TODO !!!!!!!!!!! parking should be string instead of reference to another table...
	class Vehicle : BaseColumns {
		companion object {
			const val TABLE = "Vehicle"
			const val COL_PARK = "parking"
			const val COL_FLOOR = "floor"
			const val COL_LOT = "lot"
			const val COL_CURR = "isCurrent"
			const val PARK_ID = "pid"
			const val PARK_NAME = "pname"
			const val PARK_MODF = "pmod"
			private const val IDX_VEHICLE = "idxVehicle"

			const val SQL_CREATE =
				"create table $TABLE (" +
						"$PKEY integer primary key autoincrement," +
						"$COMMON_NAME text not null COLLATE NOCASE," +
						"$COL_PARK integer," +
						"$COL_FLOOR text," +
						"$COL_LOT text," +
						"$COL_CURR integer not null," +
						"$COMMON_MODF integer," +
						"foreign key($COL_PARK) references ${Parking.TABLE}($PKEY))"
			const val SQL_CREATE_IDX = "create unique index $IDX_VEHICLE on $TABLE ($COMMON_NAME)"
			const val SQL_DROP = "drop table if exists $TABLE"
			const val SQL_DROP_IDX = "drop index if exists $IDX_VEHICLE"

			private val COLUMNS = arrayOf(
				PKEY,
				COMMON_NAME,
				COL_PARK,
				COL_FLOOR,
				COL_LOT,
				COL_CURR,
				COMMON_MODF
			)

			private const val SQL_QUERY =
				"select v.$PKEY, v.$COMMON_NAME, v.$COL_FLOOR, v.$COL_LOT, v.$COL_CURR, v.$COMMON_MODF" +
				"     , p.$PKEY as pid, p.$COMMON_NAME as pname, p.$COMMON_MODF as pmod" +
				"  from $TABLE as v" +
				" left outer join ${Parking.TABLE} as p on p.$PKEY = v.$COL_PARK"

			/**
			 * Get the current vehicle record by first sort by 'isCurrent', which all row should
			 * have value of zero except the current row. If more than one row is marked as current
			 * (which should not be the case), use the one which was updated last.
			 */
			fun select(helper: DbHelper): VehicleRecord? {
				return select(
					helper.readableDatabase.rawQuery(
						"$SQL_QUERY order by v.$COL_CURR desc, v.$COMMON_MODF desc",
						null
					)
				)
			}
			fun select(helper: DbHelper, rid: Long): VehicleRecord? {
				return select(
					helper.readableDatabase.rawQuery(
						"$SQL_QUERY where v.$PKEY = ? order by v.$COMMON_NAME",
						arrayOf(rid.toString())
					)
				)
			}
			private fun select(cursor: Cursor): VehicleRecord? {
				var result: VehicleRecord? = null
				cursor.use {
					with(it) {
						while (moveToNext()) {
							val rid = getLong(getColumnIndexOrThrow(PKEY))
							val name = getString(getColumnIndexOrThrow(COMMON_NAME))
							val flor = getString(getColumnIndexOrThrow(COL_FLOOR))
							val plot = getString(getColumnIndexOrThrow(COL_LOT))
							val curr = getInt(getColumnIndexOrThrow(COL_CURR))
							val modf = getLong(getColumnIndexOrThrow(COMMON_MODF))

							val pid = getLong(getColumnIndexOrThrow(PARK_ID))
							val pname = getString(getColumnIndexOrThrow(PARK_NAME))
							val pmod = getLong(getColumnIndexOrThrow(PARK_MODF))
							val park = pid?.let { ParkingRecord(pid, pname, pmod) }

							result = VehicleRecord(rid, name, park, flor, plot, (curr != 0), modf)
							break
						}
					}
				}
				return result
			}

			fun selectAll(helper: DbHelper): List<VehicleRecord> {
				val cursor = helper.readableDatabase
					.query(
						TABLE, COLUMNS, null, null, null, null,
						COMMON_NAME
					)

				val result = mutableListOf<VehicleRecord>()
				cursor.use {
					with(it) {
						while (moveToNext()) {
							val rid = getLong(getColumnIndexOrThrow(PKEY))
							val name = getString(getColumnIndexOrThrow(COMMON_NAME))
							val flor = getString(getColumnIndexOrThrow(COL_FLOOR))
							val plot = getString(getColumnIndexOrThrow(COL_LOT))
							val curr = getInt(getColumnIndexOrThrow(COL_CURR))
							result.add(VehicleRecord(rid, name, null, flor, plot, (curr != 0), null))
						}
					}
				}
				return result
			}

			fun insert(helper: DbHelper, record: VehicleRecord): Long {
				val newRow = ContentValues().apply {
					put(COMMON_NAME, record.name)
					if (record.parking != null) put(COL_PARK, record.parking!!.rid)
					put(COL_FLOOR, record.floor)
					put(COL_LOT, record.lot)
					put(COL_CURR, if (record.current) 1 else 0)
					put(COMMON_MODF, Date().time)
				}
				return helper.writableDatabase.insertOrThrow(TABLE, null, newRow)
			}

			fun update(helper: DbHelper, record: VehicleRecord): Int {
				val args = arrayOf(record.rid.toString())
				val newRow = ContentValues().apply {
					put(COMMON_NAME, record.name)
					if (record.parking != null) put(COL_PARK, record.parking!!.rid)
					put(COL_FLOOR, record.floor)
					put(COL_LOT, record.lot)
					put(COL_CURR, if (record.current) 1 else 0)
					put(COMMON_MODF, Date().time)
				}
				return helper.writableDatabase.update(TABLE, newRow, COMMON_PKEY, args)
			}

			fun delete(helper: DbHelper, rid: Long): Int {
				val args = arrayOf(rid.toString())
				return helper.writableDatabase.delete(TABLE, COMMON_PKEY, args)
			}
		}
	}
}