package com.ragcore.model;

import java.util.List;

/**
 * Represents a single question/answer pair in the RAG evaluation test set.
 *
 * <p>Each entry contains the question to ask, the expected answer keywords
 * (for measuring answer accuracy), and expected source keywords (for measuring
 * retrieval hit rate).</p>
 */
public class EvalQuestion {

  private final String id;
  private final String question;
  private final String expectedAnswer;
  private final List<String> expectedKeywords;
  private final List<String> expectedSourceKeywords;

  /**
   * Constructs an evaluation question.
   *
   * @param id                     unique identifier for this test case
   * @param question               the question to ask the RAG system
   * @param expectedAnswer         a reference answer for comparison
   * @param expectedKeywords       key terms that should appear in a correct answer
   * @param expectedSourceKeywords key terms that should appear in retrieved chunk sources/content
   */
  public EvalQuestion(String id, String question, String expectedAnswer,
                      List<String> expectedKeywords, List<String> expectedSourceKeywords) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("ID cannot be null or blank.");
    }
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("Question cannot be null or blank.");
    }
    this.id = id;
    this.question = question;
    this.expectedAnswer = expectedAnswer != null ? expectedAnswer : "";
    this.expectedKeywords = expectedKeywords != null ? List.copyOf(expectedKeywords) : List.of();
    this.expectedSourceKeywords = expectedSourceKeywords != null
        ? List.copyOf(expectedSourceKeywords) : List.of();
  }

  public String getId() {
    return id;
  }

  public String getQuestion() {
    return question;
  }

  public String getExpectedAnswer() {
    return expectedAnswer;
  }

  public List<String> getExpectedKeywords() {
    return expectedKeywords;
  }

  public List<String> getExpectedSourceKeywords() {
    return expectedSourceKeywords;
  }

  @Override
  public String toString() {
    return "EvalQuestion{id='" + id + "', question='" + question + "'}";
  }
}