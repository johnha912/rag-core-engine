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
 * Unit tests for RagOrchestrator.
 * Uses Mockito to mock dependencies without Spring context.
 */
class RagOrchestratorTest {

  private DocumentAdapter mockAdapter;
  private VectorStore mockVectorStore;
  private RagOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    mockAdapter = mock(DocumentAdapter.class);
    mockVectorStore = mock(VectorStore.class);
    orchestrator = new RagOrchestrator(List.of(mockAdapter), mockVectorStore);
  }

  @Test
  void initialState_isNotIndexing_andChunkCountIsZero() {
    assertFalse(orchestrator.isIndexing());
    assertEquals(0, orchestrator.getChunkCount());
  }

  @Test
  void index_validTxtFile_storesChunksAndUpdatesCount() throws Exception {
    // Arrange
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.txt", "text/plain", "hello world".getBytes()
    );
    Chunk fakeChunk = new Chunk("hello world", new HashMap<>(), "test.txt");

    when(mockAdapter.supports("test.txt")).thenReturn(true);
    when(mockAdapter.parse(any(), eq("test.txt"))).thenReturn(List.of(fakeChunk));

    // Act
    orchestrator.index(file);

    // Assert
    assertEquals(1, orchestrator.getChunkCount());
    verify(mockVectorStore).store(List.of(fakeChunk));
  }

  @Test
  void index_unsupportedFileType_throwsException() {
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.docx", "application/octet-stream", "data".getBytes()
    );
    when(mockAdapter.supports("test.docx")).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> orchestrator.index(file));
  }

  @Test
  void query_returnsStringWithChunkCount() {
    Chunk fakeChunk = new Chunk("relevant text", new HashMap<>(), "doc.txt");
    when(mockVectorStore.search("What is RAG?", 5)).thenReturn(List.of(fakeChunk));

    String result = orchestrator.query("What is RAG?");

    assertTrue(result.contains("1"));
  }
}