package com.ragcore.service;

import com.ragcore.model.Chunk;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChatService}. Note: Tests that call the actual OpenAI API are excluded
 * (require valid key and network). These tests cover input validation and the ChatResponse inner
 * class.
 */
class ChatServiceTest {

  // ========== Constructor validation ==========

  @Test
  void testConstructorNullApiKeyThrows() {
    assertThrows(IllegalArgumentException.class, () -> new ChatService(null));
  }

  @Test
  void testConstructorBlankApiKeyThrows() {
    assertThrows(IllegalArgumentException.class, () -> new ChatService(""));
    assertThrows(IllegalArgumentException.class, () -> new ChatService("   "));
  }

  @Test
  void testConstructorValidApiKey() {
    ChatService service = new ChatService("sk-test-key-123");
    assertNotNull(service);
  }

  @Test
  void testConstructorWithModel() {
    ChatService service = new ChatService("sk-test-key", "gpt-4");
    assertNotNull(service);
  }

  @Test
  void testConstructorWithBlankModelDefaultsGracefully() {
    // Should not throw — falls back to default model
    ChatService service = new ChatService("sk-test-key", "");
    assertNotNull(service);
  }

  @Test
  void testConstructorWithNullModelDefaultsGracefully() {
    ChatService service = new ChatService("sk-test-key", null);
    assertNotNull(service);
  }

  // ========== ask() input validation ==========

  @Test
  void testAskNullQuestionThrows() {
    ChatService service = new ChatService("sk-test-key");
    List<Chunk> chunks = List.of(new Chunk("content", Map.of("page", "1"), "file.pdf"));
    assertThrows(IllegalArgumentException.class,
        () -> service.ask(null, chunks));
  }

  @Test
  void testAskBlankQuestionThrows() {
    ChatService service = new ChatService("sk-test-key");
    List<Chunk> chunks = List.of(new Chunk("content", Map.of("page", "1"), "file.pdf"));
    assertThrows(IllegalArgumentException.class,
        () -> service.ask("   ", chunks));
  }

  @Test
  void testAskNullChunksThrows() {
    ChatService service = new ChatService("sk-test-key");
    assertThrows(IllegalArgumentException.class,
        () -> service.ask("What is Java?", null));
  }

  @Test
  void testAskEmptyChunksThrows() {
    ChatService service = new ChatService("sk-test-key");
    assertThrows(IllegalArgumentException.class,
        () -> service.ask("What is Java?", Collections.emptyList()));
  }

  // ========== ChatResponse inner class ==========

  @Test
  void testChatResponseGetAnswer() {
    ChatService.ChatResponse response = new ChatService.ChatResponse(
        "Java is a programming language.",
        List.of("doc.pdf (Page 1)")
    );
    assertEquals("Java is a programming language.", response.getAnswer());
  }

  @Test
  void testChatResponseGetSources() {
    ChatService.ChatResponse response = new ChatService.ChatResponse(
        "Some answer",
        List.of("file1.pdf (Page 1)", "file2.pdf (Page 3)")
    );
    assertEquals(2, response.getSources().size());
    assertTrue(response.getSources().contains("file1.pdf (Page 1)"));
    assertTrue(response.getSources().contains("file2.pdf (Page 3)"));
  }

  @Test
  void testChatResponseToString() {
    ChatService.ChatResponse response = new ChatService.ChatResponse(
        "OOP uses objects.",
        List.of("lecture.pdf (Page 5)")
    );
    String str = response.toString();
    assertTrue(str.contains("OOP uses objects."));
    assertTrue(str.contains("lecture.pdf"));
  }

  @Test
  void testChatResponseEmptySources() {
    ChatService.ChatResponse response = new ChatService.ChatResponse(
        "Answer without sources",
        Collections.emptyList()
    );
    assertEquals("Answer without sources", response.getAnswer());
    assertTrue(response.getSources().isEmpty());
  }
}