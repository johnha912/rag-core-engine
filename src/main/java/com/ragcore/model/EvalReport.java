package com.ragcore.model;

import java.util.List;

/**
 * Aggregated evaluation report summarizing the RAG system's performance
 * across all test questions.
 */
public class EvalReport {

  private final int totalQuestions;
  private final double averageRetrievalHitRate;
  private final double averageAnswerKeywordRate;
  private final List<EvalResult> details;

  public EvalReport(int totalQuestions, double averageRetrievalHitRate,
                    double averageAnswerKeywordRate, List<EvalResult> details) {
    this.totalQuestions = totalQuestions;
    this.averageRetrievalHitRate = averageRetrievalHitRate;
    this.averageAnswerKeywordRate = averageAnswerKeywordRate;
    this.details = details != null ? List.copyOf(details) : List.of();
  }

  public int getTotalQuestions() {
    return totalQuestions;
  }

  /** Average retrieval hit rate across all questions (0.0 to 1.0). */
  public double getAverageRetrievalHitRate() {
    return averageRetrievalHitRate;
  }

  /** Average answer keyword accuracy across all questions (0.0 to 1.0). */
  public double getAverageAnswerKeywordRate() {
    return averageAnswerKeywordRate;
  }

  public List<EvalResult> getDetails() {
    return details;
  }

  @Override
  public String toString() {
    return String.format(
        "EvalReport{total=%d, avgRetrieval=%.2f%%, avgAnswer=%.2f%%}",
        totalQuestions,
        averageRetrievalHitRate * 100,
        averageAnswerKeywordRate * 100);
  }
}