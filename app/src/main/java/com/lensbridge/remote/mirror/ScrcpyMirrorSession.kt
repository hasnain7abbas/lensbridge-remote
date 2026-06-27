package com.lensbridge.remote.mirror

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.flyfishxu.kadb.Kadb
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
    @Volatile private var videoStream: AdbStream? = null
    @Volatile private var serverShell: AdbShellStream? = null
    @Volatile private var decoder: MediaCodec? = null

    fun start(kadb: Kadb, surface: Surface, profile: PreviewProfile) {
        stop()
        stateCallback(PreviewState.Starting)
        job = scope.launch(Dispatchers.IO) {
            try {
                runSession(kadb, surface, profile)
            } catch (_: CancellationException) {
                // Normal stop.
            } catch (error: Throwable) {
                stateCallback(
                    PreviewState.Failed(
                        error.message?.takeIf { it.isNotBlank() }
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

    private suspend fun runSession(kadb: Kadb, surface: Surface, profile: PreviewProfile) {
        val server = copyServerToCache()
        kadb.push(server, REMOTE_SERVER_PATH, mode = 0b111101101)

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
        serverShell = kadb.openShell(command)

        val stream = openVideoStreamWithRetry(kadb, socketName)
        videoStream = stream
        decode(stream.source, surface)
    }

    private suspend fun openVideoStreamWithRetry(kadb: Kadb, socketName: String): AdbStream {
        var lastError: Throwable? = null
        repeat(30) {
            coroutineContext.ensureActive()
            try {
                return kadb.open("localabstract:$socketName")
            } catch (error: Throwable) {
                lastError = error
                delay(100)
            }
        }
        throw IllegalStateException("The preview server did not start. ${lastError?.message.orEmpty()}")
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
        videoStream?.runCatching { close() }
        videoStream = null
        serverShell?.runCatching { close() }
        serverShell = null
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
        const val PACKET_FLAG_CONFIG = 1L shl 62
        const val PACKET_FLAG_KEY_FRAME = 1L shl 61
        const val PACKET_PTS_MASK = PACKET_FLAG_KEY_FRAME - 1
    }
}
