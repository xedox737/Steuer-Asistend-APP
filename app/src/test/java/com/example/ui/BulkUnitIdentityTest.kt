package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkUnitIdentityTest {
    @Test fun twentyFiveUnitsRetainDistinctStableIdsIndependentOfDisplayNames() {
        val propertyId = "bulk-test-property"
        val units = (1..25).map { index ->
            WohneinheitStatus(
                name = "WE ${index.toString().padStart(2, '0')}",
                label = if (index % 2 == 0) "rechts" else "links",
                status = "Leerstand",
                mieter = "",
                kaltmiete = 0.0,
                wohnflaeche = if (index % 2 == 0) 68.0 else 72.5,
                unitId = java.util.UUID.nameUUIDFromBytes("test-$index".toByteArray()).toString()
            )
        }
        val ids = units.map { PropertyUnitScopedData.stableUnitId(propertyId, it) }
        assertEquals(25, units.size)
        assertEquals(25, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(72.5, units[0].wohnflaeche, 0.001)
        assertEquals(68.0, units[1].wohnflaeche, 0.001)
        assertEquals(ids[1], PropertyUnitScopedData.stableUnitId(propertyId, units[1].copy(name = "Andere Anzeige")))
        assertNotEquals(ids[0], ids[1])
    }
}
