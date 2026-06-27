package com.lensbridge.remote.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lensbridge.remote.MainViewModel
import com.lensbridge.remote.adb.EndpointValidator
import com.lensbridge.remote.common.ConnectionState
import com.lensbridge.remote.common.PreviewProfile
import com.lensbridge.remote.common.PreviewState
import com.lensbridge.remote.common.RemoteUiState
import com.lensbridge.remote.common.TriggerMethod
import com.lensbridge.remote.ui.theme.Danger
import com.lensbridge.remote.ui.theme.Frost
import com.lensbridge.remote.ui.theme.Ink
import com.lensbridge.remote.ui.theme.LensBlue
import com.lensbridge.remote.ui.theme.Muted
import com.lensbridge.remote.ui.theme.Obsidian
import com.lensbridge.remote.ui.theme.Panel
import com.lensbridge.remote.ui.theme.SignalBlue
import com.lensbridge.remote.ui.theme.Warm
import kotlinx.coroutines.delay

@Composable
fun LensBridgeApp(state: RemoteUiState, viewModel: MainViewModel) {
    if (state.onboardingComplete) {
        ControllerScreen(state, viewModel)
    } else {
        OnboardingFlow(state, viewModel)
    }
}

@Composable
private fun OnboardingFlow(state: RemoteUiState, viewModel: MainViewModel) {
    var host by rememberSaveable { mutableStateOf(state.savedDevice?.host.orEmpty()) }
    var pairingPort by rememberSaveable { mutableStateOf("") }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf(state.savedDevice?.connectPort?.toString().orEmpty()) }
    var validatePair by rememberSaveable { mutableStateOf(false) }
    var validateConnect by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = state.onboardingStep > 0) { viewModel.previousOnboardingStep() }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF132431), Obsidian),
                    center = Offset(140f, 100f),
                    radius = 900f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SignalSpine(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(52.dp))
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
            OnboardingHeader(state.onboardingStep, onBack = viewModel::previousOnboardingStep)
            Spacer(Modifier.height(28.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.onboardingStep) {
                    0 -> WelcomeStep { viewModel.nextOnboardingStep() }
                    1 -> PrepareStep { viewModel.nextOnboardingStep() }
                    2 -> PairStep(
                        host = host,
                        port = pairingPort,
                        code = pairingCode,
                        validate = validatePair,
                        busy = state.connectionState == ConnectionState.PAIRING,
                        error = state.error,
                        onHost = { host = it },
                        onPort = { pairingPort = it.filter(Char::isDigit).take(5) },
                        onCode = { pairingCode = it.filter(Char::isDigit).take(6) },
                        onPair = {
                            validatePair = true
                            if (EndpointValidator.hostError(host) == null &&
                                EndpointValidator.portError(pairingPort) == null &&
                                EndpointValidator.pairingCodeError(pairingCode) == null
                            ) viewModel.pair(host, pairingPort.toInt(), pairingCode)
                        }
                    )
                    3 -> ConnectStep(
                        host = host,
                        port = connectPort,
                        validate = validateConnect,
                        busy = state.connectionState == ConnectionState.CONNECTING,
                        error = state.error,
                        onHost = { host = it },
                        onPort = { connectPort = it.filter(Char::isDigit).take(5) },
                        onConnect = {
                            validateConnect = true
                            if (EndpointValidator.hostError(host) == null && EndpointValidator.portError(connectPort) == null) {
                                viewModel.connect(host, connectPort.toInt())
                            }
                        }
                    )
                    4 -> CameraReadyStep(state) { viewModel.nextOnboardingStep() }
                    else -> LaunchStep(state, viewModel::finishOnboarding)
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeader(step: Int, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (step > 0) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Frost) }
        } else BridgeMark(44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("LENSBRIDGE", style = MaterialTheme.typography.labelMedium, color = LensBlue)
            Text("Remote", style = MaterialTheme.typography.titleLarge)
        }
        Text("${step + 1} / 6", style = MaterialTheme.typography.labelMedium, color = Muted)
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(6) { index ->
            Box(
                Modifier.weight(1f).height(3.dp).clip(CircleShape)
                    .background(if (index <= step) LensBlue else Panel)
            )
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            BridgeHero()
            Spacer(Modifier.height(30.dp))
            Text("Your second phone,\nyour camera remote.", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(16.dp))
            Text(
                "Keep Samsung Camera or Expert RAW open on the tripod phone. LensBridge gives this phone a live view and a dependable shutter.",
                style = MaterialTheme.typography.bodyLarge, color = Muted
            )
        }
        PrimaryButton("Set up both phones", onContinue)
    }
}

