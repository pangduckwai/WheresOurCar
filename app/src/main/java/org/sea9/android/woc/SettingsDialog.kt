package org.sea9.android.woc

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.DialogFragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import org.sea9.android.woc.data.TokenAdaptor

class SettingsDialog : DialogFragment() {
	companion object {
		const val TAG = "woc.about"
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
	private lateinit var textEmail: TextView
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

		textEmail = layout.findViewById(R.id.text_email)
		textEmail.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_NEXT -> {
					callback?.getSettingsManager()?.publisherEmail = textEmail.text.toString()
				}
			}
			false
		}
		textEmail.setOnFocusChangeListener { view, hasFocus ->
			if (!hasFocus) {
				callback?.getSettingsManager()?.publisherEmail = textEmail.text.toString()
				view.clearFocus()
			}
		}

		textSubscriber = layout.findViewById(R.id.text_name)
		textSubscriber.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_NEXT -> {
					callback?.getSettingsManager()?.subscriberName = textSubscriber.text.toString()
				}
			}
			false
		}
		textSubscriber.setOnFocusChangeListener { view, hasFocus ->
			if (!hasFocus) {
				callback?.getSettingsManager()?.subscriberName = textSubscriber.text.toString()
				view.clearFocus()
			}
		}

		buttonEmail = layout.findViewById(R.id.subscribes)
		buttonEmail.setOnClickListener {
			val email = textEmail.text
			val name = textSubscriber.text
			val regex = PATTERN.toRegex()
			if (email.isNotBlank() && (regex matches email)) {
				callback?.subscribes(SettingsManager.MODE.SUBSCRIBER, email.toString(), name?.toString())
				Handler().postDelayed(
					Runnable {
						updateUi()
					}, 250
				)
			} else {
				callback?.doNotify(getString(R.string.msg_pub_email_invalid))
				textEmail.text = MainActivity.EMPTY
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
		callback?.getSettingsManager()?.publisherEmail = textEmail.text.toString()
		callback?.getSettingsManager()?.subscriberName = textSubscriber.text.toString()

		callback?.onModeChanged(
			when {
				radioSubscriber.isChecked -> SettingsManager.MODE.SUBSCRIBER
				radioPublisher.isChecked -> SettingsManager.MODE.PUBLISHER
				else -> SettingsManager.MODE.STANDALONE
			}
		)
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
		val mode = callback?.getSettingsManager()?.operationMode
		val status = callback?.getSettingsManager()?.subscriptionStatus

		radioAlone.isChecked = (mode == SettingsManager.MODE.STANDALONE)
		radioSubscriber.isChecked = (mode == SettingsManager.MODE.SUBSCRIBER)
		radioPublisher.isChecked = (mode == SettingsManager.MODE.PUBLISHER)
		textEmail.isEnabled = ((mode == SettingsManager.MODE.SUBSCRIBER) && (status == 0))
		textSubscriber.isEnabled = ((mode == SettingsManager.MODE.SUBSCRIBER) && (status == 0))
		buttonEmail.isEnabled = (mode == SettingsManager.MODE.SUBSCRIBER)

		if (mode == SettingsManager.MODE.SUBSCRIBER) {
			textEmail.text = callback?.getSettingsManager()?.publisherEmail
			textSubscriber.text = callback?.getSettingsManager()?.subscriberName
		} else {
			textEmail.text = MainActivity.EMPTY
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
		fun onModeChanged(mode: SettingsManager.MODE)
		fun subscribes(mode: SettingsManager.MODE, email: String?, subscriber: String?)
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