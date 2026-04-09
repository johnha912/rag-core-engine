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

class RagOrchestratorTest {

  private DocumentAdapter mockAdapter;
  private VectorStore mockVectorStore;
  private ChatService mockChatService;
  private RagOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    mockAdapter = mock(DocumentAdapter.class);
    mockVectorStore = mock(VectorStore.class);
    mockChatService = mock(ChatService.class);
    orchestrator = new RagOrchestrator(
        List.of(mockAdapter), mockVectorStore, mockChatService
    );
  }

  @Test
  void initialState_isNotIndexing_andChunkCountIsZero() {
    assertFalse(orchestrator.isIndexing());
    assertEquals(0, orchestrator.getChunkCount());
  }

  @Test
  void index_generalDomain_usesSupportsMethod() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.txt", "text/plain", "hello world".getBytes()
    );
    Chunk fakeChunk = new Chunk("hello world", new HashMap<>(), "test.txt");

    when(mockAdapter.supports("test.txt")).thenReturn(true);
    when(mockAdapter.parse(any(), eq("test.txt"))).thenReturn(List.of(fakeChunk));

    orchestrator.index(file, "general");

    assertEquals(1, orchestrator.getChunkCount());
    verify(mockVectorStore).store(List.of(fakeChunk));
  }

  @Test
  void index_specificDomain_usesDomainName() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "contract.pdf", "application/pdf", "legal text".getBytes()
    );
    Chunk fakeChunk = new Chunk("legal text", new HashMap<>(), "contract.pdf");

    when(mockAdapter.getDomainName()).thenReturn("Rental Law");
    when(mockAdapter.parse(any(), eq("contract.pdf"))).thenReturn(List.of(fakeChunk));

    orchestrator.index(file, "rental_law");

    assertEquals(1, orchestrator.getChunkCount());
    verify(mockVectorStore).store(List.of(fakeChunk));
  }

  @Test
  void index_unsupportedDomain_throwsException() {
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.pdf", "application/pdf", "data".getBytes()
    );
    when(mockAdapter.getDomainName()).thenReturn("Film Script");

    assertThrows(IllegalArgumentException.class,
        () -> orchestrator.index(file, "unknown_domain"));
  }

  @Test
  void index_unsupportedFileType_throwsException() {
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.docx", "application/octet-stream", "data".getBytes()
    );
    when(mockAdapter.supports("test.docx")).thenReturn(false);

    assertThrows(IllegalArgumentException.class,
        () -> orchestrator.index(file, "general"));
  }

  @Test
  void query_noChunksFound_returnsFallbackMessage() {
    when(mockVectorStore.search("What is RAG?", 5)).thenReturn(List.of());

    String result = orchestrator.query("What is RAG?");

    assertTrue(result.contains("No relevant content found"));
  }

  @Test
  void query_chunksFound_callsChatService() throws Exception {
    Chunk fakeChunk = new Chunk("relevant text", new HashMap<>(), "doc.txt");
    when(mockVectorStore.search("What is RAG?", 5)).thenReturn(List.of(fakeChunk));

    ChatService.ChatResponse fakeResponse = mock(ChatService.ChatResponse.class);
    when(fakeResponse.getAnswer()).thenReturn("RAG stands for Retrieval Augmented Generation");
    when(fakeResponse.getSources()).thenReturn(List.of("doc.txt"));
    when(mockChatService.ask("What is RAG?", List.of(fakeChunk))).thenReturn(fakeResponse);

    String result = orchestrator.query("What is RAG?");

    assertTrue(result.contains("RAG stands for Retrieval Augmented Generation"));
    verify(mockChatService).ask("What is RAG?", List.of(fakeChunk));
  }
}