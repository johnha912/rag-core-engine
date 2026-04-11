package com.ragcore.service;

import com.ragcore.model.Chunk;
import com.ragcore.model.EvalQuestion;
import com.ragcore.model.EvalReport;
import com.ragcore.model.EvalResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvalServiceTest {

  private VectorStore mockVectorStore;
  private ChatService mockChatService;
  private EvalService evalService;

  @BeforeEach
  void setUp() {
    mockVectorStore = mock(VectorStore.class);
    mockChatService = mock(ChatService.class);
    evalService = new EvalService(mockVectorStore, mockChatService);
  }

  @Test
  void loadQuestions_validResource_returnsQuestions() {
    List<EvalQuestion> questions = evalService.loadQuestions("eval-questions.json");
    assertNotNull(questions);
    assertEquals(20, questions.size());
    assertEquals("q01", questions.get(0).getId());
  }

  @Test
  void loadQuestions_invalidResource_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> evalService.loadQuestions("nonexistent.json"));
  }

  @Test
  void evaluate_nullList_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> evalService.evaluate(null));
  }

  @Test
  void evaluate_emptyList_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> evalService.evaluate(List.of()));
  }

  @Test
  void evaluateSingle_allKeywordsMatch_perfectScores() throws Exception {
    EvalQuestion eq = new EvalQuestion("q1",
        "What is habitability?",
        "Expected answer",
        List.of("habitability", "landlord"),
        List.of("section", "1941"));

    Map<String, String> meta = new HashMap<>();
    meta.put("page", "5");
    Chunk chunk = new Chunk("Section 1941 requires landlords to maintain habitability.",
        meta, "law.pdf");

    when(mockVectorStore.search("What is habitability?", 10))
        .thenReturn(List.of(chunk));

    ChatService.ChatResponse mockResponse = new ChatService.ChatResponse(
        "The implied warranty of habitability requires the landlord to maintain the unit.",
        List.of("law.pdf (Page 5)"));
    when(mockChatService.ask("What is habitability?", List.of(chunk)))
        .thenReturn(mockResponse);

    EvalResult result = evalService.evaluateSingle(eq);

    assertEquals(1.0, result.getRetrievalHitRate());
    assertEquals(1.0, result.getAnswerKeywordRate());
    assertTrue(result.getMissedKeywords().isEmpty());
    assertTrue(result.getMissedSourceKeywords().isEmpty());
  }

  @Test
  void evaluateSingle_partialKeywordMatch_partialScores() throws Exception {
    EvalQuestion eq = new EvalQuestion("q2",
        "What is the repair-and-deduct remedy?",
        "Expected answer",
        List.of("repair", "deduct", "penalty"),
        List.of("repair", "section_missing"));

    Chunk chunk = new Chunk("The repair-and-deduct remedy allows tenants to fix issues.",
        new HashMap<>(), "guide.pdf");

    when(mockVectorStore.search(eq.getQuestion(), 10))
        .thenReturn(List.of(chunk));

    ChatService.ChatResponse mockResponse = new ChatService.ChatResponse(
        "Tenants can repair defects and deduct costs from rent.",
        List.of("guide.pdf"));
    when(mockChatService.ask(eq.getQuestion(), List.of(chunk)))
        .thenReturn(mockResponse);

    EvalResult result = evalService.evaluateSingle(eq);

    // "repair" matches in retrieved, "section_missing" does not
    assertEquals(0.5, result.getRetrievalHitRate());
    // "repair" and "deduct" match in answer, "penalty" does not
    assertEquals(2.0 / 3.0, result.getAnswerKeywordRate(), 0.01);
    assertEquals(List.of("penalty"), result.getMissedKeywords());
    assertEquals(List.of("section_missing"), result.getMissedSourceKeywords());
  }

  @Test
  void evaluateSingle_noChunksRetrieved_zeroRetrievalScore() throws Exception {
    EvalQuestion eq = new EvalQuestion("q3",
        "What about something unrelated?",
        "Expected",
        List.of("keyword1"),
        List.of("source1"));

    when(mockVectorStore.search(eq.getQuestion(), 10))
        .thenReturn(List.of());

    EvalResult result = evalService.evaluateSingle(eq);

    assertEquals(0.0, result.getRetrievalHitRate());
    assertEquals(0.0, result.getAnswerKeywordRate());
    assertEquals("", result.getGeneratedAnswer());
  }

  @Test
  void evaluateSingle_noExpectedKeywords_defaultsToPerfect() throws Exception {
    EvalQuestion eq = new EvalQuestion("q4",
        "Generic question?",
        "",
        List.of(),
        List.of());

    Chunk chunk = new Chunk("Some content", new HashMap<>(), "file.txt");

    when(mockVectorStore.search(eq.getQuestion(), 10))
        .thenReturn(List.of(chunk));

    ChatService.ChatResponse mockResponse = new ChatService.ChatResponse(
        "Some answer", List.of("file.txt"));
    when(mockChatService.ask(eq.getQuestion(), List.of(chunk)))
        .thenReturn(mockResponse);

    EvalResult result = evalService.evaluateSingle(eq);

    assertEquals(1.0, result.getRetrievalHitRate());
    assertEquals(1.0, result.getAnswerKeywordRate());
  }

  @Test
  void evaluate_multipleQuestions_computesAverages() throws Exception {
    EvalQuestion eq1 = new EvalQuestion("q1", "Q1?", "", List.of("match"), List.of("source"));
    EvalQuestion eq2 = new EvalQuestion("q2", "Q2?", "", List.of("miss"), List.of("miss"));

    Chunk chunk1 = new Chunk("source content with match", new HashMap<>(), "doc.txt");
    Chunk chunk2 = new Chunk("unrelated content", new HashMap<>(), "other.txt");

    when(mockVectorStore.search("Q1?", 10)).thenReturn(List.of(chunk1));
    when(mockVectorStore.search("Q2?", 10)).thenReturn(List.of(chunk2));

    when(mockChatService.ask("Q1?", List.of(chunk1)))
        .thenReturn(new ChatService.ChatResponse("The match is found.", List.of("doc.txt")));
    when(mockChatService.ask("Q2?", List.of(chunk2)))
        .thenReturn(new ChatService.ChatResponse("No relevant info.", List.of("other.txt")));

    EvalReport report = evalService.evaluate(List.of(eq1, eq2));

    assertEquals(2, report.getTotalQuestions());
    // q1: retrieval=1.0, answer=1.0; q2: retrieval=0.0, answer=0.0
    assertEquals(0.5, report.getAverageRetrievalHitRate());
    assertEquals(0.5, report.getAverageAnswerKeywordRate());
  }
}