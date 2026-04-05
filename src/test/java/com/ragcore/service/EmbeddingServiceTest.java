package com.ragcore.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EmbeddingServiceImpl}.
 *
 * <p>All HTTP calls are intercepted by a mocked {@link OkHttpClient} — no real network
 * calls are made. Tests cover input validation, response parsing, caching, and error handling.</p>
 */
class EmbeddingServiceTest {

  private OkHttpClient mockHttpClient;
  private EmbeddingServiceImpl embeddingService;

  @BeforeEach
  void setUp() {
    mockHttpClient = mock(OkHttpClient.class);
    embeddingService = new EmbeddingServiceImpl("test-api-key", mockHttpClient);
  }

  // ── Input validation ──────────────────────────────────────────────────────

  @Test
  void embed_nullText_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> embeddingService.embed(null));
  }

  @Test
  void embed_emptyText_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> embeddingService.embed("  "));
  }

  // ── Successful response ───────────────────────────────────────────────────

  @Test
  void embed_validText_returnsEmbeddingVector() throws Exception {
    stubHttpResponse(buildEmbeddingResponse(new float[]{0.1f, 0.2f, 0.3f}));

    float[] result = embeddingService.embed("hello world");

    assertNotNull(result);
    assertEquals(3, result.length);
    assertEquals(0.1f, result[0], 1e-6f);
    assertEquals(0.2f, result[1], 1e-6f);
    assertEquals(0.3f, result[2], 1e-6f);
  }

  // ── Caching ───────────────────────────────────────────────────────────────

  @Test
  void embed_sameTextTwice_callsApiOnlyOnce() throws Exception {
    stubHttpResponse(buildEmbeddingResponse(new float[]{0.5f, 0.6f}));

    float[] first  = embeddingService.embed("cached text");
    float[] second = embeddingService.embed("cached text");

    verify(mockHttpClient, times(1)).newCall(any(Request.class));
    assertArrayEquals(first, second, 1e-6f);
  }

  @Test
  void embed_differentTexts_callsApiForEach() throws Exception {
    stubHttpResponse(buildEmbeddingResponse(new float[]{0.1f, 0.2f}));

    embeddingService.embed("text one");
    embeddingService.embed("text two");

    verify(mockHttpClient, times(2)).newCall(any(Request.class));
  }

  // ── Error handling ────────────────────────────────────────────────────────

  @Test
  void embed_apiReturnsError_throwsRuntimeException() throws Exception {
    Call mockCall = mock(Call.class);
    when(mockHttpClient.newCall(any())).thenReturn(mockCall);

    Response errorResponse = new Response.Builder()
        .request(new Request.Builder().url("https://api.openai.com/v1/embeddings").build())
        .protocol(Protocol.HTTP_1_1)
        .code(401)
        .message("Unauthorized")
        .body(ResponseBody.create("{\"error\":\"invalid api key\"}",
            MediaType.get("application/json")))
        .build();

    when(mockCall.execute()).thenReturn(errorResponse);

    assertThrows(RuntimeException.class, () -> embeddingService.embed("some text"));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Builds a JSON string that mimics the OpenAI embeddings API response.
   * Shape: {@code {"data": [{"embedding": [v0, v1, ...]}]}}
   */
  private String buildEmbeddingResponse(float[] values) {
    JsonArray embeddingArray = new JsonArray();
    for (float v : values) {
      embeddingArray.add(v);
    }
    JsonObject dataItem = new JsonObject();
    dataItem.add("embedding", embeddingArray);

    JsonArray dataArray = new JsonArray();
    dataArray.add(dataItem);

    JsonObject root = new JsonObject();
    root.add("data", dataArray);
    return root.toString();
  }

  /**
   * Configures the mock HTTP client to return the given JSON body for any request.
   *
   * <p>Uses {@code thenAnswer} instead of {@code thenReturn} so that each {@code execute()}
   * call gets a fresh {@link ResponseBody}. OkHttp response bodies can only be read once;
   * reusing the same instance would return empty content on the second read.</p>
   */
  private void stubHttpResponse(String jsonBody) throws Exception {
    Call mockCall = mock(Call.class);
    when(mockHttpClient.newCall(any())).thenReturn(mockCall);
    when(mockCall.execute()).thenAnswer(inv -> new Response.Builder()
        .request(new Request.Builder().url("https://api.openai.com/v1/embeddings").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(ResponseBody.create(jsonBody, MediaType.get("application/json")))
        .build());
  }
}
