package org.sea9.android.woc.messaging

import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.*
import android.widget.Button
import android.widget.TextView
import org.sea9.android.woc.MainContext
import org.sea9.android.woc.R
import java.text.SimpleDateFormat
import java.util.*

class SubscribeDialog : DialogFragment() {
	companion object {
		const val TAG = "woc.subscribe"
		private const val KEY_TIME = "woc.subscribe.time"

		fun getInstance(publisherId: String, requestTime: Long) : SubscribeDialog {
			val instance = SubscribeDialog()
			instance.isCancelable = false

			val args = Bundle()
			args.putString(TAG, publisherId)
			args.putLong(KEY_TIME, requestTime)

			instance.arguments = args
			return instance
		}
	}

	private lateinit var textPublisher: TextView
	private lateinit var textTime: TextView
	private lateinit var buttonAccept: Button
	private lateinit var buttonReject: Button
	private lateinit var buttonCancel: Button

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val layout = inflater.inflate(R.layout.subscribe_dialog, container, false)

		textPublisher = layout.findViewById(R.id.publisher)
		textTime = layout.findViewById(R.id.subscribe_time)

		buttonAccept = layout.findViewById(R.id.accept)
		buttonAccept.setOnClickListener {
			callback?.onAccept()
			dismiss()
		}

		buttonReject = layout.findViewById(R.id.reject)
		buttonReject.setOnClickListener {
			callback?.onReject()
			dismiss()
		}

		buttonCancel = layout.findViewById(R.id.cancel)
		buttonCancel.setOnClickListener {
			dismiss()
		}

		val formatter = SimpleDateFormat(MainContext.PATTERN_DATE, Locale.getDefault())
		val args = arguments
		if (args != null) {
			textPublisher.text = args.getString(TAG)
			textTime.text = formatter.format(Date(args.getLong(KEY_TIME)))
		}

		dialog.setOnKeyListener { _, keyCode, event ->
			if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.action == KeyEvent.ACTION_UP)) {
				dismiss()
				true
			} else {
				false
			}
		}

		val win = dialog.window
		win?.requestFeature(Window.FEATURE_NO_TITLE)
		return layout
	}

	interface Callback {
		fun onAccept()
		fun onReject()
	}
	private var callback: Callback? = null

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		try {
			callback = context as Callback
		} catch (e: ClassCastException) {
			throw ClassCastException("$context missing implementation of SubscribeDialog.Callback")
		}
	}

	override fun onDetach() {
		super.onDetach()
		callback = null
	}
}