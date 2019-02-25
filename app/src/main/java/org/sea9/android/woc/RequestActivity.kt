package org.sea9.android.woc

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.app_main.*
import java.lang.RuntimeException

class RequestActivity : AppCompatActivity(), RequestContext.Callback {
	companion object {
		const val TAG = "woc.request"
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
			val okay = ((retainedContext.status and 4) > 0)
			when {
				((retainedContext.status and 1) > 0) -> {
					if (okay) {
						// Unsubscribe
					} else {
						val obj = Toast.makeText(this, getString(R.string.msg_unsubscribe_fail), Toast.LENGTH_LONG )
						obj.setGravity(Gravity.TOP, 0, 0)
						obj.show()
					}
				}
				((retainedContext.status and 2) > 0) -> {
					if (okay) {
						// Subscribe
					} else {
						val obj = Toast.makeText(this, getString(R.string.msg_unsubscribe_fail), Toast.LENGTH_LONG )
						obj.setGravity(Gravity.TOP, 0, 0)
						obj.show()
					}
				}
				else -> {
					val obj = Toast.makeText(this, "", Toast.LENGTH_LONG )
					obj.setGravity(Gravity.TOP, 0, 0)
					obj.show()
				}
			}
		}

		retainedContext.handleIncomingIntent(intent)
	}

	override fun onProcessed(status: Int) {
		val okay = ((status and 4) > 0)
		textSubscriber.text = retainedContext.subscriber
		textToken.text = retainedContext.token

		when {
			((status and 1) > 0) -> {
				if (okay) {
					textTitle.text = getString(R.string.msg_unsubscribe_okay)
					fab.isEnabled = true
				} else {
					textTitle.text = getString(R.string.msg_unsubscribe_fail)
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
			else -> {
				throw RuntimeException("Error encountered when processing incoming requests")
			}
		}
	}
}