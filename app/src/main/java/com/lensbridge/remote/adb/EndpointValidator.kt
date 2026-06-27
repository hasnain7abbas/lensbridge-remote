package com.lensbridge.remote.adb

object EndpointValidator {
    fun hostError(value: String): String? {
        if (value.isBlank()) return "Enter the Samsung phone's IP address."
        val host = value.trim()
        if (host.any { it.isWhitespace() } || host.contains(Regex("[/:]"))) {
            return "Enter only the IP address, without a port."
        }
        return null
    }

    fun portError(value: String): String? {
        val port = value.toIntOrNull() ?: return "Enter a valid port number."
        return if (port in 1..65535) null else "Port must be between 1 and 65535."
    }

    fun pairingCodeError(value: String): String? =
        if (value.matches(Regex("\\d{6}"))) null else "Use the current 6-digit pairing code."
}
