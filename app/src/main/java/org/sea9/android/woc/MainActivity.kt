package org.sea9.android.woc

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import kotlinx.android.synthetic.main.app_main.*

class MainActivity : AppCompatActivity() {
	companion object {
		const val TAG = "woc.main"
	}

	private lateinit var retainedContext: MainContext
	private lateinit var textParking: AutoCompleteTextView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "onCreate()")
		setContentView(R.layout.app_main)
		setSupportActionBar(toolbar)

		textParking = findViewById(R.id.parking)
		retainedContext = MainContext.getInstance(supportFragmentManager)

		fab.setOnClickListener { view ->
			Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
				.setAction("Action", null).show()
		}

		retainedContext.initializeParkingAdaptor(this)
	}

	override fun onResume() {
		super.onResume()
		Log.d(TAG, "onResume()")
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
}
