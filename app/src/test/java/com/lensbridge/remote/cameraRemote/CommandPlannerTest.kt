package com.lensbridge.remote.cameraRemote

import com.lensbridge.remote.common.TriggerMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandPlannerTest {
    @Test fun mapsEveryTriggerToTheExpectedAndroidKeyEvent() {
        assertEquals("input keyevent 27", CommandPlanner.shutter(TriggerMethod.CAMERA_KEY))
        assertEquals("input keyevent 25", CommandPlanner.shutter(TriggerMethod.VOLUME_DOWN))
        assertEquals("input keyevent 24", CommandPlanner.shutter(TriggerMethod.VOLUME_UP))
    }

    @Test fun burstProducesExactlyTheRequestedCommandCount() {
        val commands = CommandPlanner.burst(TriggerMethod.CAMERA_KEY, 10)
        assertEquals(10, commands.size)
        assertEquals(List(10) { "input keyevent 27" }, commands)
    }

    @Test(expected = IllegalArgumentException::class)
    fun burstRejectsUnsafeCounts() {
        CommandPlanner.burst(TriggerMethod.CAMERA_KEY, 11)
    }
}
