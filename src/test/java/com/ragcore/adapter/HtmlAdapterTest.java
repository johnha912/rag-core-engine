package com.ragcore.adapter;

import com.ragcore.model.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HtmlAdapter}.
 *
 */
class HtmlAdapterTest {

  private HtmlAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new HtmlAdapter(new TextCleaner(), new TextSplitter());
  }

  // ========== supports() ==========

  @Test
  void testSupportsHtmlFile() {
    assertTrue(adapter.supports("doc.html"));
    assertTrue(adapter.supports("DOC.HTML"));
    assertTrue(adapter.supports("page.htm"));
  }

  @Test
  void testDoesNotSupportNonHtmlFile() {
    assertFalse(adapter.supports("file.pdf"));
    assertFalse(adapter.supports("file.md"));
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
    assertEquals("HTML", adapter.getDomainName());
  }

  // ========== parse() ==========

  @Test
  void testParseSimpleHtml() throws Exception {
    String html = "<html><body>"
        + "<h1>Introduction</h1>"
        + "<p>This is the introduction paragraph about databases.</p>"
        + "<h2>Creating Tables</h2>"
        + "<p>Use CREATE TABLE to define a new table in the database.</p>"
        + "</body></html>";

    InputStream is = toStream(html);
    List<Chunk> chunks = adapter.parse(is, "guide.html");

    assertFalse(chunks.isEmpty());
    // Verify heading metadata is present
    boolean hasCreateTablesHeading = chunks.stream()
        .anyMatch(c -> "Creating Tables".equals(c.getMetadata().get("heading")));
    assertTrue(hasCreateTablesHeading);
  }

  @Test
  void testParseHtmlStripsScriptAndStyle() throws Exception {
    String html = "<html><body>"
        + "<script>alert('xss');</script>"
        + "<style>.red { color: red; }</style>"
        + "<p>Only this visible text should remain in the output.</p>"
        + "</body></html>";

    InputStream is = toStream(html);
    List<Chunk> chunks = adapter.parse(is, "clean.html");

    assertFalse(chunks.isEmpty());
    for (Chunk chunk : chunks) {
      assertFalse(chunk.getContent().contains("alert"));
      assertFalse(chunk.getContent().contains("color: red"));
    }
  }

  @Test
  void testParseHtmlEmptyBody() throws Exception {
    String html = "<html><body></body></html>";
    InputStream is = toStream(html);
    List<Chunk> chunks = adapter.parse(is, "empty.html");

    assertTrue(chunks.isEmpty());
  }

  @Test
  void testParseNullInputStreamThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(null, "file.html"));
  }

  @Test
  void testParseNullFileNameThrows() {
    InputStream is = toStream("<html><body><p>text</p></body></html>");
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(is, null));
  }

  @Test
  void testParseBlankFileNameThrows() {
    InputStream is = toStream("<html><body><p>text</p></body></html>");
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(is, "   "));
  }

  @Test
  void testChunkSourceMatchesFileName() throws Exception {
    String html = "<html><body><p>Database documentation content here.</p></body></html>";
    InputStream is = toStream(html);
    List<Chunk> chunks = adapter.parse(is, "db-docs.html");

    for (Chunk chunk : chunks) {
      assertEquals("db-docs.html", chunk.getSource());
    }
  }

  // ========== Constructor validation ==========

  @Test
  void testConstructorNullCleanerThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new HtmlAdapter(null, new TextSplitter()));
  }

  @Test
  void testConstructorNullSplitterThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new HtmlAdapter(new TextCleaner(), null));
  }

  // ========== Helper ==========

  private InputStream toStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}