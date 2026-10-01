package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTranscriptBufferTest {
    @Test fun correctedFinalReplacesPartialAndNextSegmentAppends() {
        val buffer = VoiceTranscriptBuffer()
        buffer.updatePartial("I has")
        buffer.commitSegment("I have")
        buffer.updatePartial("a meeting")
        assertEquals("I have a meeting", buffer.text)
        buffer.commitSegment("a meeting tomorrow")
        assertEquals("I have a meeting tomorrow", buffer.text)
    }
    @Test fun interruptedSegmentKeepsPartialWithoutRepeatingIt() {
        val buffer = VoiceTranscriptBuffer()
        buffer.updatePartial("hello")
        buffer.commitSegment("")
        buffer.commitSegment("")
        assertEquals("hello", buffer.text)
    }
    @Test fun clearDiscardsAllSessionText() {
        val buffer = VoiceTranscriptBuffer()
        buffer.commitSegment("old")
        buffer.updatePartial("draft")
        buffer.clear()
        assertEquals("", buffer.text)
    }
}
