package com.ragcore.model;

import java.util.List;

/**
 * Holds the evaluation result for a single question, including retrieval and answer metrics.
 */
public class EvalResult {

  private final String questionId;
  private final String question;
  private final String generatedAnswer;
  private final double retrievalHitRate;
  private final double answerKeywordRate;
  private final List<String> matchedKeywords;
  private final List<String> missedKeywords;
  private final List<String> matchedSourceKeywords;
  private final List<String> missedSourceKeywords;

  public EvalResult(String questionId, String question, String generatedAnswer,
                    double retrievalHitRate, double answerKeywordRate,
                    List<String> matchedKeywords, List<String> missedKeywords,
                    List<String> matchedSourceKeywords, List<String> missedSourceKeywords) {
    this.questionId = questionId;
    this.question = question;
    this.generatedAnswer = generatedAnswer;
    this.retrievalHitRate = retrievalHitRate;
    this.answerKeywordRate = answerKeywordRate;
    this.matchedKeywords = matchedKeywords != null ? List.copyOf(matchedKeywords) : List.of();
    this.missedKeywords = missedKeywords != null ? List.copyOf(missedKeywords) : List.of();
    this.matchedSourceKeywords = matchedSourceKeywords != null
        ? List.copyOf(matchedSourceKeywords) : List.of();
    this.missedSourceKeywords = missedSourceKeywords != null
        ? List.copyOf(missedSourceKeywords) : List.of();
  }

  public String getQuestionId() {
    return questionId;
  }

  public String getQuestion() {
    return question;
  }

  public String getGeneratedAnswer() {
    return generatedAnswer;
  }

  /** Fraction of expectedSourceKeywords found in retrieved chunks (0.0 to 1.0). */
  public double getRetrievalHitRate() {
    return retrievalHitRate;
  }

  /** Fraction of expectedKeywords found in the generated answer (0.0 to 1.0). */
  public double getAnswerKeywordRate() {
    return answerKeywordRate;
  }

  public List<String> getMatchedKeywords() {
    return matchedKeywords;
  }

  public List<String> getMissedKeywords() {
    return missedKeywords;
  }

  public List<String> getMatchedSourceKeywords() {
    return matchedSourceKeywords;
  }

  public List<String> getMissedSourceKeywords() {
    return missedSourceKeywords;
  }

  @Override
  public String toString() {
    return String.format("EvalResult{id='%s', retrievalHit=%.2f, answerAccuracy=%.2f}",
        questionId, retrievalHitRate, answerKeywordRate);
  }
}