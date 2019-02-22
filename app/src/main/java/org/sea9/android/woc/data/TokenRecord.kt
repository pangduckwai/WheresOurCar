package org.sea9.android.woc.data

data class TokenRecord(
	  var token: String
	, var subscriber: String
	, var modified: Long?
) {
	override fun equals(other: Any?): Boolean {
		val value = other as TokenRecord
		return (token.trim() == value.token.trim())
	}

	override fun hashCode(): Int {
		return token.hashCode()
	}
}