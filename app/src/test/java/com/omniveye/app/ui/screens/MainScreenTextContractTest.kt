package com.omniveye.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenTextContractTest {

    @Test
    fun exposesOnlyMainActionLabelsForRealMode() {
        assertEquals("避障", MAIN_ACTION_OBSTACLE_AVOIDANCE)
        assertEquals("查看周围环境", MAIN_ACTION_SURROUNDINGS)
    }
}
