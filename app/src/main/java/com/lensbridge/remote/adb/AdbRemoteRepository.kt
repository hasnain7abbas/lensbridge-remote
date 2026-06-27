package com.lensbridge.remote.adb

import com.flyfishxu.kadb.Kadb
import com.lensbridge.remote.cameraRemote.CommandPlanner
import com.lensbridge.remote.common.DeviceInfo
import com.lensbridge.remote.common.SavedDevice
import com.lensbridge.remote.common.TriggerMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AdbRemoteRepository {
    private val operationMutex = Mutex()
    @Volatile private var client: Kadb? = null

    suspend fun pair(host: String, pairingPort: Int, code: String) = withContext(Dispatchers.IO) {
        require(EndpointValidator.hostError(host) == null)
        require(pairingPort in 1..65535)
        require(EndpointValidator.pairingCodeError(code) == null)
        Kadb.pair(host.trim(), pairingPort, code, "LensBridge Remote")
    }

    suspend fun connect(device: SavedDevice): DeviceInfo = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            client?.close()
            val next = Kadb.create(device.host, device.connectPort, connectTimeout = 8_000, socketTimeout = 12_000)
            try {
                val model = next.shell("getprop ro.product.model").allOutput.trim().ifBlank { device.name }
                val version = next.shell("getprop ro.build.version.release").allOutput.trim().ifBlank { "Unknown" }
                val battery = next.shell("dumpsys battery | grep level").allOutput
                    .substringAfter(':', "").trim().toIntOrNull()
                client = next
                DeviceInfo(model, version, battery)
            } catch (error: Throwable) {
                next.close()
                throw error
            }
        }
    }

    suspend fun sendShutter(trigger: TriggerMethod) = shell(CommandPlanner.shutter(trigger))
    suspend fun sendVideo(trigger: TriggerMethod) = shell(CommandPlanner.shutter(trigger))
    suspend fun wake() = shell(CommandPlanner.wake())

    suspend fun sendBurst(trigger: TriggerMethod, count: Int, intervalMs: Long = 850L) {
        CommandPlanner.burst(trigger, count).forEachIndexed { index, command ->
            shell(command)
            if (index < count - 1) delay(intervalMs)
        }
    }

    suspend fun shell(command: String): String = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val active = client ?: error("Connect to the Samsung phone first.")
            val response = active.shell(command)
            if (response.exitCode != 0) error(response.errorOutput.ifBlank { "Command failed." })
            response.allOutput
        }
    }

    fun activeClient(): Kadb = client ?: error("Connect to the Samsung phone first.")

    fun disconnect() {
        client?.close()
        client = null
    }
}
