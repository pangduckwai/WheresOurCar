package org.sea9.android.woc

import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.*

class SettingsDialog : DialogFragment() {
	companion object {
		const val TAG = "woc.about"

		fun getInstance() : SettingsDialog {
			val instance = SettingsDialog()
			instance.isCancelable = false
			return instance
		}
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val view = inflater.inflate(R.layout.settings_dialog, container, false)

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
		return view
	}
}