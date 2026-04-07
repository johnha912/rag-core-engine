package com.ragcore.adapter;

import com.ragcore.model.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoryboardAdapterTest {

  private StoryboardAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new StoryboardAdapter();
  }

  @Test
  void supports_storyboardFile_returnsTrue() {
    assertTrue(adapter.supports("film.storyboard"));
  }

  @Test
  void supports_txtFile_returnsTrue() {
    assertTrue(adapter.supports("board.txt"));
  }

  @Test
  void supports_pdfFile_returnsFalse() {
    assertFalse(adapter.supports("board.pdf"));
  }

  @Test
  void supports_nullFileName_returnsFalse() {
    assertFalse(adapter.supports(null));
  }

  @Test
  void getDomainName_returnsStoryboard() {
    assertEquals("Storyboard", adapter.getDomainName());
  }

  @Test
  void parse_withShotMarkers_returnsChunksPerShot() throws Exception {
    String content = """
                SHOT 1 - CLOSE UP
                Camera zooms into John's face.
                Action: John looks surprised.

                SHOT 2 - WIDE SHOT
                Mary enters the room from the left.
                Action: Mary drops her bag.
                """;

    InputStream is = new ByteArrayInputStream(
        content.getBytes(StandardCharsets.UTF_8));
    List<Chunk> chunks = adapter.parse(is, "test.storyboard");

    assertEquals(2, chunks.size());
    assertTrue(chunks.get(0).getContent().contains("SHOT 1"));
    assertTrue(chunks.get(1).getContent().contains("SHOT 2"));
    assertEquals("CLOSE UP", chunks.get(0).getMetadata().get("angle"));
    assertEquals("WIDE SHOT", chunks.get(1).getMetadata().get("angle"));
    assertEquals("1", chunks.get(0).getMetadata().get("shotNumber"));
  }

  @Test
  void parse_noShotMarkers_returnsSingleChunk() throws Exception {
    String content = "This is a storyboard without shot markers.";
    InputStream is = new ByteArrayInputStream(
        content.getBytes(StandardCharsets.UTF_8));
    List<Chunk> chunks = adapter.parse(is, "test.txt");

    assertEquals(1, chunks.size());
  }

  @Test
  void parse_emptyContent_returnsEmptyList() throws Exception {
    InputStream is = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
    List<Chunk> chunks = adapter.parse(is, "test.storyboard");
    assertTrue(chunks.isEmpty());
  }

  @Test
  void parse_nullInputStream_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(null, "test.storyboard"));
  }
}