package com.ragcore.service.rerank;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ragcore.model.Chunk;
import okhttp3.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
/**
 * Re-ranks candidate chunks using a single LLM call.
 *
 * <p>Sends the query and all candidate chunks to OpenAI in one request and asks
 * the model to return a JSON array of indices ordered by relevance. If the response
 * cannot be parsed, the original order is preserved as a safe fallback.</p>
 *
 * <p>Example LLM response: {@code [2, 0, 4, 1, 3]}</p>
 */
public class LlmReranker implements Reranker {

  private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
  private static final String DEFAULT_MODEL = "gpt-4o-mini";
  private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

  /** Maximum characters of each chunk shown to the LLM to keep the prompt short. */
  private static final int CHUNK_PREVIEW_LENGTH = 200;

  private final String apiKey;
  private final String model;
  private final OkHttpClient httpClient;
  private final Gson gson;

  public LlmReranker(String apiKey) {
    this(apiKey, DEFAULT_MODEL);
  }

  public LlmReranker(String apiKey, String model) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API key cannot be null or blank.");
    }
    this.apiKey = apiKey;
    this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
    this.httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();
    this.gson = new Gson();
  }

  /**
   * {@inheritDoc}
   *
   * <p>If there is only one chunk (or none), skips the LLM call and returns immediately.
   * If the LLM call fails or returns unparseable output, falls back to the original order.</p>
   */
  @Override
  public List<Chunk> rerank(String query, List<Chunk> chunks) {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("Query must not be null or empty.");
    }
    if (chunks == null) {
      throw new IllegalArgumentException("Chunks must not be null.");
    }
    if (chunks.size() <= 1) {
      return new ArrayList<>(chunks);
    }

    try {
      List<Integer> rankedIndices = callLlmForRanking(query, chunks);
      return buildRankedList(chunks, rankedIndices);
    } catch (Exception e) {
      // Fallback: return original order if the LLM call fails
      return new ArrayList<>(chunks);
    }
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  private List<Integer> callLlmForRanking(String query, List<Chunk> chunks) throws Exception {
    String prompt = buildPrompt(query, chunks);

    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("model", model);
    requestBody.addProperty("temperature", 0.0);

    JsonArray messages = new JsonArray();
    JsonObject userMessage = new JsonObject();
    userMessage.addProperty("role", "user");
    userMessage.addProperty("content", prompt);
    messages.add(userMessage);
    requestBody.add("messages", messages);

    RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON_MEDIA);
    Request request = new Request.Builder()
        .url(OPENAI_CHAT_URL)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("Content-Type", "application/json")
        .post(body)
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        throw new RuntimeException("OpenAI API error: HTTP " + response.code());
      }
      String responseText = response.body().string();
      JsonObject responseJson = gson.fromJson(responseText, JsonObject.class);
      String content = responseJson
          .getAsJsonArray("choices")
          .get(0).getAsJsonObject()
          .getAsJsonObject("message")
          .get("content").getAsString()
          .trim();

      return parseIndices(content, chunks.size());
    }
  }

  private String buildPrompt(String query, List<Chunk> chunks) {
    StringBuilder sb = new StringBuilder();
    sb.append("You are a relevance ranking assistant. ")
      .append("Given a search query and a list of text chunks, ")
      .append("rank the chunks by how relevant they are to the query.\n\n")
      .append("Return ONLY a valid JSON array of indices (0-based integers), ")
      .append("ordered from most relevant to least relevant. ")
      .append("Example: [2, 0, 4, 1, 3]\n\n")
      .append("Query: ").append(query).append("\n\n")
      .append("Chunks:\n");

    for (int i = 0; i < chunks.size(); i++) {
      String preview = chunks.get(i).getContent();
      if (preview.length() > CHUNK_PREVIEW_LENGTH) {
        preview = preview.substring(0, CHUNK_PREVIEW_LENGTH) + "...";
      }
      sb.append("[").append(i).append("] ").append(preview).append("\n");
    }
    return sb.toString();
  }

  /**
   * Parses a JSON array string like "[2, 0, 4]" into a list of valid indices.
   *
   * <p>Extracts the first {@code [...]} block from the content to tolerate
   * any extra explanation text the LLM may add around the array.
   * Returns {@code null} if parsing fails or any index is out of range —
   * the caller falls back to the original order in that case.</p>
   */
  public List<Integer> parseIndices(String content, int chunkCount) {
    try {
      int start = content.indexOf('[');
      int end   = content.lastIndexOf(']');
      if (start == -1 || end == -1 || end <= start) {
        return null;
      }
      JsonArray arr = gson.fromJson(content.substring(start, end + 1), JsonArray.class);
      List<Integer> indices = new ArrayList<>();
      for (int i = 0; i < arr.size(); i++) {
        int idx = arr.get(i).getAsInt();
        if (idx < 0 || idx >= chunkCount) {
          return null;
        }
        indices.add(idx);
      }
      return indices;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Builds the final reranked list using the LLM-provided index order.
   * Any chunks not mentioned by the LLM are appended at the end.
   * Falls back to original order if {@code rankedIndices} is null.
   */
  private List<Chunk> buildRankedList(List<Chunk> chunks, List<Integer> rankedIndices) {
    if (rankedIndices == null) {
      return new ArrayList<>(chunks);
    }
    List<Chunk> result = new ArrayList<>();
    for (int idx : rankedIndices) {
      result.add(chunks.get(idx));
    }
    Set<Integer> seen = new HashSet<>(rankedIndices);

    for (int i = 0; i < chunks.size(); i++) {
      if (!seen.contains(i)) {
        result.add(chunks.get(i));
      }
    }
    return result;
  }
}
