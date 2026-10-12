package com.example.ui

import androidx.room.Room
import com.example.data.AppDatabase
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment

/** Install before the Activity rule; close only after its ViewModels are cleared. */
class IsolatedAppDatabaseRule : ExternalResource() {
    private val singleton = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: AppDatabase? = null
    private var owned: AppDatabase? = null

    override fun before() {
        previous = singleton.get(null) as AppDatabase?
        // Clearing a shared database after Activity launch races its first queries
        // and retains the previous Robolectric Application's Room infrastructure.
        owned = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        singleton.set(null, owned)
    }

    override fun after() {
        singleton.set(null, previous)
        try {
            owned?.close()
        } finally {
            owned = null
            previous = null
        }
    }
}
