package com.sachinsharma.one_tap_recorder

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.service.quicksettings.TileService
import android.view.WindowManager
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

class RecordingService : Service() {
    companion object {
        const val ACTION_START = "recorder.START"
        const val ACTION_STOP = "recorder.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION = 901
        @Volatile var isRecording = false
            private set
    }

    private val running = AtomicBoolean(false)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var micRecord: AudioRecord? = null
    private var deviceRecord: AudioRecord? = null
    private var muxer: MediaMuxer? = null
    private var outputPfd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    private var encoderThread: Thread? = null
    private var audioThread: Thread? = null
    private var recordingWakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Screen recording", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while the screen is being recorded"
            setSound(null, null)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestStop()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || running.get()) return START_NOT_STICKY

        val stopIntent = PendingIntent.getService(this, 92,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("Recording screen")
            .setContentText("Device audio + microphone • tap Stop to save")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(NOTIFICATION, notification)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf(); return START_NOT_STICKY
        }
        running.set(true)
        isRecording = true
        acquireRecordingWakeLock()
        updateSurfaces()
        Thread({ startCapture(resultCode, resultData) }, "RecorderSetup").start()
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() = requestStop()
            }, Handler(Looper.getMainLooper()))

            val prefs = getSharedPreferences("recorder", MODE_PRIVATE)
            val metrics = resources.displayMetrics
            val landscape = metrics.widthPixels >= metrics.heightPixels
            val requestedLongEdge = if (prefs.getString("quality", "4K") == "4K") 3840 else 1920
            val ratio = max(metrics.widthPixels, metrics.heightPixels).toDouble() / minOf(metrics.widthPixels, metrics.heightPixels)
            val requestedFps = prefs.getInt("fps", 60)
            val profiles = buildList {
                add(requestedLongEdge to requestedFps)
                if (requestedFps > 30) add(requestedLongEdge to 30)
                if (requestedLongEdge > 1920) {
                    add(1920 to requestedFps)
                    add(1920 to 30)
                }
            }.distinct()
            var width = 0
            var height = 0
            var surface: android.view.Surface? = null
            for ((longEdge, frameRate) in profiles) {
                val shortEdge = ((longEdge / ratio).roundToInt() / 2) * 2
                width = if (landscape) longEdge else shortEdge
                height = if (landscape) shortEdge else longEdge
                try {
                    surface = configureVideo(width, height, frameRate)
                    break
                } catch (error: Exception) {
                    android.util.Log.w("OneTapRecorder", "Profile ${width}x$height@$frameRate unavailable", error)
                    try { videoCodec?.release() } catch (_: Exception) {}
                    videoCodec = null
                }
            }
            val captureSurface = surface ?: error("No supported H.264 recording profile")
            configureAudio()
            createOutput()

            display = projection!!.createVirtualDisplay(
                "OneTapRecorder", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, captureSurface, null, null
            )
            videoCodec!!.start()
            audioCodec!!.start()
            startAudioMixer()
            encoderThread = Thread({ drainEncoders() }, "RecorderMuxer").also { it.start() }
        } catch (e: Exception) {
            android.util.Log.e("OneTapRecorder", "Unable to start capture", e)
            requestStop()
        }
    }

    private fun configureVideo(width: Int, height: Int, fps: Int): android.view.Surface {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, if (max(width, height) >= 3000) 32_000_000 else 12_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
        }
        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        return videoCodec!!.createInputSurface()
    }

    private fun configureAudio() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48_000, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private fun createOutput() {
        val name = "Screen_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OneTapRecorder")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create output file")
        outputPfd = contentResolver.openFileDescriptor(outputUri!!, "rw")
            ?: error("Could not open output file")
        muxer = MediaMuxer(outputPfd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private fun buildMicRecord(buffer: Int): AudioRecord? = try {
        AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(48_000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(buffer).build().takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    } catch (_: Exception) { null }

    private fun buildDeviceRecord(buffer: Int): AudioRecord? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(48_000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(buffer).build().takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (_: Exception) { null }
    }

    private fun startAudioMixer() {
        val prefs = getSharedPreferences("recorder", MODE_PRIVATE)
        val useMic = prefs.getBoolean("micAudio", true)
        val useDevice = prefs.getBoolean("deviceAudio", true)
        val minBuffer = AudioRecord.getMinBufferSize(48_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = max(minBuffer * 2, 8192)
        if (useMic) micRecord = buildMicRecord(bufferSize)
        if (useDevice) deviceRecord = buildDeviceRecord(bufferSize)
        micRecord?.startRecording()
        deviceRecord?.startRecording()
        audioThread = Thread({
            val samples = 2048
            val mic = ShortArray(samples)
            val device = ShortArray(samples)
            val mixed = ByteBuffer.allocate(samples * 2)
            var totalSamples = 0L
            while (running.get()) {
                val micCount = micRecord?.read(mic, 0, samples, AudioRecord.READ_NON_BLOCKING)?.coerceAtLeast(0) ?: 0
                val deviceCount = deviceRecord?.read(device, 0, samples, AudioRecord.READ_NON_BLOCKING)?.coerceAtLeast(0) ?: 0
                val noAudioSources = micRecord == null && deviceRecord == null
                val count = if (noAudioSources) 480 else max(micCount, deviceCount)
                if (count == 0) { Thread.sleep(5); continue }
                mixed.clear()
                for (i in 0 until count) {
                    val a = if (i < micCount) mic[i].toInt() else 0
                    val b = if (i < deviceCount) device[i].toInt() else 0
                    val divisor = if (i < micCount && i < deviceCount) 2 else 1
                    mixed.putShort(((a + b) / divisor).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                }
                var offset = 0
                val bytes = count * 2
                while (offset < bytes && running.get()) {
                    val index = audioCodec?.dequeueInputBuffer(10_000) ?: -1
                    if (index >= 0) {
                        val input = audioCodec!!.getInputBuffer(index)!!
                        input.clear()
                        val amount = minOf(input.remaining(), bytes - offset)
                        input.put(mixed.array(), offset, amount)
                        val pts = totalSamples * 1_000_000L / 48_000L
                        audioCodec!!.queueInputBuffer(index, 0, amount, pts, 0)
                        totalSamples += amount / 2
                        offset += amount
                    }
                }
                if (noAudioSources) Thread.sleep(10)
            }
            try {
                val index = audioCodec?.dequeueInputBuffer(100_000) ?: -1
                if (index >= 0) audioCodec?.queueInputBuffer(index, 0, 0, totalSamples * 1_000_000L / 48_000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } catch (_: Exception) {}
        }, "AudioMixer").also { it.start() }
    }

    private fun drainEncoders() {
        var videoTrack = -1
        var audioTrack = -1
        var started = false
        var videoDone = false
        var audioDone = false
        var videoBasePts = -1L
        var audioBasePts = -1L
        var lastVideoPts = -1L
        var lastAudioPts = -1L
        val vInfo = MediaCodec.BufferInfo()
        val aInfo = MediaCodec.BufferInfo()
        try {
            while ((!videoDone || !audioDone) && (running.get() || !videoDone || !audioDone)) {
                fun drain(codec: MediaCodec, info: MediaCodec.BufferInfo, isVideo: Boolean) {
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val track = muxer!!.addTrack(codec.outputFormat)
                            if (isVideo) videoTrack = track else audioTrack = track
                            if (!started && videoTrack >= 0 && audioTrack >= 0) { muxer!!.start(); started = true }
                        }
                        index >= 0 -> {
                            val buffer = codec.getOutputBuffer(index)
                            if (buffer != null && info.size > 0 && started && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                // Surface video encoders commonly timestamp frames from system
                                // uptime, while our audio clock starts at zero. Writing those raw
                                // values makes a few-second clip appear many hours long. Rebase
                                // each track to its first media sample and keep timestamps monotonic.
                                if (isVideo) {
                                    if (videoBasePts < 0) videoBasePts = info.presentationTimeUs
                                    val normalized = (info.presentationTimeUs - videoBasePts).coerceAtLeast(lastVideoPts + 1)
                                    info.presentationTimeUs = normalized
                                    lastVideoPts = normalized
                                } else {
                                    if (audioBasePts < 0) audioBasePts = info.presentationTimeUs
                                    val normalized = (info.presentationTimeUs - audioBasePts).coerceAtLeast(lastAudioPts + 1)
                                    info.presentationTimeUs = normalized
                                    lastAudioPts = normalized
                                }
                                muxer!!.writeSampleData(if (isVideo) videoTrack else audioTrack, buffer, info)
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                if (isVideo) videoDone = true else audioDone = true
                            }
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
                if (!videoDone) drain(videoCodec!!, vInfo, true)
                if (!audioDone) drain(audioCodec!!, aInfo, false)
            }
        } catch (e: Exception) {
            android.util.Log.e("OneTapRecorder", "Encoder drain failed", e)
        } finally {
            finishRecording(started)
        }
    }

    private fun requestStop() {
        if (!running.getAndSet(false)) {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        isRecording = false
        updateSurfaces()
        try { micRecord?.stop() } catch (_: Exception) {}
        try { deviceRecord?.stop() } catch (_: Exception) {}
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { videoCodec?.signalEndOfInputStream() } catch (_: Exception) {}
    }

    private fun acquireRecordingWakeLock() {
        if (recordingWakeLock?.isHeld == true) return
        recordingWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:screen-recording")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseRecordingWakeLock() {
        try {
            if (recordingWakeLock?.isHeld == true) recordingWakeLock?.release()
        } catch (_: RuntimeException) {
        } finally {
            recordingWakeLock = null
        }
    }

    private fun finishRecording(muxerStarted: Boolean) {
        try { audioThread?.join(1000) } catch (_: Exception) {}
        try { micRecord?.release() } catch (_: Exception) {}
        try { deviceRecord?.release() } catch (_: Exception) {}
        try { videoCodec?.stop(); videoCodec?.release() } catch (_: Exception) {}
        try { audioCodec?.stop(); audioCodec?.release() } catch (_: Exception) {}
        try { if (muxerStarted) muxer?.stop(); muxer?.release() } catch (_: Exception) {}
        try { outputPfd?.close() } catch (_: Exception) {}
        outputUri?.let { contentResolver.update(it, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) }
        try { projection?.stop() } catch (_: Exception) {}
        releaseRecordingWakeLock()
        isRecording = false
        updateSurfaces()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateSurfaces() {
        RecorderWidgetProvider.updateAll(this)
        TileService.requestListeningState(this, android.content.ComponentName(this, RecorderTileService::class.java))
    }

    override fun onDestroy() {
        if (running.get()) requestStop()
        releaseRecordingWakeLock()
        super.onDestroy()
    }
}
