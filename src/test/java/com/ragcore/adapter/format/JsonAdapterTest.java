package com.ragcore.adapter.format;

import com.ragcore.adapter.domain.general.TextCleaner;
import com.ragcore.adapter.domain.general.TextSplitter;
import com.ragcore.model.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JsonAdapter}.
 */
class JsonAdapterTest {

  private JsonAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new JsonAdapter(new TextCleaner(), new TextSplitter(10000, 0));
  }

  // ========== supports() ==========

  @Test
  void testSupportsJsonFile() {
    assertTrue(adapter.supports("data.json"));
    assertTrue(adapter.supports("DATA.JSON"));
  }

  @Test
  void testDoesNotSupportNonJsonFile() {
    assertFalse(adapter.supports("file.csv"));
    assertFalse(adapter.supports("file.html"));
    assertFalse(adapter.supports("file.txt"));
  }

  @Test
  void testSupportsNullAndBlank() {
    assertFalse(adapter.supports(null));
    assertFalse(adapter.supports(""));
    assertFalse(adapter.supports("   "));
  }

  // ========== getDomainName() ==========

  @Test
  void testGetDomainName() {
    assertEquals("JSON", adapter.getDomainName());
  }

  // ========== parse() — array input ==========

  @Test
  void testParseJsonArray() throws Exception {
    String json = "["
        + "{\"name\": \"Alice\", \"role\": \"engineer\"},"
        + "{\"name\": \"Bob\",   \"role\": \"manager\"}"
        + "]";

    List<Chunk> chunks = adapter.parse(toStream(json), "team.json");

    assertEquals(2, chunks.size());
    assertTrue(chunks.get(0).getContent().contains("name: Alice"));
    assertTrue(chunks.get(0).getContent().contains("role: engineer"));
    assertTrue(chunks.get(1).getContent().contains("name: Bob"));
  }

  @Test
  void testArrayChunksHaveElementIndexMetadata() throws Exception {
    String json = "["
        + "{\"id\": \"1\", \"text\": \"First entry\"},"
        + "{\"id\": \"2\", \"text\": \"Second entry\"}"
        + "]";

    List<Chunk> chunks = adapter.parse(toStream(json), "records.json");

    assertEquals("0", chunks.get(0).getMetadata().get("elementIndex"));
    assertEquals("array", chunks.get(0).getMetadata().get("type"));
    assertEquals("1", chunks.get(1).getMetadata().get("elementIndex"));
  }

  @Test
  void testParseEmptyArrayReturnsEmpty() throws Exception {
    List<Chunk> chunks = adapter.parse(toStream("[]"), "empty.json");
    assertTrue(chunks.isEmpty());
  }

  // ========== parse() — object input ==========

  @Test
  void testParseJsonObject() throws Exception {
    String json = "{\"title\": \"Introduction to RAG\", \"author\": \"Jane Doe\", "
        + "\"summary\": \"A guide to retrieval-augmented generation systems.\"}";

    List<Chunk> chunks = adapter.parse(toStream(json), "article.json");

    assertFalse(chunks.isEmpty());
    assertTrue(chunks.get(0).getContent().contains("title: Introduction to RAG"));
    assertEquals("object", chunks.get(0).getMetadata().get("type"));
  }

  @Test
  void testObjectChunkSourceMatchesFileName() throws Exception {
    String json = "{\"key\": \"value\"}";
    List<Chunk> chunks = adapter.parse(toStream(json), "config.json");

    for (Chunk chunk : chunks) {
      assertEquals("config.json", chunk.getSource());
    }
  }

  // ========== parse() — guard clauses ==========

  @Test
  void testParseNullInputStreamThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(null, "file.json"));
  }

  @Test
  void testParseNullFileNameThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(toStream("{\"a\":\"b\"}"), null));
  }

  @Test
  void testParseBlankFileNameThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(toStream("{\"a\":\"b\"}"), "   "));
  }

  // ========== Constructor validation ==========

  @Test
  void testConstructorNullCleanerThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new JsonAdapter(null, new TextSplitter()));
  }

  @Test
  void testConstructorNullSplitterThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new JsonAdapter(new TextCleaner(), null));
  }

  // ========== Helper ==========

  private InputStream toStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}