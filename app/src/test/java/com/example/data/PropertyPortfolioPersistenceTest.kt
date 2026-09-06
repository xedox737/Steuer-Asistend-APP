package com.example.data

import androidx.room.Room
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PropertyPortfolioPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val databaseName = "property-portfolio-restart-test"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .allowMainThreadQueries()
        .build()

    @Test fun `existing property and newly created property coexist without migration`() = runTest {
        val dao = database.propertyDao()
        dao.insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "legacy", name = "Bestand"))
        val nextId = dao.nextPropertyId()
        dao.insertPropertyMetadata(PropertyMetadata(id = nextId, propertyId = "new-property", name = "Neues Objekt"))

        val stored = dao.getAllPropertiesFlow().first()
        assertEquals(listOf("legacy", "new-property"), stored.map { it.propertyId })
        assertEquals("Bestand", dao.getPropertyMetadata()?.name)
        assertEquals("Neues Objekt", dao.getPropertyByPropertyId("new-property")?.name)

        database.close()
        database = openDatabase()
        assertEquals(listOf("legacy", "new-property"), database.propertyDao().getAllProperties().map { it.propertyId })
    }
}
