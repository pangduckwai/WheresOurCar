package org.sea9.android.woc.data

import org.json.JSONObject

data class VehicleRecord(
	  var rid: Long
	, var name: String
	, var parking: String
	, var floor: String?
	, var lot: String?
	, var current: Boolean
	, var modified: Long?
) {
	companion object {
		const val RID = "id"
		const val NAM = "name"
		const val PRK = "parking"
		const val FLR = "floor"
		const val LOT = "lot"
		const val CUR = "current"
		const val MOD = "modified"
		const val EMPTY = ""
	}

	constructor(json: JSONObject) : this(
		json.getLong(RID),
		json.getString(NAM),
		json.getString(PRK),
		json.optString(FLR),
		json.optString(LOT),
		json.getBoolean(CUR),
		json.optLong(MOD)
	)
	constructor(json: String) : this(JSONObject(json))
	constructor() : this(-1, EMPTY, EMPTY, null, null, false, null)

	override fun toString(): String {
		val result = JSONObject()
		result.put(RID, rid)
		result.put(NAM, name)
		result.put(PRK, parking)
		if (floor != null) result.put(FLR, floor)
		if (lot != null) result.put(LOT, lot)
		result.put(CUR, current)
		if (modified != null) result.put(MOD, modified!!)
		return result.toString()
	}

	override fun equals(other: Any?): Boolean {
		val value = other as VehicleRecord
		return (name.trim() == value.name.trim())
	}

	override fun hashCode(): Int {
		return name.hashCode()
	}
}