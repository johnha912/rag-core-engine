package com.ragcore.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvalQuestionTest {

  @Test
  void constructor_validInputs_createsInstance() {
    EvalQuestion eq = new EvalQuestion("q1", "What is X?", "X is Y.",
        List.of("X", "Y"), List.of("source1"));
    assertEquals("q1", eq.getId());
    assertEquals("What is X?", eq.getQuestion());
    assertEquals("X is Y.", eq.getExpectedAnswer());
    assertEquals(List.of("X", "Y"), eq.getExpectedKeywords());
    assertEquals(List.of("source1"), eq.getExpectedSourceKeywords());
  }

  @Test
  void constructor_nullId_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> new EvalQuestion(null, "Q?", "A", List.of(), List.of()));
  }

  @Test
  void constructor_blankId_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> new EvalQuestion("  ", "Q?", "A", List.of(), List.of()));
  }

  @Test
  void constructor_nullQuestion_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> new EvalQuestion("q1", null, "A", List.of(), List.of()));
  }

  @Test
  void constructor_blankQuestion_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> new EvalQuestion("q1", "  ", "A", List.of(), List.of()));
  }

  @Test
  void constructor_nullOptionalFields_defaultsToEmpty() {
    EvalQuestion eq = new EvalQuestion("q1", "Q?", null, null, null);
    assertEquals("", eq.getExpectedAnswer());
    assertEquals(List.of(), eq.getExpectedKeywords());
    assertEquals(List.of(), eq.getExpectedSourceKeywords());
  }

  @Test
  void keywords_areImmutable() {
    EvalQuestion eq = new EvalQuestion("q1", "Q?", "A",
        List.of("k1"), List.of("s1"));
    assertThrows(UnsupportedOperationException.class,
        () -> eq.getExpectedKeywords().add("k2"));
    assertThrows(UnsupportedOperationException.class,
        () -> eq.getExpectedSourceKeywords().add("s2"));
  }

  @Test
  void toString_containsIdAndQuestion() {
    EvalQuestion eq = new EvalQuestion("q1", "What is X?", "A",
        List.of(), List.of());
    assertTrue(eq.toString().contains("q1"));
    assertTrue(eq.toString().contains("What is X?"));
  }
}