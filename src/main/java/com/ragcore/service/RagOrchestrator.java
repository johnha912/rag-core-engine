package com.ragcore.service;

import com.ragcore.adapter.DocumentAdapter;
import com.ragcore.model.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the RAG indexing and query pipelines.
 * Acts as the central coordinator between DocumentAdapter,
 * VectorStore, and ChatService.
 */
@Service
public class RagOrchestrator {

  private final List<DocumentAdapter> adapters;
  private final VectorStore vectorStore;

  private final AtomicBoolean indexing = new AtomicBoolean(false);
  private final AtomicInteger chunkCount = new AtomicInteger(0);

  @Autowired
  public RagOrchestrator(List<DocumentAdapter> adapters, VectorStore vectorStore) {
    this.adapters = adapters;
    this.vectorStore = vectorStore;
  }

  /**
   * Indexes a file: parse → embed → store.
   */
  public void index(MultipartFile file) throws Exception {
    indexing.set(true);
    try {
      String fileName = file.getOriginalFilename();

      // Find the right adapter for this file type
      DocumentAdapter adapter = adapters.stream()
          .filter(a -> a.supports(fileName))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException(
              "No adapter found for file: " + fileName));

      // Parse → Store
      List<Chunk> chunks = adapter.parse(file.getInputStream(), fileName);
      vectorStore.store(chunks);
      chunkCount.addAndGet(chunks.size());
    } finally {
      indexing.set(false);
    }
  }

  /**
   * Queries the system: retrieve top-K chunks → return them.
   * ChatService will be wired in Day 6.
   */
  public String query(String question) {
    // TODO Day 6: wire ChatService here
    List<Chunk> relevant = vectorStore.search(question, 5);
    return "Found " + relevant.size() + " relevant chunks. (ChatService coming Day 6)";
  }

  public boolean isIndexing() {
    return indexing.get();
  }

  public int getChunkCount() {
    return chunkCount.get();
  }
}