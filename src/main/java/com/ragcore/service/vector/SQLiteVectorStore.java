package com.ragcore.service.vector;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ragcore.model.Chunk;
import com.ragcore.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SQLite-backed implementation of {@link VectorStore}.
 *
 * <p>Chunks are persisted to a local SQLite file so they survive application restarts.
 * Embeddings are stored as raw BLOB (4 bytes per float, big-endian).
 * Metadata is stored as a JSON string.</p>
 *
 * <p>Search loads all embeddings into memory and ranks by cosine similarity —
 * identical algorithm to {@link InMemoryVectorStore}.</p>
 */
@Service
@Primary
public class SQLiteVectorStore implements VectorStore {

  private static final String CREATE_TABLE_SQL =
      "CREATE TABLE IF NOT EXISTS chunks ("
          + "id        INTEGER PRIMARY KEY AUTOINCREMENT,"
          + "content   TEXT    NOT NULL,"
          + "source    TEXT    NOT NULL,"
          + "metadata  TEXT    NOT NULL,"
          + "embedding BLOB    NOT NULL"
          + ")";

  private static final Type METADATA_TYPE =
      new TypeToken<Map<String, String>>() {}.getType();

  private final EmbeddingService embeddingService;
  private final String dbPath;
  private final Gson gson = new Gson();

  @Autowired
  public SQLiteVectorStore(EmbeddingService embeddingService,
                           @Value("${sqlite.db.path:rag-store.db}") String dbPath) {
    this.embeddingService = embeddingService;
    this.dbPath = dbPath;
    initDb();
  }

  // ── VectorStore interface ──────────────────────────────────────────────────

  /**
   * {@inheritDoc}
   *
   * <p>Chunks without embeddings are embedded first, then batch-inserted in a single
   * transaction for performance.</p>
   */
  @Override
  public void store(List<Chunk> chunks) {
    if (chunks == null) {
      throw new IllegalArgumentException("Chunks must not be null.");
    }
    if (chunks.isEmpty()) {
      return;
    }

    String sql = "INSERT INTO chunks (content, source, metadata, embedding) VALUES (?, ?, ?, ?)";
    try (Connection conn = connect();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      conn.setAutoCommit(false);
      for (Chunk chunk : chunks) {
        if (!chunk.hasEmbedding()) {
          chunk.setEmbedding(embeddingService.embed(chunk.getContent()));
        }
        ps.setString(1, chunk.getContent());
        ps.setString(2, chunk.getSource());
        ps.setString(3, gson.toJson(chunk.getMetadata()));
        ps.setBytes(4, floatsToBytes(chunk.getEmbedding()));
        ps.addBatch();
      }
      ps.executeBatch();
      conn.commit();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to store chunks: " + e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>All stored embeddings are loaded from SQLite, ranked by cosine similarity
   * against the query embedding, and the top-K results are returned.</p>
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

    return loadAll().stream()
        .filter(Chunk::hasEmbedding)
        .sorted(Comparator.comparingDouble(
            (Chunk c) -> cosineSimilarity(queryVector, c.getEmbedding())).reversed())
        .limit(topK)
        .collect(Collectors.toList());
  }

  /** {@inheritDoc} */
  @Override
  public void clear() {
    try (Connection conn = connect();
         Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM chunks");
    } catch (SQLException e) {
      throw new RuntimeException("Failed to clear chunks: " + e.getMessage(), e);
    }
  }

  // ── Internal helpers ───────────────────────────────────────────────────────

  private void initDb() {
    try (Connection conn = connect();
         Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_TABLE_SQL);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to initialize SQLite database: " + e.getMessage(), e);
    }
  }

  private Connection connect() throws SQLException {
    return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
  }

  private List<Chunk> loadAll() {
    List<Chunk> result = new ArrayList<>();
    String sql = "SELECT content, source, metadata, embedding FROM chunks";
    try (Connection conn = connect();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {
      while (rs.next()) {
        String content = rs.getString("content");
        String source  = rs.getString("source");
        Map<String, String> metadata =
            gson.fromJson(rs.getString("metadata"), METADATA_TYPE);
        float[] embedding = bytesToFloats(rs.getBytes("embedding"));

        Chunk chunk = new Chunk(content, metadata, source);
        chunk.setEmbedding(embedding);
        result.add(chunk);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to load chunks: " + e.getMessage(), e);
    }
    return result;
  }

  // ── Serialization ──────────────────────────────────────────────────────────

  /** Converts a float[] to a byte[] (4 bytes per float, big-endian). */
  public static byte[] floatsToBytes(float[] floats) {
    ByteBuffer buf = ByteBuffer.allocate(floats.length * 4);
    for (float f : floats) {
      buf.putFloat(f);
    }
    return buf.array();
  }

  /** Converts a byte[] back to float[] (inverse of {@link #floatsToBytes}). */
  public static float[] bytesToFloats(byte[] bytes) {
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    float[] floats = new float[bytes.length / 4];
    for (int i = 0; i < floats.length; i++) {
      floats[i] = buf.getFloat();
    }
    return floats;
  }

  // ── Cosine similarity ──────────────────────────────────────────────────────

  /**
   * Computes cosine similarity between two float vectors.
   * Returns 0.0 for zero vectors to prevent division by zero.
   */
  public double cosineSimilarity(float[] a, float[] b) {
    if (a.length != b.length) {
      throw new IllegalArgumentException(
          "Vectors must have the same dimension: " + a.length + " vs " + b.length);
    }
    double dot = 0.0, normA = 0.0, normB = 0.0;
    for (int i = 0; i < a.length; i++) {
      dot   += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    if (normA == 0.0 || normB == 0.0) return 0.0;
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
