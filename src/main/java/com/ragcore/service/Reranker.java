package com.ragcore.service;

import com.ragcore.model.Chunk;
import java.util.List;

/**
 * Interface for re-ranking a list of candidate chunks by relevance to a query.
 *
 * <p>Re-ranking is a second-pass scoring step that runs after the initial
 * vector similarity search. The first pass retrieves a broad set of candidates
 * quickly; the re-ranker then reorders them more carefully so the most relevant
 * chunks are at the top.</p>
 */
public interface Reranker {

  /**
   * Re-orders the given chunks by relevance to the query.
   *
   * @param query  the user's question; must not be null or blank
   * @param chunks candidate chunks from the vector store; must not be null
   * @return the same chunks reordered from most to least relevant
   * @throws IllegalArgumentException if query is null/blank or chunks is null
   */
  List<Chunk> rerank(String query, List<Chunk> chunks);
}
