package com.ragcore.service;

import com.ragcore.model.Chunk;
import com.ragcore.model.EvalQuestion;
import com.ragcore.model.EvalReport;
import com.ragcore.model.EvalResult;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.ragcore.service.vector.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evaluation service that measures the quality of the RAG pipeline.
 *
 * <p>For each {@link EvalQuestion} in a test set, this service:</p>
 * <ol>
 *   <li><b>Retrieval hit rate:</b> Searches the vector store and checks whether the
 *       retrieved chunks contain the expected source keywords.</li>
 *   <li><b>Answer accuracy:</b> Generates an answer via the full RAG pipeline and checks
 *       whether it contains the expected answer keywords.</li>
 * </ol>
 *
 * <p>Results are aggregated into an {@link EvalReport} with per-question detail and
 * overall averages.</p>
 */
@Service
public class EvalService {

  private static final int TOP_K = 10;

  private final VectorStore vectorStore;
  private final ChatService chatService;
  private final Gson gson;

  @Autowired
  public EvalService(VectorStore vectorStore, ChatService chatService) {
    this.vectorStore = vectorStore;
    this.chatService = chatService;
    this.gson = new Gson();
  }

  /**
   * Loads evaluation questions from a JSON resource on the classpath.
   *
   * <p>Expected format:</p>
   * <pre>
   * [
   *   {
   *     "id": "q1",
   *     "question": "What is ...?",
   *     "expectedAnswer": "...",
   *     "expectedKeywords": ["keyword1", "keyword2"],
   *     "expectedSourceKeywords": ["source_term1"]
   *   },
   *   ...
   * ]
   * </pre>
   *
   * @param resourcePath classpath resource path (e.g., "eval-questions.json")
   * @return list of parsed evaluation questions
   */
  public List<EvalQuestion> loadQuestions(String resourcePath) {
    InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
    if (is == null) {
      throw new IllegalArgumentException("Resource not found: " + resourcePath);
    }

    JsonArray array = gson.fromJson(
        new InputStreamReader(is, StandardCharsets.UTF_8), JsonArray.class);

    List<EvalQuestion> questions = new ArrayList<>();
    for (JsonElement element : array) {
      JsonObject obj = element.getAsJsonObject();
      String id = obj.get("id").getAsString();
      String question = obj.get("question").getAsString();
      String expectedAnswer = obj.has("expectedAnswer")
          ? obj.get("expectedAnswer").getAsString() : "";

      List<String> keywords = new ArrayList<>();
      if (obj.has("expectedKeywords")) {
        for (JsonElement kw : obj.getAsJsonArray("expectedKeywords")) {
          keywords.add(kw.getAsString());
        }
      }

      List<String> sourceKeywords = new ArrayList<>();
      if (obj.has("expectedSourceKeywords")) {
        for (JsonElement kw : obj.getAsJsonArray("expectedSourceKeywords")) {
          sourceKeywords.add(kw.getAsString());
        }
      }

      questions.add(new EvalQuestion(id, question, expectedAnswer, keywords, sourceKeywords));
    }
    return questions;
  }

  /**
   * Runs the full evaluation pipeline on the given test set.
   *
   * @param questions the evaluation questions to test
   * @return an {@link EvalReport} with per-question results and aggregate metrics
   */
  public EvalReport evaluate(List<EvalQuestion> questions) {
    if (questions == null || questions.isEmpty()) {
      throw new IllegalArgumentException("Questions list must not be null or empty.");
    }

    List<EvalResult> results = new ArrayList<>();

    for (EvalQuestion eq : questions) {
      EvalResult result = evaluateSingle(eq);
      results.add(result);
    }

    double avgRetrieval = results.stream()
        .mapToDouble(EvalResult::getRetrievalHitRate).average().orElse(0.0);
    double avgAnswer = results.stream()
        .mapToDouble(EvalResult::getAnswerKeywordRate).average().orElse(0.0);

    return new EvalReport(questions.size(), avgRetrieval, avgAnswer, results);
  }

  /**
   * Evaluates a single question against the RAG pipeline.
   */
  EvalResult evaluateSingle(EvalQuestion eq) {
    // Step 1: Retrieval evaluation — search vector store
    List<Chunk> retrieved;
    try {
      retrieved = vectorStore.search(eq.getQuestion(), TOP_K);
    } catch (Exception e) {
      retrieved = List.of();
    }

    // Combine all retrieved chunk content + source info into one string for matching
    StringBuilder retrievedText = new StringBuilder();
    for (Chunk c : retrieved) {
      retrievedText.append(c.getContent()).append(" ");
      retrievedText.append(c.getSource()).append(" ");
      for (String v : c.getMetadata().values()) {
        retrievedText.append(v).append(" ");
      }
    }
    String retrievedLower = retrievedText.toString().toLowerCase(Locale.ROOT);

    // Check source keyword hits
    List<String> matchedSource = new ArrayList<>();
    List<String> missedSource = new ArrayList<>();
    for (String kw : eq.getExpectedSourceKeywords()) {
      if (retrievedLower.contains(kw.toLowerCase(Locale.ROOT))) {
        matchedSource.add(kw);
      } else {
        missedSource.add(kw);
      }
    }
    double retrievalHitRate = eq.getExpectedSourceKeywords().isEmpty() ? 1.0
        : (double) matchedSource.size() / eq.getExpectedSourceKeywords().size();

    // Step 2: Answer evaluation — generate answer via ChatService
    String generatedAnswer = "";
    try {
      if (!retrieved.isEmpty()) {
        ChatService.ChatResponse response = chatService.ask(eq.getQuestion(), retrieved);
        generatedAnswer = response.getAnswer();
      }
    } catch (Exception e) {
      generatedAnswer = "[ERROR] " + e.getMessage();
    }

    // Check answer keyword hits
    String answerLower = generatedAnswer.toLowerCase(Locale.ROOT);
    List<String> matchedKeywords = new ArrayList<>();
    List<String> missedKeywords = new ArrayList<>();
    for (String kw : eq.getExpectedKeywords()) {
      if (answerLower.contains(kw.toLowerCase(Locale.ROOT))) {
        matchedKeywords.add(kw);
      } else {
        missedKeywords.add(kw);
      }
    }
    double answerKeywordRate = eq.getExpectedKeywords().isEmpty() ? 1.0
        : (double) matchedKeywords.size() / eq.getExpectedKeywords().size();

    return new EvalResult(
        eq.getId(), eq.getQuestion(), generatedAnswer,
        retrievalHitRate, answerKeywordRate,
        matchedKeywords, missedKeywords,
        matchedSource, missedSource);
  }
}