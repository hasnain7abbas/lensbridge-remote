package com.lensbridge.remote.mirror

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.shell.AdbShellPacket
import com.flyfishxu.kadb.shell.AdbShellStream
import com.flyfishxu.kadb.stream.AdbStream
import com.lensbridge.remote.common.PreviewProfile
import com.lensbridge.remote.common.PreviewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.BufferedSource
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/** A minimal scrcpy 4.0 video-only client. Audio and remote input are deliberately disabled. */
class ScrcpyMirrorSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val stateCallback: (PreviewState) -> Unit
) {
    private var job: Job? = null
    private var serverLogJob: Job? = null
    @Volatile private var videoStream: AdbStream? = null
    @Volatile private var serverShell: AdbShellStream? = null
    @Volatile private var serverClient: Kadb? = null
    @Volatile private var videoClient: Kadb? = null
    @Volatile private var decoder: MediaCodec? = null
    private val serverLog = StringBuilder()
    @Volatile private var serverExitCode: Int? = null

    fun start(host: String, port: Int, surface: Surface, profile: PreviewProfile) {
        stop()
        stateCallback(PreviewState.Starting)
        job = scope.launch(Dispatchers.IO) {
            try {
                runSession(host, port, surface, profile)
            } catch (_: CancellationException) {
                // Normal stop.
            } catch (error: Throwable) {
                Log.e(TAG, "Preview session failed", error)
                stateCallback(
                    PreviewState.Failed(
                        readableFailure(error)
                            .takeIf { it.isNotBlank() }
                            ?: "Preview failed, but shutter-only mode is still available."
                    )
                )
            } finally {
                closeResources()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        closeResources()
        stateCallback(PreviewState.Off)
    }

    private suspend fun runSession(host: String, port: Int, surface: Surface, profile: PreviewProfile) {
        synchronized(serverLog) { serverLog.clear() }
        serverExitCode = null

        // Keep the long-running server shell on its own ADB transport. A failed
        // localabstract open may reset Kadb's transport; sharing it would kill the
        // server before a retry could ever succeed.
        val launcher = Kadb.create(host, port, connectTimeout = 8_000, socketTimeout = 15_000)
        serverClient = launcher
        val server = copyServerToCache()
        launcher.push(server, REMOTE_SERVER_PATH, mode = 0b110100100)

        val scid = Random.nextInt(1, Int.MAX_VALUE)
        val scidHex = String.format(Locale.US, "%08x", scid)
        val socketName = "scrcpy_$scidHex"
        val command = buildString {
            append("CLASSPATH=$REMOTE_SERVER_PATH app_process / com.genymobile.scrcpy.Server $SERVER_VERSION")
            append(" scid=$scidHex log_level=warn")
            append(" video=true audio=false control=false")
            append(" video_codec=h264 max_size=${profile.maxSize} max_fps=${profile.fps}")
            append(" video_bit_rate=${profile.bitrate}")
            append(" tunnel_forward=true send_dummy_byte=false")
            append(" send_device_meta=false send_codec_meta=true send_frame_meta=true")
            append(" cleanup=true power_off_on_close=false")
        }
        val shell = launcher.openShell(command)
        serverShell = shell
        serverLogJob = scope.launch(Dispatchers.IO) { collectServerOutput(shell) }

        // A second authenticated transport mirrors an ordinary `adb forward`
        // data channel and makes socket-startup retries non-destructive.
        val receiver = Kadb.create(host, port, connectTimeout = 8_000, socketTimeout = 15_000)
        videoClient = receiver
        delay(INITIAL_SERVER_GRACE_MS)
        val stream = openVideoStreamWithRetry(receiver, socketName)
        videoStream = stream
        decode(stream.source, surface)
    }

    private suspend fun openVideoStreamWithRetry(kadb: Kadb, socketName: String): AdbStream {
        var lastError: Throwable? = null
        repeat(SOCKET_OPEN_ATTEMPTS) {
            coroutineContext.ensureActive()
            if (serverExitCode != null) {
                throw IllegalStateException("The preview server stopped before opening the video channel.")
            }
            try {
                return kadb.open("localabstract:$socketName")
            } catch (error: Throwable) {
                lastError = error
                delay(SOCKET_RETRY_DELAY_MS)
            }
        }
        throw IllegalStateException("The preview server did not start. ${lastError?.message.orEmpty()}")
    }

    private fun collectServerOutput(shell: AdbShellStream) {
        runCatching {
            while (true) {
                when (val packet = shell.read()) {
                    is AdbShellPacket.StdOut -> appendServerLog(packet.payload.decodeToString())
                    is AdbShellPacket.StdError -> appendServerLog(packet.payload.decodeToString())
                    is AdbShellPacket.Exit -> {
                        serverExitCode = packet.payload.firstOrNull()?.toUByte()?.toInt() ?: -1
                        return
                    }
                }
            }
        }.onFailure { error ->
            if (job?.isActive == true) appendServerLog(error.message.orEmpty())
        }
    }

    private fun appendServerLog(text: String) {
        if (text.isBlank()) return
        synchronized(serverLog) {
            serverLog.append(text)
            if (serverLog.length > MAX_DIAGNOSTIC_CHARS) {
                serverLog.delete(0, serverLog.length - MAX_DIAGNOSTIC_CHARS)
            }
        }
    }

    private fun readableFailure(error: Throwable): String {
        val log = synchronized(serverLog) { serverLog.toString().trim() }
        val detail = log.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .lastOrNull()
            ?.removePrefix("[server] ERROR: ")
            ?.removePrefix("ERROR: ")
        return detail ?: error.message.orEmpty()
    }

    private suspend fun decode(source: BufferedSource, surface: Surface) = withContext(Dispatchers.IO) {
        val codecId = source.readInt()
        check(codecId == H264_CODEC_ID) { "The phone returned an unsupported preview codec." }

        var width = 0
        var height = 0
        while (true) {
            coroutineContext.ensureActive()
            source.require(PACKET_HEADER_SIZE.toLong())
            val ptsAndFlags = source.readLong()
            val tail = source.readInt()

            if (ptsAndFlags < 0) {
                val resized = (ptsAndFlags and 1L) != 0L
                val nextWidth = (ptsAndFlags and 0xFFFF_FFFFL).toInt()
                val nextHeight = tail
                if (nextWidth <= 0 || nextHeight <= 0) continue
                if (decoder == null || resized || nextWidth != width || nextHeight != height) {
                    width = nextWidth
                    height = nextHeight
                    configureDecoder(surface, width, height)
                    stateCallback(PreviewState.Streaming(width, height))
                }
                continue
            }

            val packetSize = tail
            check(packetSize in 1..MAX_PACKET_SIZE) { "Invalid preview packet size: $packetSize" }
            val payload = source.readByteArray(packetSize.toLong())
            val activeDecoder = decoder ?: continue
            val isConfig = (ptsAndFlags and PACKET_FLAG_CONFIG) != 0L
            val pts = if (isConfig) 0L else ptsAndFlags and PACKET_PTS_MASK
            val flags = if (isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
            queueInput(activeDecoder, payload, pts, flags)
            drainOutput(activeDecoder)
        }
    }

    private fun configureDecoder(surface: Surface, width: Int, height: Int) {
        decoder?.runCatching { stop() }
        decoder?.release()
        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 * 1024 * 1024)
            }
            configure(format, surface, null, 0)
            start()
        }
    }

    private fun queueInput(codec: MediaCodec, bytes: ByteArray, pts: Long, flags: Int) {
        var attempts = 0
        while (attempts++ < 50) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) {
                val input = codec.getInputBuffer(index) ?: return
                input.clear()
                check(bytes.size <= input.remaining()) { "Preview frame is too large for the decoder." }
                input.put(bytes)
                codec.queueInputBuffer(index, 0, bytes.size, pts, flags)
                return
            }
        }
        error("Preview decoder stopped accepting frames.")
    }

    private fun drainOutput(codec: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 0)
            if (index < 0) return
            codec.releaseOutputBuffer(index, true)
        }
    }

    private fun copyServerToCache(): File {
        val target = File(context.cacheDir, SERVER_ASSET)
        if (!target.exists() || target.length() != SERVER_SIZE_BYTES) {
            context.assets.open(SERVER_ASSET).use { source ->
                target.outputStream().use { destination -> source.copyTo(destination) }
            }
        }
        check(target.length() == SERVER_SIZE_BYTES) { "Bundled preview server is incomplete." }
        return target
    }

    @Synchronized
    private fun closeResources() {
        serverLogJob?.cancel()
        serverLogJob = null
        videoStream?.runCatching { close() }
        videoStream = null
        serverShell?.runCatching { close() }
        serverShell = null
        videoClient?.runCatching { close() }
        videoClient = null
        serverClient?.runCatching { close() }
        serverClient = null
        decoder?.runCatching { stop() }
        decoder?.runCatching { release() }
        decoder = null
    }

    private companion object {
        const val SERVER_VERSION = "4.0"
        const val SERVER_ASSET = "scrcpy-server-v4.0"
        const val SERVER_SIZE_BYTES = 732_226L
        const val REMOTE_SERVER_PATH = "/data/local/tmp/lensbridge-scrcpy-server.jar"
        const val H264_CODEC_ID = 0x68323634
        const val PACKET_HEADER_SIZE = 12
        const val MAX_PACKET_SIZE = 8 * 1024 * 1024
        const val INITIAL_SERVER_GRACE_MS = 750L
        const val SOCKET_OPEN_ATTEMPTS = 100
        const val SOCKET_RETRY_DELAY_MS = 100L
        const val MAX_DIAGNOSTIC_CHARS = 2_000
        const val PACKET_FLAG_CONFIG = 1L shl 62
        const val PACKET_FLAG_KEY_FRAME = 1L shl 61
        const val PACKET_PTS_MASK = PACKET_FLAG_KEY_FRAME - 1
        const val TAG = "LensBridgePreview"
    }
}
