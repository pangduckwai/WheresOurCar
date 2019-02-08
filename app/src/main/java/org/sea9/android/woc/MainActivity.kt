package org.sea9.android.woc

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import kotlinx.android.synthetic.main.app_main.*
import org.sea9.android.woc.data.VehicleRecord
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), MainContext.Callback {
	companion object {
		const val TAG = "woc.main"
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
					if (textVehicle.isFocusable) textVehicle.showDropDown()
				}
				MotionEvent.ACTION_UP -> {
					view?.performClick()
				}
			}
			false
		}
		textVehicle.setOnFocusChangeListener { view, hasFocus ->
			// Show the drop down list whenever the view gain focus
			if (hasFocus) {
				(this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
				view.post {
					textVehicle.showDropDown()
				}
			} else {
				view.clearFocus()
			}
		}
		textVehicle.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_NEXT -> {
					retainedContext.switchVehicle(textVehicle.text.toString())
					false
				}
				else -> {
					false
				}
			}
		}
		textVehicle.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
			retainedContext.switchVehicle(textVehicle.text.toString())
		}

		textParking = findViewById(R.id.parking)
		textParking.setOnTouchListener { view, event ->
			when (event?.action) {
				MotionEvent.ACTION_DOWN -> {
					if (textParking.isFocusable) textParking.showDropDown() // show the drop down list whenever the car park location textbox is touched
				}
				MotionEvent.ACTION_UP -> {
					view?.performClick()
				}
			}
			false
		}
		textParking.setOnFocusChangeListener { view, hasFocus ->
			// Show the drop down list whenever the view gain focus
			if (hasFocus) {
				textParking.showDropDown()
			} else {
				view.clearFocus()
			}
		}
		textParking.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_NEXT -> {
					retainedContext.updateParking(textParking.text.toString())
					false
				}
				else -> {
					false
				}
			}
		}

		textFloor = findViewById(R.id.floor)
		textFloor.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_NEXT -> {
					retainedContext.updateFloor(textFloor.text.toString())
					false
				}
				else -> {
					false
				}
			}
		}

		textLot = findViewById(R.id.lot)
		textLot.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_DONE -> {
					retainedContext.updateLot(textLot.text.toString())
//					view?.clearFocus()
//					(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
					false
				}
				else ->
					false
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
	fun doNotify(msg: String) {
		Snackbar.make(fab, msg, Snackbar.LENGTH_LONG).show()
	}
	//======================================================

	/*===================================================
	 * @see org.sea9.android.woc.MainContext.Callback
	 */
	override fun onPopulated(data: VehicleRecord?) {
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

	override fun onUpdated() {
		val formatter = SimpleDateFormat(MainContext.PATTERN_DATE, Locale.getDefault())
		textUpdate.text = (formatter.format(Date()))
	}
}
