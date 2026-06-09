package com.destinyai.astrology.ui.chat

import com.destinyai.astrology.domain.model.ChatMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for the pure logic that decides accessibility IDs and ThinkingPill visibility.
 * Extracted from ChatScreen.kt so they are unit-testable without Compose runtime.
 */
class ChatAccessibilityLogicTest {

    // ── assistantContentDescription ───────────────────────────────────────────

    @Test
    fun `ai message not streaming gets ai_message id`() {
        val msg = ChatMessage(id = "1", role = ChatMessage.Role.ASSISTANT, content = "Hello", isStreaming = false)
        assertEquals("ai_message", assistantContentDescription(msg))
    }

    @Test
    fun `ai message streaming with content gets reading_entry id`() {
        val msg = ChatMessage(id = "1", role = ChatMessage.Role.ASSISTANT, content = "Partial", isStreaming = true)
        assertEquals("reading_entry", assistantContentDescription(msg))
    }

    @Test
    fun `ai message streaming with empty content gets streaming_indicator id`() {
        val msg = ChatMessage(id = "1", role = ChatMessage.Role.ASSISTANT, content = "", isStreaming = true)
        assertEquals("streaming_indicator", assistantContentDescription(msg))
    }

    @Test
    fun `ai message not streaming with empty content gets ai_message id`() {
        // Unexpected state but must not crash or produce streaming_indicator
        val msg = ChatMessage(id = "1", role = ChatMessage.Role.ASSISTANT, content = "", isStreaming = false)
        assertEquals("ai_message", assistantContentDescription(msg))
    }

    // ── showThinkingPillInList ────────────────────────────────────────────────

    @Test
    fun `showThinkingPill is false when not streaming`() {
        val msgs = listOf(ChatMessage(id = "1", role = ChatMessage.Role.ASSISTANT, content = "Done"))
        assertFalse(showThinkingPillInList(isStreaming = false, messages = msgs))
    }

    @Test
    fun `showThinkingPill is false once a streaming bubble exists`() {
        // Android creates the streaming assistant message lazily on first SSE chunk.
        // Once that streaming bubble appears, the per-bubble pill takes over and the
        // list-level pill must disappear. iOS parity (ChatView.swift:361 — cosmic
        // progress is bound to the streaming bubble, not the list).
        val msgs = listOf(
            ChatMessage(id = "1", role = ChatMessage.Role.ASSISTANT, content = "Jupiter is…", isStreaming = true),
        )
        assertFalse(showThinkingPillInList(isStreaming = true, messages = msgs))
    }

    @Test
    fun `showThinkingPill is true when streaming and only user message present`() {
        val msgs = listOf(
            ChatMessage(id = "1", role = ChatMessage.Role.USER, content = "Tell me my fortune"),
        )
        assertTrue(showThinkingPillInList(isStreaming = true, messages = msgs))
    }

    @Test
    fun `showThinkingPill is true when welcome assistant message exists but no streaming bubble yet`() {
        // Regression: the previous predicate suppressed the pill whenever ANY assistant
        // message had content, which made the welcome message ("Hello Prabhu!…") hide
        // the pill on every subsequent send. Pill must remain visible until the
        // streaming bubble appears.
        val msgs = listOf(
            ChatMessage(id = "welcome", role = ChatMessage.Role.ASSISTANT, content = "Hello Prabhu!"),
            ChatMessage(id = "u1", role = ChatMessage.Role.USER, content = "Tell me my fortune"),
        )
        assertTrue(showThinkingPillInList(isStreaming = true, messages = msgs))
    }
}
