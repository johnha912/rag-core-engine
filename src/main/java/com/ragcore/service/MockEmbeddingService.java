package com.ragcore.service;

import org.springframework.stereotype.Service;

/**
 * Mock implementation of EmbeddingService for Day 2-5 testing.
 * Returns a fake vector so RagOrchestrator can run without calling OpenAI.
 */
@Service
public class MockEmbeddingService implements EmbeddingService {

  @Override
  public float[] embed(String text) {
    // Mock: return a fake 4-dimension vector, real OpenAI returns 1536
    System.out.println("[MockEmbeddingService] Embedding text: " + text);
    return new float[]{0.1f, 0.2f, 0.3f, 0.4f};
  }
}