package com.ragcore.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvalReportTest {

  @Test
  void constructor_validInputs_createsInstance() {
    EvalResult r1 = new EvalResult("q1", "Q?", "A", 0.8, 0.9,
        List.of(), List.of(), List.of(), List.of());
    EvalReport report = new EvalReport(1, 0.8, 0.9, List.of(r1));

    assertEquals(1, report.getTotalQuestions());
    assertEquals(0.8, report.getAverageRetrievalHitRate());
    assertEquals(0.9, report.getAverageAnswerKeywordRate());
    assertEquals(1, report.getDetails().size());
  }

  @Test
  void constructor_nullDetails_defaultsToEmpty() {
    EvalReport report = new EvalReport(0, 0.0, 0.0, null);
    assertEquals(List.of(), report.getDetails());
  }

  @Test
  void details_areImmutable() {
    EvalReport report = new EvalReport(0, 0.0, 0.0, List.of());
    assertThrows(UnsupportedOperationException.class,
        () -> report.getDetails().add(null));
  }

  @Test
  void toString_containsPercentages() {
    EvalReport report = new EvalReport(5, 0.85, 0.70, List.of());
    String str = report.toString();
    assertTrue(str.contains("85.00%"));
    assertTrue(str.contains("70.00%"));
    assertTrue(str.contains("5"));
  }
}