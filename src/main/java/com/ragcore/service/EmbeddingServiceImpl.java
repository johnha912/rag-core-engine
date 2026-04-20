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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Primary
public class EmbeddingServiceImpl implements EmbeddingService {

  private static final String OPENAI_EMBED_URL = "https://api.openai.com/v1/embeddings";
  private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

  /** Maximum number of texts sent to OpenAI in a single embedding request. */
  private static final int EMBED_BATCH_SIZE = 100;

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
    this.httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();
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

  @Override
  public List<float[]> embedBatch(List<String> texts) {
    if (texts == null || texts.isEmpty()) return List.of();
    // Separate cached from uncached
    float[][] result = new float[texts.size()][];
    List<Integer> uncachedIdx = new ArrayList<>();
    List<String> uncachedTexts = new ArrayList<>();
    for (int i = 0; i < texts.size(); i++) {
      float[] cached = cache.get(texts.get(i));
      if (cached != null) {
        result[i] = cached;
      } else {
        uncachedIdx.add(i);
        uncachedTexts.add(texts.get(i));
      }
    }
    if (!uncachedTexts.isEmpty()) {
      List<float[]> embeddings = new ArrayList<>();
      for (int start = 0; start < uncachedTexts.size(); start += EMBED_BATCH_SIZE) {
        int end = Math.min(start + EMBED_BATCH_SIZE, uncachedTexts.size());
        embeddings.addAll(callOpenAIBatch(uncachedTexts.subList(start, end)));
      }
      for (int i = 0; i < uncachedIdx.size(); i++) {
        result[uncachedIdx.get(i)] = embeddings.get(i);
        cache.put(uncachedTexts.get(i), embeddings.get(i));
      }
    }
    return Arrays.asList(result);
  }

  private List<float[]> callOpenAIBatch(List<String> texts) {
    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("model", embedModel);
    JsonArray inputArray = new JsonArray();
    for (String t : texts) inputArray.add(t);
    requestBody.add("input", inputArray);
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
      JsonArray dataArray = responseJson.getAsJsonArray("data");
      float[][] result = new float[texts.size()][];
      for (int i = 0; i < dataArray.size(); i++) {
        JsonObject item = dataArray.get(i).getAsJsonObject();
        int index = item.get("index").getAsInt();
        JsonArray embArr = item.getAsJsonArray("embedding");
        float[] vec = new float[embArr.size()];
        for (int j = 0; j < embArr.size(); j++) vec[j] = embArr.get(j).getAsFloat();
        result[index] = vec;
      }
      return Arrays.asList(result);
    } catch (IOException e) {
      throw new RuntimeException("Failed to call OpenAI Embedding API: " + e.getMessage(), e);
    }
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