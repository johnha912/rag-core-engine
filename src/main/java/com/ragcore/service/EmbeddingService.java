package com.ragcore.service;

/**
 * Interface for converting text into numerical vector representations (embeddings).
 *
 * <p>Embeddings are dense floating-point arrays that capture semantic meaning.
 * Two texts with similar meaning will have embeddings with high cosine similarity.
 * This is the core mechanism that enables semantic search in the RAG pipeline.</p>
 *
 * <p><strong>Implementation notes:</strong></p>
 * <ul>
 *   <li>Implementations should cache results to avoid redundant API calls
 *       for identical text inputs.</li>
 *   <li>The OpenAI API key must be read from environment variables —
 *       never hardcoded.</li>
 * </ul>
 *
 * <p><strong>Contract:</strong> This interface is frozen after Day 1.
 * No modifications without full team agreement.</p>
 */
public interface EmbeddingService {

  /**
   * Converts the given text into an embedding vector.
   *
   * <p>The returned array represents the semantic meaning of the input text
   * in a high-dimensional vector space. Typical embedding dimensions are
   * 1536 (for OpenAI text-embedding-ada-002) or 3072 (for text-embedding-3-large).</p>
   *
   * @param text the text to embed; must not be null or empty
   * @return a float array representing the embedding vector
   * @throws IllegalArgumentException if text is null or empty
   * @throws RuntimeException         if the embedding API call fails
   */
  float[] embed(String text);

  /**
   * Embeds a list of texts in one batch, returning embeddings in the same order.
   *
   * <p>The default implementation falls back to calling {@link #embed} sequentially.
   * Implementations should override this with a true batch API call for performance.</p>
   *
   * @param texts list of texts to embed; must not be null
   * @return list of embedding vectors in the same order as the input
   */
  default java.util.List<float[]> embedBatch(java.util.List<String> texts) {
    java.util.List<float[]> result = new java.util.ArrayList<>();
    for (String text : texts) {
      result.add(embed(text));
    }
    return result;
  }
}