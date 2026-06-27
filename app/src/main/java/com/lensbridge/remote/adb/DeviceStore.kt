package com.lensbridge.remote.adb

import android.content.Context
import com.lensbridge.remote.common.PreviewProfile
import com.lensbridge.remote.common.SavedDevice
import com.lensbridge.remote.common.TriggerMethod

class DeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences("lensbridge_settings", Context.MODE_PRIVATE)

    fun onboardingComplete() = preferences.getBoolean("onboarding_complete", false)
    fun setOnboardingComplete(value: Boolean) = preferences.edit().putBoolean("onboarding_complete", value).apply()

    fun loadDevice(): SavedDevice? {
        val host = preferences.getString("device_host", null) ?: return null
        val port = preferences.getInt("device_port", -1).takeIf { it > 0 } ?: return null
        return SavedDevice(
            name = preferences.getString("device_name", null) ?: "Samsung phone",
            host = host,
            connectPort = port,
            triggerMethod = runCatching {
                TriggerMethod.valueOf(preferences.getString("trigger", null) ?: "")
            }.getOrDefault(TriggerMethod.CAMERA_KEY)
        )
    }

    fun saveDevice(device: SavedDevice) {
        preferences.edit()
            .putString("device_name", device.name)
            .putString("device_host", device.host)
            .putInt("device_port", device.connectPort)
            .putString("trigger", device.triggerMethod.name)
            .apply()
    }

    fun forgetDevice() {
        preferences.edit().remove("device_name").remove("device_host").remove("device_port").remove("trigger").apply()
    }

    fun previewProfile() = runCatching {
        PreviewProfile.valueOf(preferences.getString("preview_profile", null) ?: "")
    }.getOrDefault(PreviewProfile.BALANCED)

    fun savePreviewProfile(profile: PreviewProfile) =
        preferences.edit().putString("preview_profile", profile.name).apply()
}
