package org.sea9.android.woc.messaging

import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.app_main.*
import org.sea9.android.ui.MessageDialog
import org.sea9.android.woc.R
import java.lang.RuntimeException

class RequestActivity : AppCompatActivity(), RequestContext.Callback, MessageDialog.Callback {
	companion object {
		const val TAG = "woc.request"
		private const val EMPTY = ""
		const val MSG_DIALOG_IGNORE_SUBSCRIBE = 80001
		const val MSG_DIALOG_REFRESH_TOKEN = 80002
	}

	private lateinit var retainedContext: RequestContext
	private lateinit var textTitle: TextView
	private lateinit var textSubscriber: TextView
	private lateinit var textToken: TextView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.request_main)
		setSupportActionBar(toolbar)
		retainedContext = RequestContext.getInstance(supportFragmentManager)

		textTitle = findViewById(R.id.title)
		textSubscriber = findViewById(R.id.subscriber)
		textToken = findViewById(R.id.token)

		fab.setOnClickListener {
			val okay = ((retainedContext.status and 8) > 0)
			when {
				((retainedContext.status and 1) > 0) -> {
					if (okay) {
						retainedContext.unsubscribe()
					} else {
						doNotify(0, getString(R.string.msg_unsubscribe_notfound), false)
					}
				}
				((retainedContext.status and 2) > 0) -> {
					if (okay) {
						retainedContext.subscribe()
					} else {
						doNotify(0, getString(R.string.msg_subscribe_fail), false)
					}
				}
				else -> {
					Log.w(TAG, "Should not be able to click...")
				}
			}
		}

		retainedContext.handleIncomingIntent(intent)
	}

	override fun onProcessed(status: Int) {
		val okay = ((status and 8) > 0)
		textSubscriber.text = retainedContext.subscriber
		textToken.text = retainedContext.token

		when {
			((status and 1) > 0) -> {
				if (okay) {
					textTitle.text = getString(R.string.msg_unsubscribe_pending)
					fab.isEnabled = true
				} else {
					textTitle.text = getString(R.string.msg_unsubscribe_notfound)
					fab.isEnabled = false
				}
			}
			((status and 2) > 0) -> {
				if (okay) {
					textTitle.text = getString(R.string.msg_subscribe_okay)
					fab.isEnabled = true
				} else {
					textTitle.text = getString(R.string.msg_subscribe_fail)
					fab.isEnabled = false
				}
			}
			((status and 4) > 0) -> {
				if (okay) {
					MessageDialog.getOkayCancelDialog(MSG_DIALOG_REFRESH_TOKEN,
						getString(R.string.msg_refresh_token,
							retainedContext.subscriber,
							retainedContext.token,
							retainedContext.tokenNew), null)
						.show(supportFragmentManager, MessageDialog.TAG)
				} else {
					doNotify(0, getString(R.string.msg_unsubscribe_notfound), false)
					finish()
				}
			}
			else -> {
				throw RuntimeException("Error encountered when processing incoming requests")
			}
		}
	}

	override fun onRefreshed() {
		finish()
	}

	override fun doNotify(ref: Int, msg: String?, stay: Boolean) {
		if (stay || ((msg != null) && (msg.length > 70))) {
			MessageDialog.getInstance(ref, msg ?: EMPTY, null).show(supportFragmentManager, MessageDialog.TAG)
		} else {
			val obj = Toast.makeText(this, msg, Toast.LENGTH_LONG )
			obj.setGravity(Gravity.TOP, 0, 0)
			obj.show()
		}
	}

	override fun neutral(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?) {
		when(reference) {
			MSG_DIALOG_IGNORE_SUBSCRIBE -> finish()
		}
	}

	override fun positive(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?) {
		when(reference) {
			MSG_DIALOG_REFRESH_TOKEN -> {
				retainedContext.refreshToken()
			}
		}
	}

	override fun negative(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?) {
		when(reference) {
			MSG_DIALOG_REFRESH_TOKEN -> finish()
		}
	}
}