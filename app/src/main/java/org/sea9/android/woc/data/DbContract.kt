package org.sea9.android.woc.data

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
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
						TABLE, COLUMNS, null, null, null, null,
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

	class Vehicle : BaseColumns {
		companion object {
			const val TABLE = "Vehicle"
			private const val COL_PARK = "parking"
			private const val COL_FLOOR = "floor"
			private const val COL_LOT = "lot"
			private const val COL_CURR = "isCurrent"
			private const val IDX_VEHICLE = "idxVehicle"

			const val SQL_CREATE =
				"create table $TABLE (" +
						"$PKEY integer primary key autoincrement," +
						"$COMMON_NAME text not null COLLATE NOCASE," +
						"$COL_PARK text not null COLLATE NOCASE," +
						"$COL_FLOOR text," +
						"$COL_LOT text," +
						"$COL_CURR integer not null," +
						"$COMMON_MODF integer)"
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

			/**
			 * Get the current vehicle record by first sort by 'isCurrent', which all row should
			 * have value of zero except the current row. If more than one row is marked as current
			 * (which should not be the case), use the one which was updated last.
			 */
			fun select(helper: DbHelper): VehicleRecord? {
				return select(
					helper.readableDatabase.query(
						TABLE, COLUMNS, null, null, null, null,
						"$COL_CURR desc, $COMMON_MODF desc"
					), true
				)?.get(0)
			}

			/**
			 * Get vehicle record by ID.
			 */
			fun select(helper: DbHelper, rid: Long): VehicleRecord? {
				return select(
					helper.readableDatabase.query(
						TABLE, COLUMNS, COMMON_PKEY, arrayOf(rid.toString()), null, null,
						COMMON_NAME
					), true
				)?.get(0)
			}

			fun select(helper: DbHelper, name: String): List<VehicleRecord> {
				return select(
					helper.readableDatabase.query(
						TABLE, COLUMNS, "$COMMON_NAME = ?", arrayOf(name), null, null,
						COMMON_NAME
					), false
				) ?: mutableListOf()
			}

			fun selectAll(helper: DbHelper): List<VehicleRecord> {
				return select(
					helper.readableDatabase.query(
						TABLE, COLUMNS, null, null, null, null,
						COMMON_NAME
					), false
				) ?: mutableListOf()
			}

			private fun select(cursor: Cursor, firstRowOnly: Boolean): List<VehicleRecord>? {
				val result = mutableListOf<VehicleRecord>()
				cursor.use {
					with(it) {
						while (moveToNext()) {
							val rid = getLong(getColumnIndexOrThrow(PKEY))
							val name = getString(getColumnIndexOrThrow(COMMON_NAME))
							val park = getString(getColumnIndexOrThrow(COL_PARK))
							val flor = getString(getColumnIndexOrThrow(COL_FLOOR))
							val plot = getString(getColumnIndexOrThrow(COL_LOT))
							val curr = getInt(getColumnIndexOrThrow(COL_CURR))
							val modf = getLong(getColumnIndexOrThrow(COMMON_MODF))
							result.add(VehicleRecord(rid, name, park, flor, plot, (curr != 0), modf))
							if (firstRowOnly) break
						}
					}
				}
				return if (result.size <= 0)
					null
				else
					result
			}

			fun insert(helper: DbHelper, record: VehicleRecord): Long {
				val newRow = ContentValues().apply {
					put(COMMON_NAME, record.name)
					put(COL_PARK, record.parking)
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
					put(COL_PARK, record.parking)
					put(COL_FLOOR, record.floor)
					put(COL_LOT, record.lot)
					put(COL_CURR, 1)
					put(COMMON_MODF, record.modified ?: Date().time)
				}
				return helper.writableDatabase.update(TABLE, newRow, COMMON_PKEY, args)
			}

			fun delete(helper: DbHelper, rid: Long): Int {
				val args = arrayOf(rid.toString())
				return helper.writableDatabase.delete(TABLE, COMMON_PKEY, args)
			}

			fun switch(helper: DbHelper, rid: Long): VehicleRecord? {
				val db = helper.writableDatabase
				val args = arrayOf(rid.toString())
				val newRow0 = ContentValues().apply { put(COL_CURR, 0) }
				val newRow1 = ContentValues().apply { put(COL_CURR, 1) }
				var ret = -1
				db.beginTransactionNonExclusive()
				try {
					if (db.update(TABLE, newRow0, null, null) > 0) {
						ret = db.update(TABLE, newRow1, COMMON_PKEY, args)
						if (ret == 1) db.setTransactionSuccessful()
					}
				} finally {
					db.endTransaction()
				}

				return if (ret == 1)
					select(helper)
				else
					null
			}

			fun add(helper: DbHelper, record: VehicleRecord): VehicleRecord? {
				val now = Date().time
				val db = helper.writableDatabase
				val newRow0 = ContentValues().apply { put(COL_CURR, 0) }
				val newRow1 = ContentValues().apply {
					put(COMMON_NAME, record.name)
					put(COL_PARK, record.parking)
					put(COL_FLOOR, record.floor)
					put(COL_LOT, record.lot)
					put(COL_CURR, 1)
					put(COMMON_MODF, record.modified ?: now)
				}

				var result: VehicleRecord? = null
				db.beginTransactionNonExclusive()
				try {
					val count = DatabaseUtils.queryNumEntries(db, TABLE)
					if ((count == 0L) || db.update(TABLE, newRow0, null, null) > 0) {
						val ret = db.insertOrThrow(TABLE, null, newRow1)
						if (ret >= 0) {
							db.setTransactionSuccessful()
							result = VehicleRecord(ret, record.name, record.parking, record.floor, record.lot, true, record.modified ?: now)
						}
					}
				} finally {
					db.endTransaction()
				}
				return result
			}
		}
	}

	class Token : BaseColumns {
		companion object {
			private const val TABLE = "Token"
			private const val IDX_TOKEN = "idxToken"
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
			const val SQL_CREATE_IDX = "create unique index $IDX_TOKEN on $TABLE ($COMMON_NAME)"
			const val SQL_DROP = "drop table if exists $TABLE"
			const val SQL_DROP_IDX = "drop index if exists $IDX_TOKEN"
			private const val SQL_WHERE = "$COMMON_NAME = ?"

			fun select(helper: DbHelper): List<String> {
				val cursor = helper.readableDatabase
					.query(
						TABLE, COLUMNS, null, null, null, null,
						COMMON_MODF
					)

				val result = mutableListOf<String>()
				cursor.use {
					with(it) {
						while (moveToNext()) {
							val name = getString(getColumnIndexOrThrow(COMMON_NAME))
							result.add(name)
						}
					}
				}
				return result
			}

			fun insert(helper: DbHelper, token: String): Long {
				val newRow = ContentValues().apply {
					put(COMMON_NAME, token)
					put(COMMON_MODF, Date().time)
				}
				return helper.writableDatabase.insertOrThrow(TABLE, null, newRow)
			}

			fun delete(helper: DbHelper, token: String): Int {
				val args = arrayOf(token)
				return helper.writableDatabase.delete(TABLE, SQL_WHERE, args)
			}
		}
	}
}