@Composable
private fun PrepareStep(onContinue: () -> Unit) {
    StepScaffold(
        eyebrow = "MAIN SAMSUNG PHONE",
        title = "Turn on Wireless debugging",
        body = "This creates a local, user-approved bridge between your phones. LensBridge never uses a cloud server.",
        action = { PrimaryButton("Wireless debugging is ready", onContinue) }
    ) {
        Instruction("1", "Open Settings", "Go to About phone → Software information.")
        Instruction("2", "Enable Developer options", "Tap Build number seven times, then return to Settings.")
        Instruction("3", "Enable Wireless debugging", "Open Developer options. Keep both phones on the same Wi-Fi.")
        Instruction("4", "Open pairing code", "Tap Wireless debugging → Pair device with pairing code. Keep that dialog open.")
    }
}

@Composable
private fun PairStep(
    host: String, port: String, code: String, validate: Boolean, busy: Boolean, error: String?,
    onHost: (String) -> Unit, onPort: (String) -> Unit, onCode: (String) -> Unit, onPair: () -> Unit
) {
    StepScaffold(
        eyebrow = "SECURE PAIRING",
        title = "Enter the temporary code",
        body = "Use the IP, pairing port, and six-digit code from the pairing dialog. The code is used once and never saved.",
        action = { PrimaryButton(if (busy) "Pairing…" else "Pair phones", onPair, enabled = !busy, loading = busy) }
    ) {
        EndpointFields(host, port, onHost, onPort, validate)
        Spacer(Modifier.height(12.dp))
        LensTextField(
            value = code,
            onValueChange = onCode,
            label = "6-digit pairing code",
            keyboardType = KeyboardType.NumberPassword,
            error = if (validate) EndpointValidator.pairingCodeError(code) else null
        )
        ErrorCard(error)
    }
}

@Composable
private fun ConnectStep(
    host: String, port: String, validate: Boolean, busy: Boolean, error: String?,
    onHost: (String) -> Unit, onPort: (String) -> Unit, onConnect: () -> Unit
) {
    StepScaffold(
        eyebrow = "LOCAL CONNECTION",
        title = "Connect to the camera phone",
        body = "Now use the separate IP address & port shown on the main Wireless debugging screen—not the pairing port.",
        action = { PrimaryButton(if (busy) "Connecting…" else "Connect", onConnect, enabled = !busy, loading = busy) }
    ) {
        EndpointFields(host, port, onHost, onPort, validate)
        Spacer(Modifier.height(12.dp))
        InfoCard("Pairing and connection ports are different. Android may change the connection port after Wireless debugging restarts.")
        ErrorCard(error)
    }
}

@Composable
private fun CameraReadyStep(state: RemoteUiState, onContinue: () -> Unit) {
    StepScaffold(
        eyebrow = "${state.deviceInfo?.model ?: "SAMSUNG PHONE"} · ANDROID ${state.deviceInfo?.androidVersion ?: ""}",
        title = "Open the camera you want",
        body = "On the Samsung phone, open Samsung Camera or Expert RAW and choose Photo, Portrait, Night, Pro, Video, or Astro manually.",
        action = { PrimaryButton("I opened Camera", onContinue) }
    ) {
        CameraModeCard("PORTRAIT", "Keep Samsung's own depth processing.")
        CameraModeCard("NIGHT / ASTRO", "Keep the phone still after the shutter command.")
        CameraModeCard("VIDEO", "Select Video or Portrait Video before sending record.")
        Spacer(Modifier.height(10.dp))
        InfoCard("If Camera Key does not react later, set Samsung Camera's volume-key action to shutter and switch LensBridge to a Volume trigger.")
    }
}

@Composable
private fun LaunchStep(state: RemoteUiState, onLaunch: () -> Unit) {
    StepScaffold(
        eyebrow = "BRIDGE READY",
        title = "Your remote cockpit is ready",
        body = "LensBridge will try a low-latency preview first. If the network is weak, shutter-only mode remains fully usable.",
        action = { PrimaryButton("Open remote", onLaunch) }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF162A38), Ink)))
                .border(1.dp, LensBlue.copy(alpha = .22f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            BridgeMark(82.dp)
            Text(
                state.savedDevice?.name?.uppercase() ?: "SAMSUNG PHONE",
                Modifier.align(Alignment.BottomStart).padding(18.dp),
                style = MaterialTheme.typography.labelMedium,
                color = LensBlue
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("LOCAL ONLY", true)
            StatusPill("NO AUDIO", true)
            StatusPill("H.264", true)
        }
    }
}

