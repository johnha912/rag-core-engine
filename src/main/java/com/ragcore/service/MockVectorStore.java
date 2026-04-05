package com.ragcore.service;

import com.ragcore.model.Chunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Mock implementation of VectorStore for Day 2-5 testing.
 * Returns fake data so RagOrchestrator can be tested without Ash's real implementation.
 */
@Service
public class MockVectorStore implements VectorStore {

  @Override
  public void store(List<Chunk> chunks) {
    // Mock: pretend we stored everything, do nothing
    System.out.println("[MockVectorStore] Stored " + chunks.size() + " chunks.");
  }

  @Override
  public List<Chunk> search(String query, int topK) {
    // Mock: return 2 fake chunks so query pipeline has something to work with
    System.out.println("[MockVectorStore] Searching for: " + query);

    List<Chunk> mockResults = new ArrayList<>();

    mockResults.add(new Chunk(
        "This is a mock result about: " + query,
        new HashMap<>(),
        "mock-document.pdf"
    ));

    mockResults.add(new Chunk(
        "Another relevant mock chunk for: " + query,
        new HashMap<>(),
        "mock-document.pdf"
    ));

    return mockResults;
  }
}