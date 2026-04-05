package com.ragcore.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production implementation of {@link EmbeddingService} backed by the OpenAI Embeddings API.
 *
 * <p>Uses model {@code text-embedding-ada-002}, which returns 1536-dimensional vectors.
 * Results are cached in memory to avoid redundant API calls for identical inputs.</p>
 *
 * <p>Requires the {@code OPENAI_API_KEY} environment variable to be set before startup.</p>
 */
@Service
@Primary
public class EmbeddingServiceImpl implements EmbeddingService {

  private static final String OPENAI_EMBED_URL = "https://api.openai.com/v1/embeddings";
  private static final String EMBED_MODEL = "text-embedding-ada-002";
  private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

  private final String apiKey;
  private final OkHttpClient httpClient;
  private final Gson gson;
  private final Map<String, float[]> cache = new ConcurrentHashMap<>();

  /**
   * Default constructor used by Spring. Reads the API key from the environment.
   *
   * @throws IllegalStateException if {@code OPENAI_API_KEY} is not set
   */
  public EmbeddingServiceImpl() {
    String key = System.getenv("OPENAI_API_KEY");
    if (key == null || key.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is not set.");
    }
    this.apiKey = key;
    this.httpClient = new OkHttpClient();
    this.gson = new Gson();
  }

  /**
   * Package-private constructor for unit tests. Accepts a pre-built {@link OkHttpClient}
   * so tests can inject a mock and avoid real network calls.
   *
   * @param apiKey     the API key to use
   * @param httpClient the HTTP client to use
   */
  EmbeddingServiceImpl(String apiKey, OkHttpClient httpClient) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API key must not be null or blank.");
    }
    this.apiKey = apiKey;
    this.httpClient = httpClient;
    this.gson = new Gson();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Results are cached: a second call with the same text returns the cached vector
   * without making another API request.</p>
   */
  @Override
  public float[] embed(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Text must not be null or empty.");
    }
    return cache.computeIfAbsent(text, this::callOpenAI);
  }

  /**
   * Sends a POST request to the OpenAI Embeddings endpoint and parses the response.
   *
   * @param text the text to embed
   * @return the embedding as a float array
   * @throws RuntimeException if the API call fails or returns a non-2xx status
   */
  private float[] callOpenAI(String text) {
    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("model", EMBED_MODEL);
    requestBody.addProperty("input", text);

    RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON_MEDIA);

    Request request = new Request.Builder()
        .url(OPENAI_EMBED_URL)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("Content-Type", "application/json")
        .post(body)
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        String errorBody = response.body() != null ? response.body().string() : "No body";
        throw new RuntimeException(
            "OpenAI Embedding API error (HTTP " + response.code() + "): " + errorBody);
      }

      String responseString = response.body().string();
      JsonObject responseJson = gson.fromJson(responseString, JsonObject.class);

      // Response shape: { "data": [ { "embedding": [float, ...] } ] }
      JsonArray embeddingArray = responseJson
          .getAsJsonArray("data")
          .get(0).getAsJsonObject()
          .getAsJsonArray("embedding");

      float[] embedding = new float[embeddingArray.size()];
      for (int i = 0; i < embeddingArray.size(); i++) {
        embedding[i] = embeddingArray.get(i).getAsFloat();
      }
      return embedding;

    } catch (IOException e) {
      throw new RuntimeException("Failed to call OpenAI Embedding API: " + e.getMessage(), e);
    }
  }
}
