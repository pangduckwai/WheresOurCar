package org.sea9.android.woc

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import kotlinx.android.synthetic.main.app_main.*
import org.sea9.android.woc.data.VehicleRecord
import org.sea9.android.woc.ui.MessageDialog
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), MainContext.Callback, MessageDialog.Callback {
	companion object {
		const val TAG = "woc.main"

		const val MSG_DIALOG_NOTIFY  = 90001
		const val MSG_DIALOG_NEW_VEHICLE = 90002
	}

	private lateinit var retainedContext: MainContext
	private lateinit var textVehicle: AutoCompleteTextView
	private lateinit var textParking: AutoCompleteTextView
	private lateinit var textFloor: EditText
	private lateinit var textLot: EditText
	private lateinit var textUpdate: TextView

	@SuppressLint("ClickableViewAccessibility")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "onCreate()")
		setContentView(R.layout.app_main)
		setSupportActionBar(toolbar)
		retainedContext = MainContext.getInstance(supportFragmentManager)
		retainedContext.initializeAdaptors(this)

		textVehicle = findViewById(R.id.vehicle)
		textVehicle.setOnTouchListener { view, event ->
			// Show the drop down list whenever the view is touched
			when (event?.action) {
				MotionEvent.ACTION_DOWN -> {
					if (textVehicle.isFocusable) {
						textVehicle.showDropDown()
					}
				}
				MotionEvent.ACTION_UP -> view?.performClick()
			}
			false
		}
		textVehicle.setOnFocusChangeListener { view, hasFocus ->
			// Show the drop down list whenever the view gain focus
			if (hasFocus) {
				(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
				view.postDelayed({
					textVehicle.showDropDown()
				}, 100)
			} else {
				Log.w(TAG, "Vehicle lose focus ${textVehicle.text}")
				retainedContext.switchVehicle(textVehicle.text.toString())
				view.clearFocus()
			}
		}
		textVehicle.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_NEXT -> retainedContext.switchVehicle(textVehicle.text.toString())
			}
			false
		}
		textVehicle.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
			retainedContext.switchVehicle(textVehicle.text.toString())
		}

		textParking = findViewById(R.id.parking)
		textParking.setOnTouchListener { view, event ->
			// show the drop down list whenever the car park location textbox is touched
			when (event?.action) {
				MotionEvent.ACTION_DOWN -> {
					if (textParking.isFocusable) {
						textParking.showDropDown()
					}
				}
				MotionEvent.ACTION_UP -> view?.performClick()
			}
			false
		}
		textParking.setOnFocusChangeListener { view, hasFocus ->
			// Show the drop down list whenever the view gain focus
			if (hasFocus) {
				(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
				view.postDelayed({
					textParking.showDropDown()
				}, 100)
			} else {
				Log.w(TAG, "Parking lose focus ${textParking.text}")
				retainedContext.updateParking(textParking.text.toString())
				view.clearFocus()
			}
		}
		textParking.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_NEXT -> retainedContext.updateParking(textParking.text.toString())
			}
			false
		}
		textParking.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
			retainedContext.updateParking(textParking.text.toString())
		}

		textFloor = findViewById(R.id.floor)
		textFloor.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_NEXT -> {
					retainedContext.updateFloor(textFloor.text.toString())
				}
			}
			false
		}
		textFloor.setOnFocusChangeListener { view, hasFocus ->
			if (!hasFocus) {
				Log.w(TAG, "Floor lose focus ${textFloor.text}")
				retainedContext.updateFloor(textFloor.text.toString())
				view.clearFocus()
			}
		}

		textLot = findViewById(R.id.lot)
		textLot.setOnEditorActionListener { view, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_DONE -> {
					retainedContext.updateLot(textLot.text.toString())
					view?.clearFocus()
					(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
				}
			}
			false
		}
		textLot.setOnFocusChangeListener { view, hasFocus ->
			if (!hasFocus) {
				Log.w(TAG, "Lot lose focus ${textLot.text}")
				retainedContext.updateLot(textLot.text.toString())
				view.clearFocus()
			}
		}

		textUpdate = findViewById(R.id.update)

		fab.setOnClickListener { view ->
			val veh = textVehicle.text
			val prk = textParking.text
			val flr = textFloor.text
			val lot = textLot.text
			Snackbar.make(view, "Vehicle $veh in $prk on no. $lot of the $flr floor (${(retainedContext.isUpdated)})"
				, Snackbar.LENGTH_LONG).setAction("Action", null).show()
		}
	}

	override fun onResume() {
		super.onResume()
		Log.d(TAG, "onResume()")
		textVehicle.setAdapter(retainedContext.vehicleAdaptor)
		textParking.setAdapter(retainedContext.parkingAdaptor)
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		// Inflate the menu; this adds items to the action bar if it is present.
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		return when (item.itemId) {
			R.id.action_settings -> true
			else -> super.onOptionsItemSelected(item)
		}
	}

	/*======================================================
	 * Common implementation of several Callback interfaces
	 */
	override fun doNotify(msg: String) {
		doNotify(msg, false)
	}
	override fun doNotify(msg: String, stay: Boolean) {
		doNotify(MSG_DIALOG_NOTIFY, msg, stay)
	}
	override fun doNotify(ref: Int, msg: String, stay: Boolean) {
		if (stay || (msg.length > 70)) {
			MessageDialog.getInstance(ref, msg, null).show(supportFragmentManager, MessageDialog.TAG)
		} else {
			Snackbar.make(fab, msg, Snackbar.LENGTH_LONG).show()
		}
	}
	//======================================================

	/*================================================
	 * @see org.sea9.android.woc.MainContext.Callback
	 */
	override fun onPopulated(data: VehicleRecord?, clearFocus: Boolean) {
		if (clearFocus) {
			var view = currentFocus
			if (view == null) view = View(this)
			view.postDelayed({
				Log.w(TAG, "Clearing focus!!!")
				(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
				view.clearFocus()
			}, 100)
		}

		val formatter = SimpleDateFormat(MainContext.PATTERN_DATE, Locale.getDefault())
		textVehicle.setText(data?.name)
		textParking.setText(data?.parking)
		textFloor.setText(data?.floor)
		textLot.setText(data?.lot)
		textUpdate.text = (formatter.format(
			if ((data != null) && (data.modified != null))
				Date(data.modified!!)
			else
				Date())
		)
	}

	override fun onNewVehicle(vehicle: String) {
		if (!dialogShowing) {
			val bundle = Bundle()
			bundle.putString(MainContext.TAG, vehicle)
			MessageDialog.getOkayCancelDialog(MSG_DIALOG_NEW_VEHICLE, "Add new vehicle '$vehicle'?", bundle)
				.show(supportFragmentManager, MessageDialog.TAG)
			dialogShowing = true
		}
	}
	//================================================

	/*=====================================================
	 * @see org.sea9.android.woc.ui.MessageDialog.Callback
	 */
	private var dialogShowing = false
	override fun neutral(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?) {
		when (reference) {
			MSG_DIALOG_NOTIFY -> {
				dialogShowing = false
				dialog?.dismiss()
			}
		}
	}

	override fun positive(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?) {
		when (reference) {
			MSG_DIALOG_NEW_VEHICLE -> {
				Log.w(TAG, "Adding new vehcile '${bundle?.getString(MainContext.TAG)}'")
			}
		}
		dialogShowing = false
		dialog?.dismiss()
	}

	override fun negative(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?) {
		dialogShowing = false
		dialog?.dismiss()
	}
}