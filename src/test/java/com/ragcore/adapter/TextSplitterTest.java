package com.ragcore.adapter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TextSplitter}.
 */
class TextSplitterTest {

  // ========== Constructor validation ==========

  @Test
  void testConstructorChunkSizeZeroThrows() {
    assertThrows(IllegalArgumentException.class, () -> new TextSplitter(0, 0));
  }

  @Test
  void testConstructorNegativeChunkSizeThrows() {
    assertThrows(IllegalArgumentException.class, () -> new TextSplitter(-5, 0));
  }

  @Test
  void testConstructorNegativeOverlapThrows() {
    assertThrows(IllegalArgumentException.class, () -> new TextSplitter(100, -1));
  }

  @Test
  void testConstructorOverlapEqualsChunkSizeThrows() {
    assertThrows(IllegalArgumentException.class, () -> new TextSplitter(10, 10));
  }

  @Test
  void testConstructorOverlapExceedsChunkSizeThrows() {
    assertThrows(IllegalArgumentException.class, () -> new TextSplitter(10, 15));
  }

  @Test
  void testConstructorValidParams() {
    TextSplitter splitter = new TextSplitter(100, 20);
    assertEquals(100, splitter.getChunkSize());
    assertEquals(20, splitter.getOverlap());
  }

  // ========== Null / empty / blank ==========

  @Test
  void testSplitNullReturnsEmpty() {
    TextSplitter splitter = new TextSplitter(100, 0);
    assertTrue(splitter.split(null).isEmpty());
  }

  @Test
  void testSplitEmptyReturnsEmpty() {
    TextSplitter splitter = new TextSplitter(100, 0);
    assertTrue(splitter.split("").isEmpty());
  }

  @Test
  void testSplitBlankReturnsEmpty() {
    TextSplitter splitter = new TextSplitter(100, 0);
    assertTrue(splitter.split("     ").isEmpty());
  }

  // ========== Single chunk (zero overlap, text fits in one chunk) ==========

  @Test
  void testShortTextZeroOverlapSingleChunk() {
    TextSplitter splitter = new TextSplitter(100, 0);
    List<String> result = splitter.split("Hello world");
    assertEquals(1, result.size());
    assertEquals("Hello world", result.get(0));
  }

  @Test
  void testExactChunkSizeZeroOverlapSingleChunk() {
    TextSplitter splitter = new TextSplitter(10, 0);
    List<String> result = splitter.split("ABCDEFGHIJ"); // exactly 10 chars
    assertEquals(1, result.size());
    assertEquals("ABCDEFGHIJ", result.get(0));
  }

  // ========== Multiple chunks with zero overlap ==========

  @Test
  void testSplitNoSpacesZeroOverlap() {
    TextSplitter splitter = new TextSplitter(5, 0);
    String text = "ABCDEFGHIJ"; // 10 chars
    List<String> chunks = splitter.split(text);

    assertEquals(2, chunks.size());
    assertEquals("ABCDE", chunks.get(0));
    assertEquals("FGHIJ", chunks.get(1));
  }

  @Test
  void testSplitLongTextZeroOverlap() {
    TextSplitter splitter = new TextSplitter(10, 0);
    String text = "A".repeat(30);
    List<String> chunks = splitter.split(text);

    assertEquals(3, chunks.size());
    for (String chunk : chunks) {
      assertEquals(10, chunk.length());
    }
  }

  // ========== Multiple chunks with overlap (no spaces) ==========

  @Test
  void testOverlapNoSpaces() {
    TextSplitter splitter = new TextSplitter(10, 3);
    String text = "ABCDEFGHIJKLMNOPQRST"; // 20 chars, no spaces
    List<String> chunks = splitter.split(text);

    assertTrue(chunks.size() >= 2, "Should produce multiple chunks");

    // Each chunk <= chunkSize
    for (String chunk : chunks) {
      assertTrue(chunk.length() <= 10);
    }

    // Verify overlap: last 3 chars of chunk[0] == first 3 chars of chunk[1]
    String endOfFirst = chunks.get(0).substring(chunks.get(0).length() - 3);
    String startOfSecond = chunks.get(1).substring(0, 3);
    assertEquals(endOfFirst, startOfSecond,
        "Overlap should match between consecutive chunks");
  }

