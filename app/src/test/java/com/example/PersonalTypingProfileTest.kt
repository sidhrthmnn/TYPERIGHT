package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersonalTypingProfileTest {
    private lateinit var context: Context
    private lateinit var profile: PersonalTypingProfile
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        profile = PersonalTypingProfile(context).apply { clear() }
    }
    @Test fun acceptedTypoPersistsAndRestoresCase() {
        profile.acceptPolish("Send the mesage", "Send the message") { it != "mesage" }
        assertEquals("message", profile.correction("mesage", emptyList()))
        assertEquals("Message", profile.correction("Mesage", emptyList()))
        profile.flush()
        assertEquals("message", PersonalTypingProfile(context).also { kotlinx.coroutines.runBlocking { it.ready.await() } }.correction("mesage", emptyList()))
    }
    @Test fun insertionDoesNotShiftLaterCorrection() {
        profile.acceptPolish("send mesage tomororw", "please send message tomorrow") { it in setOf("send", "message", "tomorrow", "please") }
        assertEquals("message", profile.correction("mesage", emptyList()))
        assertEquals("tomorrow", profile.correction("tomororw", emptyList()))
    }
    @Test fun realWordRequiresRepeatedAcceptanceInSameContext() {
        repeat(2) { profile.acceptPolish("they has arrived", "they had arrived") { true } }
        assertNull(profile.correction("has", listOf("they")))
        profile.acceptPolish("they has arrived", "they had arrived") { true }
        assertEquals("had", profile.correction("has", listOf("they")))
        assertNull(profile.correction("has", listOf("she")))
        assertNull(profile.correction("has", emptyList()))
    }
    @Test fun rewritesNumbersUrlsAndUnknownNamesNeverBecomeTypos() {
        profile.acceptPolish("great code 123456 https://mesage.com", "excellent code 654321 https://message.com") { true }
        assertNull(profile.correction("great", emptyList()))
        assertNull(profile.correction("123456", emptyList()))
        assertNull(profile.correction("mesage", emptyList()))
        profile.acceptPolish("Call Zorabel", "Call Zorabell") { it == "call" }
        assertNull(profile.correction("zorabel", emptyList()))
    }
    @Test fun conflictingChoicesNeedADominantRepeatedResolution() {
        profile.acceptPolish("mesage", "message") { it == "message" }
        profile.acceptPolish("mesage", "massage") { it == "massage" }
        assertNull(profile.correction("mesage", emptyList()))
        repeat(2) { profile.acceptPolish("mesage", "message") { it == "message" } }
        assertEquals("message", profile.correction("mesage", emptyList()))
    }
    @Test fun undoAndRejectedCorrectionStopLearningIt() {
        val receipt = profile.acceptPolish("mesage", "message") { it == "message" }
        profile.retract(receipt)
        assertNull(profile.correction("mesage", emptyList()))
        profile.acceptPolish("mesage", "message") { it == "message" }
        profile.reject("mesage", "message")
        assertNull(profile.correction("mesage", emptyList()))
    }
    @Test fun repeatedPersonalContextChangesRankingAndSurvivesRestart() {
        repeat(6) { profile.observe("tea", listOf("morning")) }
        repeat(9) { profile.observe("coffee", listOf("afternoon")) }
        assertEquals("tea", profile.candidates("", listOf("morning")).first())
        assertEquals("coffee", profile.candidates("", listOf("afternoon")).first())
        profile.flush()
        assertEquals("tea", PersonalTypingProfile(context).also { kotlinx.coroutines.runBlocking { it.ready.await() } }.candidates("", listOf("morning")).first())
        profile.clear()
        assertTrue(profile.candidates("", emptyList()).isEmpty())
    }
    @Test fun predictionEngineUsesPersonalContextButPreservesCompleteWords() {
        val dictionary = DictionaryManager(context)
        dictionary.personalProfile.clear()
        repeat(8) { dictionary.personalProfile.observe("tea", listOf("morning")) }
        assertEquals("tea", dictionary.getGboardPredictions("", listOf("morning"), null).centerCandidate)
        assertEquals("morning", dictionary.getGboardPredictions("morning", emptyList(), null).centerCandidate)
        assertFalse(dictionary.getGboardPredictions("te", listOf("morning"), null).isCenterAutocorrecting)
    }
    @Test fun digitTokensAndAddressesAreExcludedFromUsage() {
        listOf("123456", "test@example.com", "https://example.com", "3.14").forEach { profile.observe(it, emptyList()) }
        assertTrue(profile.candidates("", emptyList()).isEmpty())
    }
    @Test fun glideCaseIgnoresSentenceShiftAndHonorsCapsLock() {
        assertEquals("hello", TypingPolicy.swipeCase("Hello", false))
        assertEquals("world", TypingPolicy.swipeCase("WORLD", false))
        assertEquals("HELLO", TypingPolicy.swipeCase("Hello", true))
    }
}
