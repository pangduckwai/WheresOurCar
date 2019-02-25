package org.sea9.android.woc

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.util.Log
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper
import org.sea9.android.woc.data.TokenRecord
import java.lang.RuntimeException

class RequestContext: Fragment(), DbHelper.Caller {
	companion object {
		const val TAG = "woc.request_retained"
		private const val URL_TOKEN = "t"
		private const val URL_SUBSCRIBER = "c"
		private const val URL_SUBSCRIBE = "s"
		private const val URL_UNSUBSCRIBE = "u"

		fun getInstance(sfm: FragmentManager): RequestContext {
			var instance = sfm.findFragmentByTag(TAG) as RequestContext?
			if (instance == null) {
				instance = RequestContext()
				sfm.beginTransaction().add(instance, TAG).commit()
			}
			return instance
		}
	}

	lateinit var settingsManager: SettingsManager

	var isSubscribe = false
		private set
	var subscriber: String? = null
		private set
	var token: String? = null
		private set
	fun handleIncomingIntent(intent: Intent?) {
		subscriber = intent?.data?.getQueryParameter(URL_SUBSCRIBER)
		token = intent?.data?.getQueryParameter(URL_TOKEN)

		isSubscribe = when(intent?.data?.lastPathSegment) {
			URL_SUBSCRIBE -> true
			URL_UNSUBSCRIBE -> false
			else -> {
				throw RuntimeException("Invalid request")
			}
		}
	}

	var tokenList: List<String>? = null
	fun populate() {
		tokenList = DbContract.Token.select(dbHelper!!).map {
			it.token
		}.toList()
	}

	var status: Int = 0
		private set
	private fun process() {
		AsyncProcessTask(this).execute()
	}
	private class AsyncProcessTask(private val caller: RequestContext): AsyncTask<Void, Void, Int>() {
		override fun doInBackground(vararg params: Void?): Int {
			var result = 0
			if ((caller.tokenList != null) && caller.tokenList!!.isNotEmpty()) {
				result = if (caller.isSubscribe)
					2 //Is subscribe
				else
					1 //Is unsubscribe

				val found = caller.tokenList?.contains(caller.token) ?: false
				if ((caller.isSubscribe && !found) || (!caller.isSubscribe && found))
					result = result or 4
			}
			return result //000 - Okay|Subscribe|Unsubscribe
		}
		override fun onPostExecute(result: Int?) {
			caller.status = result ?: 0
			caller.activity?.runOnUiThread {
				caller.callback?.onProcessed(result ?: 0)
			}
		}
	}

	private var dbHelper: DbHelper? = null
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
			process()
		}
	}

	private fun initDb() {
		Log.d(TAG, "init db")
		AsyncDbInitTask(this).execute()
	}
	private class AsyncDbInitTask (private val caller: RequestContext): AsyncTask<Void, Void, Void>() {
		override fun doInBackground(vararg params: Void?): Void? {
			val helper = DbHelper(caller)
			caller.setDbHelper(helper)
			caller.populate()
			return null
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "onCreate()")
		retainInstance = true
		settingsManager = SettingsManager(context)
	}

	override fun onResume() {
		super.onResume()
		Log.d(TAG, "onResume()")

		if (!isDbReady()) {
			initDb()
		} else {
			populate()
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

	interface Callback {
		fun onProcessed(status: Int)
	}
	private var callback: Callback? = null

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		try {
			callback = context as Callback
		} catch (e: ClassCastException) {
			throw ClassCastException("$context missing implementation of RequestContext.Callback")
		}
	}

	override fun onDetach() {
		super.onDetach()
		callback = null
	}
}