package org.sea9.android.woc

import android.content.Context
import android.database.SQLException
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.sea9.android.woc.data.DbContract
import org.sea9.android.woc.data.DbHelper

@RunWith(AndroidJUnit4::class)
class TokenInstrumentedTest {
	companion object {
		private lateinit var context: Context
		private lateinit var helper: DbHelper

		@BeforeClass
		@JvmStatic
		fun prepare() {
			context = InstrumentationRegistry.getTargetContext()
			helper = DbHelper(object : DbHelper.Caller {
				override fun getContext(): Context? {
					return context
				}

				override fun onReady() {
					Log.w("woc.itest", "DB test connection ready")
				}
			})

			val tokens = DbContract.Token.select(helper)
			if (tokens.isNotEmpty()) {
				Log.w("woc.itest", "${tokens.size} tokens already exists")
				helper.writableDatabase.execSQL("delete from ${DbContract.Token.TABLE}")
			}

			Log.w("woc.itest", "Adding new tokens...")
			DbContract.Token.insert(helper, "luke.chat@gmail.com", "00000000A_c:APA91bHGTYvlvV-Wycb6dekWBbkG1v05g2MLZv1CuI-Sr29a6End1y2XfYJ94Dn9b8g4eR2KFVjBxaaGh-bMMTPPIcw4ti5c-FYghs4oPq5SodLq1O7TOg6PsaKM3rLoOhlmTQxSKxNZ")
			DbContract.Token.insert(helper, "luke.warm@gmail.com", "00000001A_c:APA91bHGTYvlvV-Wycb6dekWBbkG1v05g2MLZv1CuI-Sr29a6End1y2XfYJ94Dn9b8g4eR2KFVjBxaaGh-bMMTPPIcw4ti5c-FYghs4oPq5SodLq1O7TOg6PsaKM3rLoOhlmTQxSKxNZ")
			DbContract.Token.insert(helper, "luke.chan@gmail.com", "efZuffH-A_c:APA91bHGTYvlvV-Wycb6dekWBbkG1v05g2MLZv1CuI-Sr29a6End1y2XfYJ94Dn9b8g4eR2KFVjBxaaGh-bMMTPPIcw4ti5c-FYghs4oPq5SodLq1O7TOg6PsaKM3rLoOhlmTQxSKxNZ")
			DbContract.Token.insert(helper, "luke.chen@gmail.com", "11111111A_c:APA91bHGTYvlvV-Wycb6dekWBbkG1v05g2MLZv1CuI-Sr29a6End1y2XfYJ94Dn9b8g4eR2KFVjBxaaGh-bMMTPPIcw4ti5c-FYghs4oPq5SodLq1O7TOg6PsaKM3rLoOhlmTQxSKxNZ")
			DbContract.Token.insert(helper, "luke.wong@gmail.com", "00000002A_c:APA91bHGTYvlvV-Wycb6dekWBbkG1v05g2MLZv1CuI-Sr29a6End1y2XfYJ94Dn9b8g4eR2KFVjBxaaGh-bMMTPPIcw4ti5c-FYghs4oPq5SodLq1O7TOg6PsaKM3rLoOhlmTQxSKxNZ")
			DbContract.Token.insert(helper, "luke.warm@gmail.com", "00000003A_c:APA91bHGTYvlvV-Wycb6dekWBbkG1v05g2MLZv1CuI-Sr29a6End1y2XfYJ94Dn9b8g4eR2KFVjBxaaGh-bMMTPPIcw4ti5c-FYghs4oPq5SodLq1O7TOg6PsaKM3rLoOhlmTQxSKxNZ")
		}
	}

	@Test(expected = SQLException::class)
	fun testUniqueIndex() {
		Log.w("woc.itest.testUniqueIndex", "Attempt to add duplicated token")
		DbContract.Token.insert(helper, "john.chen@gmail.com", "00000002A_c:APA91bHGTYvlvV-Wycb6dekWBbkG1v05g2MLZv1CuI-Sr29a6End1y2XfYJ94Dn9b8g4eR2KFVjBxaaGh-bMMTPPIcw4ti5c-FYghs4oPq5SodLq1O7TOg6PsaKM3rLoOhlmTQxSKxNZ")
	}

	@Test
	fun testDeleteToken() {
		Log.w("woc.itest.testDeleteToken", "Attempt to delete a token")
		assertTrue(
			DbContract.Token.delete(helper, "11111111A_c:APA91bHGTYvlvV-Wycb6dekWBbkG1v05g2MLZv1CuI-Sr29a6End1y2XfYJ94Dn9b8g4eR2KFVjBxaaGh-bMMTPPIcw4ti5c-FYghs4oPq5SodLq1O7TOg6PsaKM3rLoOhlmTQxSKxNZ")
			== 1
		)
	}
}