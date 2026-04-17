package com.ragcore.service;

import com.ragcore.model.Chunk;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ChatService {

  private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
  private static final String DEFAULT_MODEL = "gpt-4o-mini";
  private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

  private final String apiKey;
  private final String model;
  private final OkHttpClient httpClient;
  private final Gson gson;

  // Bounded LRU map: evicts the least-recently-used session once the cap is exceeded.
  // Collections.synchronizedMap wraps it so concurrent requests don't corrupt the structure.
  private final Map<String, List<JsonObject>> conversationHistories =
      Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<JsonObject>> eldest) {
          return size() > 100;
        }
      });

  public ChatService(String apiKey) {
    this(apiKey, DEFAULT_MODEL);
  }

  public ChatService(String apiKey, String model) {
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

  // ── Public API ─────────────────────────────────────────────────────────────

  public ChatResponse ask(String question, List<Chunk> chunks) throws Exception {
    validateInputs(question, chunks);
    return ask(question, chunks, null);
  }

  public ChatResponse ask(String question, List<Chunk> chunks, String conversationId)
      throws Exception {
    return ask(question, chunks, conversationId, null);
  }

  /**
   * Answers a question using the given role instruction as the system prompt.
   *
   * @param roleInstruction the domain-specific role description; if null or blank,
   *                        a generic fallback is used
   */
  public ChatResponse ask(String question, List<Chunk> chunks, String conversationId,
      String roleInstruction) throws Exception {
    validateInputs(question, chunks);

    String systemPrompt = buildSystemPrompt(roleInstruction, chunks);
    JsonArray messages = new JsonArray();
    messages.add(buildMessage("system", systemPrompt));

    if (conversationId != null && !conversationId.isBlank()) {
      List<JsonObject> history = conversationHistories.getOrDefault(conversationId, List.of());
      for (JsonObject msg : history) {
        messages.add(msg);
      }
    }

    messages.add(buildMessage("user", question));

    String answer = callApi(messages, false);

    if (conversationId != null && !conversationId.isBlank()) {
      List<JsonObject> history = conversationHistories
          .computeIfAbsent(conversationId, k -> new ArrayList<>());
      history.add(buildMessage("user", question));
      history.add(buildMessage("assistant", answer));
    }

    return new ChatResponse(answer, buildSources(chunks));
  }

  public void askStream(String question, List<Chunk> chunks,
      java.util.function.Consumer<String> onToken) throws Exception {
    askStream(question, chunks, onToken, null);
  }

  public void askStream(String question, List<Chunk> chunks,
      java.util.function.Consumer<String> onToken, String roleInstruction) throws Exception {
    validateInputs(question, chunks);

    String systemPrompt = buildSystemPrompt(roleInstruction, chunks);
    JsonArray messages = new JsonArray();
    messages.add(buildMessage("system", systemPrompt));
    messages.add(buildMessage("user", question));

    JsonObject requestBody = buildRequestBody(messages, true);
    Request request = buildRequest(requestBody);

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        throw new RuntimeException("OpenAI API error (HTTP " + response.code() + ")");
      }
      var source = response.body().source();
      while (!source.exhausted()) {
        String line = source.readUtf8Line();
        if (line == null || line.isEmpty()) continue;
        if (!line.startsWith("data: ")) continue;

        String data = line.substring(6);
        if (data.equals("[DONE]")) break;

        try {
          JsonObject json = gson.fromJson(data, JsonObject.class);
          JsonObject delta = json.getAsJsonArray("choices")
              .get(0).getAsJsonObject()
              .getAsJsonObject("delta");
          if (delta.has("content")) {
            onToken.accept(delta.get("content").getAsString());
          }
        } catch (Exception ignored) {}
      }
    }
  }

  public void clearConversation(String conversationId) {
    if (conversationId != null) {
      conversationHistories.remove(conversationId);
    }
  }

  public int getConversationSize(String conversationId) {
    if (conversationId == null) return 0;
    return conversationHistories.getOrDefault(conversationId, List.of()).size();
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  private void validateInputs(String question, List<Chunk> chunks) {
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("Question cannot be null or blank.");
    }
    if (chunks == null || chunks.isEmpty()) {
      throw new IllegalArgumentException("Chunks cannot be null or empty.");
    }
  }

  private String buildContext(List<Chunk> chunks) {
    StringBuilder sb = new StringBuilder();
    for (Chunk c : chunks) {
      sb.append("[Source: ").append(c.getSource());
      if (c.getMetadata().containsKey("page")) {
        sb.append(", Page ").append(c.getMetadata().get("page"));
      }
      sb.append("]\n");
      sb.append(c.getContent()).append("\n\n");
    }
    return sb.toString();
  }

  /**
   * Builds the full system prompt from an external role instruction and the retrieved chunks.
   *
   * <p>The {@code roleInstruction} should describe the assistant's persona and domain expertise.
   * If null or blank, a generic fallback is used so the method is safe to call without one.</p>
   *
   * <p>Context (source + content of each chunk) is always appended at the end regardless of
   * the role instruction, so callers never need to include it themselves.</p>
   *
   * @param roleInstruction domain-specific persona text; null falls back to a generic prompt
   * @param chunks          the retrieved document chunks to include as context
   * @return the complete system prompt string ready to send to the LLM
   */
  private String buildSystemPrompt(String roleInstruction, List<Chunk> chunks) {
    String role = (roleInstruction != null && !roleInstruction.isBlank())
        ? roleInstruction
        : "You are a helpful document analysis assistant. "
            + "Answer the user's question based on the provided context. "
            + "If the context does not contain enough information, say so clearly. "
            + "Always cite which source and page your answer comes from. "
            + "Structure your answer clearly with numbered steps when giving advice.";
    return role + "\n\nContext:\n" + buildContext(chunks);
  }

  private JsonObject buildMessage(String role, String content) {
    JsonObject msg = new JsonObject();
    msg.addProperty("role", role);
    msg.addProperty("content", content);
    return msg;
  }

  private JsonObject buildRequestBody(JsonArray messages, boolean stream) {
    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.add("messages", messages);
    body.addProperty("temperature", 0.3);
    if (stream) body.addProperty("stream", true);
    return body;
  }

  private Request buildRequest(JsonObject requestBody) {
    RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON_MEDIA);
    return new Request.Builder()
        .url(OPENAI_CHAT_URL)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("Content-Type", "application/json")
        .post(body)
        .build();
  }

  private String callApi(JsonArray messages, boolean stream) throws Exception {
    Request request = buildRequest(buildRequestBody(messages, stream));
    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        String errorBody = response.body() != null ? response.body().string() : "No body";
        throw new RuntimeException("OpenAI API error (HTTP " + response.code() + "): " + errorBody);
      }
      String responseString = response.body().string();
      JsonObject responseJson = gson.fromJson(responseString, JsonObject.class);
      return responseJson.getAsJsonArray("choices")
          .get(0).getAsJsonObject()
          .getAsJsonObject("message")
          .get("content").getAsString();
    }
  }

  private List<String> buildSources(List<Chunk> chunks) {
    return chunks.stream()
        .map(c -> c.getSource()
            + (c.getMetadata().containsKey("page")
            ? " (Page " + c.getMetadata().get("page") + ")" : ""))
        .distinct()
        .collect(Collectors.toList());
  }

  // ── Inner class ────────────────────────────────────────────────────────────

  public static class ChatResponse {
    private final String answer;
    private final List<String> sources;

    public ChatResponse(String answer, List<String> sources) {
      this.answer = answer;
      this.sources = sources;
    }

    public String getAnswer() { return answer; }
    public List<String> getSources() { return sources; }

    @Override
    public String toString() {
      return "Answer: " + answer + "\nSources: " + sources;
    }
  }
}