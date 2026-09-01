package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Thread-safe 16kHz 16-bit Mono PCM Audio Capture Manager.
 * Uses Android AudioRecord for low-latency continuous buffer recording
 * and real-time normalized amplitude/waveform extraction.
 */
class AudioCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE_HZ = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isRecording: Boolean = false
        private set

    private val audioBufferStream = ByteArrayOutputStream()

    /**
     * Starts capturing 16kHz 16-bit mono PCM audio from the microphone.
     * @param onAmplitude Callback with normalized RMS amplitude (0.0f - 1.0f) for real-time waveform UI.
     * @param onPcmChunk Optional stream callback emitting raw PCM byte chunks as they arrive.
     */
    @SuppressLint("MissingPermission")
    fun startCapture(
        onAmplitude: (Float) -> Unit = {},
        onPcmChunk: (ByteArray) -> Unit = {}
    ): Boolean {
        if (isRecording) {
            Log.w(TAG, "Audio capture is already active")
            return true
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid AudioRecord buffer configuration: $minBufferSize")
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioBufferStream.reset()
            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch {
                val readBuffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteBuffer.allocate(readBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)

                while (isActive && isRecording) {
                    val readCount = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1

                    if (readCount > 0) {
                        // Calculate normalized RMS amplitude for waveform
                        var sumSquares = 0.0
                        byteBuffer.clear()

                        for (i in 0 until readCount) {
                            val sample = readBuffer[i]
                            sumSquares += (sample * sample).toDouble()
                            byteBuffer.putShort(sample)
                        }

                        val rms = sqrt(sumSquares / readCount)
                        val normalizedAmp = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        withContext(Dispatchers.Main) {
                            onAmplitude(normalizedAmp)
                        }

                        val chunkBytes = byteBuffer.array().copyOf(readCount * 2)
                        synchronized(audioBufferStream) {
                            audioBufferStream.write(chunkBytes)
                        }
                        onPcmChunk(chunkBytes)
                    } else if (readCount < 0) {
                        Log.e(TAG, "Error reading audio data: $readCount")
                        break
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting audio capture: ${e.message}", e)
            cleanUp()
            return false
        }
    }

    /**
     * Stops capturing and returns the entire recorded 16kHz 16-bit mono PCM payload.
     */
    fun stopCapture(): ByteArray {
        isRecording = false
        recordingJob?.cancel()

        try {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED && it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }

        val capturedBytes = synchronized(audioBufferStream) {
            audioBufferStream.toByteArray()
        }
        audioBufferStream.reset()
        return capturedBytes
    }

    /**
     * Cancels recording and discards all buffered audio data.
     */
    fun cancelCapture() {
        isRecording = false
        recordingJob?.cancel()
        cleanUp()
        synchronized(audioBufferStream) {
            audioBufferStream.reset()
        }
    }

    private fun cleanUp() {
        try {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }
    }
}
