package com.destinyai.astrology.ui.chat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The chat Send button is derived from ground truth, never a stored flag. These lock the
 * invariant behind the "prefilled question + dead Send + no popup" bug: whenever there is
 * non-blank text and nothing is streaming/loading, Send MUST be enabled.
 */
class ChatSendEnabledTest {

    @Test
    fun `enabled when text present and idle`() {
        assertTrue(chatSendEnabled("Is this a good time?", isStreaming = false, isLoading = false))
    }

    @Test
    fun `the home-question bug case — prefilled text with idle state is always sendable`() {
        // A Home suggested-question hand-off whose auto-send was cancelled leaves the
        // question in the box. Send must NOT be dead — this is the reported symptom.
        assertTrue(chatSendEnabled("What's my most attractive quality?", isStreaming = false, isLoading = false))
    }

    @Test
    fun `disabled when text blank`() {
        assertFalse(chatSendEnabled("", isStreaming = false, isLoading = false))
        assertFalse(chatSendEnabled("   ", isStreaming = false, isLoading = false))
    }

    @Test
    fun `disabled while streaming`() {
        assertFalse(chatSendEnabled("follow up", isStreaming = true, isLoading = false))
    }

    @Test
    fun `disabled while loading`() {
        assertFalse(chatSendEnabled("follow up", isStreaming = false, isLoading = true))
    }
}