@Composable
private fun ControllerScreen(state: RemoteUiState, viewModel: MainViewModel) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message, state.error) {
        if (state.message != null || state.error != null) {
            delay(3_000)
            viewModel.clearNotice()
        }
    }
    Scaffold(containerColor = Obsidian) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().navigationBarsPadding()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B1118), Obsidian)))
        ) {
            Column(Modifier.fillMaxSize()) {
                ControllerHeader(state, onSettings = { settingsOpen = true }, onReconnect = viewModel::reconnect)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val wide = maxWidth > 720.dp
                    if (wide) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            PreviewPanel(state, viewModel, Modifier.weight(1.35f).fillMaxHeight())
                            ControlDeck(state, viewModel, Modifier.weight(.65f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            PreviewPanel(state, viewModel, Modifier.weight(1f).fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            ControlDeck(state, viewModel, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            NoticeBanner(state.error ?: state.message, state.error != null, Modifier.align(Alignment.TopCenter).padding(top = 72.dp))
        }
    }
    if (settingsOpen) {
        SettingsSheet(state, viewModel, onDismiss = { settingsOpen = false })
    }
}

@Composable
private fun ControllerHeader(state: RemoteUiState, onSettings: () -> Unit, onReconnect: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BridgeMark(38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("LENSBRIDGE", style = MaterialTheme.typography.labelMedium, color = LensBlue)
            Text(state.savedDevice?.name ?: "No camera phone", style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        val connected = state.connectionState == ConnectionState.CONNECTED
        StatusPill(if (connected) "CONNECTED" else state.connectionState.name, connected)
        if (!connected) IconButton(onClick = onReconnect) { Icon(Icons.Rounded.Refresh, "Reconnect", tint = LensBlue) }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = Frost) }
    }
}

@Composable
private fun PreviewPanel(state: RemoteUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(26.dp)).background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(26.dp))
    ) {
        if (state.connectionState == ConnectionState.CONNECTED && !state.shutterOnly) {
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = viewModel.attachPreviewSurface(holder.surface)
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                            override fun surfaceDestroyed(holder: SurfaceHolder) = viewModel.detachPreviewSurface(holder.surface)
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (state.gridEnabled && state.previewState is PreviewState.Streaming) PreviewGrid()
        PreviewOverlay(state, viewModel)
    }
}

@Composable
private fun BoxScope.PreviewOverlay(state: RemoteUiState, viewModel: MainViewModel) {
    when {
        state.shutterOnly -> EmptyPreview(
            icon = { BridgeMark(42.dp) },
            title = "Shutter-only mode",
            body = "Preview is off. Controls still travel over the local ADB bridge.",
            action = "Try preview",
            onAction = viewModel::toggleShutterOnly
        )
        state.connectionState != ConnectionState.CONNECTED -> EmptyPreview(
            icon = { Icon(Icons.Rounded.Warning, null, tint = Muted, modifier = Modifier.size(36.dp)) },
            title = "Camera phone offline",
            body = "Turn on Wireless debugging, then reconnect.",
            action = "Reconnect",
            onAction = viewModel::reconnect
        )
        state.previewState is PreviewState.Starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(30.dp), color = LensBlue, strokeWidth = 2.dp)
                Spacer(Modifier.height(12.dp))
                Text("OPENING LIVE VIEW", style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        }
        state.previewState is PreviewState.Failed -> EmptyPreview(
            icon = { Icon(Icons.Rounded.Warning, null, tint = Warm, modifier = Modifier.size(36.dp)) },
            title = "Preview unavailable",
            body = state.previewState.message,
            action = "Use shutter-only",
            onAction = viewModel::toggleShutterOnly
        )
    }
    Row(Modifier.align(Alignment.TopStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (state.previewState is PreviewState.Streaming) {
            StatusPill("LIVE", true)
            StatusPill(state.previewProfile.label.uppercase(), true)
        }
    }
    if (state.timerRemaining != null) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .60f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.timerRemaining.toString(), fontSize = 72.sp, fontWeight = FontWeight.Light, color = Frost)
                TextButton(onClick = viewModel::cancelTimer) { Text("Cancel timer", color = LensBlue) }
            }
        }
    }
}

