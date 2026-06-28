package com.lensbridge.remote

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lensbridge.remote.adb.AdbRemoteRepository
import com.lensbridge.remote.adb.DeviceStore
import com.lensbridge.remote.common.ConnectionState
import com.lensbridge.remote.common.PreviewProfile
import com.lensbridge.remote.common.PreviewState
import com.lensbridge.remote.common.RemoteUiState
import com.lensbridge.remote.common.SavedDevice
import com.lensbridge.remote.common.TriggerMethod
import com.lensbridge.remote.mirror.ScrcpyMirrorSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AdbRemoteRepository()
    private val store = DeviceStore(application)
    private val mirror = ScrcpyMirrorSession(application, viewModelScope) { preview ->
        _uiState.update { current ->
            current.copy(
                previewState = preview,
                shutterOnly = current.shutterOnly
            )
        }
    }
    private var timerJob: Job? = null
    private var attachedSurface: Surface? = null

    private val _uiState = MutableStateFlow(
        RemoteUiState(
            onboardingComplete = store.onboardingComplete(),
            savedDevice = store.loadDevice(),
            previewProfile = store.previewProfile()
        )
    )
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    fun nextOnboardingStep() = _uiState.update { it.copy(onboardingStep = (it.onboardingStep + 1).coerceAtMost(5)) }
    fun previousOnboardingStep() = _uiState.update { it.copy(onboardingStep = (it.onboardingStep - 1).coerceAtLeast(0)) }

    fun pair(host: String, pairingPort: Int, code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = ConnectionState.PAIRING, error = null, message = null) }
            runCatching { repository.pair(host, pairingPort, code) }
                .onSuccess {
                    _uiState.update { it.copy(connectionState = ConnectionState.PAIRED, message = "Paired. Now use the connection port shown on the Wireless debugging screen.") }
                    nextOnboardingStep()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(connectionState = ConnectionState.ERROR, error = pairingMessage(error))
                    }
                }
        }
    }

    fun connect(host: String, connectPort: Int) {
        val existing = _uiState.value.savedDevice
        val requested = SavedDevice(
            name = existing?.name ?: "Samsung phone",
            host = host.trim(),
            connectPort = connectPort,
            triggerMethod = existing?.triggerMethod ?: TriggerMethod.CAMERA_KEY
        )
        connect(requested, advanceOnboarding = !_uiState.value.onboardingComplete)
    }

    fun reconnect() {
        val device = _uiState.value.savedDevice ?: run {
            _uiState.update { it.copy(onboardingComplete = false, onboardingStep = 2) }
            return
        }
        connect(device, advanceOnboarding = false)
    }

    private fun connect(device: SavedDevice, advanceOnboarding: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING, error = null, message = "Opening a local ADB session…") }
            runCatching { repository.connect(device) }
                .onSuccess { info ->
                    val saved = device.copy(name = info.model)
                    store.saveDevice(saved)
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.CONNECTED,
                            savedDevice = saved,
                            deviceInfo = info,
                            message = "Connected to ${info.model}"
                        )
                    }
                    if (advanceOnboarding) nextOnboardingStep()
                    startPreviewIfReady()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(connectionState = ConnectionState.ERROR, error = connectionMessage(error)) }
                }
        }
    }

    fun finishOnboarding() {
        store.setOnboardingComplete(true)
        _uiState.update { it.copy(onboardingComplete = true, message = "Remote ready") }
        startPreviewIfReady()
    }

    fun attachPreviewSurface(surface: Surface) {
        attachedSurface = surface
        startPreviewIfReady()
    }

    fun detachPreviewSurface(surface: Surface) {
        if (attachedSurface == surface) {
            attachedSurface = null
            mirror.stop()
        }
    }

    fun toggleShutterOnly() {
        val next = !_uiState.value.shutterOnly
        _uiState.update { it.copy(shutterOnly = next, previewState = if (next) PreviewState.Off else it.previewState) }
        if (next) mirror.stop() else startPreviewIfReady()
    }

    fun restartPreview() {
        _uiState.update { it.copy(shutterOnly = false, error = null) }
        startPreviewIfReady(force = true)
    }

    fun sendPhoto() {
        val timer = _uiState.value.timerSeconds
        if (timer > 0) startTimer(timer) { executePhoto() } else executePhoto()
    }

    fun sendVideo() = runCommand("Recording command sent") {
        repository.sendVideo(currentTrigger())
    }

    fun startBurst() {
        if (_uiState.value.timerRemaining != null || _uiState.value.burstRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(burstRunning = true, message = "Burst started") }
            runCatching { repository.sendBurst(currentTrigger(), _uiState.value.burstCount) }
                .onSuccess { _uiState.update { it.copy(burstRunning = false, message = "${it.burstCount}-shot burst sent") } }
                .onFailure { error -> _uiState.update { it.copy(burstRunning = false, error = commandMessage(error)) } }
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(timerRemaining = null, message = "Timer cancelled") }
    }

    fun setTimer(seconds: Int) = _uiState.update { it.copy(timerSeconds = seconds) }
    fun setBurst(count: Int) = _uiState.update { it.copy(burstCount = count) }
    fun setGrid(enabled: Boolean) = _uiState.update { it.copy(gridEnabled = enabled) }
    fun setKeepAwake(enabled: Boolean) = _uiState.update { it.copy(keepAwake = enabled) }

    fun setTrigger(method: TriggerMethod) {
        val saved = _uiState.value.savedDevice?.copy(triggerMethod = method)
        if (saved != null) store.saveDevice(saved)
        _uiState.update { it.copy(savedDevice = saved ?: it.savedDevice, message = "Trigger set to ${method.label}") }
    }

    fun setPreviewProfile(profile: PreviewProfile) {
        store.savePreviewProfile(profile)
        _uiState.update { it.copy(previewProfile = profile) }
        if (!_uiState.value.shutterOnly) startPreviewIfReady(force = true)
    }

    fun disconnect() {
        timerJob?.cancel()
        mirror.stop()
        repository.disconnect()
        _uiState.update {
            it.copy(connectionState = ConnectionState.DISCONNECTED, deviceInfo = null, previewState = PreviewState.Off, message = "Disconnected")
        }
    }

    fun forgetDevice() {
        disconnect()
        store.forgetDevice()
        store.setOnboardingComplete(false)
        _uiState.update { RemoteUiState(onboardingStep = 0, previewProfile = it.previewProfile) }
    }

    fun clearNotice() = _uiState.update { it.copy(message = null, error = null) }

    private fun executePhoto() = runCommand("Shot command sent") { repository.sendShutter(currentTrigger()) }

    private fun startTimer(seconds: Int, action: () -> Unit) {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            try {
                for (remaining in seconds downTo 1) {
                    _uiState.update { it.copy(timerRemaining = remaining, message = "Timer running: $remaining") }
                    delay(1_000)
                }
                _uiState.update { it.copy(timerRemaining = null) }
                action()
            } catch (_: CancellationException) {
                _uiState.update { it.copy(timerRemaining = null) }
            }
        }
    }

    private fun runCommand(successMessage: String, command: suspend () -> Unit) {
        if (_uiState.value.connectionState != ConnectionState.CONNECTED) {
            _uiState.update { it.copy(error = "Connect to the Samsung phone first.") }
            return
        }
        viewModelScope.launch {
            runCatching { command() }
                .onSuccess { _uiState.update { it.copy(message = successMessage, error = null) } }
                .onFailure { error -> _uiState.update { it.copy(error = commandMessage(error)) } }
        }
    }

    private fun currentTrigger() = _uiState.value.savedDevice?.triggerMethod ?: TriggerMethod.CAMERA_KEY

    private fun startPreviewIfReady(force: Boolean = false) {
        val state = _uiState.value
        val surface = attachedSurface ?: return
        if (state.connectionState != ConnectionState.CONNECTED || state.shutterOnly) return
        if (!force && state.previewState is PreviewState.Streaming) return
        val device = state.savedDevice ?: return
        mirror.start(device.host, device.connectPort, surface, state.previewProfile)
    }

    private fun pairingMessage(error: Throwable): String {
        val text = error.message.orEmpty().lowercase()
        return when {
            "timeout" in text -> "Pairing timed out. Keep the pairing-code dialog open and generate a new code."
            "code" in text || "pair" in text -> "Pairing failed. The code may have expired; generate a new one on the Samsung phone."
            else -> "Pairing failed. Both phones must be on the same Wi-Fi network."
        }
    }

    private fun connectionMessage(error: Throwable): String =
        if (error.message.orEmpty().contains("timeout", ignoreCase = true)) {
            "Connection timed out. Wireless Debugging may be off or the connection port may have changed."
        } else {
            "Could not connect. Use the IP and connection port from the main Wireless debugging screen."
        }

    private fun commandMessage(error: Throwable) =
        error.message?.takeIf { it.isNotBlank() } ?: "Samsung Camera did not react. Try a different trigger method."

    override fun onCleared() {
        mirror.stop()
        repository.disconnect()
        super.onCleared()
    }
}
