package com.cydoniancitizen.bingee.testutil

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Both the Activity and its dialogs need the requested locale; a composition-only override is insufficient. */
private val localeTag: String
    get() = (InstrumentationRegistry.getArguments().getString("uiLocale") ?: "en").also {
        require(it == "en" || it == "it") { "uiLocale must be en or it" }
    }

internal val localizedTestContext: Context
    get() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(localeTag)) }
        )
    }

class TestLocaleRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val manager = instrumentation.targetContext.getSystemService(LocaleManager::class.java)
            val previous = manager.applicationLocales
            val requested = LocaleList.forLanguageTags(localeTag)
            try {
                if (previous != requested) {
                    manager.applicationLocales = requested
                    instrumentation.waitForIdleSync()
                }
                base.evaluate()
            } finally {
                if (manager.applicationLocales != previous) {
                    manager.applicationLocales = previous
                    instrumentation.waitForIdleSync()
                }
            }
        }
    }
}
