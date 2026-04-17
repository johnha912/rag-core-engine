package com.ragcore.service;

import com.ragcore.adapter.DocumentAdapter;
import com.ragcore.model.Chunk;
import com.ragcore.service.rerank.Reranker;
import com.ragcore.service.vector.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagOrchestratorTest {

  private DocumentAdapter mockAdapter;
  private VectorStore mockVectorStore;
  private ChatService mockChatService;
  private Reranker mockReranker;
  private RagOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    mockAdapter = mock(DocumentAdapter.class);
    mockVectorStore = mock(VectorStore.class);
    mockChatService = mock(ChatService.class);
    mockReranker = mock(Reranker.class);
    orchestrator = new RagOrchestrator(
        List.of(mockAdapter), mockVectorStore, mockChatService, mockReranker
    );
  }

  @Test
  void initialState_isNotIndexing_andChunkCountIsZero() {
    assertFalse(orchestrator.isIndexing());
    assertEquals(0, orchestrator.getChunkCount());
  }

  // ── domain metadata regression ───────────────────────────────────────────

  @Test
  void index_domainIsActuallyWrittenIntoChunk_notLostInDefensiveCopy() throws Exception {
    // Regression: getMetadata() returns a defensive copy, so the old code
    //   chunk.getMetadata().put("domain", ...) silently had no effect.
    // This test fails on the buggy code and passes after the fix.
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.txt", "text/plain", "hello".getBytes()
    );
    Chunk chunk = new Chunk("hello", new HashMap<>(), "test.txt");

    when(mockAdapter.supports("test.txt")).thenReturn(true);
    when(mockAdapter.parse(any(), eq("test.txt"))).thenReturn(List.of(chunk));

    orchestrator.index(file, "film_script");

    // Domain must be readable back through getMetadata() — not silently discarded
    assertEquals("film_script", chunk.getMetadata().get("domain"),
        "domain stamp was lost — putMetadata() must write to the internal map, not a copy");
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
    assertEquals("general", fakeChunk.getMetadata().get("domain"));
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
    assertEquals("rental_law", fakeChunk.getMetadata().get("domain"));
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
    when(mockVectorStore.search("What is RAG?", 10)).thenReturn(List.of());

    String result = orchestrator.query("What is RAG?");

    assertTrue(result.contains("No relevant content found"));
  }

  @Test
  void query_chunksFound_reranksAndCallsChatService() throws Exception {
    // Chunk carries "film" domain in metadata — orchestrator should infer film persona.
    HashMap<String, String> meta = new HashMap<>();
    meta.put("domain", "film");
    Chunk fakeChunk = new Chunk("relevant text", meta, "doc.txt");

    when(mockVectorStore.search("What is RAG?", 10)).thenReturn(List.of(fakeChunk));
    when(mockReranker.rerank("What is RAG?", List.of(fakeChunk))).thenReturn(List.of(fakeChunk));

    ChatService.ChatResponse fakeResponse = mock(ChatService.ChatResponse.class);
    when(fakeResponse.getAnswer()).thenReturn("RAG stands for Retrieval Augmented Generation");
    when(fakeResponse.getSources()).thenReturn(List.of("doc.txt"));
    when(mockChatService.ask(eq("What is RAG?"), eq(List.of(fakeChunk)), isNull(), anyString()))
        .thenReturn(fakeResponse);

    String result = orchestrator.query("What is RAG?");

    assertTrue(result.contains("RAG stands for Retrieval Augmented Generation"));
    verify(mockReranker).rerank("What is RAG?", List.of(fakeChunk));
    verify(mockChatService).ask(eq("What is RAG?"), eq(List.of(fakeChunk)), isNull(), anyString());
  }

  @Test
  void query_infersDomainFromChunkMetadata_majoritVote() throws Exception {
    // 2 film chunks vs 1 legal chunk → should infer "film" and use film role instruction.
    HashMap<String, String> filmMeta = new HashMap<>();
    filmMeta.put("domain", "film");
    HashMap<String, String> legalMeta = new HashMap<>();
    legalMeta.put("domain", "legal");

    Chunk film1 = new Chunk("scene 1", filmMeta, "script.pdf");
    Chunk film2 = new Chunk("scene 2", new HashMap<>(filmMeta), "script.pdf");
    Chunk legal1 = new Chunk("clause A", legalMeta, "contract.pdf");

    when(mockVectorStore.search("hero", 10)).thenReturn(List.of(film1, film2, legal1));
    when(mockReranker.rerank(eq("hero"), any())).thenReturn(List.of(film1, film2, legal1));

    ChatService.ChatResponse fakeResponse = mock(ChatService.ChatResponse.class);
    when(fakeResponse.getAnswer()).thenReturn("The hero appears in scene 1.");
    when(fakeResponse.getSources()).thenReturn(List.of());

    // Capture the roleInstruction argument to assert it's the film persona.
    when(mockChatService.ask(eq("hero"), any(), isNull(), argThat(role ->
        role != null && role.toLowerCase().contains("screenplay"))))
        .thenReturn(fakeResponse);

    String result = orchestrator.query("hero");
    assertTrue(result.contains("The hero appears in scene 1."));
  }

  @Test
  void query_withConversationId_passesIdToChatService() throws Exception {
    HashMap<String, String> meta = new HashMap<>();
    meta.put("domain", "general");
    Chunk fakeChunk = new Chunk("relevant text", meta, "doc.txt");

    when(mockVectorStore.search("Tell me more", 10)).thenReturn(List.of(fakeChunk));
    when(mockReranker.rerank("Tell me more", List.of(fakeChunk))).thenReturn(List.of(fakeChunk));

    ChatService.ChatResponse fakeResponse = mock(ChatService.ChatResponse.class);
    when(fakeResponse.getAnswer()).thenReturn("Here is more detail.");
    when(fakeResponse.getSources()).thenReturn(List.of("doc.txt"));
    when(mockChatService.ask(eq("Tell me more"), eq(List.of(fakeChunk)), eq("session-1"), anyString()))
        .thenReturn(fakeResponse);

    String result = orchestrator.query("Tell me more", "session-1");

    assertTrue(result.contains("Here is more detail."));
    verify(mockChatService).ask(eq("Tell me more"), eq(List.of(fakeChunk)), eq("session-1"), anyString());
  }

  @Test
  void clearConversation_delegatesToChatService() {
    orchestrator.clearConversation("session-1");
    verify(mockChatService).clearConversation("session-1");
  }

  // ── queryStream reranking parity tests ───────────────────────────────────

  @Test
  void queryStream_noChunksFound_emitsNoContentToken() {
    when(mockVectorStore.search(anyString(), eq(10))).thenReturn(List.of());

    List<String> tokens = new java.util.ArrayList<>();
    orchestrator.queryStream("What is RAG?", tokens::add);

    assertEquals(List.of("[NO_CONTENT]"), tokens);
    verifyNoInteractions(mockReranker);
    verifyNoInteractions(mockChatService);
  }

  @Test
  void queryStream_chunksFound_reranksBeforeAskStream() throws Exception {
    HashMap<String, String> meta = new HashMap<>();
    meta.put("domain", "film");
    Chunk fakeChunk = new Chunk("scene 1", meta, "script.pdf");

    when(mockVectorStore.search("hero", 10)).thenReturn(List.of(fakeChunk));
    when(mockReranker.rerank("hero", List.of(fakeChunk))).thenReturn(List.of(fakeChunk));

    orchestrator.queryStream("hero", token -> {});

    // Reranker must be called — this was the missing step before the fix.
    verify(mockReranker).rerank("hero", List.of(fakeChunk));
    // askStream must receive the reranked top-5, not the raw search result.
    verify(mockChatService).askStream(eq("hero"), eq(List.of(fakeChunk)), any(), anyString());
  }

  @Test
  void queryStream_infersDomainFromRerankedChunks() throws Exception {
    HashMap<String, String> filmMeta = new HashMap<>();
    filmMeta.put("domain", "film");
    HashMap<String, String> legalMeta = new HashMap<>();
    legalMeta.put("domain", "legal");

    Chunk film1 = new Chunk("scene 1", filmMeta, "script.pdf");
    Chunk film2 = new Chunk("scene 2", new HashMap<>(filmMeta), "script.pdf");
    Chunk legal1 = new Chunk("clause A", legalMeta, "contract.pdf");

    when(mockVectorStore.search("hero", 10)).thenReturn(List.of(film1, film2, legal1));
    when(mockReranker.rerank(eq("hero"), any())).thenReturn(List.of(film1, film2, legal1));

    orchestrator.queryStream("hero", token -> {});

    // Role instruction passed to askStream should be the film persona (majority vote).
    verify(mockChatService).askStream(
        eq("hero"),
        eq(List.of(film1, film2, legal1)),
        any(),
        argThat(role -> role != null && role.toLowerCase().contains("screenplay"))
    );
  }

  @Test
  void queryStream_limitsContextToTop5() throws Exception {
    // Build 8 reranked chunks; only the first 5 should reach askStream.
    HashMap<String, String> meta = new HashMap<>();
    meta.put("domain", "general");
    List<Chunk> eightChunks = new java.util.ArrayList<>();
    for (int i = 0; i < 8; i++) {
      eightChunks.add(new Chunk("chunk " + i, new HashMap<>(meta), "doc.txt"));
    }

    when(mockVectorStore.search("q", 10)).thenReturn(eightChunks);
    when(mockReranker.rerank(eq("q"), any())).thenReturn(eightChunks);

    orchestrator.queryStream("q", token -> {});

    verify(mockChatService).askStream(
        eq("q"),
        eq(eightChunks.subList(0, 5)),
        any(),
        anyString()
    );
  }
}