package com.glosc.images

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @Before
    fun clearLocalState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("glosc-images.db")
        context.getSharedPreferences("secure_api_keys", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun onboardingSurvivesActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertOnboardingVisible()

            scenario.recreate()

            assertOnboardingVisible()
        }
    }

    private fun assertOnboardingVisible() {
        onView(withText(containsString("配置引导"))).check(matches(isDisplayed()))
        onView(withText(containsString("完成初始化并开始使用")))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }
}
