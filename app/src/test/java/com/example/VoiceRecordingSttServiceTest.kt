package com.example

import android.Manifest
import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceRecordingSttServiceTest {
    private lateinit var service: VoiceRecordingSttService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val updates = mutableListOf<String>()
    private val errors = mutableListOf<String>()
    private fun result(value: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(value))
    }
    private fun recognizer() = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        service = VoiceRecordingSttService(app)
        service.startRecording(scope, { updates.add(it) }, {}, { errors.add(it) })
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After fun cleanup() { service.cancelRecording(); scope.cancel() }

    @Test fun finishingWaitsForTheProvidersFinalCorrection() {
        val speech = recognizer()
        speech.triggerOnPartialResults(result("meet at four"))
        val finals = mutableListOf<String>()
        service.stopRecording(scope) { finals.add(it) }
        assertTrue(finals.isEmpty())
        assertFalse(speech.isDestroyed)
        speech.triggerOnResults(result("meet at five"))
        assertEquals(1, finals.size)
        assertTrue(finals.single().contains("five", true) || finals.single().contains("5"))
        assertFalse(finals.single().contains("four", true))
        assertTrue(speech.isDestroyed)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(1, finals.size)
    }

    @Test fun finalTimeoutPreservesLastPartialAndOnlyFinishesOnce() {
        val speech = recognizer()
        speech.triggerOnPartialResults(result("hello there"))
        val finals = mutableListOf<String>()
        service.stopRecording(scope) { finals.add(it) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_600))
        assertEquals(1, finals.size)
        assertTrue(finals.single().contains("hello there", true))
        speech.triggerOnResults(result("late result"))
        assertEquals(1, finals.size)
    }

    @Test fun revisedPartialReplacesTheHypothesisWithoutDuplicatingIt() {
        val speech = recognizer()
        speech.triggerOnPartialResults(result("hello their"))
        speech.triggerOnPartialResults(result("hello there"))
        speech.triggerOnPartialResults(result("hello there"))
        assertEquals(2, updates.size)
        assertTrue(updates.last().contains("hello there", true))
        assertFalse(updates.last().contains("their", true))
    }

    @Test fun ordinaryDictationDoesNotRemoveWordsThatLookLikeFillers() {
        recognizer().triggerOnPartialResults(result("I like coffee"))
        assertTrue(updates.last().contains("I like coffee", true))
    }

    @Test fun pauseContinuesAndPreservesTheCompletedSegment() {
        val speech = recognizer()
        speech.triggerOnResults(result("hello there"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        speech.triggerOnPartialResults(result("how are you"))
        assertTrue(updates.last().contains("hello there", true))
        assertTrue(updates.last().contains("how are you", true))
        assertTrue(service.isRecording.value)
    }

    @Test fun cancellationAndNewSessionIgnoreOldRecognizerResults() {
        val old = recognizer()
        old.triggerOnPartialResults(result("old draft"))
        service.cancelRecording()
        service.startRecording(scope, { updates.add(it) }, {}, { errors.add(it) })
        shadowOf(Looper.getMainLooper()).idle()
        val count = updates.size
        old.triggerOnResults(result("old result must not enter the new editor"))
        assertEquals(count, updates.size)
        assertEquals("", service.currentTranscript.value)
    }

    @Test fun repeatedFailureStopsAndShowsAnActionableError() {
        repeat(3) {
            recognizer().triggerOnError(SpeechRecognizer.ERROR_AUDIO)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }
        assertFalse(service.isRecording.value)
        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("microphone"))
    }
}
