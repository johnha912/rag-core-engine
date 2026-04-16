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
 * Unit tests for {@link MarkdownAdapter}.
 */
class MarkdownAdapterTest {

  private MarkdownAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new MarkdownAdapter(new TextCleaner(), new TextSplitter());
  }

  // ========== supports() ==========

  @Test
  void testSupportsMdFile() {
    assertTrue(adapter.supports("readme.md"));
    assertTrue(adapter.supports("GUIDE.MD"));
  }

  @Test
  void testDoesNotSupportNonMdFile() {
    assertFalse(adapter.supports("file.html"));
    assertFalse(adapter.supports("file.pdf"));
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
    assertEquals("Markdown", adapter.getDomainName());
  }

  // ========== parse() ==========

  @Test
  void testParseMarkdownWithHeadings() throws Exception {
    String md = "# Getting Started\n"
        + "This guide helps you set up the database.\n\n"
        + "## Installation\n"
        + "Download the installer from the official website.\n\n"
        + "### Configuration\n"
        + "Edit the config file to set your connection string.\n";

    InputStream is = toStream(md);
    List<Chunk> chunks = adapter.parse(is, "setup.md");

    assertFalse(chunks.isEmpty());

    boolean hasInstallation = chunks.stream()
        .anyMatch(c -> "Installation".equals(c.getMetadata().get("heading")));
    assertTrue(hasInstallation);

    boolean hasLevel2 = chunks.stream()
        .anyMatch(c -> "2".equals(c.getMetadata().get("headingLevel")));
    assertTrue(hasLevel2);
  }

  @Test
  void testParseMarkdownNoHeadings() throws Exception {
    String md = "This is a plain markdown file with no headings.\n"
        + "It should still produce chunks with Introduction as heading.";

    InputStream is = toStream(md);
    List<Chunk> chunks = adapter.parse(is, "plain.md");

    assertFalse(chunks.isEmpty());
    for (Chunk chunk : chunks) {
      assertEquals("Introduction", chunk.getMetadata().get("heading"));
      assertEquals("0", chunk.getMetadata().get("headingLevel"));
    }
  }

  @Test
  void testParseEmptyMarkdown() throws Exception {
    String md = "   \n\n   ";
    InputStream is = toStream(md);
    List<Chunk> chunks = adapter.parse(is, "empty.md");

    assertTrue(chunks.isEmpty());
  }

  @Test
  void testParseNullInputStreamThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(null, "file.md"));
  }

  @Test
  void testParseNullFileNameThrows() {
    InputStream is = toStream("# Title\nSome content.");
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(is, null));
  }

  @Test
  void testParseBlankFileNameThrows() {
    InputStream is = toStream("# Title\nSome content.");
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(is, "   "));
  }

  @Test
  void testChunkSourceMatchesFileName() throws Exception {
    String md = "# API Reference\nDetails about the API endpoints.";
    InputStream is = toStream(md);
    List<Chunk> chunks = adapter.parse(is, "api-docs.md");

    for (Chunk chunk : chunks) {
      assertEquals("api-docs.md", chunk.getSource());
    }
  }

  // ========== Constructor validation ==========

  @Test
  void testConstructorNullCleanerThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new MarkdownAdapter(null, new TextSplitter()));
  }

  @Test
  void testConstructorNullSplitterThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new MarkdownAdapter(new TextCleaner(), null));
  }

  // ========== Helper ==========

  private InputStream toStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}