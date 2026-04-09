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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles retrieved chunks and the user's question into a prompt,
 * sends it to the OpenAI Chat API, and returns the answer with source metadata.
 */
public class ChatService {

  private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
  private static final String DEFAULT_MODEL = "gpt-4o-mini";
  private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

  private final String apiKey;
  private final String model;
  private final OkHttpClient httpClient;
  private final Gson gson;

  /**
   * Constructs a ChatService with the given API key and default model.
   *
   * @param apiKey the OpenAI API key (from environment variable)
   * @throws IllegalArgumentException if apiKey is null or blank
   */
  public ChatService(String apiKey) {
    this(apiKey, DEFAULT_MODEL);
  }

  /**
   * Constructs a ChatService with the given API key and model.
   *
   * @param apiKey the OpenAI API key
   * @param model  the model name (e.g., "gpt-3.5-turbo", "gpt-4")
   * @throws IllegalArgumentException if apiKey is null or blank
   */
  public ChatService(String apiKey, String model) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API key cannot be null or blank.");
    }
    this.apiKey = apiKey;
    this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
    this.httpClient = new OkHttpClient();
    this.gson = new Gson();
  }

  /**
   * Generates an answer to the user's question using the provided context chunks.
   *
   * <p>Assembles a system prompt with the top-K chunks as context,
   * sends the question to OpenAI, and returns the response along with
   * source information for traceability.
   *
   * @param question the user's question
   * @param chunks   the top-K retrieved chunks for context
   * @return a {@link ChatResponse} containing the answer and sources
   * @throws Exception if the API call fails
   */
  public ChatResponse ask(String question, List<Chunk> chunks) throws Exception {
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("Question cannot be null or blank.");
    }
    if (chunks == null || chunks.isEmpty()) {
      throw new IllegalArgumentException("Chunks cannot be null or empty.");
    }

    // Build context from chunks
    StringBuilder contextBuilder = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      Chunk c = chunks.get(i);
      contextBuilder.append("[Source: ").append(c.getSource());
      if (c.getMetadata().containsKey("page")) {
        contextBuilder.append(", Page ").append(c.getMetadata().get("page"));
      }
      contextBuilder.append("]\n");
      contextBuilder.append(c.getContent()).append("\n\n");
    }

    String systemPrompt = "You are an expert legal and document analysis assistant with deep knowledge "
        + "of California tenant rights law, including California Civil Code Section 1941 "
        + "(implied warranty of habitability), constructive eviction, quiet enjoyment rights, "
        + "and tenant remedies. "
        + "Answer the user's question based primarily on the provided context. "
        + "When the context supports it, enrich your answer with relevant legal concepts "
        + "such as 'constructive eviction', 'implied warranty of habitability', "
        + "'California Civil Code Section 1941', or other applicable laws. "
        + "If the context does not contain enough information, say so clearly. "
        + "Always cite which source and page your answer comes from. "
        + "Structure your answer clearly with numbered steps when giving advice.\n\n"
        + "Context:\n" + contextBuilder;;

    // Build JSON request body
    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("model", model);

    JsonArray messages = new JsonArray();

    JsonObject systemMessage = new JsonObject();
    systemMessage.addProperty("role", "system");
    systemMessage.addProperty("content", systemPrompt);
    messages.add(systemMessage);

    JsonObject userMessage = new JsonObject();
    userMessage.addProperty("role", "user");
    userMessage.addProperty("content", question);
    messages.add(userMessage);

    requestBody.add("messages", messages);
    requestBody.addProperty("temperature", 0.3);

    // Send HTTP request using OkHttp
    RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON_MEDIA);

    Request request = new Request.Builder()
        .url(OPENAI_CHAT_URL)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("Content-Type", "application/json")
        .post(body)
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        String errorBody = response.body() != null ? response.body().string() : "No body";
        throw new RuntimeException("OpenAI API error (HTTP " + response.code()
            + "): " + errorBody);
      }

      String responseString = response.body().string();
      JsonObject responseJson = gson.fromJson(responseString, JsonObject.class);

      String answer = responseJson
          .getAsJsonArray("choices")
          .get(0).getAsJsonObject()
          .getAsJsonObject("message")
          .get("content").getAsString();

      // Collect source info
      List<String> sources = chunks.stream()
          .map(c -> c.getSource()
              + (c.getMetadata().containsKey("page")
              ? " (Page " + c.getMetadata().get("page") + ")" : ""))
          .distinct()
          .collect(Collectors.toList());

      return new ChatResponse(answer, sources);
    }
  }

  /**
   * Represents a chat response containing the AI-generated answer and source references.
   */
  public static class ChatResponse {

    private final String answer;
    private final List<String> sources;

    /**
     * Constructs a ChatResponse.
     *
     * @param answer  the generated answer text
     * @param sources list of source references (filename + page)
     */
    public ChatResponse(String answer, List<String> sources) {
      this.answer = answer;
      this.sources = sources;
    }

    public String getAnswer() {
      return answer;
    }

    public List<String> getSources() {
      return sources;
    }

    @Override
    public String toString() {
      return "Answer: " + answer + "\nSources: " + sources;
    }
  }
}