package com.lensbridge.remote.cameraRemote

import com.lensbridge.remote.common.TriggerMethod

object CommandPlanner {
    fun shutter(trigger: TriggerMethod) = trigger.command
    fun wake() = "input keyevent WAKEUP"

    fun burst(trigger: TriggerMethod, count: Int): List<String> {
        require(count in 1..10)
        return List(count) { trigger.command }
    }
}
