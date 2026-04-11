package com.ragcore.service;

import com.ragcore.model.Chunk;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for multi-turn conversation support in {@link ChatService}.
 * Tests cover input validation and conversation history management.
 * Actual API calls are not tested here (require valid key and network).
 */
class ChatServiceConversationTest {

  // ========== ask(question, chunks, conversationId) input validation ==========

  @Test
  void askWithConversationId_nullQuestion_throwsException() {
    ChatService service = new ChatService("sk-test-key");
    List<Chunk> chunks = List.of(new Chunk("content", Map.of("page", "1"), "file.pdf"));
    assertThrows(IllegalArgumentException.class,
        () -> service.ask(null, chunks, "session-1"));
  }

  @Test
  void askWithConversationId_blankQuestion_throwsException() {
    ChatService service = new ChatService("sk-test-key");
    List<Chunk> chunks = List.of(new Chunk("content", Map.of("page", "1"), "file.pdf"));
    assertThrows(IllegalArgumentException.class,
        () -> service.ask("  ", chunks, "session-1"));
  }

  @Test
  void askWithConversationId_nullChunks_throwsException() {
    ChatService service = new ChatService("sk-test-key");
    assertThrows(IllegalArgumentException.class,
        () -> service.ask("What is Java?", null, "session-1"));
  }

  @Test
  void askWithConversationId_emptyChunks_throwsException() {
    ChatService service = new ChatService("sk-test-key");
    assertThrows(IllegalArgumentException.class,
        () -> service.ask("What is Java?", Collections.emptyList(), "session-1"));
  }

  // ========== Conversation history management ==========

  @Test
  void getConversationSize_noHistory_returnsZero() {
    ChatService service = new ChatService("sk-test-key");
    assertEquals(0, service.getConversationSize("nonexistent"));
  }

  @Test
  void getConversationSize_nullId_returnsZero() {
    ChatService service = new ChatService("sk-test-key");
    assertEquals(0, service.getConversationSize(null));
  }

  @Test
  void clearConversation_nonexistentSession_doesNotThrow() {
    ChatService service = new ChatService("sk-test-key");
    assertDoesNotThrow(() -> service.clearConversation("nonexistent"));
  }

  @Test
  void clearConversation_nullId_doesNotThrow() {
    ChatService service = new ChatService("sk-test-key");
    assertDoesNotThrow(() -> service.clearConversation(null));
  }
}