package com.ragcore.adapter;

import com.ragcore.adapter.domain.film.ScriptAdapter;
import com.ragcore.adapter.domain.film.ScriptSplitter;
import com.ragcore.adapter.domain.film.ScriptTextCleaner;
import com.ragcore.model.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptAdapterTest {

  private ScriptAdapter adapter;
  private ScriptTextCleaner cleaner;
  private ScriptSplitter splitter;

  @BeforeEach
  void setUp() {
    cleaner = new ScriptTextCleaner();
    splitter = new ScriptSplitter();
    adapter = new ScriptAdapter(cleaner, splitter);
  }

  @Test
  void supports_mdFile_returnsTrue() {
    assertTrue(adapter.supports("script.md"));
  }

  @Test
  void supports_docxFile_returnsTrue() {
    assertTrue(adapter.supports("script.docx"));
  }

  @Test
  void supports_pdfFile_returnsFalse() {
    assertFalse(adapter.supports("script.pdf"));
  }

  @Test
  void supports_nullFileName_returnsFalse() {
    assertFalse(adapter.supports(null));
  }

  @Test
  void getDomainName_returnsFilmScript() {
    assertEquals("Film Script", adapter.getDomainName());
  }

  @Test
  void parse_mdWithScenes_returnsChunksPerScene() throws Exception {
    String script = """
                INT. COFFEE SHOP - DAY
                John walks in and sits down.
                JOHN: I need to talk to you.

                EXT. STREET - NIGHT
                Mary walks alone under the rain.
                """;

    InputStream is = new ByteArrayInputStream(
        script.getBytes(StandardCharsets.UTF_8));

    List<Chunk> chunks = adapter.parse(is, "test.md");

    assertEquals(2, chunks.size());
    assertTrue(chunks.get(0).getContent().contains("INT. COFFEE SHOP"));
    assertTrue(chunks.get(1).getContent().contains("EXT. STREET"));
    assertEquals("test.md", chunks.get(0).getSource());
    assertEquals("1", chunks.get(0).getMetadata().get("sceneIndex"));
    assertEquals("2", chunks.get(1).getMetadata().get("sceneIndex"));
  }

  @Test
  void parse_mdNoScenes_returnsFallbackChunks() throws Exception {
    String script = "This is a script without scene headings.\n\nSecond paragraph here.";
    InputStream is = new ByteArrayInputStream(
        script.getBytes(StandardCharsets.UTF_8));

    List<Chunk> chunks = adapter.parse(is, "test.md");

    assertFalse(chunks.isEmpty());
  }

  @Test
  void parse_emptyContent_returnsEmptyList() throws Exception {
    InputStream is = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
    List<Chunk> chunks = adapter.parse(is, "test.md");
    assertTrue(chunks.isEmpty());
  }

  @Test
  void parse_nullInputStream_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(null, "test.md"));
  }
}