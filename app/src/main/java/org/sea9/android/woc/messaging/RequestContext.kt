package org.sea9.android.woc.messaging

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.util.Log
import org.sea9.android.woc.R
import org.sea9.android.woc.RetainedContext
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper
import org.sea9.android.woc.settings.SettingsManager
import java.lang.RuntimeException

class RequestContext: Fragment(), RetainedContext, DbHelper.Caller {
	companion object {
		const val TAG = "woc.request_retained"
		private const val QUERY_PARAM_TOKEN = "t"
		private const val QUERY_PARAM_SUBSCRIBER = "c"
		private const val QUERY_PARAM_TOKEN_OLD = "o"
		private const val QUERY_PARAM_TOKEN_NEW = "N"
		private const val URL_SUBSCRIBE = "s"
		private const val URL_UNSUBSCRIBE = "u"
		private const val URL_REFRESH = "d"

		fun getInstance(sfm: FragmentManager): RequestContext {
			var instance = sfm.findFragmentByTag(TAG) as RequestContext?
			if (instance == null) {
				instance = RequestContext()
				sfm.beginTransaction().add(instance, TAG).commit()
			}
			return instance
		}
	}

	private lateinit var settingsManager: SettingsManager
	override fun getSettingsManager(): SettingsManager {
		return settingsManager
	}

	private lateinit var publishingUtils: PublishingUtils

	var requestMode = -1
		private set
	var subscriber: String? = null
		private set
	var token: String? = null
		private set
	var tokenNew: String? = null
		private set
	fun handleIncomingIntent(intent: Intent?) {
		requestMode = when(intent?.data?.lastPathSegment) {
			URL_SUBSCRIBE -> {
				subscriber = intent.data?.getQueryParameter(QUERY_PARAM_SUBSCRIBER)
				token = intent.data?.getQueryParameter(QUERY_PARAM_TOKEN)
				2
			}
			URL_UNSUBSCRIBE -> {
				token = intent.data?.getQueryParameter(QUERY_PARAM_TOKEN)
				1
			}
			URL_REFRESH -> {
				token = intent.data?.getQueryParameter(QUERY_PARAM_TOKEN_OLD)
				tokenNew = intent.data?.getQueryParameter(QUERY_PARAM_TOKEN_NEW)
				4
			}
			else -> {
				throw RuntimeException("Invalid request")
			}
		}
	}

	private var dbHelper: DbHelper? = null
	override fun getDbHelper(): DbHelper? {
		return dbHelper
	}
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

	var tokenList: List<String>? = null
	fun populate() {
		tokenList = DbContract.Token.select(dbHelper!!).map {
			it.token
		}.toList()
	}

	var status: Int = 0
		private set
	private fun process() {
		if (!settingsManager.isPublisher()) {
			callback?.doNotify(RequestActivity.MSG_DIALOG_IGNORE_SUBSCRIBE, getString(R.string.msg_not_a_publisher), true)
		} else if (requestMode == 8) {
		} else {
			AsyncProcessTask(this).execute()
		}
	}
	private class AsyncProcessTask(private val caller: RequestContext): AsyncTask<Void, Void, Int>() {
		override fun onPreExecute() {
			if (caller.tokenList == null) caller.populate() //Just in case...
		}
		override fun doInBackground(vararg params: Void?): Int {
			if (caller.tokenList != null) {
				if (caller.tokenList!!.isEmpty() || (!caller.tokenList!!.contains(caller.token))) {
					if (caller.requestMode == 2) //Empty db or token not found, therefore should be subscribing
						return caller.requestMode or 8
				} else { //'else' means non-empty db and token found, therefore should be un-subscribing or refreshing
					if (caller.requestMode == 1) {
						return caller.requestMode or 8
					} else if (caller.requestMode == 4) {
						caller.subscriber = caller.tokenList!![caller.tokenList!!.indexOf(caller.token)]
						return caller.requestMode or 8
					}
				}
			}
			return 0 //0000 - Okay|Refresh|Subscribe|Unsubscribe
		}
		override fun onPostExecute(result: Int?) {
			caller.status = result ?: 0
			caller.activity?.runOnUiThread {
				caller.callback?.onProcessed(result ?: 0)
			}
		}
	}

	fun subscribe() {
		if (token != null) {
			if (DbContract.Token.insert(dbHelper!!, subscriber!!, token!!) >= 0) {
				publishingUtils.publish(DbContract.Vehicle.select(dbHelper!!))
				callback?.doNotify(0, getString(R.string.msg_subscribe_succeed), false)
				activity?.finish()
				return
			}
		}
		callback?.doNotify(0, getString(R.string.msg_subscribe_failed), false)
	}

	fun unsubscribe() {
		if (token != null) {
			if (DbContract.Token.delete(dbHelper!!, token!!) == 1) {
				callback?.doNotify(0, getString(R.string.msg_unsubscribe_succeed), false)
				activity?.finish()
				return
			}
		}
		callback?.doNotify(0, getString(R.string.msg_unsubscribe_failed), false)
	}

	fun refreshToken() {
		if ((token != null) && (tokenNew != null)) {
			if (DbContract.Token.refresh(dbHelper!!, token!!, tokenNew!!))
				callback?.doNotify(0, getString(R.string.msg_refresh_succeed), false)
			else
				callback?.doNotify(0, getString(R.string.msg_refresh_failed), false)
		}
		callback?.onRefreshed()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "onCreate()")
		retainInstance = true
		settingsManager = SettingsManager(context)

		val projectId = getString(R.string.firebase_project_id)
		publishingUtils = PublishingUtils(this, projectId,
			getString(R.string.firebase_account_key),
			getString(R.string.firebase_scope_fcm),
			getString(R.string.firebase_endpoint_fcm, projectId),
			getString(R.string.firebase_succeed_fcm)
		)
	}

	override fun onResume() {
		super.onResume()
		Log.d(TAG, "onResume()")

		if (!isDbReady()) {
			initDb()
		} else {
			process()
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
		fun onRefreshed()
		fun doNotify(ref: Int, msg: String?, stay: Boolean)
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