package com.ragcore.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for the {@link Chunk} model class.
 */
class ChunkTest {

  @Test
  void testChunkCreation() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("page", "1");
    Chunk chunk = new Chunk("Hello world", metadata, "test.pdf");

    assertEquals("Hello world", chunk.getContent());
    assertEquals("test.pdf", chunk.getSource());
    assertEquals("1", chunk.getMetadata().get("page"));
    assertFalse(chunk.hasEmbedding());
  }

  @Test
  void testSetAndGetEmbedding() {
    Chunk chunk = new Chunk("text", new HashMap<>(), "file.pdf");
    float[] embedding = {0.1f, 0.2f, 0.3f};
    chunk.setEmbedding(embedding);

    assertTrue(chunk.hasEmbedding());
    assertArrayEquals(embedding, chunk.getEmbedding());
  }

  @Test
  void testDefensiveCopyEmbedding() {
    Chunk chunk = new Chunk("text", new HashMap<>(), "file.pdf");
    float[] embedding = {0.1f, 0.2f, 0.3f};
    chunk.setEmbedding(embedding);

    // Modifying original array should not affect chunk's embedding
    embedding[0] = 999f;
    assertEquals(0.1f, chunk.getEmbedding()[0]);
  }

  @Test
  void testDefensiveCopyMetadata() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("page", "1");
    Chunk chunk = new Chunk("text", metadata, "file.pdf");

    // Modifying original map should not affect chunk's metadata
    metadata.put("page", "999");
    assertEquals("1", chunk.getMetadata().get("page"));
  }

  @Test
  void testNullContentThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new Chunk(null, new HashMap<>(), "file.pdf"));
  }

  @Test
  void testNullSourceThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new Chunk("text", new HashMap<>(), null));
  }

  @Test
  void testNullMetadataThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new Chunk("text", null, "file.pdf"));
  }

  @Test
  void testEqualsAndHashCode() {
    Map<String, String> meta = new HashMap<>();
    meta.put("page", "1");
    Chunk chunk1 = new Chunk("text", meta, "file.pdf");
    Chunk chunk2 = new Chunk("text", meta, "file.pdf");

    assertEquals(chunk1, chunk2);
    assertEquals(chunk1.hashCode(), chunk2.hashCode());
  }
}