@Composable
private fun ControlDeck(state: RemoteUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val enabled = state.connectionState == ConnectionState.CONNECTED && state.timerRemaining == null
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("TIMER", style = MaterialTheme.typography.labelMedium, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(0, 3, 5, 10).forEach { seconds ->
                        CompactChoice(if (seconds == 0) "OFF" else "${seconds}s", state.timerSeconds == seconds) { viewModel.setTimer(seconds) }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("BURST", style = MaterialTheme.typography.labelMedium, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(3, 5, 10).forEach { count -> CompactChoice("$count", state.burstCount == count) { viewModel.setBurst(count) } }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RoundAction(
                icon = { Icon(Icons.Rounded.PlayArrow, "Send record command", tint = if (enabled) Danger else Muted) },
                label = "VIDEO",
                enabled = enabled,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.sendVideo() }
            )
            ShutterButton(enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.sendPhoto()
            }
            RoundAction(
                icon = { Text("×${state.burstCount}", fontWeight = FontWeight.Bold, color = if (enabled) LensBlue else Muted) },
                label = if (state.burstRunning) "SENDING" else "BURST",
                enabled = enabled && !state.burstRunning,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.startBurst() }
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (state.timerSeconds > 0) "Shutter fires after ${state.timerSeconds}s" else "${state.savedDevice?.triggerMethod?.label ?: "Camera key"} · command mode",
            style = MaterialTheme.typography.labelMedium,
            color = Muted
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: RemoteUiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink, contentColor = Frost) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 32.dp)) {
            Text("Remote settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text("Tune the bridge for this Samsung phone.", color = Muted)
            SectionLabel("TRIGGER METHOD")
            TriggerMethod.entries.forEach { method ->
                SettingChoice(method.label, method == state.savedDevice?.triggerMethod) { viewModel.setTrigger(method) }
            }
            Text("Samsung Camera should be configured so the chosen volume key takes a picture or records video.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            SectionLabel("PREVIEW QUALITY")
            PreviewProfile.entries.forEach { profile ->
                SettingChoice("${profile.label} · ${profile.fps} fps · ${profile.bitrate / 1_000_000} Mbps", profile == state.previewProfile) {
                    viewModel.setPreviewProfile(profile)
                }
            }
            SectionLabel("VIEWFINDER")
            ToggleRow("Composition grid", "Drawn locally on this phone", state.gridEnabled, viewModel::setGrid)
            ToggleRow("Keep screen awake", "Useful during a tripod session", state.keepAwake, viewModel::setKeepAwake)
            ToggleRow("Shutter-only mode", "Stop video while keeping controls", state.shutterOnly) { viewModel.toggleShutterOnly() }
            SectionLabel("SESSION")
            OutlinedButton(onClick = { viewModel.disconnect(); onDismiss() }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text("Disconnect")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.forgetDevice(); onDismiss() }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Delete, null, tint = Danger); Spacer(Modifier.width(8.dp)); Text("Forget this phone", color = Danger)
            }
            Text("Turn off Wireless debugging on the Samsung phone when you are finished.", style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Text(
                "LensBridge Remote 0.1.0 · GPL-3.0-or-later · provided without warranty",
                style = MaterialTheme.typography.labelMedium,
                color = Muted.copy(alpha = .72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StepScaffold(eyebrow: String, title: String, body: String, action: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = LensBlue)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Muted)
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(18.dp))
        }
        action()
    }
}

@Composable
private fun Instruction(number: String, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
            Text(number, fontFamily = FontFamily.Monospace, color = LensBlue, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}

@Composable
private fun EndpointFields(host: String, port: String, onHost: (String) -> Unit, onPort: (String) -> Unit, validate: Boolean) {
    LensTextField(host, onHost, "IP address", KeyboardType.Uri, if (validate) EndpointValidator.hostError(host) else null)
    Spacer(Modifier.height(12.dp))
    LensTextField(port, onPort, "Port", KeyboardType.Number, if (validate) EndpointValidator.portError(port) else null)
}

@Composable
private fun LensTextField(value: String, onValueChange: (String) -> Unit, label: String, keyboardType: KeyboardType, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LensBlue,
            focusedLabelColor = LensBlue,
            unfocusedBorderColor = Color.White.copy(alpha = .16f),
            unfocusedContainerColor = Ink.copy(alpha = .72f),
            focusedContainerColor = Ink
        )
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LensBlue, contentColor = Obsidian)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Obsidian, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoCard(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LensBlue.copy(alpha = .08f))
            .border(1.dp, LensBlue.copy(alpha = .18f), RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Icon(Icons.Rounded.ArrowForward, null, tint = LensBlue, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
private fun ErrorCard(error: String?) {
    AnimatedVisibility(error != null, enter = fadeIn(), exit = fadeOut()) {
        if (error != null) {
            Text(error, color = Danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun CameraModeCard(name: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Ink.copy(alpha = .8f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = LensBlue, modifier = Modifier.width(104.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
private fun EmptyPreview(icon: @Composable () -> Unit, title: String, body: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF142330), Color.Black))), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            icon(); Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(5.dp))
            Text(body, color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(action, color = LensBlue) }
        }
    }
}

@Composable
private fun CompactChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) LensBlue else Panel).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) Obsidian else Muted)
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(88.dp).clip(CircleShape).background(Color.Transparent)
            .border(3.dp, if (enabled) Frost else Muted.copy(alpha = .4f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick).padding(7.dp)
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(if (enabled) Frost else Panel))
    }
}

