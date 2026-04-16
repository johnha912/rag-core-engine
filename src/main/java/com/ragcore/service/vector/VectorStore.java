package com.ragcore.service.vector;

import com.ragcore.model.Chunk;
import com.ragcore.service.EmbeddingService;
import java.util.List;

/**
 * Interface for storing and searching document chunks by vector similarity.
 *
 * <p>The VectorStore is responsible for:
 * <ul>
 *   <li>Storing chunks along with their embedding vectors</li>
 *   <li>Performing similarity search to find the most relevant chunks
 *       for a given query</li>
 * </ul>
 *
 * <p><strong>Implementation notes:</strong></p>
 * <ul>
 *   <li>Cosine similarity must be implemented manually — do not use
 *       an external math library.</li>
 *   <li>Use {@code CopyOnWriteArrayList} or {@code synchronized} for
 *       thread-safe storage to handle concurrent file uploads.</li>
 * </ul>
 *
 * <p><strong>Contract:</strong> This interface is frozen after Day 1.
 * No modifications without full team agreement.</p>
 */
public interface VectorStore {

  /**
   * Stores a list of chunks into the vector store.
   *
   * <p>Each chunk will be embedded (if not already) and stored for
   * later retrieval via similarity search. Implementations should
   * use the {@link EmbeddingService} to generate embeddings for
   * chunks that do not already have one.</p>
   *
   * @param chunks the list of chunks to store; must not be null
   * @throws IllegalArgumentException if chunks is null
   */
  void store(List<Chunk> chunks);

  /**
   * Searches the vector store for the top-K most relevant chunks
   * to the given query string.
   *
   * <p>The query is first converted to an embedding vector, then compared
   * against all stored chunk embeddings using cosine similarity.
   * Results are returned in descending order of similarity.</p>
   *
   * @param query the search query text; must not be null or empty
   * @param topK  the maximum number of results to return; must be positive
   * @return a list of the most similar chunks, ordered by descending similarity
   * @throws IllegalArgumentException if query is null/empty or topK is not positive
   */
  List<Chunk> search(String query, int topK);
  void clear();
}