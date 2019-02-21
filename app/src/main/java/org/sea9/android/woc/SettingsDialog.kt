package org.sea9.android.woc

import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView

class SettingsDialog : DialogFragment() {
	companion object {
		const val TAG = "woc.about"

		fun getInstance(caller: MainContext) : SettingsDialog {
			val instance = SettingsDialog()
			instance.isCancelable = false
			instance.retainedContext = caller
			return instance
		}
	}

	private lateinit var retainedContext: MainContext
	private lateinit var radioAlone: RadioButton
	private lateinit var radioPublisher: RadioButton
	private lateinit var radioSubscriber: RadioButton
	private lateinit var textEmail: TextView
	private lateinit var buttonEmail: ImageButton
	private lateinit var recycler: RecyclerView

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val layout = inflater.inflate(R.layout.settings_dialog, container, false)

		radioAlone = layout.findViewById(R.id.radio_alone)
		radioAlone.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				radioPublisher.isChecked = false
				radioSubscriber.isChecked = false
				textEmail.isEnabled = false
				buttonEmail.isEnabled = false
				retainedContext.tokenAdaptor.clearCache()
				retainedContext.tokenAdaptor.notifyDataSetChanged()
			}
		}

		radioPublisher = layout.findViewById(R.id.radio_publisher)
		radioPublisher.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				radioAlone.isChecked = false
				radioSubscriber.isChecked = false
				textEmail.isEnabled = false
				buttonEmail.isEnabled = false
				retainedContext.tokenAdaptor.populateCache()
				retainedContext.tokenAdaptor.notifyDataSetChanged()
			}
		}

		radioSubscriber = layout.findViewById(R.id.radio_subscriber)
		radioSubscriber.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				radioAlone.isChecked = false
				radioPublisher.isChecked = false
				textEmail.isEnabled = true
				buttonEmail.isEnabled = true
				retainedContext.tokenAdaptor.clearCache()
				retainedContext.tokenAdaptor.notifyDataSetChanged()
			}
		}

		textEmail = layout.findViewById(R.id.text_email)

		buttonEmail = layout.findViewById(R.id.subscribes)
		buttonEmail.setOnClickListener {
			callback?.subscribes(textEmail.text.toString())
		}

		recycler = layout.findViewById(R.id.subscribers)
		recycler.setHasFixedSize(true)
		recycler.layoutManager = LinearLayoutManager(context)

		dialog.setOnKeyListener { _, keyCode, event ->
			if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.action == KeyEvent.ACTION_UP)) {
				close()
				true
			} else {
				false
			}
		}

		val win = dialog.window
		win?.requestFeature(Window.FEATURE_NO_TITLE)
		return layout
	}

	override fun onResume() {
		super.onResume()
		radioAlone.isChecked = !retainedContext.isPublisher() && !retainedContext.isSubscriber()
		radioPublisher.isChecked = retainedContext.isPublisher()
		radioSubscriber.isChecked = retainedContext.isSubscriber()
		recycler.adapter = retainedContext.tokenAdaptor

		if (radioPublisher.isChecked) retainedContext.tokenAdaptor.populateCache()
	}

	private fun close() {
		callback?.onSettingChanged(
			when {
				radioSubscriber.isChecked -> 1
				radioPublisher.isChecked -> 2
				else -> 0
			},
			if (radioSubscriber.isChecked)
				textEmail.text.toString()
			else
				null
		)
		dismiss()
	}

	/*========================================
	 * Callback interface to the MainActivity
	 */
	interface Callback {
		fun onSettingChanged(selection: Int, email: String?)
		fun subscribes(email: String?)
	}
	private var callback: Callback? = null

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		try {
			callback = context as Callback
		} catch (e: ClassCastException) {
			throw ClassCastException("$context missing implementation of SettingsDialog.Callback")
		}
	}

	override fun onDetach() {
		super.onDetach()
		callback = null
	}
}