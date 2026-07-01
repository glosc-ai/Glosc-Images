package com.glosc.images

import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.glosc.images.ui.AppScreen
import com.glosc.images.ui.MainViewModel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityScreenSizeTest {
    @Test
    fun onboardingRendersAcrossRepresentativeScreenSizes() {
        val screens = mapOf(
            "compact phone" to "w360dp-h640dp-port",
            "medium landscape" to "w673dp-h360dp-land",
            "expanded tablet" to "w840dp-h1100dp-port"
        )

        screens.forEach { (name, qualifiers) ->
            RuntimeEnvironment.setQualifiers(qualifiers)
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            try {
                val activity = controller.get()
                drainMainThread()

                assertHasText(activity, "配置引导")
                assertHasText(activity, "完成初始化并开始使用")
                assertHorizontallyContained(activity, name)
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    @Test
    fun selectedScreenSurvivesConfigurationRecreation() {
        RuntimeEnvironment.setQualifiers("w840dp-h1100dp-port")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

            waitFor("bootstrap default provider") { viewModel.providers.value.isNotEmpty() }
            activity.runOnUiThread { viewModel.open(AppScreen.Generate) }
            waitFor("studio screen") { renderedTexts(activity).any { it.contains("Generated Images") } }
            assertHorizontallyContained(activity, "expanded tablet before recreate")

            controller.recreate()
            val recreated = controller.get()
            waitFor("studio screen after recreate") {
                renderedTexts(recreated).any { it.contains("Generated Images") }
            }
            assertHorizontallyContained(recreated, "expanded tablet after recreate")
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun assertHasText(activity: MainActivity, expected: String) {
        assertTrue(
            "Expected to render text \"$expected\", but saw: ${renderedTexts(activity)}",
            renderedTexts(activity).any { it.contains(expected) }
        )
    }

    private fun assertHorizontallyContained(activity: MainActivity, screenName: String) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val config = activity.resources.configuration
        val density = activity.resources.displayMetrics.density
        val width = (config.screenWidthDp * density).roundToInt().coerceAtLeast(1)
        val height = (config.screenHeightDp * density).roundToInt().coerceAtLeast(1)

        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        content.layout(0, 0, width, height)

        assertTrue("$screenName should attach content", content.childCount > 0)
        assertChildrenFit(content, screenName)
    }

    private fun assertChildrenFit(parent: ViewGroup, screenName: String) {
        if (parent is HorizontalScrollView || parent.visibility == View.GONE) return

        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child.visibility == View.GONE) continue

            assertTrue(
                "$screenName horizontal overflow in ${describe(child)}: " +
                    "left=${child.left}, right=${child.right}, parentWidth=${parent.width}",
                child.left >= 0 && child.right <= parent.width
            )
            if (child is ViewGroup) assertChildrenFit(child, screenName)
        }
    }

    private fun renderedTexts(activity: MainActivity): List<String> {
        val texts = mutableListOf<String>()
        collectTexts(activity.findViewById(android.R.id.content), texts)
        return texts
    }

    private fun collectTexts(view: View, texts: MutableList<String>) {
        if (view is TextView) {
            view.text?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectTexts(view.getChildAt(index), texts)
            }
        }
    }

    private fun waitFor(label: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            drainMainThread()
            if (condition()) return
            Thread.sleep(25)
        }
        drainMainThread()
        assertTrue("Timed out waiting for $label", condition())
    }

    private fun drainMainThread() {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    private fun describe(view: View): String {
        val text = (view as? TextView)?.text?.toString()
        return if (text.isNullOrBlank()) {
            view.javaClass.simpleName
        } else {
            "${view.javaClass.simpleName}(\"${text.take(30)}\")"
        }
    }
}
