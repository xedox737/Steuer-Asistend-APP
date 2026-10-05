package com.example.ui

import androidx.lifecycle.ViewModelProvider
import org.junit.rules.ExternalResource

/** Keep Robolectric's per-test Application and AndroidViewModels on the same context. */
class IsolatedAndroidApplicationRule : ExternalResource() {
    private val instance = ViewModelProvider.AndroidViewModelFactory::class.java
        .getDeclaredField("_instance").apply { isAccessible = true }
    private var previous: Any? = null

    override fun before() {
        previous = instance.get(null)
        instance.set(null, null)
    }

    override fun after() {
        instance.set(null, previous)
        previous = null
    }
}
