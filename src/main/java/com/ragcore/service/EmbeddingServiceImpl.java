package com.ragcore.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Primary
public class EmbeddingServiceImpl implements EmbeddingService {

  private static final String OPENAI_EMBED_URL = "https://api.openai.com/v1/embeddings";
  private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

  @Value("${openai.embedding.model:text-embedding-ada-002}")
  private String embedModel;

  private final String apiKey;
  private final OkHttpClient httpClient;
  private final Gson gson;
  private final Map<String, float[]> cache = new ConcurrentHashMap<>();

  public EmbeddingServiceImpl() {
    String key = System.getenv("OPENAI_API_KEY");
    if (key == null || key.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is not set.");
    }
    this.apiKey = key;
    this.httpClient = new OkHttpClient();
    this.gson = new Gson();
  }

  EmbeddingServiceImpl(String apiKey, OkHttpClient httpClient) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API key must not be null or blank.");
    }
    this.apiKey = apiKey;
    this.httpClient = httpClient;
    this.gson = new Gson();
  }

  @Override
  public float[] embed(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Text must not be null or empty.");
    }
    return cache.computeIfAbsent(text, this::callOpenAI);
  }

  private float[] callOpenAI(String text) {
    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("model", embedModel);
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