  @Test
  void testZeroOverlapNoSharedChars() {
    TextSplitter splitter = new TextSplitter(10, 0);
    String text = "ABCDEFGHIJKLMNOPQRST"; // 20 chars
    List<String> chunks = splitter.split(text);

    assertEquals(2, chunks.size());
    assertEquals("ABCDEFGHIJ", chunks.get(0));
    assertEquals("KLMNOPQRST", chunks.get(1));
  }

  // ========== Whitespace-breaking behavior ==========

  @Test
  void testBreaksAtWhitespaceZeroOverlap() {
    TextSplitter splitter = new TextSplitter(10, 0);
    // "Hello beautiful" = 15 chars
    // start=0, end=10, end<15 so try lastIndexOf(' ', 10)
    // "Hello beau" → lastIndexOf(' ',10) finds space at 5
    // chunk = "Hello", start advances to 5
    String text = "Hello beautiful day";
    List<String> chunks = splitter.split(text);

    // First chunk should break at space, not mid-word
    assertEquals("Hello", chunks.get(0));
    assertTrue(chunks.size() >= 2);
  }

  @Test
  void testNoSpaceInChunkDoesNotBreakAtWhitespace() {
    TextSplitter splitter = new TextSplitter(5, 0);
    // No spaces → no whitespace-breaking possible
    String text = "ABCDEFGHIJ";
    List<String> chunks = splitter.split(text);

    assertEquals(2, chunks.size());
    assertEquals("ABCDE", chunks.get(0));
    assertEquals("FGHIJ", chunks.get(1));
  }

  // ========== Forward progress guarantee ==========

  @Test
  void testForwardProgressWithLargeOverlap() {
    // overlap=4, chunkSize=5 → step = end-start-4, could be very small
    TextSplitter splitter = new TextSplitter(5, 4);
    String text = "ABCDEFGHIJ";
    List<String> chunks = splitter.split(text);

    // Should terminate and produce chunks (not infinite loop)
    assertFalse(chunks.isEmpty());
    // Last chunk should contain the last character
    String lastChunk = chunks.get(chunks.size() - 1);
    assertTrue(lastChunk.contains("J"), "Should reach end of text");
  }

  @Test
  void testLargeOverlapRelativeToTextProducesManySlidingChunks() {
    // When overlap > text length, step=1 each time → sliding window behavior
    TextSplitter splitter = new TextSplitter(100, 20);
    List<String> result = splitter.split("Hello world");
    // step = 11 - 0 - 20 = -9 → step=1, so we get ~11 chunks (sliding by 1 char)
    assertTrue(result.size() > 1, "Large overlap on short text creates sliding window");
    assertEquals("Hello world", result.get(0)); // first chunk is full text
  }

  // ========== End-to-end: all text is covered ==========

  @Test
  void testAllTextCoveredZeroOverlap() {
    TextSplitter splitter = new TextSplitter(10, 0);
    String text = "ABCDEFGHIJKLMNOPQRSTUVWXY"; // 25 chars
    List<String> chunks = splitter.split(text);

    // Reconstruct: with zero overlap, concatenating all chunks should give original
    StringBuilder reconstructed = new StringBuilder();
    for (String chunk : chunks) {
      reconstructed.append(chunk);
    }
    assertEquals(text, reconstructed.toString());
  }

  // ========== Getters ==========

  @Test
  void testGetChunkSize() {
    TextSplitter splitter = new TextSplitter(200, 30);
    assertEquals(200, splitter.getChunkSize());
  }

  @Test
  void testGetOverlap() {
    TextSplitter splitter = new TextSplitter(200, 30);
    assertEquals(30, splitter.getOverlap());
  }
}