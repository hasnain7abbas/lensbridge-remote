package com.lensbridge.remote.common

enum class ConnectionState { DISCONNECTED, PAIRING, PAIRED, CONNECTING, CONNECTED, ERROR }

enum class TriggerMethod(val label: String, val command: String) {
    CAMERA_KEY("Camera key", "input keyevent 27"),
    VOLUME_DOWN("Volume down", "input keyevent 25"),
    VOLUME_UP("Volume up", "input keyevent 24")
}

enum class PreviewProfile(val label: String, val maxSize: Int, val fps: Int, val bitrate: Int) {
    LOW("Low", 720, 15, 1_000_000),
    BALANCED("Balanced", 1024, 24, 2_000_000),
    SMOOTH("Smooth", 1024, 30, 3_000_000)
}

data class SavedDevice(
    val name: String,
    val host: String,
    val connectPort: Int,
    val triggerMethod: TriggerMethod = TriggerMethod.CAMERA_KEY
)

data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    val batteryPercent: Int?
)

sealed interface PreviewState {
    data object Off : PreviewState
    data object Starting : PreviewState
    data class Streaming(val width: Int, val height: Int) : PreviewState
    data class Failed(val message: String) : PreviewState
}

data class RemoteUiState(
    val onboardingStep: Int = 0,
    val onboardingComplete: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val savedDevice: SavedDevice? = null,
    val deviceInfo: DeviceInfo? = null,
    val previewState: PreviewState = PreviewState.Off,
    val shutterOnly: Boolean = false,
    val timerSeconds: Int = 0,
    val timerRemaining: Int? = null,
    val burstCount: Int = 3,
    val burstRunning: Boolean = false,
    val previewProfile: PreviewProfile = PreviewProfile.BALANCED,
    val gridEnabled: Boolean = false,
    val keepAwake: Boolean = true,
    val message: String? = null,
    val error: String? = null
)
