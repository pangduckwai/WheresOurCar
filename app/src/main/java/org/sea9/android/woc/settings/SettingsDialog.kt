package org.sea9.android.woc.settings

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.DialogFragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import org.sea9.android.woc.MainActivity
import org.sea9.android.woc.R
import org.sea9.android.woc.data.TokenAdaptor

class SettingsDialog : DialogFragment() {
	companion object {
		const val TAG = "woc.settings"
		private const val PATTERN = "^[a-zA-Z_0-9.-]+[@][a-zA-Z0-9.]+?[.][a-zA-Z]{2,}$"

		fun getInstance() : SettingsDialog {
			val instance = SettingsDialog()
			instance.isCancelable = false
			return instance
		}
	}

	private lateinit var radioAlone: RadioButton
	private lateinit var radioPublisher: RadioButton
	private lateinit var radioSubscriber: RadioButton
	private lateinit var textPublisher: TextView
	private lateinit var textSubscriber: TextView
	private lateinit var buttonEmail: ImageButton
	private lateinit var recycler: RecyclerView

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val layout = inflater.inflate(R.layout.settings_dialog, container, false)

		radioAlone = layout.findViewById(R.id.radio_alone)
		radioAlone.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				updateUi(SettingsManager.MODE.STANDALONE)
				callback?.getAdaptor()?.clearCache()
				callback?.getAdaptor()?.notifyDataSetChanged()
			}
		}

		radioPublisher = layout.findViewById(R.id.radio_publisher)
		radioPublisher.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				updateUi(SettingsManager.MODE.PUBLISHER)
				callback?.getAdaptor()?.populateCache()
				callback?.getAdaptor()?.notifyDataSetChanged()
			}
		}

		radioSubscriber = layout.findViewById(R.id.radio_subscriber)
		radioSubscriber.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				updateUi(SettingsManager.MODE.SUBSCRIBER)
				callback?.getAdaptor()?.clearCache()
				callback?.getAdaptor()?.notifyDataSetChanged()
			}
		}

		textPublisher = layout.findViewById(R.id.email_publisher)
		textPublisher.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_NEXT -> {
					callback?.getSettingsManager()?.publisherEmail = textPublisher.text.toString()
				}
			}
			false
		}
		textPublisher.setOnFocusChangeListener { view, hasFocus ->
			if (!hasFocus) {
				callback?.getSettingsManager()?.publisherEmail = textPublisher.text.toString()
				view.clearFocus()
			}
		}

		textSubscriber = layout.findViewById(R.id.email_subscriber)
		textSubscriber.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_NEXT -> {
					callback?.getSettingsManager()?.subscriberEmail = textSubscriber.text.toString()
				}
			}
			false
		}
		textSubscriber.setOnFocusChangeListener { view, hasFocus ->
			if (!hasFocus) {
				callback?.getSettingsManager()?.subscriberEmail = textSubscriber.text.toString()
				view.clearFocus()
			}
		}

		buttonEmail = layout.findViewById(R.id.subscribes)
		buttonEmail.setOnClickListener {
			val publisher = textPublisher.text
			val subscriber = textSubscriber.text
			val regex = PATTERN.toRegex()
			if (publisher.isBlank() || !(regex matches publisher)) {
				callback?.doNotify(getString(R.string.msg_pub_email_invalid))
				textPublisher.text = MainActivity.EMPTY
			} else if (subscriber.isBlank() || !(regex matches subscriber)) {
				callback?.doNotify(getString(R.string.msg_sub_email_invalid))
				textSubscriber.text = MainActivity.EMPTY
			} else {
				callback?.subscribes(SettingsManager.MODE.SUBSCRIBER, publisher.toString(), subscriber.toString())
				Handler().postDelayed({
					updateUi()
				}, 250)
			}
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
		updateUi(callback?.getSettingsManager()?.operationMode)

		recycler.adapter = callback?.getAdaptor()
		if (radioPublisher.isChecked)
			callback?.getAdaptor()?.populateCache()
	}

	private fun close() {
		callback?.getSettingsManager()?.publisherEmail = textPublisher.text.toString()
		callback?.getSettingsManager()?.subscriberEmail = textSubscriber.text.toString()

		callback?.onModeChanged(
			when {
				radioSubscriber.isChecked -> SettingsManager.MODE.SUBSCRIBER
				radioPublisher.isChecked -> SettingsManager.MODE.PUBLISHER
				else -> SettingsManager.MODE.STANDALONE
			}
		)
		callback?.onClose()
		dismiss()
	}

	/**
	 * status: 0 - stand alone, 1 - subscriber, 2 - publisher
	 */
	private fun updateUi(mode: SettingsManager.MODE?) {
		callback?.getSettingsManager()?.operationMode = mode ?: SettingsManager.MODE.STANDALONE
		updateUi()
	}
	private fun updateUi() {
		val tmp = callback?.getSettingsManager()
		if (tmp != null)
			Log.w(TAG, "[Dialog] Mode: ${tmp.operationMode}; Status: ${tmp.subscriptionStatus}; ID: ${tmp.publisherId ?: "[NULL]"}")

		val mode = callback?.getSettingsManager()?.operationMode
		val status = callback?.getSettingsManager()?.subscriptionStatus

		radioAlone.isChecked = (mode == SettingsManager.MODE.STANDALONE)
		radioSubscriber.isChecked = (mode == SettingsManager.MODE.SUBSCRIBER)
		radioPublisher.isChecked = (mode == SettingsManager.MODE.PUBLISHER)
		textPublisher.isEnabled = ((mode == SettingsManager.MODE.SUBSCRIBER) && (status == 0))
		textSubscriber.isEnabled = ((mode == SettingsManager.MODE.SUBSCRIBER) && (status == 0))
		buttonEmail.isEnabled = (mode == SettingsManager.MODE.SUBSCRIBER)

		if (mode == SettingsManager.MODE.SUBSCRIBER) {
			textPublisher.text = callback?.getSettingsManager()?.publisherEmail
			textSubscriber.text = callback?.getSettingsManager()?.subscriberEmail
		} else {
			textPublisher.text = MainActivity.EMPTY
			textSubscriber.text = MainActivity.EMPTY
		}

		when(status) {
			0 -> buttonEmail.setImageDrawable(activity?.getDrawable(R.drawable.icon_mail))
			1 -> buttonEmail.setImageDrawable(activity?.getDrawable(R.drawable.icon_clear))
			2 -> buttonEmail.setImageDrawable(activity?.getDrawable(R.drawable.icon_stop))
		}
	}

	/*========================================
	 * Callback interface to the MainActivity
	 */
	interface Callback {
		fun onClose()
		fun onModeChanged(mode: SettingsManager.MODE)
		fun subscribes(mode: SettingsManager.MODE, publisher: String, subscriber: String)
		fun getAdaptor(): TokenAdaptor
		fun getSettingsManager(): SettingsManager
		fun doNotify(msg: String?)
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