package com.ragcore.model;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Represents a chunk of text extracted from a document.
 * Each chunk carries its content, metadata about its origin,
 * the source document name, and an optional embedding vector.
 */
public class Chunk {

  /** The text content of this chunk. */
  private final String content;

  /** Metadata about this chunk (e.g., page number, position). */
  private final Map<String, String> metadata;

  /** The source document name or path this chunk was extracted from. */
  private final String source;

  /** The embedding vector for this chunk (null if not yet embedded). */
  private float[] embedding;

  /**
   * Constructs a Chunk with the given content, metadata, and source.
   *
   * @param content  the text content of this chunk; must not be null or empty
   * @param metadata key-value metadata about this chunk; must not be null
   * @param source   the source document identifier; must not be null or empty
   * @throws IllegalArgumentException if any argument is null or content/source is empty
   */
  public Chunk(String content, Map<String, String> metadata, String source) {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Content must not be null or empty.");
    }
    if (metadata == null) {
      throw new IllegalArgumentException("Metadata must not be null.");
    }
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("Source must not be null or empty.");
    }
    this.content = content;
    this.metadata = new HashMap<>(metadata);  // defensive copy
    this.source = source;
    this.embedding = null;
  }

  /**
   * Returns the text content of this chunk.
   *
   * @return the content string
   */
  public String getContent() {
    return content;
  }

  /**
   * Returns a defensive copy of the metadata map.
   *
   * @return a copy of the metadata
   */
  public Map<String, String> getMetadata() {
    return new HashMap<>(metadata);  // defensive copy
  }

  /**
   * Returns the source document identifier.
   *
   * @return the source string
   */
  public String getSource() {
    return source;
  }

  /**
   * Returns a defensive copy of the embedding vector, or null if not set.
   *
   * @return a copy of the embedding array, or null
   */
  public float[] getEmbedding() {
    if (embedding == null) {
      return null;
    }
    return Arrays.copyOf(embedding, embedding.length);  // defensive copy
  }

  /**
   * Sets the embedding vector for this chunk (stores a defensive copy).
   *
   * @param embedding the embedding vector; must not be null or empty
   * @throws IllegalArgumentException if embedding is null or empty
   */
  public void setEmbedding(float[] embedding) {
    if (embedding == null || embedding.length == 0) {
      throw new IllegalArgumentException("Embedding must not be null or empty.");
    }
    this.embedding = Arrays.copyOf(embedding, embedding.length);  // defensive copy
  }

  /**
   * Checks whether this chunk has an embedding vector set.
   *
   * @return true if the embedding is not null
   */
  public boolean hasEmbedding() {
    return embedding != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Chunk chunk = (Chunk) o;
    return Objects.equals(content, chunk.content)
        && Objects.equals(source, chunk.source)
        && Objects.equals(metadata, chunk.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(content, source, metadata);
  }

  @Override
  public String toString() {
    return "Chunk{"
        + "source='" + source + '\''
        + ", contentLength=" + content.length()
        + ", metadata=" + metadata
        + ", hasEmbedding=" + hasEmbedding()
        + '}';
  }
}