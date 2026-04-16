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
 * Unit tests for {@link CsvAdapter}.
 */
class CsvAdapterTest {

  private CsvAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new CsvAdapter(new TextCleaner(), new TextSplitter(10000, 0));
  }

  // ========== supports() ==========

  @Test
  void testSupportsCsvFile() {
    assertTrue(adapter.supports("data.csv"));
    assertTrue(adapter.supports("DATA.CSV"));
  }

  @Test
  void testDoesNotSupportNonCsvFile() {
    assertFalse(adapter.supports("file.json"));
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
    assertEquals("CSV", adapter.getDomainName());
  }

  // ========== parse() ==========

  @Test
  void testParseSimpleCsv() throws Exception {
    String csv = "name,age,city\n"
        + "Alice,30,New York\n"
        + "Bob,25,Los Angeles\n";

    List<Chunk> chunks = adapter.parse(toStream(csv), "people.csv");

    assertEquals(2, chunks.size());
    assertTrue(chunks.get(0).getContent().contains("name: Alice"));
    assertTrue(chunks.get(0).getContent().contains("age: 30"));
    assertTrue(chunks.get(1).getContent().contains("name: Bob"));
  }

  @Test
  void testParseQuotedFields() throws Exception {
    String csv = "title,description\n"
        + "\"Hello, World\",\"A greeting, very common\"\n";

    List<Chunk> chunks = adapter.parse(toStream(csv), "quotes.csv");

    assertEquals(1, chunks.size());
    assertTrue(chunks.get(0).getContent().contains("Hello, World"));
    assertTrue(chunks.get(0).getContent().contains("A greeting, very common"));
  }

  @Test
  void testParseSkipsBlankRows() throws Exception {
    String csv = "name,city\n"
        + "Alice,NYC\n"
        + "\n"
        + "Bob,LA\n";

    List<Chunk> chunks = adapter.parse(toStream(csv), "data.csv");

    assertEquals(2, chunks.size());
  }

  @Test
  void testParseEmptyFileReturnsEmpty() throws Exception {
    List<Chunk> chunks = adapter.parse(toStream(""), "empty.csv");
    assertTrue(chunks.isEmpty());
  }

  @Test
  void testParseHeaderOnlyReturnsEmpty() throws Exception {
    String csv = "name,age,city\n";
    List<Chunk> chunks = adapter.parse(toStream(csv), "headers-only.csv");
    assertTrue(chunks.isEmpty());
  }

  @Test
  void testRowIndexMetadata() throws Exception {
    String csv = "product,price\n"
        + "Widget,9.99\n"
        + "Gadget,14.99\n";

    List<Chunk> chunks = adapter.parse(toStream(csv), "products.csv");

    assertEquals("0", chunks.get(0).getMetadata().get("rowIndex"));
    assertEquals("1", chunks.get(1).getMetadata().get("rowIndex"));
  }

  @Test
  void testChunkSourceMatchesFileName() throws Exception {
    String csv = "name,value\nFoo,Bar\n";
    List<Chunk> chunks = adapter.parse(toStream(csv), "test.csv");

    for (Chunk chunk : chunks) {
      assertEquals("test.csv", chunk.getSource());
    }
  }

  @Test
  void testParseNullInputStreamThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(null, "file.csv"));
  }

  @Test
  void testParseNullFileNameThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(toStream("a,b\n1,2\n"), null));
  }

  @Test
  void testParseBlankFileNameThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(toStream("a,b\n1,2\n"), "   "));
  }

  // ========== Constructor validation ==========

  @Test
  void testConstructorNullCleanerThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new CsvAdapter(null, new TextSplitter()));
  }

  @Test
  void testConstructorNullSplitterThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new CsvAdapter(new TextCleaner(), null));
  }

  // ========== Helper ==========

  private InputStream toStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}