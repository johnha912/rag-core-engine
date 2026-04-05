package com.ragcore.adapter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Splits long text into smaller chunks with configurable size and overlap. This enables the RAG
 * system to process text within token limits while preserving context at chunk boundaries through
 * overlapping.
 */
@Component
public class TextSplitter {

  private final int chunkSize;
  private final int overlap;

  public TextSplitter() {
    this.chunkSize = 1000;
    this.overlap = 200;
  }

  /**
   * Constructs a TextSplitter with the specified chunk size and overlap.
   *
   * @param chunkSize the maximum number of characters per chunk
   * @param overlap   the number of overlapping characters between consecutive chunks
   * @throws IllegalArgumentException if chunkSize is less than 1, overlap is negative, or overlap
   *                                  is greater than or equal to chunkSize
   */
  public TextSplitter(int chunkSize, int overlap) {
    if (chunkSize < 1) {
      throw new IllegalArgumentException("Chunk size must be at least 1.");
    }
    if (overlap < 0) {
      throw new IllegalArgumentException("Overlap cannot be negative.");
    }
    if (overlap >= chunkSize) {
      throw new IllegalArgumentException("Overlap must be less than chunk size.");
    }
    this.chunkSize = chunkSize;
    this.overlap = overlap;
  }

  /**
   * Splits the given text into a list of chunk strings.
   *
   * <p>Each chunk is at most {@code chunkSize} characters. Consecutive chunks
   * share {@code overlap} characters to preserve context at boundaries. The splitter attempts to
   * break at the last whitespace within the chunk boundary for cleaner splits.
   *
   * @param text the text to split
   * @return a list of text chunks; empty list if text is null or blank
   */
  public List<String> split(String text) {
    List<String> chunks = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return chunks;
    }

    int length = text.length();
    int start = 0;

    while (start < length) {
      int end = Math.min(start + chunkSize, length);

      // Try to break at the last whitespace within the chunk if we're not at the end
      if (end < length) {
        int lastSpace = text.lastIndexOf(' ', end);
        if (lastSpace > start) {
          end = lastSpace;
        }
      }

      String chunk = text.substring(start, end).strip();
      if (!chunk.isEmpty()) {
        chunks.add(chunk);
      }

      // Move start forward by (end - start - overlap), ensuring progress
      int step = end - start - overlap;
      if (step <= 0) {
        step = 1; // guarantee forward progress
      }
      start += step;
    }

    return chunks;
  }

  /**
   * Returns the configured chunk size.
   *
   * @return the chunk size
   */
  public int getChunkSize() {
    return chunkSize;
  }

  /**
   * Returns the configured overlap.
   *
   * @return the overlap
   */
  public int getOverlap() {
    return overlap;
  }
}