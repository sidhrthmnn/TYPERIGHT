package com.example

import android.view.textservice.TextInfo
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpellCheckerRegressionTest {
    private lateinit var service: TypeRightSpellCheckerService
    @Before fun setup() { service = Robolectric.buildService(TypeRightSpellCheckerService::class.java).create().get() }
    @After fun tearDown() { service.onDestroy() }

    @Test fun usesSharedCorrectionsAndPreservesRequestIdentity() {
        val result = service.createSession().onGetSuggestions(TextInfo("teh", 42, 7), 3)
        assertEquals("the", result.getSuggestionAt(0))
        assertEquals(42, result.cookie)
        assertEquals(7, result.sequence)
    }

    @Test fun zeroLimitDoesNotReturnSuggestions() {
        assertEquals(0, service.createSession().onGetSuggestions(TextInfo("teh"), 0).suggestionsCount)
    }

    @Test fun sentenceSuggestionsRetainCorrectOffsets() {
        val sentence = service.createSession().onGetSentenceSuggestionsMultiple(arrayOf(TextInfo("hello teh", 4, 2)), 3)[0]
        assertEquals(1, sentence.suggestionsCount)
        assertEquals(6, sentence.getOffsetAt(0))
        assertEquals(3, sentence.getLengthAt(0))
        assertEquals("the", sentence.getSuggestionsInfoAt(0).getSuggestionAt(0))
    }
}
