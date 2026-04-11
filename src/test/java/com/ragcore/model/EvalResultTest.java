package com.ragcore.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvalResultTest {

  @Test
  void constructor_validInputs_createsInstance() {
    EvalResult result = new EvalResult("q1", "What?", "Answer here",
        0.8, 0.75,
        List.of("k1"), List.of("k2"),
        List.of("s1"), List.of("s2"));

    assertEquals("q1", result.getQuestionId());
    assertEquals("What?", result.getQuestion());
    assertEquals("Answer here", result.getGeneratedAnswer());
    assertEquals(0.8, result.getRetrievalHitRate());
    assertEquals(0.75, result.getAnswerKeywordRate());
    assertEquals(List.of("k1"), result.getMatchedKeywords());
    assertEquals(List.of("k2"), result.getMissedKeywords());
    assertEquals(List.of("s1"), result.getMatchedSourceKeywords());
    assertEquals(List.of("s2"), result.getMissedSourceKeywords());
  }

  @Test
  void constructor_nullLists_defaultsToEmpty() {
    EvalResult result = new EvalResult("q1", "Q?", "A",
        1.0, 1.0, null, null, null, null);
    assertEquals(List.of(), result.getMatchedKeywords());
    assertEquals(List.of(), result.getMissedKeywords());
    assertEquals(List.of(), result.getMatchedSourceKeywords());
    assertEquals(List.of(), result.getMissedSourceKeywords());
  }

  @Test
  void lists_areImmutable() {
    EvalResult result = new EvalResult("q1", "Q?", "A",
        1.0, 1.0,
        List.of("k1"), List.of("k2"),
        List.of("s1"), List.of("s2"));
    assertThrows(UnsupportedOperationException.class,
        () -> result.getMatchedKeywords().add("x"));
  }

  @Test
  void toString_containsMetrics() {
    EvalResult result = new EvalResult("q1", "Q?", "A",
        0.80, 0.60,
        List.of(), List.of(), List.of(), List.of());
    String str = result.toString();
    assertTrue(str.contains("q1"));
    assertTrue(str.contains("0.80"));
    assertTrue(str.contains("0.60"));
  }
}