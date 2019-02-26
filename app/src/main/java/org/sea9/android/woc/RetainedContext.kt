package org.sea9.android.woc

import android.content.Context
import org.sea9.android.woc.data.DbHelper

interface RetainedContext {
	fun getContext(): Context?

	fun getSettingsManager(): SettingsManager

	fun getDbHelper(): DbHelper?
}