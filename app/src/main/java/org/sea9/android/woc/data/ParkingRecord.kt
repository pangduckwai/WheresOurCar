package org.sea9.android.woc.data

import org.json.JSONObject

data class ParkingRecord(
	  var rid: Long
	, var name: String
	, var modified: Long?
) {
	companion object {
		const val RID = "id"
		const val NAM = "name"
		const val MOD = "modified"
	}

	constructor(json: JSONObject) : this(
		json.getLong(RID),
		json.getString(NAM),
		json.optLong(MOD)
	)
	constructor(json: String) : this(JSONObject(json))

	override fun toString(): String {
		val result = JSONObject()
		result.put(RID, rid)
		result.put(NAM, name)
		if (modified != null) result.put(MOD, modified!!)
		return result.toString()
	}

	override fun equals(other: Any?): Boolean {
		val value = other as ParkingRecord
		return (name.trim() == value.name.trim())
	}

	override fun hashCode(): Int {
		return name.hashCode()
	}
}