@Composable
private fun RoundAction(icon: @Composable () -> Unit, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Panel).clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) Muted else Muted.copy(alpha = .45f))
    }
}

@Composable
private fun NoticeBanner(text: String?, isError: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(text != null, modifier = modifier, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        if (text != null) {
            Row(
                Modifier.padding(horizontal = 16.dp).clip(CircleShape)
                    .background(if (isError) Color(0xFF3A2028) else Color(0xFF16313E))
                    .border(1.dp, if (isError) Danger.copy(.35f) else LensBlue.copy(.30f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (isError) Icons.Rounded.Close else Icons.Rounded.Check, null, tint = if (isError) Danger else LensBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    Row(
        Modifier.clip(CircleShape).background(if (active) LensBlue.copy(.10f) else Panel).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (active) LensBlue else Muted))
        Spacer(Modifier.width(6.dp)); Text(text, style = MaterialTheme.typography.labelMedium, color = if (active) LensBlue else Muted)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(26.dp)); Text(text, style = MaterialTheme.typography.labelMedium, color = LensBlue); Spacer(Modifier.height(7.dp))
}

@Composable
private fun SettingChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1f), color = if (selected) Frost else Muted)
        if (selected) Icon(Icons.Rounded.Check, null, tint = LensBlue, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted) }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Obsidian, checkedTrackColor = LensBlue, uncheckedTrackColor = Panel)
        )
    }
}

@Composable
private fun PreviewGrid() {
    Canvas(Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = .23f)
        drawLine(line, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), 1f)
        drawLine(line, Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), 1f)
        drawLine(line, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), 1f)
        drawLine(line, Offset(0f, size.height * 2 / 3), Offset(size.width, size.height * 2 / 3), 1f)
        drawLine(LensBlue.copy(.55f), Offset(size.width / 2 - 10, size.height / 2), Offset(size.width / 2 + 10, size.height / 2), 2f)
        drawLine(LensBlue.copy(.55f), Offset(size.width / 2, size.height / 2 - 10), Offset(size.width / 2, size.height / 2 + 10), 2f)
    }
}

@Composable
private fun BridgeMark(size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        drawCircle(Panel, radius = this.size.minDimension / 2, center = center)
        drawCircle(LensBlue, radius = this.size.minDimension * .29f, center = center, style = Stroke(this.size.minDimension * .055f))
        drawCircle(Frost, radius = this.size.minDimension * .10f, center = center)
        val y = center.y - this.size.minDimension * .13f
        drawArc(LensBlue, 205f, 130f, false, topLeft = Offset(this.size.width * .13f, y), size = androidx.compose.ui.geometry.Size(this.size.width * .74f, this.size.height * .42f), style = Stroke(this.size.minDimension * .045f, cap = StrokeCap.Round))
        drawCircle(Warm, radius = this.size.minDimension * .035f, center = Offset(this.size.width * .76f, this.size.height * .22f))
    }
}

@Composable
private fun BridgeHero() {
    Box(Modifier.fillMaxWidth().height(126.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val left = Offset(size.width * .15f, size.height * .58f)
            val right = Offset(size.width * .85f, size.height * .42f)
            val path = Path().apply {
                moveTo(left.x, left.y)
                cubicTo(size.width * .36f, -10f, size.width * .62f, size.height * 1.05f, right.x, right.y)
            }
            drawPath(path, Brush.horizontalGradient(listOf(LensBlue.copy(.15f), LensBlue, Warm)), style = Stroke(5f, cap = StrokeCap.Round))
            drawCircle(Panel, 30f, left); drawCircle(LensBlue, 10f, left)
            drawCircle(Panel, 38f, right); drawCircle(Frost, 12f, right)
        }
        Text("LOCAL SIGNAL BRIDGE", Modifier.align(Alignment.BottomCenter), style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

@Composable
private fun SignalSpine(modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(360f, tween(12_000), label = "signal")
    Canvas(modifier.rotate(rotation * 0.01f)) {
        drawLine(LensBlue.copy(.10f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f)
        repeat(8) { index ->
            drawCircle(LensBlue.copy(alpha = .08f + index * .018f), 3f + index, Offset(size.width / 2, size.height * (index + 1) / 9))
        }
    }
}
