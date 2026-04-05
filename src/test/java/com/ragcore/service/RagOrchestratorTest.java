package com.ragcore.service;

import com.ragcore.adapter.DocumentAdapter;
import com.ragcore.model.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RagOrchestrator}.
 *
 * <p>All dependencies (DocumentAdapter, VectorStore, ChatService) are mocked.
 * No Spring context is loaded — tests run in plain JUnit with Mockito.</p>
 */
class RagOrchestratorTest {

  private DocumentAdapter mockAdapter;
  private VectorStore mockVectorStore;
  private ChatService mockChatService;
  private RagOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    mockAdapter     = mock(DocumentAdapter.class);
    mockVectorStore = mock(VectorStore.class);
    mockChatService = mock(ChatService.class);
    orchestrator    = new RagOrchestrator(List.of(mockAdapter), mockVectorStore, mockChatService);
  }

  // ── Initial state ─────────────────────────────────────────────────────────

  @Test
  void initialState_isNotIndexing_andChunkCountIsZero() {
    assertFalse(orchestrator.isIndexing());
    assertEquals(0, orchestrator.getChunkCount());
  }

  // ── index() ───────────────────────────────────────────────────────────────

  @Test
  void index_validTxtFile_storesChunksAndUpdatesCount() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.txt", "text/plain", "hello world".getBytes());

    Chunk fakeChunk = new Chunk("hello world", new HashMap<>(), "test.txt");
    when(mockAdapter.supports("test.txt")).thenReturn(true);
    when(mockAdapter.parse(any(), eq("test.txt"))).thenReturn(List.of(fakeChunk));

    orchestrator.index(file);

    assertEquals(1, orchestrator.getChunkCount());
    verify(mockVectorStore).store(List.of(fakeChunk));
  }

  @Test
  void index_unsupportedFileType_throwsException() {
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.docx", "application/octet-stream", "data".getBytes());

    when(mockAdapter.supports("test.docx")).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> orchestrator.index(file));
  }

  // ── query() ───────────────────────────────────────────────────────────────

  @Test
  void query_withRelevantChunks_returnsAnswerFromChatService() throws Exception {
    Chunk fakeChunk = new Chunk(
        "RAG stands for Retrieval-Augmented Generation.", new HashMap<>(), "doc.pdf");

    when(mockVectorStore.search("What is RAG?", 5)).thenReturn(List.of(fakeChunk));

    ChatService.ChatResponse fakeResponse = new ChatService.ChatResponse(
        "RAG stands for Retrieval-Augmented Generation.", List.of("doc.pdf"));

    when(mockChatService.ask(eq("What is RAG?"), anyList())).thenReturn(fakeResponse);

    String result = orchestrator.query("What is RAG?");

    assertTrue(result.contains("RAG stands for Retrieval-Augmented Generation."));
    assertTrue(result.contains("doc.pdf"));
  }

  @Test
  void query_noRelevantChunks_returnsFallbackMessage() {
    when(mockVectorStore.search(anyString(), anyInt())).thenReturn(List.of());

    String result = orchestrator.query("What is RAG?");

    assertTrue(result.toLowerCase().contains("no relevant content"));
    // ChatService must not be called when there are no chunks to provide as context.
    verifyNoInteractions(mockChatService);
  }

  @Test
  void query_chatServiceThrows_throwsRuntimeException() throws Exception {
    Chunk fakeChunk = new Chunk("some content", new HashMap<>(), "doc.pdf");
    when(mockVectorStore.search(anyString(), anyInt())).thenReturn(List.of(fakeChunk));
    when(mockChatService.ask(anyString(), anyList()))
        .thenThrow(new RuntimeException("API failure"));

    assertThrows(RuntimeException.class, () -> orchestrator.query("What is RAG?"));
  }
}
