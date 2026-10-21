package com.example.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Phase11AIsolationTest {
    @Test fun productionRejectedEvenWhenDebuggable() {
        assertEquals(Phase11AIsolation.PRODUCTION_ERROR,
            assertThrows(IllegalStateException::class.java) {
                Phase11AIsolation.requireTestPackage(Phase11AIsolation.PRODUCTION, true)
            }.message)
    }
    @Test fun unknownPackageRejected() {
        assertThrows(IllegalStateException::class.java) {
            Phase11AIsolation.requireTestPackage(Phase11AIsolation.EXPECTED + ".other", true)
        }
    }
    @Test fun nonDebugBuildRejected() {
        assertThrows(IllegalStateException::class.java) {
            Phase11AIsolation.requireTestPackage(Phase11AIsolation.EXPECTED, false)
        }
    }
    @Test fun isolatedDebugBuildAccepted() {
        Phase11AIsolation.requireTestPackage(Phase11AIsolation.EXPECTED, true)
    }
}
