package com.ragcore.service;

import com.ragcore.model.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link VectorStore}.
 *
 * <p>Chunks are stored in a {@link CopyOnWriteArrayList}, which is thread-safe and allows
 * concurrent uploads without data corruption. Similarity search uses cosine similarity
 * computed manually — no external math library is used.</p>
 *
 * <p>All data is lost on application restart. This implementation is intended for
 * development and course use; a persistent store would be needed for production.</p>
 */
@Service
public class InMemoryVectorStore implements VectorStore {

  private final List<Chunk> store = new CopyOnWriteArrayList<>();
  private final EmbeddingService embeddingService;

  @Autowired
  public InMemoryVectorStore(EmbeddingService embeddingService) {
    this.embeddingService = embeddingService;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Any chunk that does not already carry an embedding will be embedded via
   * {@link EmbeddingService#embed} before being added to the store.</p>
   */
  @Override
  public void store(List<Chunk> chunks) {
    if (chunks == null) {
      throw new IllegalArgumentException("Chunks must not be null.");
    }
    for (Chunk chunk : chunks) {
      if (!chunk.hasEmbedding()) {
        chunk.setEmbedding(embeddingService.embed(chunk.getContent()));
      }
    }
    store.addAll(chunks);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The query string is embedded and then compared against every stored chunk using
   * cosine similarity. Results are returned in descending order of similarity.</p>
   */
  @Override
  public List<Chunk> search(String query, int topK) {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("Query must not be null or empty.");
    }
    if (topK <= 0) {
      throw new IllegalArgumentException("topK must be positive.");
    }

    float[] queryVector = embeddingService.embed(query);

    return store.stream()
        .filter(Chunk::hasEmbedding)
        .sorted(Comparator.comparingDouble(
            (Chunk chunk) -> cosineSimilarity(queryVector, chunk.getEmbedding())).reversed())
        .limit(topK)
        .collect(Collectors.toList());
  }

  /**
   * Computes the cosine similarity between two float vectors.
   *
   * <p>Formula: {@code cos(θ) = (A · B) / (|A| * |B|)}</p>
   *
   * <p>Returns values in [-1, 1]: 1 means identical direction (most similar),
   * 0 means perpendicular (unrelated), -1 means opposite direction.</p>
   *
   * <p>Uses {@code double} accumulation to avoid rounding errors over high-dimensional
   * vectors (e.g. 1536 dimensions from OpenAI). Returns 0.0 for zero vectors to
   * prevent division by zero.</p>
   *
   * @param a first vector
   * @param b second vector
   * @return cosine similarity in [-1, 1]
   * @throws IllegalArgumentException if the vectors have different lengths
   */
  double cosineSimilarity(float[] a, float[] b) {
    if (a.length != b.length) {
      throw new IllegalArgumentException(
          "Vectors must have the same dimension: " + a.length + " vs " + b.length);
    }

    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (int i = 0; i < a.length; i++) {
      dotProduct += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }

    if (normA == 0.0 || normB == 0.0) {
      return 0.0;
    }
    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  /**
   * Clears all stored chunks from the vector store.
   */
  @Override
  public void clear() {
    store.clear();
  }

  /**
   * Returns the number of chunks currently held in the store.
   *
   * @return chunk count
   */
  public int size() {
    return store.size();
  }
}
