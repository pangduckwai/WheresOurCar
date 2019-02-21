package org.sea9.android.woc.data

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.sea9.android.woc.R

class TokenAdaptor(private val caller: Caller): RecyclerView.Adapter<TokenAdaptor.ViewHolder>() {
	companion object {
		const val TAG = "woc.tokens"
	}

	private lateinit var recyclerView: RecyclerView

	private var cache: MutableList<String> = mutableListOf()

	/*=====================================================
	 * @see android.support.v7.widget.RecyclerView.Adapter
	 */
	override fun onAttachedToRecyclerView(recycler: RecyclerView) {
		super.onAttachedToRecyclerView(recycler)
		Log.d(TAG, "onAttachedToRecyclerView")
		recyclerView = recycler
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val item = LayoutInflater.from(parent.context).inflate(R.layout.settings_item, parent, false)
		return ViewHolder(item)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.token.text = cache[position]
	}

	override fun getItemCount(): Int {
		return cache.size
	}

	/*======================
	 * Data access methods.
	 */
	fun populateCache() {
		cache = DbContract.Token.select(caller.getDbHelper()!!) as MutableList<String>
	}

	fun clearCache() {
		cache.clear()
	}

	fun delete(position: Int): Int {
		var ret = -1
//		if ((position >= 0) && (position < shown.size)) {
//			ret = DbContract.Notes.delete(caller.getDbHelper()!!, shown[position].pid)
//			if (ret >= 0) {
//				if (position < selectedPos) {
//					selectedPos --
//				}
//			}
//		}
		return ret
	}

	/*=============
	 * View holder
	 */
	class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
		val token: TextView = view.findViewById(R.id.token)
	}

	/*=====================================
	 * Access interface to the MainContext
	 */
	interface Caller {
		fun getContext(): Context?
		fun getDbHelper(): DbHelper?
	}
}