package com.ragcore.adapter;

import com.ragcore.model.Chunk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BasePdfAdapter}.
 */
class BasePdfAdapterTest {

  private BasePdfAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new BasePdfAdapter(new TextCleaner(), new TextSplitter(200, 30));
  }

  // ========== Constructor validation ==========

  @Test
  void testConstructorNullCleanerThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new BasePdfAdapter(null, new TextSplitter(100, 10)));
  }

  @Test
  void testConstructorNullSplitterThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new BasePdfAdapter(new TextCleaner(), null));
  }

  @Test
  void testConstructorValid() {
    BasePdfAdapter a = new BasePdfAdapter(new TextCleaner(), new TextSplitter(100, 10));
    assertNotNull(a);
  }

  // ========== supports() ==========

  @Test
  void testSupportsNull() {
    assertFalse(adapter.supports(null));
  }

  @Test
  void testSupportsBlank() {
    assertFalse(adapter.supports(""));
    assertFalse(adapter.supports("   "));
  }

  @Test
  void testSupportsPdf() {
    assertTrue(adapter.supports("document.pdf"));
    assertTrue(adapter.supports("REPORT.PDF"));
    assertTrue(adapter.supports("My File.pdf"));
  }

  @Test
  void testSupportsTxt() {
    assertTrue(adapter.supports("notes.txt"));
    assertTrue(adapter.supports("README.TXT"));
  }

  @Test
  void testDoesNotSupportOtherFormats() {
    assertFalse(adapter.supports("image.png"));
    assertFalse(adapter.supports("data.csv"));
    assertFalse(adapter.supports("doc.docx"));
    assertFalse(adapter.supports("page.html"));
  }

  // ========== getDomainName() ==========

  @Test
  void testGetDomainName() {
    assertEquals("PDF/TXT", adapter.getDomainName());
  }

  // ========== parse() input validation ==========

  @Test
  void testParseNullInputStreamThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(null, "file.pdf"));
  }

  @Test
  void testParseNullFileNameThrows() {
    InputStream is = new ByteArrayInputStream("content".getBytes());
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(is, null));
  }

  @Test
  void testParseBlankFileNameThrows() {
    InputStream is = new ByteArrayInputStream("content".getBytes());
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(is, "   "));
  }

  @Test
  void testParseUnsupportedFileTypeThrows() {
    InputStream is = new ByteArrayInputStream("content".getBytes());
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(is, "image.png"));
  }

  // ========== parse() TXT files ==========

  @Test
  void testParseTxtProducesChunks() throws Exception {
    String content = "This is a test document with some meaningful content "
        + "that should be cleaned and split into one or more chunks by the adapter.";
    InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

    List<Chunk> chunks = adapter.parse(is, "test.txt");

    assertFalse(chunks.isEmpty(), "Should produce at least one chunk");
    for (Chunk chunk : chunks) {
      assertEquals("test.txt", chunk.getSource());
      assertNotNull(chunk.getMetadata().get("page"));
      assertEquals("1", chunk.getMetadata().get("page"));
      assertNotNull(chunk.getMetadata().get("chunkIndex"));
      assertFalse(chunk.getContent().isBlank());
    }
  }

  @Test
  void testParseTxtEmptyContentReturnsEmptyList() throws Exception {
    InputStream is = new ByteArrayInputStream("   ".getBytes(StandardCharsets.UTF_8));
    List<Chunk> chunks = adapter.parse(is, "empty.txt");
    assertTrue(chunks.isEmpty());
  }

  @Test
  void testParseTxtPreservesFileName() throws Exception {
    String content = "Some content for testing";
    InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

    List<Chunk> chunks = adapter.parse(is, "my-notes.txt");

    assertFalse(chunks.isEmpty());
    assertEquals("my-notes.txt", chunks.get(0).getSource());
  }

  @Test
  void testParseTxtChunkIndexIncrementsCorrectly() throws Exception {
    // Long enough text to produce multiple chunks with chunkSize=200
    String content = "word ".repeat(200);
    InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

    List<Chunk> chunks = adapter.parse(is, "long.txt");

    assertTrue(chunks.size() > 1, "Long text should produce multiple chunks");
    for (int i = 0; i < chunks.size(); i++) {
      assertEquals(String.valueOf(i), chunks.get(i).getMetadata().get("chunkIndex"));
    }
  }

  // ========== parse() TXT from actual file ==========

  @Test
  void testParseTxtFromFile(@TempDir Path tempDir) throws Exception {
    File txtFile = tempDir.resolve("sample.txt").toFile();
    try (FileWriter writer = new FileWriter(txtFile)) {
      writer.write("Object-oriented programming is a paradigm based on the concept of objects. "
          + "Objects contain data in the form of fields and code in the form of methods.");
    }

    try (FileInputStream fis = new FileInputStream(txtFile)) {
      List<Chunk> chunks = adapter.parse(fis, "sample.txt");
      assertFalse(chunks.isEmpty());
      assertTrue(chunks.get(0).getContent().contains("Object-oriented"));
    }
  }

  // ========== parse() PDF (basic validation) ==========

  @Test
  void testParsePdfWithInvalidDataThrows() {
    // Random bytes that are not a valid PDF
    byte[] garbage = "this is not a pdf file at all".getBytes(StandardCharsets.UTF_8);
    InputStream is = new ByteArrayInputStream(garbage);

    assertThrows(Exception.class, () -> adapter.parse(is, "bad.pdf"));
  }
}