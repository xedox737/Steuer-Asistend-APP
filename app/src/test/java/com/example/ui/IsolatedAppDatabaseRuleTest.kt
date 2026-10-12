package com.example.ui

import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.PropertyMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IsolatedAppDatabaseRuleTest {
    private val singleton = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }

    @Test fun eachCaseStartsEmptyAndRestoresThePreviousDatabaseWithoutChangingItsRows() {
        val application = RuntimeEnvironment.getApplication()
        val original = singleton.get(null)
        val previous = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        val history = PropertyMetadata(id = 1, propertyId = "previous", name = "Previous test")
        runBlocking(Dispatchers.IO) { previous.propertyDao().insertPropertyMetadata(history) }
        singleton.set(null, previous)
        val owned = mutableListOf<AppDatabase>()
        try {
            val rule = IsolatedAppDatabaseRule()
            repeat(2) { index ->
                rule.apply(object : Statement() {
                    override fun evaluate() {
                        val current = AppDatabase.getDatabase(application, CoroutineScope(Dispatchers.IO))
                        assertNotSame(previous, current)
                        assertTrue(owned.none { it === current })
                        owned += current
                        runBlocking(Dispatchers.IO) {
                            assertTrue(current.propertyDao().getAllProperties().isEmpty())
                            assertTrue(current.receiptDao().getAllReceiptsList().isEmpty())
                            val fixture = history.copy(propertyId = "case-$index")
                            current.propertyDao().insertPropertyMetadata(fixture)
                            assertEquals(listOf(fixture), current.propertyDao().getAllPropertiesFlow().first())
                        }
                    }
                }, Description.EMPTY).evaluate()
                assertSame(previous, singleton.get(null))
                assertFalse(owned.last().isOpen)
                runBlocking(Dispatchers.IO) {
                    assertEquals(listOf(history), previous.propertyDao().getAllProperties())
                }
            }
        } finally {
            singleton.set(null, original)
            previous.close()
            owned.forEach { it.close() }
        }
    }

    @Test fun failingCaseStillClosesItsDatabaseAndRestoresTheSingleton() {
        val original = singleton.get(null)
        var owned: AppDatabase? = null
        val failure = AssertionError("fixture failure")
        try {
            IsolatedAppDatabaseRule().apply(object : Statement() {
                override fun evaluate() {
                    owned = singleton.get(null) as AppDatabase
                    runBlocking(Dispatchers.IO) { owned!!.propertyDao().getAllProperties() }
                    throw failure
                }
            }, Description.EMPTY).evaluate()
            fail("The fixture failure must propagate")
        } catch (caught: AssertionError) {
            assertSame(failure, caught)
        }
        assertSame(original, singleton.get(null))
        assertFalse(owned!!.isOpen)
    }
}
