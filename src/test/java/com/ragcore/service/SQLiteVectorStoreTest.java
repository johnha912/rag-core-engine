package com.ragcore.service;

import com.ragcore.model.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SQLiteVectorStore}.
 *
 * <p>Uses a temp directory so each test gets a fresh, isolated database file.
 * {@link EmbeddingService} is mocked — no real API calls are made.</p>
 */
class SQLiteVectorStoreTest {

  @TempDir
  Path tempDir;

  private EmbeddingService mockEmbeddingService;
  private SQLiteVectorStore vectorStore;

  @BeforeEach
  void setUp() {
    mockEmbeddingService = mock(EmbeddingService.class);
    String dbPath = tempDir.resolve("test-store.db").toString();
    vectorStore = new SQLiteVectorStore(mockEmbeddingService, dbPath);
  }

  // ── store() validation ────────────────────────────────────────────────────

  @Test
  void store_nullList_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> vectorStore.store(null));
  }

  @Test
  void store_emptyList_storesNothing() {
    vectorStore.store(Collections.emptyList());
    assertTrue(vectorStore.search("anything", 5).isEmpty());
  }

  @Test
  void store_chunksWithoutEmbedding_embedsEachChunk() {
    when(mockEmbeddingService.embed(anyString())).thenReturn(new float[]{1f, 0f});

    Chunk c1 = new Chunk("text one", new HashMap<>(), "doc.pdf");
    Chunk c2 = new Chunk("text two", new HashMap<>(), "doc.pdf");
    vectorStore.store(List.of(c1, c2));

    // embed called once per chunk during store
    verify(mockEmbeddingService, times(2)).embed(anyString());
    assertTrue(c1.hasEmbedding());
    assertTrue(c2.hasEmbedding());
  }

  @Test
  void store_chunkAlreadyHasEmbedding_doesNotReEmbed() {
    Chunk chunk = new Chunk("pre-embedded", new HashMap<>(), "doc.pdf");
    chunk.setEmbedding(new float[]{0.5f, 0.5f});
    vectorStore.store(List.of(chunk));

    verify(mockEmbeddingService, never()).embed(anyString());
  }

  // ── search() validation ───────────────────────────────────────────────────

  @Test
  void search_nullQuery_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> vectorStore.search(null, 5));
  }

  @Test
  void search_blankQuery_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> vectorStore.search("  ", 5));
  }

  @Test
  void search_zeroTopK_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> vectorStore.search("query", 0));
  }

  @Test
  void search_negativeTopK_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> vectorStore.search("query", -1));
  }

  // ── search() behavior ─────────────────────────────────────────────────────

  @Test
  void search_emptyStore_returnsEmptyList() {
    when(mockEmbeddingService.embed(anyString())).thenReturn(new float[]{1f, 0f});
    assertTrue(vectorStore.search("anything", 5).isEmpty());
  }

  @Test
  void search_returnsTopKMostSimilarChunks() {
    // Query [1,0]: chunkA sim=1.0, chunkB sim=0.0, chunkC sim=-1.0
    when(mockEmbeddingService.embed("query")).thenReturn(new float[]{1f, 0f});

    Chunk chunkA = new Chunk("closest",    new HashMap<>(), "doc.pdf");
    chunkA.setEmbedding(new float[]{1f,  0f});
    Chunk chunkB = new Chunk("orthogonal", new HashMap<>(), "doc.pdf");
    chunkB.setEmbedding(new float[]{0f,  1f});
    Chunk chunkC = new Chunk("opposite",   new HashMap<>(), "doc.pdf");
    chunkC.setEmbedding(new float[]{-1f, 0f});

    vectorStore.store(List.of(chunkA, chunkB, chunkC));

    List<Chunk> results = vectorStore.search("query", 2);
    assertEquals(2, results.size());
    assertEquals("closest",    results.get(0).getContent());
    assertEquals("orthogonal", results.get(1).getContent());
  }

  @Test
  void search_resultsOrderedByDescendingSimilarity() {
    when(mockEmbeddingService.embed("query")).thenReturn(new float[]{1f, 0f, 0f});

    Chunk high = new Chunk("high similarity",   new HashMap<>(), "doc.pdf");
    high.setEmbedding(new float[]{1f,   0f,  0f});
    Chunk mid  = new Chunk("medium similarity", new HashMap<>(), "doc.pdf");
    mid.setEmbedding(new float[]{0.5f, 0.5f, 0f});
    Chunk low  = new Chunk("low similarity",    new HashMap<>(), "doc.pdf");
    low.setEmbedding(new float[]{0f,   1f,  0f});

    vectorStore.store(Arrays.asList(low, high, mid));

    List<Chunk> results = vectorStore.search("query", 3);
    assertEquals("high similarity",   results.get(0).getContent());
    assertEquals("medium similarity", results.get(1).getContent());
    assertEquals("low similarity",    results.get(2).getContent());
  }

  @Test
  void search_topKExceedsStoreSize_returnsAllChunks() {
    when(mockEmbeddingService.embed(anyString())).thenReturn(new float[]{1f, 0f});

    Chunk chunk = new Chunk("only chunk", new HashMap<>(), "doc.pdf");
    chunk.setEmbedding(new float[]{1f, 0f});
    vectorStore.store(List.of(chunk));

    assertEquals(1, vectorStore.search("query", 10).size());
  }

  // ── Persistence (key feature over InMemoryVectorStore) ────────────────────

  @Test
  void persistence_chunkssurviveRestartOfStore() {
    // Store data with first instance
    Chunk chunk = new Chunk("persistent content", new HashMap<>(), "doc.pdf");
    chunk.setEmbedding(new float[]{1f, 0f});
    vectorStore.store(List.of(chunk));

    // Create a second instance pointing to the same DB file
    String dbPath = tempDir.resolve("test-store.db").toString();
    SQLiteVectorStore secondStore = new SQLiteVectorStore(mockEmbeddingService, dbPath);

    when(mockEmbeddingService.embed("query")).thenReturn(new float[]{1f, 0f});
    List<Chunk> results = secondStore.search("query", 5);

    assertEquals(1, results.size());
    assertEquals("persistent content", results.get(0).getContent());
  }

  @Test
  void persistence_metadataRoundtrips() {
    HashMap<String, String> metadata = new HashMap<>();
    metadata.put("page", "42");
    metadata.put("chapter", "Introduction");

    Chunk chunk = new Chunk("content with metadata", metadata, "book.pdf");
    chunk.setEmbedding(new float[]{1f, 0f});
    vectorStore.store(List.of(chunk));

    when(mockEmbeddingService.embed("query")).thenReturn(new float[]{1f, 0f});
    List<Chunk> results = vectorStore.search("query", 1);

    assertEquals("42",           results.get(0).getMetadata().get("page"));
    assertEquals("Introduction", results.get(0).getMetadata().get("chapter"));
  }

  // ── clear() ───────────────────────────────────────────────────────────────

  @Test
  void clear_removesAllChunks() {
    when(mockEmbeddingService.embed(anyString())).thenReturn(new float[]{1f, 0f});

    Chunk chunk = new Chunk("some content", new HashMap<>(), "doc.pdf");
    chunk.setEmbedding(new float[]{1f, 0f});
    vectorStore.store(List.of(chunk));

    vectorStore.clear();

    assertTrue(vectorStore.search("query", 5).isEmpty());
  }

  // ── Serialization roundtrip ────────────────────────────────────────────────

  @Test
  void floatsToBytes_andBytesToFloats_roundtrip() {
    float[] original = {0.1f, -0.5f, 1.0f, Float.MAX_VALUE, Float.MIN_VALUE};
    byte[] bytes = SQLiteVectorStore.floatsToBytes(original);
    float[] restored = SQLiteVectorStore.bytesToFloats(bytes);

    assertArrayEquals(original, restored, 1e-6f);
  }

  // ── cosineSimilarity() ────────────────────────────────────────────────────

  @Test
  void cosineSimilarity_identicalVectors_returnsOne() {
    float[] v = {1f, 2f, 3f};
    assertEquals(1.0, vectorStore.cosineSimilarity(v, v), 1e-6);
  }

  @Test
  void cosineSimilarity_orthogonalVectors_returnsZero() {
    assertEquals(0.0, vectorStore.cosineSimilarity(
        new float[]{1f, 0f}, new float[]{0f, 1f}), 1e-6);
  }

  @Test
  void cosineSimilarity_oppositeVectors_returnsNegativeOne() {
    assertEquals(-1.0, vectorStore.cosineSimilarity(
        new float[]{1f, 0f}, new float[]{-1f, 0f}), 1e-6);
  }

  @Test
  void cosineSimilarity_zeroVector_returnsZero() {
    assertEquals(0.0, vectorStore.cosineSimilarity(
        new float[]{0f, 0f}, new float[]{1f, 1f}), 1e-6);
  }

  @Test
  void cosineSimilarity_differentLengths_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () ->
        vectorStore.cosineSimilarity(new float[]{1f, 2f}, new float[]{1f, 2f, 3f}));
  }
}
